package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Marker drawing mode
 */
class MarkerDrawer(geoman: Geoman) : BaseDraw(geoman) {

    override val modeName: String = DrawModeName.MARKER.name

    private var lastClickTime = 0L

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val currentTime = System.currentTimeMillis()

        val feature = Feature(
            id = "marker_$currentTime",
            geometry = Point.fromLngLat(LngLat(point.longitude, point.latitude)),
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to "marker_$currentTime",
                "markerType" to "default",
            ),
        )

        val featureData = geomanInstance.features.addGeoJsonFeature(
            feature,
            GeomanCoreConstants.SOURCE_MARKERS,
        )

        temporaryFeatures.add(featureData)

        geomanInstance.scope.launch {
            fireCreateEvent(featureData)
        }

        finishDrawing()
    }

    override fun onMapLongClick(point: LatLng) {
        onMapClick(point)
    }

    override fun finishDrawing() {
        temporaryFeatures.clear()
        geomanInstance.disableMode(modeType, modeName)
    }
}
