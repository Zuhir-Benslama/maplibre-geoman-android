package com.geoman.maplibre.geoman.core.features

import androidx.compose.ui.graphics.toArgb
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.LayerType
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import com.geoman.maplibre.geoman.utils.GeometryUtils
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
) {
    val geometry: Geometry get() = feature.geometry
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
    const val CIRCLE_MARKER = "gm_circle_markers"
    const val EDIT = GeomanCoreConstants.SOURCE_EDIT
    const val HELPER = GeomanCoreConstants.SOURCE_HELPER
    const val SNAP_GUIDES = "gm_snap_guides"
}

/**
 * Features manager for handling GeoJSON features
 */
class Features(private val geoman: Geoman? = null) {
    private val featuresMap = mutableMapOf<String, MutableMap<String, FeatureData>>()
    private val _featuresFlow = MutableStateFlow<Map<String, Map<String, FeatureData>>>(emptyMap())

    private companion object {
        const val COLOR_MASK = 0xFFFFFF
    }

    val featuresFlow: StateFlow<Map<String, Map<String, FeatureData>>> = _featuresFlow.asStateFlow()

    // Map adapter reference for rendering
    private var mapAdapter: BaseMapAdapter<*>? = null

    /**
     * Initialize features manager with optional map adapter reference
     */
    fun init(adapter: BaseMapAdapter<*>? = null) {
        mapAdapter = adapter
    }

    /**
     * Get all features
     */
    @Synchronized
    fun getAllFeatures(): Map<String, Map<String, FeatureData>> = featuresMap.toMap()

    /**
     * Get features by source name
     */
    @Synchronized
    fun getFeatures(sourceName: String): Map<String, FeatureData> = featuresMap[sourceName]?.toMap() ?: emptyMap()

    /**
     * Get a specific feature
     */
    @Synchronized
    fun getFeature(sourceName: String, featureId: String): FeatureData? = featuresMap[sourceName]?.get(featureId)

    /**
     * Add a feature
     */
    @Synchronized
    fun addFeature(featureData: FeatureData) {
        val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { mutableMapOf() }
        sourceFeatures[featureData.id] = featureData
        updateFeaturesFlow()
    }

    /**
     * Add a GeoJSON feature
     */
    @Synchronized
    fun addGeoJsonFeature(feature: Feature, sourceName: String = FeatureSources.POLYGON): FeatureData {
        val featureId = feature.id ?: generateFeatureId()
        val featureData = FeatureData(
            id = featureId,
            sourceName = sourceName,
            feature = feature.copy(id = featureId),
            properties = feature.properties.toMutableMap(),
        )
        addFeature(featureData)

        // Sync to map if adapter is available
        syncSourceToMap(sourceName)

        return featureData
    }

    /**
     * Update a feature
     */
    @Synchronized
    fun updateFeature(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData) {
        featuresMap[sourceName]?.get(featureId)?.let { existingFeature ->
            val updatedFeature = update(existingFeature)
            featuresMap[sourceName]?.put(featureId, updatedFeature)
            updateFeaturesFlow()
            syncSourceToMap(sourceName)
        }
    }

    /**
     * Remove a feature
     */
    @Synchronized
    fun removeFeature(sourceName: String, featureId: String): FeatureData? {
        val removedFeature = featuresMap[sourceName]?.remove(featureId)
        if (featuresMap[sourceName]?.isEmpty() == true) {
            featuresMap.remove(sourceName)
        }
        updateFeaturesFlow()
        // Re-sync the source
        syncSourceToMap(sourceName)
        return removedFeature
    }

    /**
     * Remove all features from a source
     */
    @Synchronized
    fun clearSource(sourceName: String) {
        featuresMap.remove(sourceName)
        updateFeaturesFlow()
        syncSourceToMap(sourceName)
    }

    /**
     * Clear all features
     */
    @Synchronized
    fun clearAll() {
        val sourceNames = featuresMap.keys.toList()
        featuresMap.clear()
        updateFeaturesFlow()
        // Re-sync all sources to clear them
        sourceNames.forEach { syncSourceToMap(it) }
    }

    /**
     * Re-sync every in-memory feature to the map.
     * Used after the map style has been replaced, which destroys all style-bound
     * sources and layers created for the previous style.
     */
    @Synchronized
    fun reSyncAll() {
        val sourceNames = featuresMap.keys.toList()
        sourceNames.forEach { syncSourceToMap(it) }
    }

    /**
     * Get features whose bounding box intersects the given bounds
     */
    @Synchronized
    fun getFeaturesInBounds(bounds: List<LngLat>, sourceNames: List<String>? = null): List<FeatureData> {
        require(bounds.isNotEmpty()) { "bounds must contain at least one coordinate" }
        val sources = sourceNames ?: featuresMap.keys.toList()
        val boundsBbox = GeometryUtils.bbox(bounds)
        return sources.flatMap { sourceName ->
            featuresMap[sourceName]?.values?.filter { feature ->
                featureBboxIntersects(feature.geometry, boundsBbox)
            } ?: emptyList()
        }
    }

    /**
     * Get features at screen coordinates (delegates to the map adapter query)
     */
    fun getFeaturesAtPoint(point: ScreenPoint, sourceNames: List<String>? = null): List<FeatureData> {
        val adapter = mapAdapter ?: return emptyList()
        val sources = sourceNames ?: featuresMap.keys.toList()
        return adapter.queryFeaturesByScreenCoordinates(point, sources)
    }

    private fun featureBboxIntersects(geometry: Geometry, boundsBbox: List<Double>): Boolean {
        val geometryBbox = GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(geometry))
        return geometryBbox[0] <= boundsBbox[2] && geometryBbox[2] >= boundsBbox[0] &&
            geometryBbox[1] <= boundsBbox[3] && geometryBbox[3] >= boundsBbox[1]
    }

    /**
     * Sync a source's features to the map
     */
    private fun syncSourceToMap(sourceName: String) {
        val adapter = mapAdapter ?: return
        val sourceFeatures = featuresMap[sourceName] ?: emptyMap()

        // Build FeatureCollection from in-memory features
        val featureCollection = FeatureCollection(
            features = sourceFeatures.values.map { it.feature }.toList(),
        )

        // Create or update the source on the map
        val existingSource = adapter.getSource(sourceName)
        if (existingSource != null) {
            existingSource.setData(featureCollection)
        } else {
            adapter.addSource(sourceName, featureCollection)
            // Also add rendering layers for this source
            addRenderingLayersForSource(sourceName, adapter)
        }
    }

    /**
     * Add rendering layers for a source on the map
     */
    private fun addRenderingLayersForSource(sourceName: String, adapter: BaseMapAdapter<*>) {
        val layerId = when (sourceName) {
            FeatureSources.MARKER -> "${sourceName}_symbol"
            FeatureSources.LINE -> "${sourceName}_line"
            else -> "${sourceName}_stroke"
        }

        // Only add layers once per source
        if (adapter.getLayer(layerId) != null) return

        try {
            adapter.addLayer(buildLayerOptions(sourceName, layerId))
        } catch (e: Exception) {
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
        else -> styles?.rectangle?.width
    }

    private fun toHex(color: androidx.compose.ui.graphics.Color): String =
        String.format("#%06X", color.toArgb() and COLOR_MASK)

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }

    private fun generateFeatureId(): String = "feature_${java.util.UUID.randomUUID()}"
}
