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

        // Update or create the line feature
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
            geomanInstance.scope.launch {
                fireCreateEvent(featureToFire)
            }
        }

        coordinates.clear()
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    private fun updateLineFeature() {
        if (coordinates.isEmpty()) return

        val geometry = LineString.fromLngLats(coordinates)

        val feature = Feature(
            id = "line_${System.currentTimeMillis()}",
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "line_${System.currentTimeMillis()}",
                "shapeType" to "line"
            )
        )

        currentFeature?.let {
            geomanInstance.features.removeFeature(GeomanCoreConstants.SOURCE_LINES, it.id)
        }

        currentFeature = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_LINES)
    }
}
