package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.utils.GeometryUtils
import org.maplibre.android.geometry.LatLng

/**
 * One-shot helper that fits the map viewport to every stored feature.
 *
 * The mode disables itself after zooming, mirroring web Geoman's
 * `ZoomToFeatures` control behavior.
 */
class ZoomToFitHelper(geoman: Geoman) : BaseHelper(geoman) {

    override val modeName: String = HelperModeName.ZOOM_TO_FEATURES.name

    override fun enable() {
        super.enable()
        zoomToFit()
        geoman.disableMode(modeType, modeName)
    }

    /**
     * Fit the map camera to the bounds of all stored features.
     */
    fun zoomToFit() {
        val coordinates = geoman.features.getAllFeatures()
            .values
            .flatMap { it.values }
            .flatMap { GeometryUtils.extractAllCoordinates(it.geometry) }

        if (coordinates.isEmpty()) {
            GeomanLogger.w(TAG, "zoomToFit skipped: no features to fit")
            return
        }

        geoman.mapAdapter.fitBounds(LatLngBounds.from(coordinates))
    }

    override fun onMapClick(point: LatLng) {
        // No map interaction; the helper is a one-shot action
    }

    private companion object {
        const val TAG = "ZoomToFitHelper"
    }
}
