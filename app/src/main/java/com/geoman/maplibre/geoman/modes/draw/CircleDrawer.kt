package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Circle drawing mode
 * First click sets center, second click sets radius
 */
class CircleDrawer(geoman: Geoman) : BaseDraw(geoman) {

    override val modeName: String = DrawModeName.CIRCLE.name

    private var center: LngLat? = null

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickLngLat = LngLat(point.longitude, point.latitude)
        val c = center

        if (c == null) {
            // First click - set center
            center = clickLngLat
        } else {
            // Second click - set radius and finish
            val radius = GeometryUtils.calculateDistance(c, clickLngLat)
            createCircleFeature(c, radius)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || center == null) return

        // Cancel drawing
        center = null
        geomanInstance.disableMode(modeType, modeName)
    }

    override fun finishDrawing() {
        center = null
        geomanInstance.disableMode(modeType, modeName)
    }

    private fun createCircleFeature(center: LngLat, radius: Double) {
        val circleCoordinates = GeometryUtils.generateCircleCoordinates(center, radius)

        val geometry = Polygon.fromLngLats(listOf(circleCoordinates))

        val now = System.currentTimeMillis()
        val feature = Feature(
            id = "circle_$now",
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "circle_$now",
                "shapeType" to "circle",
                "center" to center,
                "radius" to radius,
            ),
        )

        val featureToFire = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_CIRCLES)
        geomanInstance.scope.launch {
            fireCreateEvent(featureToFire)
        }
    }
}
