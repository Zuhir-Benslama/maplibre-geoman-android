package com.geoman.maplibre.geoman.modes.draw

import androidx.annotation.MainThread
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Shared one-shot lifecycle for point-drawing modes: every map click places a
 * single point feature, fires Create, and disables the mode.
 *
 * Subclasses only describe what makes them distinct: target source, marker
 * rendering type, and ID prefix.
 */
abstract class BaseMarkerDrawer(geoman: Geoman) : BaseDraw(geoman) {

    /** Source the created point feature is added to. */
    protected abstract val sourceName: String

    /** Value stored under the "markerType" property. */
    protected abstract val markerType: String

    /** Prefix for generated feature IDs. */
    protected abstract val idPrefix: String

    @MainThread
    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val featureId = createFeatureId(idPrefix)

        val feature = Feature(
            id = featureId,
            geometry = Point.fromLngLat(LngLat(point.longitude, point.latitude)),
            properties = mapOf(
                GeomanCoreConstants.FEATURE_ID_PROPERTY to featureId,
                MARKER_TYPE_PROPERTY to markerType,
            ),
        )

        val featureData = geoman.features.addGeoJsonFeature(
            feature,
            sourceName,
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

    private companion object {
        const val MARKER_TYPE_PROPERTY = "markerType"
    }
}
