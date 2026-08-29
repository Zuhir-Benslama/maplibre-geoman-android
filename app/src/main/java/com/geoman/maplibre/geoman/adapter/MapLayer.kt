package com.geoman.maplibre.geoman.adapter

/**
 * Map layer interface
 */
interface MapLayer {
    val layerId: String
    fun setPaintProperty(name: String, value: Any)
    fun setLayoutProperty(name: String, value: Any)
    fun remove()
}
