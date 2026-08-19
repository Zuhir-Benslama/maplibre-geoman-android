package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Line drawing mode
 */
class LineDrawer(geoman: Geoman) : BaseDraw(geoman) {

    override val modeName: String = DrawModeName.LINE.name

    private val coordinates = mutableListOf<LngLat>()
    private var currentFeature: FeatureData? = null

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        coordinates.add(LngLat(point.longitude, point.latitude))

        // Update or create the line feature (kept stable across clicks)
        updateLineFeature()
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || coordinates.size < 2) return

        finishDrawing()
    }

    override fun finishDrawing() {
        if (coordinates.size >= 2 && currentFeature != null) {
            // Capture the feature before launching coroutine to avoid race condition
            val featureToFire = currentFeature
            geoman.scope.launch {
                fireCreateEvent(featureToFire)
            }
        }

        coordinates.clear()
        currentFeature = null
        geoman.disableMode(modeType, modeName)
    }

    override fun disable() {
        // Remove the uncommitted partial line if the mode is cancelled mid-draw
        currentFeature?.let {
            geoman.features.removeFeature(GeomanCoreConstants.SOURCE_LINES, it.id)
        }
        currentFeature = null
        coordinates.clear()
        super.disable()
    }

    private fun updateLineFeature() {
        if (coordinates.isEmpty()) return

        val geometry = LineString.fromLngLats(coordinates)
        val existing = currentFeature

        if (existing != null) {
            val updated = existing.copy(feature = existing.feature.copy(geometry = geometry))
            geoman.features.updateFeature(GeomanCoreConstants.SOURCE_LINES, existing.id) { updated }
            currentFeature = updated
        } else {
            val now = System.currentTimeMillis()
            val feature = Feature(
                id = "line_$now",
                geometry = geometry,
                properties = mapOf(
                    GeomanCoreConstants.FEATURE_ID_PROPERTY to "line_$now",
                    "shapeType" to "line",
                ),
            )
            currentFeature = geoman.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_LINES)
        }
    }
}
