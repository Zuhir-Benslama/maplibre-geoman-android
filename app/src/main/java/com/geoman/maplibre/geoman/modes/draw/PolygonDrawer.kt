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
 * Polygon drawing mode
 */
class PolygonDrawer(geoman: Geoman) : BaseDraw(geoman) {

    override val modeName: String = DrawModeName.POLYGON.name

    private val coordinates = mutableListOf<LngLat>()
    private var currentFeature: FeatureData? = null

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        coordinates.add(LngLat(point.longitude, point.latitude))

        // Update or create the polygon feature (kept stable across clicks)
        updatePolygonFeature()
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || coordinates.size < 3) return

        finishDrawing()
    }

    override fun finishDrawing() {
        if (coordinates.size >= 3 && currentFeature != null) {
            coordinates.add(coordinates.first())
            updatePolygonFeature()

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

    override fun disable() {
        // Remove the uncommitted partial polygon if the mode is cancelled mid-draw
        currentFeature?.let {
            geomanInstance.features.removeFeature(GeomanCoreConstants.SOURCE_POLYGONS, it.id)
        }
        currentFeature = null
        coordinates.clear()
        super.disable()
    }

    private fun updatePolygonFeature() {
        if (coordinates.size < 3) return

        val ring = if (coordinates.first() != coordinates.last()) {
            coordinates + coordinates.first()
        } else {
            coordinates
        }

        val geometry = Polygon.fromLngLats(listOf(ring))
        val existing = currentFeature

        if (existing != null) {
            val updated = existing.copy(feature = existing.feature.copy(geometry = geometry))
            geomanInstance.features.updateFeature(GeomanCoreConstants.SOURCE_POLYGONS, existing.id) { updated }
            currentFeature = updated
        } else {
            val now = System.currentTimeMillis()
            val feature = Feature(
                id = "polygon_$now",
                geometry = geometry,
                properties = mapOf(
                    GeomanCoreConstants.FEATURE_ID_PROPERTY to "polygon_$now",
                    "shapeType" to "polygon",
                ),
            )
            currentFeature = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_POLYGONS)
        }
    }
}
