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
 * Features manager for handling GeoJSON features.
 *
 * Thread safety: all mutable state is guarded by `this` lock via
 * [synchronized]. Map adapter calls ([syncSourceToMap]) are performed
 * *outside* the lock to avoid holding it during I/O or re-entrant callbacks.
 */
class Features(private val geoman: Geoman? = null) {

    private companion object {
        const val RGB_MASK = 0xFFFFFF
    }

    // Guarded by `this` — use plain HashMap since we always hold the lock
    private val featuresMap = HashMap<String, MutableMap<String, FeatureData>>()
    private val _featuresFlow = MutableStateFlow<Map<String, Map<String, FeatureData>>>(emptyMap())

    val featuresFlow: StateFlow<Map<String, Map<String, FeatureData>>> = _featuresFlow.asStateFlow()

    @Volatile
    private var mapAdapter: BaseMapAdapter<*>? = null

    fun init(adapter: BaseMapAdapter<*>? = null) {
        mapAdapter = adapter
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

    fun addGeoJsonFeature(feature: Feature, sourceName: String = FeatureSources.POLYGON): FeatureData {
        val featureId = feature.id ?: generateFeatureId()
        val featureData = FeatureData(
            id = featureId,
            sourceName = sourceName,
            feature = feature.copy(id = featureId),
            properties = feature.properties.toMutableMap(),
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

    fun removeFeature(sourceName: String, featureId: String): FeatureData? {
        val removedFeature = synchronized(this) {
            val removed = featuresMap[sourceName]?.remove(featureId)
            if (featuresMap[sourceName]?.isEmpty() == true) {
                featuresMap.remove(sourceName)
            }
            updateFeaturesFlow()
            removed
        }
        syncSourceToMap(sourceName)
        return removedFeature
    }

    fun clearSource(sourceName: String) {
        synchronized(this) {
            featuresMap.remove(sourceName)
            updateFeaturesFlow()
        }
        syncSourceToMap(sourceName)
    }

    fun clearAll() {
        val sourceNames = synchronized(this) {
            val names = featuresMap.keys.toList()
            featuresMap.clear()
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

    private fun featureBboxIntersects(geometry: Geometry, boundsBbox: List<Double>): Boolean {
        val geometryBbox = GeometryUtils.bbox(GeometryUtils.extractAllCoordinates(geometry))
        return geometryBbox[0] <= boundsBbox[2] && geometryBbox[2] >= boundsBbox[0] &&
            geometryBbox[1] <= boundsBbox[3] && geometryBbox[3] >= boundsBbox[1]
    }

    private fun syncSourceToMap(sourceName: String) {
        val adapter = mapAdapter ?: return
        val sourceFeatures = synchronized(this) {
            featuresMap[sourceName]?.toMap() ?: emptyMap()
        }

        val featureCollection = FeatureCollection(
            features = sourceFeatures.values.map { it.feature }.toList(),
        )

        val existingSource = adapter.getSource(sourceName)
        if (existingSource != null) {
            existingSource.setData(featureCollection)
        } else {
            adapter.addSource(sourceName, featureCollection)
            addRenderingLayersForSource(sourceName, adapter)
        }
    }

    private fun addRenderingLayersForSource(sourceName: String, adapter: BaseMapAdapter<*>) {
        val layerId = when (sourceName) {
            FeatureSources.MARKER -> "${sourceName}_symbol"
            FeatureSources.LINE -> "${sourceName}_line"
            else -> "${sourceName}_stroke"
        }

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
        String.format("#%06X", color.toArgb() and RGB_MASK)

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }

    private fun generateFeatureId(): String = "feature_${java.util.UUID.randomUUID()}"
}
