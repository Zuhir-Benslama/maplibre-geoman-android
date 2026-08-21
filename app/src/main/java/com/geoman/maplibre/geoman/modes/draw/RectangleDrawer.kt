package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
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

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickLngLat = LngLat(point.longitude, point.latitude)
        val corner = firstCorner

        if (corner == null) {
            // First click - set first corner
            firstCorner = clickLngLat
        } else {
            // Second click - create rectangle and finish
            createRectangleFeature(corner, clickLngLat)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || firstCorner == null) return

        // Cancel drawing
        firstCorner = null
        geoman.disableMode(modeType, modeName)
    }

    override fun finishDrawing() {
        firstCorner = null
        geoman.disableMode(modeType, modeName)
    }

    private fun createRectangleFeature(corner1: LngLat, corner2: LngLat) {
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
