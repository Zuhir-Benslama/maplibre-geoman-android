package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.maplibre.android.geometry.LatLng

/**
 * Shared two-click lifecycle for shape drawing modes (circle, rectangle):
 * the first click anchors the shape, the second click completes it (or a
 * long press cancels). Subclasses implement [createFeature] to build and
 * store their shape from the two clicks.
 */
abstract class BaseTwoClickDrawer(geoman: Geoman) : BaseDraw(geoman) {

    private var firstClick: LngLat? = null

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickLngLat = LngLat(point.longitude, point.latitude)
        val first = firstClick

        if (first == null) {
            // First click - anchor the shape
            firstClick = clickLngLat
        } else {
            // Second click - build the shape and finish
            createFeature(first, clickLngLat)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LatLng) {
        if (!enabled || firstClick == null) return

        // Cancel drawing
        firstClick = null
        geoman.disableMode(modeType, modeName)
    }

    override fun finishDrawing() {
        firstClick = null
        geoman.disableMode(modeType, modeName)
    }

    /** Build and store the shape from the two clicks, then fire its Create event. */
    protected abstract fun createFeature(firstClick: LngLat, secondClick: LngLat)
}
