package com.geoman.maplibre.geoman.utils

import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.MultiLineString
import com.geoman.maplibre.geoman.types.geojson.MultiPoint
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core geometry measurement and transform functions for spatial calculations.
 *
 * Similar to Turf.js functionality in the web version, this object keeps the
 * primary measurement, normalization, simplification and bounds-checking
 * primitives. Flat-coordinate coercion and legacy aliases live in
 * [GeometryCoercion].
 */
@Suppress("TooManyFunctions")
object GeometryUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val HALF_CIRCLE_DEGREES = 180.0
    private const val FULL_CIRCLE_DEGREES = 360.0

    /**
     * Normalize an angle in degrees into the range (-180, 180].
     *
     * Used to keep incremental rotation deltas small when a bearing crosses
     * the ±180° discontinuity: e.g. normalizeAngleDegrees(350.0) == -10.0.
     */
    fun normalizeAngleDegrees(degrees: Double): Double {
        val wrapped = degrees % FULL_CIRCLE_DEGREES
        return when {
            wrapped > HALF_CIRCLE_DEGREES -> wrapped - FULL_CIRCLE_DEGREES
            wrapped <= -HALF_CIRCLE_DEGREES -> wrapped + FULL_CIRCLE_DEGREES
            else -> wrapped
        }
    }

    /**
     * Wrap [longitude] into the GeoJSON range [-180, 180] so coordinates
     * computed near the antimeridian (e.g. circle vertices at 180.05°)
     * stay valid.
     */
    fun normalizeLongitude(longitude: Double): Double {
        var normalized = (longitude + HALF_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
        if (normalized < 0) normalized += FULL_CIRCLE_DEGREES
        return normalized - HALF_CIRCLE_DEGREES
    }

    /**
     * Compute the geodesic centroid (center of mass) of [coordinates].
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
            latitude = Math.toDegrees(latRad),
        )
    }

    /**
     * Great-circle distance in meters between [point1] and [point2].
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
     * Compute the bounding box [minLon, minLat, maxLon, maxLat] of [coordinates].
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
     * Whether [point] lies within the axis-aligned bounds described by [bounds].
     * Detects and correctly handles boxes that span the antimeridian.
     */
    fun isPointInBounds(point: LngLat, bounds: List<LngLat>): Boolean {
        require(bounds.isNotEmpty()) { "Bounds must contain at least one point" }

        val minLat = bounds.minOf { it.latitude }
        val maxLat = bounds.maxOf { it.latitude }
        val west = bounds.minOf { it.longitude }
        val east = bounds.maxOf { it.longitude }
        val crossesAntimeridian = (east - west) > 180.0

        // When the bound span exceeds 180°, the enclosed range wraps around the
        // antimeridian: [east, 180] ∪ [-180, west]
        val lonInRange = if (crossesAntimeridian) {
            point.longitude >= east || point.longitude <= west
        } else {
            point.longitude in west..east
        }

        return lonInRange && point.latitude in minLat..maxLat
    }

    /**
     * Extract every coordinate from a [Geometry] into a flat [List] of [LngLat].
     */
    fun extractAllCoordinates(geometry: Geometry): List<LngLat> = when (geometry) {
        is Point -> {
            listOf(LngLat(geometry.coordinates[0], geometry.coordinates[1]))
        }

        is MultiPoint -> {
            geometry.coordinates.map { LngLat(it[0], it[1]) }
        }

        is LineString -> {
            geometry.coordinates.map { LngLat(it[0], it[1]) }
        }

        is MultiLineString -> {
            geometry.coordinates.flatMap { ring ->
                ring.map { LngLat(it[0], it[1]) }
            }
        }

        is Polygon -> {
            geometry.coordinates.flatMap { ring ->
                ring.map { LngLat(it[0], it[1]) }
            }
        }

        is MultiPolygon -> {
            geometry.coordinates.flatMap { polygon ->
                polygon.flatMap { ring ->
                    ring.map { LngLat(it[0], it[1]) }
                }
            }
        }

        is com.geoman.maplibre.geoman.types.geojson.GeometryCollection -> {
            geometry.geometries.flatMap { extractAllCoordinates(it) }
        }
    }

    /**
     * Spherical excess polygon area in square meters from [coordinates].
     */
    fun area(coordinates: List<LngLat>): Double {
        require(coordinates.size >= 3) { "Polygon must have at least 3 coordinates" }

        var area = 0.0
        val n = coordinates.size

        for (i in 0 until n) {
            val j = (i + 1) % n
            area += Math.toRadians(coordinates[j].longitude - coordinates[i].longitude) *
                (
                    2 + sin(Math.toRadians(coordinates[i].latitude)) +
                        sin(Math.toRadians(coordinates[j].latitude))
                    )
        }

        return abs(area * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2)
    }

    /**
     * Perimeter of a polygon or line in meters. Adds the closing edge when the
     * ring is not already closed.
     */
    fun perimeter(coordinates: List<LngLat>): Double {
        if (coordinates.size < 2) return 0.0

        var perimeter = 0.0

        for (i in 0 until coordinates.size - 1) {
            perimeter += distance(coordinates[i], coordinates[i + 1])
        }

        if (coordinates.first() != coordinates.last()) {
            perimeter += distance(coordinates.last(), coordinates.first())
        }

        return perimeter
    }

    /**
     * Simplify coordinates using Douglas-Peucker algorithm.
     * Uses an iterative approach to avoid stack overflow on large coordinate lists.
     */
    fun simplify(coordinates: List<LngLat>, tolerance: Double): List<LngLat> {
        if (coordinates.size <= 2) return coordinates

        val stack = ArrayDeque<Pair<Int, Int>>()
        val keep = BooleanArray(coordinates.size)

        stack.addLast(0 to coordinates.size - 1)
        keep[0] = true
        keep[coordinates.size - 1] = true

        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end <= start + 1) continue

            var maxDistance = 0.0
            var index = start

            val lineStart = coordinates[start]
            val lineEnd = coordinates[end]

            for (i in (start + 1) until end) {
                val nearest = nearestPointOnSegment(coordinates[i], lineStart, lineEnd)
                val dist = distance(coordinates[i], nearest)
                if (dist > maxDistance) {
                    maxDistance = dist
                    index = i
                }
            }

            if (maxDistance > tolerance) {
                keep[index] = true
                stack.addLast(start to index)
                stack.addLast(index to end)
            }
        }

        return coordinates.indices.filter { keep[it] }.map { coordinates[it] }
    }

    /**
     * Generate the coordinates of a [radius]-meter circle centred on [center]
     * using [steps] vertices, closed back onto the first vertex.
     */
    fun generateCircleCoordinates(center: LngLat, radius: Double, steps: Int = 64): List<LngLat> {
        val coordinates = mutableListOf<LngLat>()

        for (i in 0 until steps) {
            val bearing = (i * 360.0 / steps)
            val point = calculateDestination(center, bearing, radius)
            coordinates.add(point)
        }

        coordinates.add(coordinates.first())
        return coordinates
    }

    /**
     * Destination point after travelling [distance] meters from [start] along
     * compass [bearing] (degrees clockwise from north).
     */
    fun calculateDestination(start: LngLat, bearing: Double, distance: Double): LngLat {
        val bearingRad = Math.toRadians(bearing)
        val distanceRad = distance / EARTH_RADIUS_METERS
        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)

        val lat2Rad = asin(
            sin(lat1Rad) * cos(distanceRad) +
                cos(lat1Rad) * sin(distanceRad) * cos(bearingRad),
        )

        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(distanceRad) * cos(lat1Rad),
            cos(distanceRad) - sin(lat1Rad) * sin(lat2Rad),
        )

        return LngLat(
            longitude = normalizeLongitude(Math.toDegrees(lon2Rad)),
            latitude = Math.toDegrees(lat2Rad),
        )
    }

    /**
     * Nearest point on a polyline (series of segments) to [point].
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

    /**
     * Find the nearest point on a line segment defined by [segmentStart] and
     * [segmentEnd] to the given [point]. Also used by MapLibreAdapter to avoid
     * code duplication.
     *
     * The orthogonal projection runs in equirectangular space: longitude
     * differences are scaled by cos(mean segment latitude) so degrees of
     * longitude carry their true metric weight relative to degrees of
     * latitude. Without this correction, east-west offsets dominate near the
     * poles and the projection point drifts. The result remains a local
     * planar approximation, not a geodesic projection.
     */
    internal fun nearestPointOnSegment(point: LngLat, segmentStart: LngLat, segmentEnd: LngLat): LngLat {
        val dx = segmentEnd.longitude - segmentStart.longitude
        val dy = segmentEnd.latitude - segmentStart.latitude

        if (dx == 0.0 && dy == 0.0) {
            return segmentStart
        }

        val latScale = cos(Math.toRadians((segmentStart.latitude + segmentEnd.latitude) / 2))
        val bx = dx * latScale

        if (bx == 0.0 && dy == 0.0) {
            return segmentStart
        }

        val px = (point.longitude - segmentStart.longitude) * latScale
        val py = point.latitude - segmentStart.latitude

        val u = ((px * bx) + (py * dy)) / (bx * bx + dy * dy)
        val clampedU = u.coerceIn(0.0, 1.0)

        return LngLat(
            longitude = segmentStart.longitude + clampedU * dx,
            latitude = segmentStart.latitude + clampedU * dy,
        )
    }
}
