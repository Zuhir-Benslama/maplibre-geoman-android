package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry helpers shared by the edit modes.
 *
 * Kept free of Android/map dependencies so the ring-closing and rotation
 * rules can be unit tested on the JVM.
 */
internal object EditorGeometry {

    /**
     * Move vertex [index] of a polygon's rings to [coord], keeping closed rings
     * closed: the closing coordinate duplicates the first, so moving either end
     * must move both.
     *
     * @return the updated rings, or the original instance when the move is a no-op.
     */
    fun movePolygonVertex(
        coordinates: List<List<List<Double>>>,
        index: Int,
        coord: List<Double>,
    ): List<List<List<Double>>> {
        if (coordinates.isEmpty()) return coordinates

        val rings = coordinates.map { ring -> ring.toMutableList() }
        val exteriorRing = rings[0]
        if (index !in exteriorRing.indices) return coordinates

        // Check closure before mutating: moving an end vertex breaks the
        // first == last equality, which would hide the closed-ring case
        val wasClosed = exteriorRing.size > 1 && exteriorRing.first() == exteriorRing.last()
        exteriorRing[index] = coord
        if (wasClosed) {
            when (index) {
                0 -> exteriorRing[exteriorRing.size - 1] = coord
                exteriorRing.size - 1 -> exteriorRing[0] = coord
            }
        }
        return rings
    }

    /**
     * Remove vertex [index] from a polygon's rings, preserving validity:
     * at least 3 unique points plus the closing coordinate must remain, the
     * closing coordinate itself is never removed, and closed rings are
     * re-closed around the new first vertex.
     *
     * @return the updated rings, or the original instance when removal is not allowed.
     */
    fun removePolygonVertex(coordinates: List<List<List<Double>>>, index: Int): List<List<List<Double>>> {
        if (coordinates.isEmpty()) return coordinates

        val rings = coordinates.map { ring -> ring.toMutableList() }
        val exteriorRing = rings[0]
        // Can't remove if only 3 unique points + closing
        if (exteriorRing.size <= 4) return coordinates
        // The closing coordinate duplicates the first; never remove it
        if (index !in 0 until exteriorRing.size - 1) return coordinates

        val wasClosed = exteriorRing.first() == exteriorRing.last()
        exteriorRing.removeAt(index)
        if (wasClosed && exteriorRing.isNotEmpty()) {
            // Re-close the ring around the new first vertex
            exteriorRing[exteriorRing.size - 1] = exteriorRing.first()
        }
        return rings
    }

    /**
     * Translate every position in a geometry by ([deltaLon], [deltaLat]).
     *
     * @return a new coordinate structure; the input is never mutated.
     */
    fun translatePoint(coordinates: List<Double>, deltaLon: Double, deltaLat: Double): List<Double> =
        listOf(coordinates[0] + deltaLon, coordinates[1] + deltaLat)

    fun translateLine(coordinates: List<List<Double>>, deltaLon: Double, deltaLat: Double): List<List<Double>> =
        coordinates.map { translatePoint(it, deltaLon, deltaLat) }

    fun translatePolygonRings(
        coordinates: List<List<List<Double>>>,
        deltaLon: Double,
        deltaLat: Double,
    ): List<List<List<Double>>> = coordinates.map { ring -> translateLine(ring, deltaLon, deltaLat) }

    /**
     * Midpoint of two [longitude, latitude] positions.
     */
    fun midpoint(a: List<Double>, b: List<Double>): List<Double> = listOf((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)

    /**
     * Rotate [point] around [center] by [angleRad] in equirectangular
     * (metre-like) space so shapes keep their proportions when projected.
     */
    fun rotatePoint(point: LngLat, center: LngLat, angleRad: Double): LngLat {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        val cosLat = cos(Math.toRadians(center.latitude)).coerceAtLeast(1e-6)
        val ex = (point.longitude - center.longitude) * cosLat
        val ey = point.latitude - center.latitude

        val rotatedEx = ex * cosA - ey * sinA
        val rotatedEy = ex * sinA + ey * cosA

        return LngLat(
            longitude = center.longitude + rotatedEx / cosLat,
            latitude = center.latitude + rotatedEy,
        )
    }
}
