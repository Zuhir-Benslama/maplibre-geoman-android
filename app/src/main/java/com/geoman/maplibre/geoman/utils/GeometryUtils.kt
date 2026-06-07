package com.geoman.maplibre.geoman.utils

import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlin.math.*

/**
 * Geometry utility functions for spatial calculations
 * Similar to Turf.js functionality in the web version
 */
object GeometryUtils {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    
    /**
     * Calculate the centroid of a list of coordinates
     * Similar to @turf/centroid
     */
    fun centroid(coordinates: List<LngLat>): LngLat {
        require(coordinates.isNotEmpty()) { "Coordinates list cannot be empty" }
        
        if (coordinates.size == 1) {
            return coordinates.first()
        }
        
        var x = 0.0
        var y = 0.0
        var z = 0.0
        
        for (coord in coordinates) {
            val latRad = Math.toRadians(coord.latitude)
            val lonRad = Math.toRadians(coord.longitude)
            
            x += cos(latRad) * cos(lonRad)
            y += cos(latRad) * sin(lonRad)
            z += sin(latRad)
        }
        
        val count = coordinates.size
        x /= count
        y /= count
        z /= count
        
        val lonRad = atan2(y, x)
        val hyp = sqrt(x * x + y * y)
        val latRad = atan2(z, hyp)
        
        return LngLat(
            longitude = Math.toDegrees(lonRad),
            latitude = Math.toDegrees(latRad)
        )
    }
    
    /**
     * Calculate centroid from flat coordinate array
     */
    fun centroidFromFlat(coordinates: List<Double>): LngLat {
        val pairs = coordinates.chunked(2)
        val lngLats = pairs.map { LngLat(it[0], it[1]) }
        return centroid(lngLats)
    }
    
    /**
     * Calculate distance between two points in meters
     * Uses Haversine formula
     */
    fun distance(point1: LngLat, point2: LngLat): Double {
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLonRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * Calculate the bounding box of coordinates
     * Returns [west, south, east, north]
     */
    fun bbox(coordinates: List<LngLat>): List<Double> {
        require(coordinates.isNotEmpty()) { "Coordinates list cannot be empty" }
        
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        
        for (coord in coordinates) {
            minLon = minOf(minLon, coord.longitude)
            maxLon = maxOf(maxLon, coord.longitude)
            minLat = minOf(minLat, coord.latitude)
            maxLat = maxOf(maxLat, coord.latitude)
        }
        
        return listOf(minLon, minLat, maxLon, maxLat)
    }
    
    /**
     * Calculate bounding box from flat coordinate array
     */
    fun bboxFromFlat(coordinates: List<Double>): List<Double> {
        val pairs = coordinates.chunked(2)
        val lngLats = pairs.map { LngLat(it[0], it[1]) }
        return bbox(lngLats)
    }
    
    /**
     * Check if a point is within bounds
     */
    fun isPointInBounds(point: LngLat, bounds: List<LngLat>): Boolean {
        require(bounds.size >= 2) { "Bounds must have at least 2 points (SW, NE)" }
        
        val sw = bounds.minByOrNull { it.latitude + it.longitude } ?: return false
        val ne = bounds.maxByOrNull { it.latitude + it.longitude } ?: return false
        
        return point.longitude in sw.longitude..ne.longitude &&
               point.latitude in sw.latitude..ne.latitude
    }
    
    /**
     * Check if a point is within bounds (flat array version)
     */
    fun isPointInBoundsFromFlat(point: LngLat, bounds: List<Double>): Boolean {
        val pairs = bounds.chunked(2)
        val lngLats = pairs.map { LngLat(it[0], it[1]) }
        return isPointInBounds(point, lngLats)
    }
    
    /**
     * Check if geometry is within bounds
     */
    fun isGeometryInBounds(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry, bounds: List<LngLat>): Boolean {
        val coords = extractAllCoordinates(geometry)
        return coords.all { isPointInBounds(it, bounds) }
    }
    
    /**
     * Extract all coordinates from a geometry
     */
    fun extractAllCoordinates(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry): List<LngLat> {
        return when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.Point -> {
                listOf(LngLat(geometry.coordinates[0], geometry.coordinates[1]))
            }
            is com.geoman.maplibre.geoman.types.geojson.MultiPoint -> {
                geometry.coordinates.map { LngLat(it[0], it[1]) }
            }
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                geometry.coordinates.map { LngLat(it[0], it[1]) }
            }
            is com.geoman.maplibre.geoman.types.geojson.MultiLineString -> {
                geometry.coordinates.flatMap { ring ->
                    ring.map { LngLat(it[0], it[1]) }
                }
            }
            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                geometry.coordinates.flatMap { ring ->
                    ring.map { LngLat(it[0], it[1]) }
                }
            }
            is com.geoman.maplibre.geoman.types.geojson.MultiPolygon -> {
                geometry.coordinates.flatMap { polygon ->
                    polygon.flatMap { ring ->
                        ring.map { LngLat(it[0], it[1]) }
                    }
                }
            }
            else -> emptyList()
        }
    }
    
    /**
     * Calculate the area of a polygon in square meters
     * Uses the shoelace formula with spherical correction
     */
    fun area(coordinates: List<LngLat>): Double {
        require(coordinates.size >= 3) { "Polygon must have at least 3 coordinates" }
        
        var area = 0.0
        val n = coordinates.size
        
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += Math.toRadians(coordinates[j].longitude - coordinates[i].longitude) *
                    (2 + sin(Math.toRadians(coordinates[i].latitude)) +
                            sin(Math.toRadians(coordinates[j].latitude)))
        }
        
        return abs(area * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2)
    }
    
    /**
     * Calculate the area from flat coordinate array
     */
    fun areaFromFlat(coordinates: List<Double>): Double {
        val pairs = coordinates.chunked(2)
        val lngLats = pairs.map { LngLat(it[0], it[1]) }
        return area(lngLats)
    }
    
    /**
     * Calculate the perimeter of a polygon in meters
     */
    fun perimeter(coordinates: List<LngLat>): Double {
        if (coordinates.size < 2) return 0.0
        
        var perimeter = 0.0
        
        for (i in 0 until coordinates.size - 1) {
            perimeter += distance(coordinates[i], coordinates[i + 1])
        }
        
        // Close the polygon if needed
        if (coordinates.first() != coordinates.last()) {
            perimeter += distance(coordinates.last(), coordinates.first())
        }
        
        return perimeter
    }
    
    /**
     * Simplify coordinates using Douglas-Peucker algorithm
     */
    fun simplify(coordinates: List<LngLat>, tolerance: Double): List<LngLat> {
        if (coordinates.size <= 2) return coordinates
        
        val result = mutableListOf<LngLat>()
        douglasPeucker(coordinates, 0, coordinates.size - 1, tolerance, result)
        
        return result
    }
    
    private fun douglasPeucker(
        points: List<LngLat>,
        start: Int,
        end: Int,
        tolerance: Double,
        result: MutableList<LngLat>
    ) {
        if (end <= start + 1) {
            if (start == 0) result.add(points[start])
            result.add(points[end])
            return
        }
        
        var maxDistance = 0.0
        var index = start
        
        val lineStart = points[start]
        val lineEnd = points[end]
        
        for (i in (start + 1) until end) {
            val dist = perpendicularDistance(points[i], lineStart, lineEnd)
            if (dist > maxDistance) {
                maxDistance = dist
                index = i
            }
        }
        
        if (maxDistance > tolerance) {
            douglasPeucker(points, start, index, tolerance, result)
            douglasPeucker(points, index, end, tolerance, result)
        } else {
            if (start == 0) result.add(points[start])
            result.add(points[end])
        }
    }
    
    private fun perpendicularDistance(point: LngLat, lineStart: LngLat, lineEnd: LngLat): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        
        if (dx == 0.0 && dy == 0.0) {
            return distance(point, lineStart)
        }
        
        val u = ((point.longitude - lineStart.longitude) * dx +
                (point.latitude - lineStart.latitude) * dy) /
                (dx * dx + dy * dy)
        
        val nearestX = lineStart.longitude + u * dx
        val nearestY = lineStart.latitude + u * dy
        
        return distance(point, LngLat(nearestX, nearestY))
    }
    
    /**
     * Convert flat coordinates to LngLat list
     */
    fun flatToLngLat(coordinates: List<Double>): List<LngLat> {
        return coordinates.chunked(2).map { LngLat(it[0], it[1]) }
    }
    
    /**
     * Convert LngLat list to flat coordinates
     */
    fun lngLatToFlat(lngLats: List<LngLat>): List<Double> {
        return lngLats.flatMap { listOf(it.longitude, it.latitude) }
    }
    
    /**
     * Calculate distance between two LngLat points (existing API)
     */
    fun calculateDistance(point1: LngLat, point2: LngLat): Double {
        return distance(point1, point2)
    }
    
    /**
     * Generate circle coordinates (existing API)
     */
    fun generateCircleCoordinates(center: LngLat, radius: Double, steps: Int = 64): List<LngLat> {
        val coordinates = mutableListOf<LngLat>()
        
        for (i in 0 until steps) {
            val bearing = (i * 360.0 / steps)
            val point = calculateDestination(center, bearing, radius)
            coordinates.add(point)
        }
        
        // Close the circle
        coordinates.add(coordinates.first())
        
        return coordinates
    }
    
    /**
     * Calculate destination point (existing API)
     */
    fun calculateDestination(start: LngLat, bearing: Double, distance: Double): LngLat {
        val bearingRad = Math.toRadians(bearing)
        val distanceRad = distance / EARTH_RADIUS_METERS
        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)
        
        val lat2Rad = asin(
            sin(lat1Rad) * cos(distanceRad) +
            cos(lat1Rad) * sin(distanceRad) * cos(bearingRad)
        )
        
        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(distanceRad) * cos(lat1Rad),
            cos(distanceRad) - sin(lat1Rad) * sin(lat2Rad)
        )
        
        return LngLat(
            longitude = Math.toDegrees(lon2Rad),
            latitude = Math.toDegrees(lat2Rad)
        )
    }
    
    /**
     * Calculate centroid for edit mode (existing API alias)
     */
    fun calculateCentroid(coordinates: List<LngLat>): LngLat {
        return centroid(coordinates)
    }
    
    /**
     * Find nearest point on polyline (existing API)
     */
    fun nearestPointOnPolyline(point: LngLat, coordinates: List<LngLat>): LngLat {
        require(coordinates.size >= 2) { "Polyline must have at least 2 points" }
        
        var nearestPoint = coordinates.first()
        var minDistance = Double.MAX_VALUE
        
        for (i in 0 until coordinates.size - 1) {
            val nearestOnSegment = nearestPointOnSegment(point, coordinates[i], coordinates[i + 1])
            val dist = distance(point, nearestOnSegment)
            
            if (dist < minDistance) {
                minDistance = dist
                nearestPoint = nearestOnSegment
            }
        }
        
        return nearestPoint
    }
    
    private fun nearestPointOnSegment(point: LngLat, segmentStart: LngLat, segmentEnd: LngLat): LngLat {
        val dx = segmentEnd.longitude - segmentStart.longitude
        val dy = segmentEnd.latitude - segmentStart.latitude
        
        if (dx == 0.0 && dy == 0.0) {
            return segmentStart
        }
        
        val u = ((point.longitude - segmentStart.longitude) * dx +
                (point.latitude - segmentStart.latitude) * dy) /
                (dx * dx + dy * dy)
        
        val clampedU = u.coerceIn(0.0, 1.0)
        
        return LngLat(
            longitude = segmentStart.longitude + clampedU * dx,
            latitude = segmentStart.latitude + clampedU * dy
        )
    }
}
