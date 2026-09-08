package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.utils.GeometryUtils

/**
 * One-shot helper that fits the map viewport to every stored feature.
 *
 * The mode disables itself after zooming, mirroring web Geoman's
 * `ZoomToFeatures` control behavior.
 */
class ZoomToFitHelper(geoman: GeomanApi) : BaseHelper(geoman) {

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

        geoman.mapActions.fitBounds(LatLngBounds.from(coordinates))
    }

    override fun onMapClick(point: LngLat) {
        // No map interaction; the helper is a one-shot action
    }

    private companion object {
        const val TAG = "ZoomToFitHelper"
    }
}
