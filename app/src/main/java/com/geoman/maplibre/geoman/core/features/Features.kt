package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.LayerType
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
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
    val properties: MutableMap<String, Any?> = mutableMapOf(),
) {
    val geometry: Geometry get() = feature.geometry
}

/**
 * Source names for different feature types
 */
object FeatureSources {
    const val MARKER = "gm_markers"
    const val LINE = "gm_lines"
    const val POLYGON = "gm_polygons"
    const val CIRCLE = "gm_circles"
    const val RECTANGLE = "gm_rectangles"
    const val CIRCLE_MARKER = "gm_circle_markers"
    const val EDIT = "gm_edit"
    const val HELPER = "gm_helper"
    const val SNAP_GUIDES = "gm_snap_guides"
}

/**
 * Features manager for handling GeoJSON features
 */
class Features {
    private val featuresMap = mutableMapOf<String, MutableMap<String, FeatureData>>()
    private val _featuresFlow = MutableStateFlow<Map<String, Map<String, FeatureData>>>(emptyMap())

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
    fun getAllFeatures(): Map<String, Map<String, FeatureData>> = featuresMap.toMap()

    /**
     * Get features by source name
     */
    fun getFeatures(sourceName: String): Map<String, FeatureData> = featuresMap[sourceName]?.toMap() ?: emptyMap()

    /**
     * Get a specific feature
     */
    fun getFeature(sourceName: String, featureId: String): FeatureData? = featuresMap[sourceName]?.get(featureId)

    /**
     * Add a feature
     */
    fun addFeature(featureData: FeatureData) {
        val sourceFeatures = featuresMap.getOrPut(featureData.sourceName) { mutableMapOf() }
        sourceFeatures[featureData.id] = featureData
        updateFeaturesFlow()
    }

    /**
     * Add a GeoJSON feature
     */
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
    fun updateFeature(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData) {
        featuresMap[sourceName]?.get(featureId)?.let { existingFeature ->
            val updatedFeature = update(existingFeature)
            featuresMap[sourceName]?.put(featureId, updatedFeature)
            updateFeaturesFlow()
            // Re-sync the source to reflect changes
            syncSourceToMap(sourceName)
        }
    }

    /**
     * Remove a feature
     */
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
    fun clearSource(sourceName: String) {
        featuresMap.remove(sourceName)
        updateFeaturesFlow()
        syncSourceToMap(sourceName)
    }

    /**
     * Clear all features
     */
    fun clearAll() {
        val sourceNames = featuresMap.keys.toList()
        featuresMap.clear()
        updateFeaturesFlow()
        // Re-sync all sources to clear them
        sourceNames.forEach { syncSourceToMap(it) }
    }

    /**
     * Get features within bounds
     */
    fun getFeaturesInBounds(bounds: List<LngLat>, sourceNames: List<String>? = null): List<FeatureData> {
        val sources = sourceNames ?: featuresMap.keys.toList()
        return sources.flatMap { sourceName ->
            featuresMap[sourceName]?.values?.filter { feature ->
                isGeometryInBounds(feature.geometry, bounds)
            } ?: emptyList()
        }
    }

    /**
     * Get features at screen coordinates (requires map adapter query)
     */
    fun getFeaturesAtPoint(
        @Suppress("UNUSED_PARAMETER") point: com.geoman.maplibre.geoman.types.geojson.ScreenPoint,
        @Suppress("UNUSED_PARAMETER") sourceNames: List<String>? = null,
    ): List<FeatureData> {
        // This will be implemented with map adapter query
        return emptyList()
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
        // Only add layers once per source
        if (adapter.getLayer("${sourceName}_fill") != null) return

        when (sourceName) {
            FeatureSources.MARKER -> {
                try {
                    adapter.addLayer(
                        LayerOptions(
                            id = "${sourceName}_symbol",
                            type = LayerType.SYMBOL,
                            source = sourceName,
                            layout = mapOf(
                                "icon-image" to "default-marker",
                                "icon-size" to 0.5f,
                                "icon-allow-overlap" to true,
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("Features", "Error adding marker layer: ${e.message}")
                }
            }

            FeatureSources.LINE -> {
                try {
                    adapter.addLayer(
                        LayerOptions(
                            id = "${sourceName}_line",
                            type = LayerType.LINE,
                            source = sourceName,
                            paint = mapOf(
                                "line-color" to "#3498db",
                                "line-width" to 3f,
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("Features", "Error adding line layer: ${e.message}")
                }
            }

            FeatureSources.POLYGON -> {
                try {
                    // No fill layer - outline only
                    adapter.addLayer(
                        LayerOptions(
                            id = "${sourceName}_stroke",
                            type = LayerType.LINE,
                            source = sourceName,
                            paint = mapOf(
                                "line-color" to "#8e44ad",
                                "line-width" to 2f,
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("Features", "Error adding polygon layers: ${e.message}")
                }
            }

            FeatureSources.CIRCLE -> {
                try {
                    // No fill layer - outline only
                    adapter.addLayer(
                        LayerOptions(
                            id = "${sourceName}_stroke",
                            type = LayerType.LINE,
                            source = sourceName,
                            paint = mapOf(
                                "line-color" to "#e74c3c",
                                "line-width" to 2f,
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("Features", "Error adding circle layers: ${e.message}")
                }
            }

            FeatureSources.RECTANGLE -> {
                try {
                    adapter.addLayer(
                        LayerOptions(
                            id = "${sourceName}_stroke",
                            type = LayerType.LINE,
                            source = sourceName,
                            paint = mapOf(
                                "line-color" to "#2ecc71",
                                "line-width" to 2f,
                            ),
                        ),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("Features", "Error adding rectangle layers: ${e.message}")
                }
            }
        }
    }

    private fun isGeometryInBounds(geometry: Geometry, bounds: List<LngLat>): Boolean = when (geometry) {
        is com.geoman.maplibre.geoman.types.geojson.Point -> {
            val point = geometry.toLngLat()
            bounds.any { it.latitude == point.latitude && it.longitude == point.longitude }
        }

        else -> true
    }

    private fun updateFeaturesFlow() {
        _featuresFlow.value = featuresMap.mapValues { it.value.toMap() }
    }

    private fun generateFeatureId(): String = "feature_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
}
