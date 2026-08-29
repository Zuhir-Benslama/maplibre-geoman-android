package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Outcome of removing a feature from the store.
 *
 * [removed] is the feature that was removed, or null when it was unknown. The
 * owner of the store uses [sourcesToSync] to re-render every source that lost
 * a feature (the feature itself plus any cascaded descendants).
 */
data class FeatureRemoval(val removed: FeatureData?, val sourcesToSync: List<String>)

/**
 * In-memory feature store guarded by a single monitor.
 *
 * All mutable state — the source-bucketed feature map, the parent-child
 * registry and the emitted [featuresFlow] snapshot — is guarded by `this`;
 * every mutation is performed inside a `synchronized(this)` block and emits a
 * fresh snapshot before returning. Read queries return copies so callers never
 * observe a store mid-mutation.
 *
 * The store is free of map/renderer concerns: mutators return the set of
 * affected source names and let the owning `Features` facade decide when and
 * how to sync them to the map.
 */
class InMemoryFeatureStore : FeatureStore {

    // Guarded by `this` — use plain HashMap since we always hold the lock
    private val featuresMap = HashMap<String, MutableMap<String, FeatureData>>()

    // Parent-child registry (web parity: FeatureData.parent/children).
    // Guarded by `this` like the store itself.
    private val relationships = FeatureRelationships()

    private val _featuresFlow = MutableStateFlow<Map<String, Map<String, FeatureData>>>(emptyMap())

    override val featuresFlow: StateFlow<Map<String, Map<String, FeatureData>>> = _featuresFlow.asStateFlow()

    override fun getAllFeatures(): Map<String, Map<String, FeatureData>> = synchronized(this) {
        featuresMap.mapValues { it.value.toMap() }
    }

    override fun getFeatures(sourceName: String): Map<String, FeatureData> = synchronized(this) {
        featuresMap[sourceName]?.toMap() ?: emptyMap()
    }

    override fun getFeature(sourceName: String, featureId: String): FeatureData? = synchronized(this) {
        featuresMap[sourceName]?.get(featureId)
    }

    override fun getFeaturesInBounds(bounds: List<LngLat>, sourceNames: List<String>?): List<FeatureData> {
        require(bounds.isNotEmpty()) { "bounds must contain at least one coordinate" }
        return synchronized(this) {
            val sources = sourceNames ?: featuresMap.keys.toList()
            val boundsBbox = GeometryUtils.bbox(bounds)
            sources.flatMap { sourceName ->
                featuresMap[sourceName]?.values?.filter { feature ->
                    val geometryBbox = GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(feature.geometry))
                    geometryBbox[0] <= boundsBbox[2] && geometryBbox[2] >= boundsBbox[0] &&
                        geometryBbox[1] <= boundsBbox[3] && geometryBbox[3] >= boundsBbox[1]
                } ?: emptyList()
            }
        }
    }

    /**
     * Store [featureData] under its source. Returns the affected source name so
     * the caller can sync it to the map.
     */
    fun add(featureData: FeatureData): String = synchronized(this) {
        val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { HashMap() }
        sourceFeatures[featureData.id] = featureData
        updateFeaturesFlow()
        featureData.sourceName
    }

    /**
     * Replace the stored feature with the result of applying [update]; returns
     * true when a feature existed and was replaced, false for unknown ids (the
     * update lambda is not invoked in that case).
     */
    fun update(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData): Boolean {
        val shouldSync = synchronized(this) {
            featuresMap[sourceName]?.get(featureId)?.let { existingFeature ->
                val updatedFeature = update(existingFeature)
                featuresMap[sourceName]?.put(featureId, updatedFeature)
                updateFeaturesFlow()
                true
            } ?: false
        }
        return shouldSync
    }

    /**
     * Remove the feature [featureId] from [sourceName]; its descendants
     * (transitive children), which may live in other sources, are removed with
     * it. Returns the removed feature (or null when unknown) plus every source
     * that lost features.
     */
    fun remove(sourceName: String, featureId: String): FeatureRemoval = synchronized(this) {
        val removed = featuresMap[sourceName]?.remove(featureId)
        if (featuresMap[sourceName]?.isEmpty() == true) {
            featuresMap.remove(sourceName)
        }

        // Cascade removal of all descendants. The parent, its children, and
        // their transitively-linked descendants may live across sources, so
        // build a reverse id->source index once and remove each id in place.
        val cascadeIds = relationships.descendantsOf(featureId)
        val sourcesToSync = if (cascadeIds.isNotEmpty()) {
            val idToSource = buildMap {
                featuresMap.forEach { (source, features) ->
                    features.keys.forEach { id ->
                        if (id !in this) this[id] = source
                    }
                }
            }
            cascadeIds.mapNotNull { id ->
                val owningSource = idToSource[id] ?: return@mapNotNull null
                featuresMap[owningSource]?.remove(id)
                if (featuresMap[owningSource]?.isEmpty() == true) {
                    featuresMap.remove(owningSource)
                }
                owningSource
            }
        } else {
            emptyList()
        }

        cascadeIds.forEach { relationships.detach(it) }
        relationships.detach(featureId)
        updateFeaturesFlow()
        FeatureRemoval(removed, sourcesToSync)
    }

    /** Remove an entire source bucket. Parent-child links of its features are cleared. */
    fun clearSource(sourceName: String) {
        synchronized(this) {
            val removedIds = featuresMap.remove(sourceName)?.keys.orEmpty()
            // Detach within the same critical section so no reader can observe
            // features that are gone while their parent-child links remain.
            removedIds.forEach { relationships.detach(it) }
            updateFeaturesFlow()
        }
    }

    /** Drop every feature and every parent-child link. Returns the touched sources. */
    fun clearAll(): List<String> = synchronized(this) {
        val sourceNames = featuresMap.keys.toList()
        featuresMap.clear()
        relationships.clear()
        updateFeaturesFlow()
        sourceNames
    }

    /** Snapshot of every known source name. */
    fun allSourceNames(): List<String> = synchronized(this) {
        featuresMap.keys.toList()
    }

    /**
     * Link [childId] as a child of [parentId] (web parity: helper features
     * belonging to a shape). Pass `null` to clear the link.
     *
     * Both features must exist; linking may not create a cycle.
     *
     * @throws IllegalArgumentException on unknown ids or cyclic links
     */
    fun setFeatureParent(childId: String, parentId: String?) {
        synchronized(this) {
            if (parentId == null) {
                relationships.detach(childId)
                return
            }

            require(featuresMap.any { entry -> entry.value.containsKey(childId) }) {
                "unknown child feature id: $childId"
            }
            require(featuresMap.any { entry -> entry.value.containsKey(parentId) }) {
                "unknown parent feature id: $parentId"
            }
            // Walking up from parentId must never reach childId, otherwise
            // linking would close a cycle in the ancestry chain.
            require(!relationships.isAncestorOrSelf(childId, parentId)) {
                "linking $childId to $parentId would create a cycle"
            }

            relationships.link(childId, parentId)
        }
    }

    override fun getParentFeatureId(featureId: String): String? = synchronized(this) {
        relationships.parentIdOf(featureId)
    }

    override fun getChildFeatureIds(parentId: String): Set<String> = synchronized(this) {
        relationships.childrenOf(parentId)
    }

    override fun getDescendantFeatureIds(parentId: String): Set<String> = synchronized(this) {
        relationships.descendantsOf(parentId)
    }

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }
}
