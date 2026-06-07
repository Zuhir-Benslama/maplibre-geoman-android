package com.geoman.maplibre.geoman.types.geojson

/**
 * GeoJSON Feature data class holding feature information with source reference
 */
data class GeoJsonFeatureData(
    val id: String,
    val sourceName: String,
    val feature: Feature
) {
    val geometry: Geometry get() = feature.geometry
}
