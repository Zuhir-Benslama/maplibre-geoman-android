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
 * Circle marker drawing mode: places a point rendered as a circle via the
 * map's circle layer (as opposed to [MarkerDrawer], which renders an icon).
 */
class CircleMarkerDrawer(geoman: Geoman) : BaseDraw(geoman) {

    override val modeName: String = DrawModeName.CIRCLE_MARKER.name

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val featureId = createFeatureId("circle_marker")

        val feature = Feature(
            id = featureId,
            geometry = Point.fromLngLat(LngLat(point.longitude, point.latitude)),
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to featureId,
                "markerType" to "circle",
            ),
        )

        val featureData = geoman.features.addGeoJsonFeature(
            feature,
            GeomanCoreConstants.SOURCE_CIRCLE_MARKERS,
        )

        temporaryFeatures.add(featureData)

        geoman.scope.launch {
            fireCreateEvent(featureData)
        }

        finishDrawing()
    }

    override fun onMapLongClick(point: LatLng) {
        onMapClick(point)
    }

    override fun finishDrawing() {
        temporaryFeatures.clear()
        geoman.disableMode(modeType, modeName)
    }
}
