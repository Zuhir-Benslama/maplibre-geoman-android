package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Rectangle drawing mode
 * First click sets one corner, second click sets opposite corner
 */
class RectangleDrawer(geoman: Geoman) : BaseDraw(geoman) {
    
    override val modeName: String = DrawModeName.RECTANGLE.name
    
    private var firstCorner: LngLat? = null
    private var currentFeature: FeatureData? = null
    
    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickLngLat = LngLat(point.longitude, point.latitude)

        if (firstCorner == null) {
            // First click - set first corner
            firstCorner = clickLngLat
        } else {
            // Second click - create rectangle and finish
            createRectangleFeature(firstCorner!!, clickLngLat)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || firstCorner == null) return

        // Cancel drawing
        firstCorner = null
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    override fun finishDrawing() {
        firstCorner = null
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    private fun createRectangleFeature(corner1: LngLat, corner2: LngLat) {
        // Calculate rectangle corners
        val corners = listOf(
            LngLat(corner1.longitude, corner1.latitude),
            LngLat(corner2.longitude, corner1.latitude),
            LngLat(corner2.longitude, corner2.latitude),
            LngLat(corner1.longitude, corner2.latitude),
            LngLat(corner1.longitude, corner1.latitude) // Close the polygon
        )

        val geometry = Polygon.fromLngLats(listOf(corners))

        val feature = Feature(
            id = "rectangle_${System.currentTimeMillis()}",
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "rectangle_${System.currentTimeMillis()}",
                "shapeType" to "rectangle",
                "corner1" to corner1,
                "corner2" to corner2
            )
        )

        currentFeature = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_RECTANGLES)

        // Capture the feature before launching coroutine to avoid race condition
        val featureToFire = currentFeature
        geomanInstance.scope.launch {
            fireCreateEvent(featureToFire)
        }
    }
}
