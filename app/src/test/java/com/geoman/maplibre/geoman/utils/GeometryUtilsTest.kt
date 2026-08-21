package com.geoman.maplibre.geoman.utils

import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeometryUtilsTest {

    private companion object {
        const val DELTA = 1e-6
        const val METERS_DELTA = 1.0
        const val ONE_DEGREE_METERS = 111_194.9
    }

    // region isPointInBounds

    @Test
    fun `isPointInBounds inside normal bounds`() {
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertTrue(GeometryUtils.isPointInBounds(LngLat(5.0, 5.0), bounds))
        assertFalse(GeometryUtils.isPointInBounds(LngLat(11.0, 5.0), bounds))
        assertFalse(GeometryUtils.isPointInBounds(LngLat(-1.0, 5.0), bounds))
        assertFalse(GeometryUtils.isPointInBounds(LngLat(5.0, -0.5), bounds))
    }

    @Test
    fun `isPointInBounds handles antimeridian-crossing bounds`() {
        // Bounds spanning the antimeridian: longitudes 170..180 and -180..-170
        val bounds = listOf(LngLat(170.0, -10.0), LngLat(-170.0, 10.0))

        assertTrue(GeometryUtils.isPointInBounds(LngLat(180.0, 0.0), bounds))
        assertTrue(GeometryUtils.isPointInBounds(LngLat(-180.0, 0.0), bounds))
        assertTrue(GeometryUtils.isPointInBounds(LngLat(175.0, 0.0), bounds))
        assertTrue(GeometryUtils.isPointInBounds(LngLat(-175.0, 0.0), bounds))

        // Longitudes in the gap on the other side of the world must be excluded
        assertFalse(GeometryUtils.isPointInBounds(LngLat(0.0, 0.0), bounds))
        assertFalse(GeometryUtils.isPointInBounds(LngLat(100.0, 0.0), bounds))
        assertFalse(GeometryUtils.isPointInBounds(LngLat(-100.0, 0.0), bounds))

        // Latitude must still be respected
        assertFalse(GeometryUtils.isPointInBounds(LngLat(180.0, 20.0), bounds))
    }

    // endregion

    // region bbox / centroid

    @Test
    fun `bbox returns min-max extents`() {
        val bbox = GeometryUtils.bbox(
            listOf(LngLat(1.0, 2.0), LngLat(-3.0, 8.0), LngLat(5.0, -4.0)),
        )

        assertEquals(listOf(-3.0, -4.0, 5.0, 8.0), bbox)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bbox rejects empty coordinates`() {
        GeometryUtils.bbox(emptyList())
    }

    @Test
    fun `centroid of single point is the point itself`() {
        val point = LngLat(12.0, 34.0)

        assertEquals(point, GeometryUtils.centroid(listOf(point)))
    }

    @Test
    fun `centroid of symmetric points is their midpoint`() {
        val centroid = GeometryUtils.centroid(listOf(LngLat(0.0, 0.0), LngLat(2.0, 0.0)))

        assertEquals(1.0, centroid.longitude, 1e-9)
        assertEquals(0.0, centroid.latitude, 1e-9)
    }

    // endregion

    // region distance / destination

    @Test
    fun `distance of one degree at equator matches expected meters`() {
        val d = GeometryUtils.distance(LngLat(0.0, 0.0), LngLat(1.0, 0.0))

        assertEquals(ONE_DEGREE_METERS, d, METERS_DELTA)
    }

    @Test
    fun `distance is zero for identical points`() {
        assertEquals(0.0, GeometryUtils.distance(LngLat(5.0, 5.0), LngLat(5.0, 5.0)), DELTA)
    }

    @Test
    fun `destination travels north by expected angle`() {
        // One degree of arc along a great circle
        val dest = GeometryUtils.calculateDestination(
            LngLat(0.0, 0.0),
            bearing = 0.0,
            distance = ONE_DEGREE_METERS,
        )

        assertEquals(0.0, dest.longitude, 1e-9)
        assertEquals(1.0, dest.latitude, 1e-6)
    }

    // endregion

    // region area / perimeter

    @Test
    fun `area of one-degree square is plausible`() {
        val area = GeometryUtils.area(
            listOf(
                LngLat(0.0, 0.0),
                LngLat(1.0, 0.0),
                LngLat(1.0, 1.0),
                LngLat(0.0, 1.0),
            ),
        )

        // Planar expectation ~ (111194.9)^2 ≈ 1.2364e10 m²
        assertTrue("unexpected area: $area", area in 1.20e10..1.27e10)
    }

    @Test
    fun `perimeter does not double-count closed rings`() {
        val closed = listOf(
            LngLat(0.0, 0.0),
            LngLat(1.0, 0.0),
            LngLat(1.0, 1.0),
            LngLat(0.0, 1.0),
            LngLat(0.0, 0.0),
        )
        val open = closed.dropLast(1)

        val perimeterClosed = GeometryUtils.perimeter(closed)
        val perimeterOpen = GeometryUtils.perimeter(open)

        assertEquals(perimeterClosed, perimeterOpen, METERS_DELTA)
        // Spherical excess makes each side slightly shorter than the planar
        // 1° arc, hence the loose absolute tolerance
        assertEquals(4 * ONE_DEGREE_METERS, perimeterClosed, 50.0)
    }

    // endregion

    // region simplify

    @Test
    fun `simplify drops collinear middle points`() {
        val simplified = GeometryUtils.simplify(
            listOf(LngLat(0.0, 0.0), LngLat(1.0, 0.0), LngLat(2.0, 0.0)),
            tolerance = 1.0,
        )

        assertEquals(listOf(LngLat(0.0, 0.0), LngLat(2.0, 0.0)), simplified)
    }

    @Test
    fun `simplify keeps corners beyond tolerance`() {
        val input = listOf(LngLat(0.0, 0.0), LngLat(1.0, 1.0), LngLat(2.0, 0.0))

        assertEquals(input, GeometryUtils.simplify(input, tolerance = 1_000.0))
    }

    @Test
    fun `simplify keeps short inputs untouched`() {
        val input = listOf(LngLat(0.0, 0.0), LngLat(1.0, 1.0))

        assertEquals(input, GeometryUtils.simplify(input, tolerance = 1_000.0))
    }

    // endregion

    // region circles / segments

    @Test
    fun `generated circle is closed and has requested step count`() {
        val radius = 100_000.0
        val steps = 64
        val circle = GeometryUtils.generateCircleCoordinates(LngLat(0.0, 0.0), radius, steps)

        assertEquals(steps + 1, circle.size)
        assertEquals(circle.first(), circle.last())

        circle.dropLast(1).forEach { point ->
            val d = GeometryUtils.distance(LngLat(0.0, 0.0), point)
            assertTrue("radius deviation too large: $d", abs(d - radius) / radius < 0.005)
        }
    }

    @Test
    fun `nearestPointOnSegment clamps to segment endpoints`() {
        val start = LngLat(0.0, 0.0)
        val end = LngLat(2.0, 0.0)

        val clampedToEnd = GeometryUtils.nearestPointOnSegment(LngLat(5.0, 1.0), start, end)
        assertEquals(end.longitude, clampedToEnd.longitude, DELTA)
        assertEquals(end.latitude, clampedToEnd.latitude, DELTA)

        val clampedToStart = GeometryUtils.nearestPointOnSegment(LngLat(-3.0, 1.0), start, end)
        assertEquals(start.longitude, clampedToStart.longitude, DELTA)
        assertEquals(start.latitude, clampedToStart.latitude, DELTA)
    }

    @Test
    fun `nearestPointOnPolyline projects onto closest segment`() {
        val polyline = listOf(LngLat(0.0, 0.0), LngLat(2.0, 0.0), LngLat(4.0, 0.0))

        val nearest = GeometryUtils.nearestPointOnPolyline(LngLat(3.0, 1.0), polyline)

        assertEquals(3.0, nearest.longitude, DELTA)
        assertEquals(0.0, nearest.latitude, DELTA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nearestPointOnPolyline rejects short polylines`() {
        GeometryUtils.nearestPointOnPolyline(LngLat(0.0, 0.0), listOf(LngLat(1.0, 1.0)))
    }

    // endregion
}
