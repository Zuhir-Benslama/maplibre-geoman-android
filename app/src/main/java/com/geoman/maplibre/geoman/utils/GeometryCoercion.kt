package com.geoman.maplibre.geoman.utils

import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Flat-coordinate coercion and convenience aggregate helpers.
 *
 * Converts between the GeoJSON "flat" representation (flattened
 * [longitude, latitude, longitude, latitude, ...] arrays) and [LngLat] lists,
 * and exposes the `*FromFlat` / bounds convenience variants of the core
 * measurements in [GeometryUtils] along with the deprecated legacy aliases.
 */
object GeometryCoercion {

    /**
     * Convert a flat `[lon, lat, lon, lat, ...]` [coordinates] array into a
     * [List] of [LngLat].
     */
    fun flatToLngLat(coordinates: List<Double>): List<LngLat> = toLngLats(coordinates)

    /**
     * Convert a [List] of [LngLat] into a flat `[lon, lat, lon, lat, ...]` array.
     */
    fun lngLatToFlat(lngLats: List<LngLat>): List<Double> = lngLats.flatMap { listOf(it.longitude, it.latitude) }

    /**
     * [GeometryUtils.centroid] over a flat `[lon, lat, ...]` [coordinates] array.
     */
    fun centroidFromFlat(coordinates: List<Double>): LngLat = GeometryUtils.centroid(toLngLats(coordinates))

    /**
     * [GeometryUtils.bbox] over a flat `[lon, lat, ...]` [coordinates] array.
     */
    fun bboxFromFlat(coordinates: List<Double>): List<Double> = GeometryUtils.bbox(toLngLats(coordinates))

    /**
     * [GeometryUtils.area] over a flat `[lon, lat, ...]` [coordinates] array.
     */
    fun areaFromFlat(coordinates: List<Double>): Double = GeometryUtils.area(toLngLats(coordinates))

    /**
     * [GeometryUtils.isPointInBounds] where [bounds] are supplied as a flat
     * `[lon, lat, ...]` array.
     */
    fun isPointInBoundsFromFlat(point: LngLat, bounds: List<Double>): Boolean =
        GeometryUtils.isPointInBounds(point, toLngLats(bounds))

    /**
     * Whether every coordinate of [geometry] lies within [bounds].
     */
    fun isGeometryInBounds(geometry: Geometry, bounds: List<LngLat>): Boolean {
        val coords = GeometryUtils.extractAllCoordinates(geometry)
        return coords.all { GeometryUtils.isPointInBounds(it, bounds) }
    }

    /**
     * Calculate distance between two LngLat points (existing API)
     */
    @Deprecated("Use distance() instead", ReplaceWith("distance(point1, point2)"))
    fun calculateDistance(point1: LngLat, point2: LngLat): Double = GeometryUtils.distance(point1, point2)

    /**
     * Calculate centroid for edit mode (existing API alias)
     */
    @Deprecated("Use centroid() instead", ReplaceWith("centroid(coordinates)"))
    fun calculateCentroid(coordinates: List<LngLat>): LngLat = GeometryUtils.centroid(coordinates)

    private fun toLngLats(coordinates: List<Double>): List<LngLat> {
        require(coordinates.size % 2 == 0) {
            "Flat coordinate array must have an even number of values, got ${coordinates.size}"
        }
        return coordinates.chunked(2).map { LngLat(it[0], it[1]) }
    }
}
