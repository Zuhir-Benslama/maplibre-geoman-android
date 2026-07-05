package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
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
    private var currentFeature: FeatureData? = null

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickLngLat = LngLat(point.longitude, point.latitude)

        if (center == null) {
            // First click - set center
            center = clickLngLat
        } else {
            // Second click - set radius and finish
            val radius = GeometryUtils.calculateDistance(center!!, clickLngLat)
            createCircleFeature(center!!, radius)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || center == null) return

        center = null
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    override fun finishDrawing() {
        center = null
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    private fun createCircleFeature(center: LngLat, radius: Double) {
        val circleCoordinates = GeometryUtils.generateCircleCoordinates(center, radius)

        val geometry = com.geoman.maplibre.geoman.types.geojson.Polygon.fromLngLats(
            listOf(circleCoordinates),
        )

        val feature = Feature(
            id = "circle_${System.currentTimeMillis()}",
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "circle_${System.currentTimeMillis()}",
                "shapeType" to "circle",
                "center" to center,
                "radius" to radius,
            ),
        )

        currentFeature = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_CIRCLES)

        // Capture the feature before launching coroutine to avoid race condition
        val featureToFire = currentFeature
        geomanInstance.scope.launch {
            fireCreateEvent(featureToFire)
        }
    }
}
