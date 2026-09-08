package com.geoman.maplibre.geoman.modes.draw

import androidx.annotation.MainThread
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Shared two-click lifecycle for shape drawing modes (circle, rectangle):
 * the first click anchors the shape, the second click completes it (or a
 * long press cancels). Subclasses implement [createFeature] to build and
 * store their shape from the two clicks.
 */
abstract class BaseTwoClickDrawer(geoman: GeomanApi) : BaseDraw(geoman) {

    private var firstClick: LngLat? = null

    @MainThread
    override fun onMapClick(point: LngLat): Unit = synchronized(this) {
        if (!enabled) return@synchronized

        val first = firstClick

        if (first == null) {
            // First click - anchor the shape
            firstClick = point
        } else {
            // Second click - build the shape and finish
            createFeature(first, point)
            finishDrawing()
        }
    }

    override fun onMapLongClick(point: LngLat): Unit = synchronized(this) {
        if (!enabled || firstClick == null) return@synchronized

        // Cancel drawing
        firstClick = null
        geoman.disableMode(modeType, modeName)
    }

    override fun finishDrawing(): Unit = synchronized(this) {
        firstClick = null
        geoman.disableMode(modeType, modeName)
    }

    /** Build and store the shape from the two clicks, then fire its Create event. */
    protected abstract fun createFeature(firstClick: LngLat, secondClick: LngLat)
}
