package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.types.geojson.FeatureCollection

/**
 * Map source interface
 */
interface MapSource {
    val sourceId: String
    fun setData(geoJson: FeatureCollection)
    fun getData(): FeatureCollection?
    fun remove()
}
