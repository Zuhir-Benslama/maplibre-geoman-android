package com.geoman.maplibre.geoman.core.features

import androidx.compose.ui.graphics.toArgb
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.FeatureStoreRenderer
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.LayerType
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
     * A copy that shares no mutable collections with the original: the
     * property maps are duplicated. Leaf values are assumed immutable
     * (JSON-style primitives, lists, and maps), matching what the store and
     * GeoJSON codec produce.
     */
    fun deepCopy(): FeatureData = copy(
        feature = feature.copy(properties = feature.properties.mapValues { (_, value) -> value }),
        properties = properties.toMutableMap(),
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

    private companion object {
        const val RGB_MASK = 0xFFFFFF
        const val DEFAULT_CIRCLE_MARKER_RADIUS = 10.0f
    }

    // Guarded by `this` — use plain HashMap since we always hold the lock
    private val featuresMap = HashMap<String, MutableMap<String, FeatureData>>()

    // Parent-child registry (web parity: FeatureData.parent/children).
    // childId -> parentId, and the reverse index. Guarded by `this`.
    private val childToParent = HashMap<String, String>()
    private val parentToChildren = HashMap<String, MutableSet<String>>()

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
            cascadeIds.forEach { detachParent(it) }
            updateFeaturesFlow()
        }
        (sourcesToSync + sourceName).distinct().forEach { syncSourceToMap(it) }
        return removedFeature
    }

    fun clearSource(sourceName: String) {
        val removedIds: List<String> = synchronized(this) {
            val ids = featuresMap.remove(sourceName)?.keys.orEmpty()
            updateFeaturesFlow()
            ids.toList()
        }
        synchronized(this) { removedIds.forEach { detachParent(it) } }
        syncSourceToMap(sourceName)
    }

    fun clearAll() {
        val sourceNames = synchronized(this) {
            val names = featuresMap.keys.toList()
            featuresMap.clear()
            childToParent.clear()
            parentToChildren.clear()
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
                detachParent(childId)
                return
            }

            requireFeatureExists(childId, "child")
            requireFeatureExists(parentId, "parent")
            // Walking up from parentId must never reach childId, otherwise
            // linking would close a cycle in the ancestry chain.
            require(!isAncestor(childId, parentId)) {
                "linking $childId to $parentId would create a cycle"
            }

            detachParent(childId)
            childToParent[childId] = parentId
            parentToChildren.getOrPut(parentId) { mutableSetOf() }.add(childId)
        }
    }

    /** The registered parent of [featureId], or null when it has none. */
    fun getParentFeatureId(featureId: String): String? = synchronized(this) {
        childToParent[featureId]
    }

    /** Direct children of [parentId]; empty when it has none. */
    fun getChildFeatureIds(parentId: String): Set<String> = synchronized(this) {
        parentToChildren[parentId]?.toSet() ?: emptySet()
    }

    /** All descendants of [parentId], breadth-first, excluding the parent itself. */
    fun getDescendantFeatureIds(parentId: String): Set<String> = synchronized(this) {
        val descendants = mutableSetOf<String>()
        val queue = ArrayDeque(parentToChildren[parentId].orEmpty())
        while (queue.isNotEmpty()) {
            val childId = queue.removeFirst()
            if (descendants.add(childId)) {
                queue.addAll(parentToChildren[childId].orEmpty())
            }
        }
        descendants
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

    /** True when [candidateAncestor] is [featureId] itself or a transitive parent. */
    private fun isAncestor(candidateAncestor: String, featureId: String): Boolean {
        var current: String? = featureId
        while (current != null) {
            if (current == candidateAncestor) return true
            current = childToParent[current]
        }
        return false
    }

    private fun detachParent(childId: String) {
        val previousParent = childToParent.remove(childId) ?: return
        parentToChildren[previousParent]?.let { children ->
            children.remove(childId)
            if (children.isEmpty()) parentToChildren.remove(previousParent)
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
            addRenderingLayersForSource(sourceName, target)
        }
    }

    private fun addRenderingLayersForSource(sourceName: String, target: FeatureStoreRenderer) {
        val layerId = when (sourceName) {
            FeatureSources.MARKER -> "${sourceName}_symbol"
            FeatureSources.CIRCLE_MARKER -> "${sourceName}_circle"
            FeatureSources.LINE -> "${sourceName}_line"
            else -> "${sourceName}_stroke"
        }

        if (target.getLayer(layerId) != null) return

        try {
            target.addLayer(buildLayerOptions(sourceName, layerId))
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            GeomanLogger.w("Features", "Error adding layer $layerId: ${e.message}")
        }
    }

    private fun buildLayerOptions(sourceName: String, layerId: String): LayerOptions {
        if (sourceName == FeatureSources.MARKER) {
            return LayerOptions(
                id = layerId,
                type = LayerType.SYMBOL,
                source = sourceName,
                layout = mapOf(
                    "icon-image" to "default-marker",
                    "icon-size" to 0.5f,
                    "icon-allow-overlap" to true,
                ),
            )
        }

        if (sourceName == FeatureSources.CIRCLE_MARKER) {
            val styles = geoman?.options?.layerStyles
            val circleMarkerStyle = styles?.circleMarker
            val fillColor = resolveLineColor(styles, sourceName)
                ?: circleMarkerStyle?.fillColor?.let { toHex(it) }
                ?: resolveDefaults(sourceName).color

            return LayerOptions(
                id = layerId,
                type = LayerType.CIRCLE,
                source = sourceName,
                paint = mapOf<String, Any>(
                    "circle-radius" to (circleMarkerStyle?.radius ?: DEFAULT_CIRCLE_MARKER_RADIUS),
                    "circle-color" to fillColor,
                    "circle-opacity" to (circleMarkerStyle?.opacity ?: 1.0f),
                    "circle-stroke-width" to (circleMarkerStyle?.width ?: 2.0f),
                    "circle-stroke-color" to (circleMarkerStyle?.color?.let { toHex(it) } ?: "#FFFFFF"),
                ),
            )
        }

        val (defaultColor, defaultWidth) = resolveDefaults(sourceName)
        val layerStyles = geoman?.options?.layerStyles
        val color = resolveLineColor(layerStyles, sourceName) ?: defaultColor
        val width = resolveLineWidth(layerStyles, sourceName) ?: defaultWidth

        return LayerOptions(
            id = layerId,
            type = LayerType.LINE,
            source = sourceName,
            paint = mapOf(
                "line-color" to color,
                "line-width" to width,
            ),
        )
    }

    private data class LayerDefaults(val color: String, val width: Float)

    private fun resolveDefaults(sourceName: String) = when (sourceName) {
        FeatureSources.LINE -> LayerDefaults("#3498db", 3f)
        FeatureSources.POLYGON -> LayerDefaults("#8e44ad", 2f)
        FeatureSources.CIRCLE -> LayerDefaults("#e74c3c", 2f)
        FeatureSources.CIRCLE_MARKER -> LayerDefaults("#3498db", 2f)
        else -> LayerDefaults("#2ecc71", 2f)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun resolveLineColor(
        styles: com.geoman.maplibre.geoman.core.options.LayerStyles?,
        sourceName: String,
    ): String? {
        val color = when (sourceName) {
            FeatureSources.LINE -> styles?.line?.color
            FeatureSources.POLYGON -> styles?.polygon?.color
            FeatureSources.CIRCLE -> styles?.circle?.color
            FeatureSources.CIRCLE_MARKER -> styles?.circleMarker?.fillColor
            else -> styles?.rectangle?.color
        }
        return color?.let { toHex(it) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun resolveLineWidth(
        styles: com.geoman.maplibre.geoman.core.options.LayerStyles?,
        sourceName: String,
    ): Float? = when (sourceName) {
        FeatureSources.LINE -> styles?.line?.width
        FeatureSources.POLYGON -> styles?.polygon?.width
        FeatureSources.CIRCLE -> styles?.circle?.width
        FeatureSources.CIRCLE_MARKER -> styles?.circleMarker?.width
        else -> styles?.rectangle?.width
    }

    private fun toHex(color: androidx.compose.ui.graphics.Color): String =
        String.format("#%06X", color.toArgb() and RGB_MASK)

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }

    private fun generateFeatureId(): String = "feature_${java.util.UUID.randomUUID()}"
}
