package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Popup interface
 */
abstract class Popup(protected val map: Any) {
    abstract fun getLngLat(): LngLat?
    abstract fun setLngLat(lngLat: LngLat): Popup
    abstract fun getContent(): String
    abstract fun setContent(content: String): Popup
    abstract fun addToMap(): Popup
    abstract fun remove()
    abstract fun isOpen(): Boolean
    abstract fun close(): Popup
}
