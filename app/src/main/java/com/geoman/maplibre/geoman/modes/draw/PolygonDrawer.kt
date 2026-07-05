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
        android.util.Log.d("PolygonDrawer", "onMapClick called: point=$point, enabled=$enabled")
        if (!enabled) {
            android.util.Log.e("PolygonDrawer", "PolygonDrawer is NOT enabled, returning")
            return
        }

        coordinates.add(LngLat(point.longitude, point.latitude))
        android.util.Log.d(
            "PolygonDrawer",
            "Coordinate added: ${point.longitude}, ${point.latitude}, total=${coordinates.size}",
        )

        // Update or create the polygon feature
        updatePolygonFeature()
    }

    override fun onMapLongClick(point: LatLng) {
        android.util.Log.d(
            "PolygonDrawer",
            "onMapLongClick called: point=$point, enabled=$enabled, coordinates.size=${coordinates.size}",
        )
        if (!enabled || coordinates.size < 3) return

        finishDrawing()
    }

    override fun finishDrawing() {
        android.util.Log.d(
            "PolygonDrawer",
            "finishDrawing called: coordinates.size=${coordinates.size}, currentFeature=${currentFeature != null}",
        )
        if (coordinates.size >= 3 && currentFeature != null) {
            coordinates.add(coordinates.first())
            updatePolygonFeature()

            // Capture the feature before launching coroutine to avoid race condition
            val featureToFire = currentFeature
            android.util.Log.d("PolygonDrawer", "Firing create event with captured feature: ${featureToFire?.id}")
            geomanInstance.scope.launch {
                fireCreateEvent(featureToFire)
            }
        } else {
            android.util.Log.e(
                "PolygonDrawer",
                "finishDrawing: coordinates.size=${coordinates.size}, currentFeature=${currentFeature != null} - SKIPPING",
            )
        }

        coordinates.clear()
        currentFeature = null
        geomanInstance.disableMode(modeType, modeName)
    }

    private fun updatePolygonFeature() {
        android.util.Log.d("PolygonDrawer", "updatePolygonFeature called: coordinates.size=${coordinates.size}")
        if (coordinates.size < 3) {
            android.util.Log.w("PolygonDrawer", "updatePolygonFeature: not enough coordinates (< 3)")
            return
        }

        val ring = if (coordinates.first() != coordinates.last()) {
            coordinates + coordinates.first()
        } else {
            coordinates
        }

        val geometry = Polygon.fromLngLats(listOf(ring))
        android.util.Log.d("PolygonDrawer", "Created polygon geometry with ${ring.size} points")

        val feature = Feature(
            id = "polygon_${System.currentTimeMillis()}",
            geometry = geometry,
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "polygon_${System.currentTimeMillis()}",
                "shapeType" to "polygon",
            ),
        )

        currentFeature?.let {
            android.util.Log.d("PolygonDrawer", "Removing old feature: ${it.id}")
            geomanInstance.features.removeFeature(GeomanCoreConstants.SOURCE_POLYGONS, it.id)
        }

        currentFeature = geomanInstance.features.addGeoJsonFeature(feature, GeomanCoreConstants.SOURCE_POLYGONS)
        android.util.Log.d("PolygonDrawer", "Added new feature to Geoman: ${currentFeature?.id}")
    }
}
