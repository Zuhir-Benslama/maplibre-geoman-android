package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.FeatureStoreRenderer
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feature data class holding feature information
 */
data class FeatureData(
    val id: String,
    val sourceName: String,
    val feature: Feature,
    val properties: Map<String, Any?> = emptyMap(),
    val shape: FeatureShape? = null,
) {
    val geometry: Geometry get() = feature.geometry

    /**
     * A copy that shares no mutable collections with the original: both
     * property-map containers are recreated, so later mutation of either
     * instance's maps cannot leak into the other. Leaf values are shared and
     * assumed immutable (JSON-style primitives, lists, and maps), matching
     * what the store and GeoJSON codec produce.
     */
    fun deepCopy(): FeatureData = copy(
        feature = feature.copy(properties = HashMap(feature.properties)),
        properties = HashMap(properties),
    )
}

/**
 * Source names for different feature types
 */
object FeatureSources {
    const val MARKER = GeomanCoreConstants.SOURCE_MARKERS
    const val LINE = GeomanCoreConstants.SOURCE_LINES
    const val POLYGON = GeomanCoreConstants.SOURCE_POLYGONS
    const val CIRCLE = GeomanCoreConstants.SOURCE_CIRCLES
    const val RECTANGLE = GeomanCoreConstants.SOURCE_RECTANGLES
    const val CIRCLE_MARKER = GeomanCoreConstants.SOURCE_CIRCLE_MARKERS
    const val EDIT = GeomanCoreConstants.SOURCE_EDIT
    const val HELPER = GeomanCoreConstants.SOURCE_HELPER
    const val SNAP_GUIDES = "gm_snap_guides"
}

/**
 * Features manager for handling GeoJSON features.
 *
 * Thread safety: all mutable state is guarded by `this` lock via
 * [synchronized]. Map adapter calls are performed *outside* the lock to
 * avoid holding it during I/O or re-entrant callbacks.
 *
 * Rendering: source creation is applied synchronously (layers must exist
 * before the next frame); subsequent updates for an existing source are
 * coalesced through [SourceUpdateManager] so drag-frame edits produce one
 * `setData` per debounce window instead of one per frame. Call
 * [flushPendingUpdates] when the final state must land immediately, and
 * [shutdown] on teardown.
 */
class Features(
    private val geoman: Geoman? = null,
    updateScope: CoroutineScope? = null,
    debounceMs: Long = SourceUpdateManager.DEFAULT_DEBOUNCE_MS,
) {
    private val styler = FeatureLayerStyler(geoman)

    // Guarded by `this` — use plain HashMap since we always hold the lock
    private val featuresMap = HashMap<String, MutableMap<String, FeatureData>>()

    // Parent-child registry (web parity: FeatureData.parent/children).
    // Guarded by `this` like the store itself.
    private val relationships = FeatureRelationships()

    private val _featuresFlow = MutableStateFlow<Map<String, Map<String, FeatureData>>>(emptyMap())

    val featuresFlow: StateFlow<Map<String, Map<String, FeatureData>>> = _featuresFlow.asStateFlow()

    @Volatile
    private var mapAdapter: BaseMapAdapter<*>? = null

    @Volatile
    private var renderer: FeatureStoreRenderer? = null

    private val updateScope: CoroutineScope = updateScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ownsUpdateScope = updateScope == null

    private val updateManager = SourceUpdateManager(
        applyUpdate = { sourceName, collection -> applySourceUpdate(sourceName, collection) },
        scope = this.updateScope,
        debounceMs = debounceMs,
    )

    fun init(adapter: BaseMapAdapter<*>? = null) {
        mapAdapter = adapter
        renderer = adapter
    }

    /**
     * Initialize with a renderer only; screen-coordinate queries stay
     * unavailable. Used by tests.
     */
    fun init(renderer: FeatureStoreRenderer?) {
        renderer?.let { mapAdapter = it as? BaseMapAdapter<*> }
        this.renderer = renderer
    }

    fun getAllFeatures(): Map<String, Map<String, FeatureData>> = synchronized(this) {
        featuresMap.mapValues { it.value.toMap() }
    }

    fun getFeatures(sourceName: String): Map<String, FeatureData> = synchronized(this) {
        featuresMap[sourceName]?.toMap() ?: emptyMap()
    }

    fun getFeature(sourceName: String, featureId: String): FeatureData? = synchronized(this) {
        featuresMap[sourceName]?.get(featureId)
    }

    fun addFeature(featureData: FeatureData) {
        val sourcesToSync = synchronized(this) {
            val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { HashMap() }
            sourceFeatures[featureData.id] = featureData
            updateFeaturesFlow()
            listOf(featureData.sourceName)
        }
        sourcesToSync.forEach { syncSourceToMap(it) }
    }

    /**
     * Add a GeoJSON feature after structural validation.
     *
     * A missing feature ID is generated before validation, so only explicitly
     * supplied invalid IDs (blank, oversized) are rejected.
     *
     * @throws IllegalArgumentException when [PropertyValidators.validateFeature]
     * reports errors (non-finite coordinates, out-of-range latitudes, unclosed
     * polygon rings, malformed IDs). The feature is not stored in that case.
     */
    fun addGeoJsonFeature(feature: Feature, sourceName: String = FeatureSources.POLYGON): FeatureData {
        val featureId = feature.id ?: generateFeatureId()
        val resolved = feature.copy(id = featureId)

        val result = PropertyValidators.validateFeature(resolved)
        require(result.isValid) {
            "Invalid GeoJSON feature: ${result.errors.joinToString("; ")}"
        }

        val featureData = FeatureData(
            id = featureId,
            sourceName = sourceName,
            feature = resolved,
            properties = feature.properties.toMutableMap(),
            shape = FeatureShape.fromSourceName(sourceName),
        )
        addFeature(featureData)
        return featureData
    }

    fun updateFeature(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData) {
        val shouldSync = synchronized(this) {
            featuresMap[sourceName]?.get(featureId)?.let { existingFeature ->
                val updatedFeature = update(existingFeature)
                featuresMap[sourceName]?.put(featureId, updatedFeature)
                updateFeaturesFlow()
                true
            } ?: false
        }
        if (shouldSync) {
            syncSourceToMap(sourceName)
        }
    }

    /**
     * Remove a feature; its descendants (transitive children) are removed
     * with it. Returns the removed feature, or null when unknown.
     */
    fun removeFeature(sourceName: String, featureId: String): FeatureData? {
        val sourcesToSync: List<String>
        val removedFeature: FeatureData?
        synchronized(this) {
            removedFeature = featuresMap[sourceName]?.remove(featureId)
            if (featuresMap[sourceName]?.isEmpty() == true) {
                featuresMap.remove(sourceName)
            }

            val cascadeIds = getDescendantFeatureIds(featureId) + featureId
            sourcesToSync = cascadeIds.mapNotNull { id ->
                val owningSource = featuresMap.entries.firstOrNull { entry -> id in entry.value }?.key
                if (owningSource != null) featuresMap[owningSource]?.remove(id)
                owningSource
            }
            cascadeIds.forEach { relationships.detach(it) }
            updateFeaturesFlow()
        }
        (sourcesToSync + sourceName).distinct().forEach { syncSourceToMap(it) }
        return removedFeature
    }

    fun clearSource(sourceName: String) {
        val removedIds: List<String> = synchronized(this) {
            val ids = featuresMap.remove(sourceName)?.keys.orEmpty()
            // Detach within the same critical section so no reader can observe
            // features that are gone while their parent-child links remain.
            ids.forEach { relationships.detach(it) }
            updateFeaturesFlow()
            ids.toList()
        }
        syncSourceToMap(sourceName)
    }

    fun clearAll() {
        val sourceNames = synchronized(this) {
            val names = featuresMap.keys.toList()
            featuresMap.clear()
            relationships.clear()
            updateFeaturesFlow()
            names
        }
        sourceNames.forEach { syncSourceToMap(it) }
    }

    fun reSyncAll() {
        val sourceNames = synchronized(this) { featuresMap.keys.toList() }
        sourceNames.forEach { syncSourceToMap(it) }
    }

    fun getFeaturesInBounds(bounds: List<LngLat>, sourceNames: List<String>? = null): List<FeatureData> {
        require(bounds.isNotEmpty()) { "bounds must contain at least one coordinate" }
        return synchronized(this) {
            val sources = sourceNames ?: featuresMap.keys.toList()
            val boundsBbox = GeometryUtils.bbox(bounds)
            sources.flatMap { sourceName ->
                featuresMap[sourceName]?.values?.filter { feature ->
                    featureBboxIntersects(feature.geometry, boundsBbox)
                } ?: emptyList()
            }
        }
    }

    fun getFeaturesAtPoint(point: ScreenPoint, sourceNames: List<String>? = null): List<FeatureData> {
        val adapter = mapAdapter ?: return emptyList()
        val sources = synchronized(this) { sourceNames ?: featuresMap.keys.toList() }
        return adapter.queryFeaturesByScreenCoordinates(point, sources)
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

            requireFeatureExists(childId, "child")
            requireFeatureExists(parentId, "parent")
            // Walking up from parentId must never reach childId, otherwise
            // linking would close a cycle in the ancestry chain.
            require(!relationships.isAncestorOrSelf(childId, parentId)) {
                "linking $childId to $parentId would create a cycle"
            }

            relationships.link(childId, parentId)
        }
    }

    /** The registered parent of [featureId], or null when it has none. */
    fun getParentFeatureId(featureId: String): String? = synchronized(this) {
        relationships.parentIdOf(featureId)
    }

    /** Direct children of [parentId]; empty when it has none. */
    fun getChildFeatureIds(parentId: String): Set<String> = synchronized(this) {
        relationships.childrenOf(parentId)
    }

    /** All descendants of [parentId], breadth-first, excluding the parent itself. */
    fun getDescendantFeatureIds(parentId: String): Set<String> = synchronized(this) {
        relationships.descendantsOf(parentId)
    }

    private fun featureBboxIntersects(geometry: Geometry, boundsBbox: List<Double>): Boolean {
        val geometryBbox = GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(geometry))
        return geometryBbox[0] <= boundsBbox[2] && geometryBbox[2] >= boundsBbox[0] &&
            geometryBbox[1] <= boundsBbox[3] && geometryBbox[3] >= boundsBbox[1]
    }

    private fun requireFeatureExists(featureId: String, role: String) {
        require(featuresMap.any { entry -> entry.value.containsKey(featureId) }) {
            "unknown $role feature id: $featureId"
        }
    }

    /**
     * Apply every pending debounced source update immediately.
     */
    fun flushPendingUpdates() {
        updateManager.flushAll()
    }

    /**
     * Flush pending updates and stop the internal update scope. The store
     * remains usable for in-memory queries, but no further map syncs occur.
     */
    fun shutdown() {
        updateManager.flushAll()
        if (ownsUpdateScope) {
            updateScope.cancel()
        }
        renderer = null
    }

    private fun buildSourceCollection(sourceName: String): FeatureCollection {
        val sourceFeatures = synchronized(this) {
            featuresMap[sourceName]?.toMap() ?: emptyMap()
        }
        return FeatureCollection(
            features = sourceFeatures.values.map { it.feature }.toList(),
        )
    }

    private fun syncSourceToMap(sourceName: String) {
        val target = renderer ?: return
        val collection = buildSourceCollection(sourceName)

        // Source creation must be synchronous so layers are registered before
        // the next frame; existing sources get coalesced debounced updates.
        if (target.getSource(sourceName) == null) {
            applySourceUpdate(sourceName, collection)
        } else {
            updateManager.schedule(sourceName, collection)
        }
    }

    private fun applySourceUpdate(sourceName: String, collection: FeatureCollection) {
        val target = renderer ?: return

        val existingSource = target.getSource(sourceName)
        if (existingSource != null) {
            existingSource.setData(collection)
        } else {
            target.addSource(sourceName, collection)
            styler.addRenderingLayersForSource(sourceName, target)
        }
    }

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }

    private fun generateFeatureId(): String = "feature_${java.util.UUID.randomUUID()}"
}
