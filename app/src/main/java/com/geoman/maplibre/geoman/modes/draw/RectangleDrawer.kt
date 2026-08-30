package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.launch

/**
 * Rectangle drawing mode
 * First click sets one corner, second click sets opposite corner
 */
class RectangleDrawer(geoman: Geoman) : BaseTwoClickDrawer(geoman) {

    override val modeName: String = DrawModeName.RECTANGLE.name

    override fun createFeature(firstClick: LngLat, secondClick: LngLat) {
        val corner1 = firstClick
        val corner2 = secondClick
        // Calculate rectangle corners
        val corners = listOf(
            LngLat(corner1.longitude, corner1.latitude),
            LngLat(corner2.longitude, corner1.latitude),
            LngLat(corner2.longitude, corner2.latitude),
            LngLat(corner1.longitude, corner2.latitude),
            LngLat(corner1.longitude, corner1.latitude), // Close the polygon
        )

        val geometry = Polygon.fromLngLats(listOf(corners))

        val featureId = createFeatureId("rectangle")
        val feature = Feature(
            id = featureId,
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to featureId,
                "shapeType" to "rectangle",
                "corner1" to corner1,
                "corner2" to corner2,
            ),
        )

        val featureToFire = geoman.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_RECTANGLES)
        geoman.scope.launch {
            fireCreateEvent(featureToFire)
        }
    }
}
