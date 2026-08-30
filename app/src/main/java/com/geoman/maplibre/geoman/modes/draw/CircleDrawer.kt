package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch

/**
 * Circle drawing mode
 * First click sets center, second click sets radius
 */
class CircleDrawer(geoman: Geoman) : BaseTwoClickDrawer(geoman) {

    override val modeName: String = DrawModeName.CIRCLE.name

    override fun createFeature(firstClick: LngLat, secondClick: LngLat) {
        val center = firstClick
        val radius = GeometryUtils.distance(center, secondClick)
        val circleCoordinates = GeometryUtils.generateCircleCoordinates(center, radius)

        val geometry = Polygon.fromLngLats(listOf(circleCoordinates))

        val featureId = createFeatureId("circle")
        val feature = Feature(
            id = featureId,
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to featureId,
                "shapeType" to "circle",
                "center" to center,
                "radius" to radius,
            ),
        )

        val featureToFire = geoman.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_CIRCLES)
        geoman.scope.launch {
            fireCreateEvent(featureToFire)
        }
    }
}
