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
@Suppress("TooManyFunctions") // Bounding-box cache helpers are cohesive internals
class InMemoryFeatureStore : FeatureStore {

    // Guarded by `this` — use plain HashMap since we always hold the lock
    private val featuresMap = HashMap<String, MutableMap<String, FeatureData>>()

    // Reverse id -> source index maintained alongside [featuresMap] so cascade
    // removal can locate a descendant's source in O(1) instead of rescanning
    // every source. Feature ids are globally unique (generated collision-free),
    // so each id maps to exactly one source. Guarded by `this`.
    private val featureSourceIndex = HashMap<String, String>()

    // Parent-child registry (web parity: FeatureData.parent/children).
    // Guarded by `this` like the store itself.
    private val relationships = FeatureRelationships()

    // Cached per-feature bounding boxes (degrees) keyed by source, used to
    // avoid recomputing the full-coordinate bbox on every bounds query.
    // Guarded by `this`; invalidated on every geometry-affecting mutation.
    private val bboxCache = HashMap<String, MutableMap<String, List<Double>>>()

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
                    val geometryBbox = bboxOf(feature)
                    geometryBbox[0] <= boundsBbox[2] && geometryBbox[2] >= boundsBbox[0] &&
                        geometryBbox[1] <= boundsBbox[3] && geometryBbox[3] >= boundsBbox[1]
                } ?: emptyList()
            }
        }
    }

    private fun bboxOf(feature: FeatureData): List<Double> {
        val bySource = bboxCache.getOrPut(feature.sourceName) { HashMap() }
        return bySource.getOrPut(feature.id) {
            GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(feature.geometry))
        }
    }

    private fun cacheBbox(feature: FeatureData) {
        val bySource = bboxCache.getOrPut(feature.sourceName) { HashMap() }
        bySource[feature.id] = GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(feature.geometry))
    }

    /**
     * Store [featureData] under its source. Returns the affected source name so
     * the caller can sync it to the map.
     */
    fun add(featureData: FeatureData): String = synchronized(this) {
        val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { HashMap() }
        sourceFeatures[featureData.id] = featureData
        featureSourceIndex[featureData.id] = featureData.sourceName
        cacheBbox(featureData)
        updateFeaturesFlow()
        featureData.sourceName
    }

    /**
     * Store [featureDataList] as one atomic batch: a single [featuresFlow]
     * snapshot is emitted and every source index entry is maintained in place.
     * Returns the distinct affected source names so the caller can sync each
     * of them exactly once.
     */
    fun addAll(featureDataList: List<FeatureData>): List<String> = synchronized(this) {
        if (featureDataList.isEmpty()) return emptyList()
        val touchedSources = LinkedHashSet<String>()
        featureDataList.forEach { featureData ->
            val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { HashMap() }
            sourceFeatures[featureData.id] = featureData
            featureSourceIndex[featureData.id] = featureData.sourceName
            cacheBbox(featureData)
            touchedSources.add(featureData.sourceName)
        }
        updateFeaturesFlow()
        touchedSources.toList()
    }

    /**
     * Replace the stored feature with the result of applying [update]; returns
     * true when a feature existed and was replaced, false for unknown ids (the
     * update lambda is not invoked in that case).
     *
     * The [update] lambda runs *outside* the store monitor so callers that
     * re-enter the store (or perform slow work) cannot deadlock the critical
     * section.
     */
    fun update(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData): Boolean {
        val existingFeature = synchronized(this) {
            featuresMap[sourceName]?.get(featureId)
        } ?: return false

        val updatedFeature = update(existingFeature)

        return synchronized(this) {
            // Re-check the feature still exists; it may have been removed while
            // the update lambda ran outside the lock.
            if (featuresMap[sourceName]?.containsKey(featureId) == true) {
                featuresMap[sourceName]?.put(featureId, updatedFeature)
                cacheBbox(updatedFeature)
                updateFeaturesFlow()
                true
            } else {
                false
            }
        }
    }

    /**
     * Remove the feature [featureId] from [sourceName]; its descendants
     * (transitive children), which may live in other sources, are removed with
     * it. Returns the removed feature (or null when unknown) plus every source
     * that lost features.
     */
    fun remove(sourceName: String, featureId: String): FeatureRemoval = synchronized(this) {
        val removed = featuresMap[sourceName]?.remove(featureId)
        featureSourceIndex.remove(featureId)
        if (featuresMap[sourceName]?.isEmpty() == true) {
            featuresMap.remove(sourceName)
        }

        // Cascade removal of all descendants. The parent, its children, and
        // their transitively-linked descendants may live across sources; the
        // maintained id->source index locates each descendant in O(1).
        val cascadeIds = relationships.descendantsOf(featureId)
        val sourcesToSync = cascadeIds.mapNotNull { id ->
            val owningSource = featureSourceIndex.remove(id) ?: return@mapNotNull null
            featuresMap[owningSource]?.remove(id)
            if (featuresMap[owningSource]?.isEmpty() == true) {
                featuresMap.remove(owningSource)
            }
            owningSource
        }

        cascadeIds.forEach { relationships.detach(it) }
        relationships.detach(featureId)
        if (removed != null) {
            bboxCache.remove(sourceName)
        }
        sourcesToSync.forEach { bboxCache.remove(it) }
        updateFeaturesFlow()
        FeatureRemoval(removed, sourcesToSync)
    }

    /** Remove an entire source bucket. Parent-child links of its features are cleared. */
    fun clearSource(sourceName: String) {
        synchronized(this) {
            val removedIds = featuresMap.remove(sourceName)?.keys.orEmpty()
            // Detach within the same critical section so no reader can observe
            // features that are gone while their parent-child links remain.
            removedIds.forEach {
                relationships.detach(it)
                featureSourceIndex.remove(it)
            }
            bboxCache.remove(sourceName)
            updateFeaturesFlow()
        }
    }

    /** Drop every feature and every parent-child link. Returns the touched sources. */
    fun clearAll(): List<String> = synchronized(this) {
        val sourceNames = featuresMap.keys.toList()
        featuresMap.clear()
        featureSourceIndex.clear()
        relationships.clear()
        bboxCache.clear()
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
