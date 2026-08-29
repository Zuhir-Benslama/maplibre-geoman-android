package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint

/**
 * Viewport and camera operations contract for map adapters.
 */
interface MapViewport {
    /**
     * Get the current map bounds
     */
    fun getBounds(): LatLngBounds

    /**
     * Fit the map to bounds
     */
    fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions? = null)

    /**
     * Project lngLat to screen coordinates
     */
    fun project(position: LngLat): ScreenPoint

    /**
     * Unproject screen coordinates to lngLat
     */
    fun unproject(point: ScreenPoint): LngLat

    /**
     * Convert coordinate bounds to screen bounds
     */
    fun coordBoundsToScreenBounds(bounds: LatLngBounds): Pair<ScreenPoint, ScreenPoint>
}
