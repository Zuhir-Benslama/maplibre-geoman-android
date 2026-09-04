package com.geoman.maplibre.geoman.utils

import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryCoercionTest {

    private companion object {
        const val DELTA = 1e-6
    }

    // region flatToLngLat / lngLatToFlat

    @Test
    fun `flatToLngLat converts correctly`() {
        val flat = listOf(1.0, 2.0, 3.0, 4.0)
        val result = GeometryCoercion.flatToLngLat(flat)

        assertEquals(2, result.size)
        assertEquals(LngLat(1.0, 2.0), result[0])
        assertEquals(LngLat(3.0, 4.0), result[1])
    }

    @Test
    fun `lngLatToFlat converts correctly`() {
        val lngLats = listOf(LngLat(1.0, 2.0), LngLat(3.0, 4.0))
        val result = GeometryCoercion.lngLatToFlat(lngLats)

        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), result)
    }

    @Test
    fun `flatToLngLat and lngLatToFlat round-trip`() {
        val original = listOf(10.5, 20.3, -30.1, 40.7)
        val roundTripped = GeometryCoercion.lngLatToFlat(GeometryCoercion.flatToLngLat(original))

        assertEquals(original, roundTripped)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `flatToLngLat rejects odd-length arrays`() {
        GeometryCoercion.flatToLngLat(listOf(1.0, 2.0, 3.0))
    }

    @Test
    fun `flatToLngLat handles empty array`() {
        assertTrue(GeometryCoercion.flatToLngLat(emptyList()).isEmpty())
    }

    // endregion

    // region centroidFromFlat

    @Test
    fun `centroidFromFlat computes centroid`() {
        val flat = listOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0)
        val centroid = GeometryCoercion.centroidFromFlat(flat)

        // Spherical centroid is close to (5,5) but not exact for degree-scale polygons
        assertEquals(5.0, centroid.longitude, 0.5)
        assertEquals(5.0, centroid.latitude, 0.5)
    }

    // endregion

    // region bboxFromFlat

    @Test
    fun `bboxFromFlat computes bounding box`() {
        val flat = listOf(3.0, 1.0, 1.0, 3.0, 5.0, 2.0)
        val bbox = GeometryCoercion.bboxFromFlat(flat)

        assertEquals(4, bbox.size)
        assertEquals(1.0, bbox[0], DELTA) // min lon
        assertEquals(1.0, bbox[1], DELTA) // min lat
        assertEquals(5.0, bbox[2], DELTA) // max lon
        assertEquals(3.0, bbox[3], DELTA) // max lat
    }

    // endregion

    // region areaFromFlat

    @Test
    fun `areaFromFlat computes area of square`() {
        // 1-degree square at equator ≈ 12,364 km²
        val flat = listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        val area = GeometryCoercion.areaFromFlat(flat)
        assertTrue(area > 12_000_000_000.0) // > 12,000 km²
        assertTrue(area < 13_000_000_000.0) // < 13,000 km²
    }

    // endregion

    // region isPointInBoundsFromFlat

    @Test
    fun `isPointInBoundsFromFlat inside bounds`() {
        val bounds = listOf(0.0, 0.0, 10.0, 10.0)
        assertTrue(GeometryCoercion.isPointInBoundsFromFlat(LngLat(5.0, 5.0), bounds))
    }

    @Test
    fun `isPointInBoundsFromFlat outside bounds`() {
        val bounds = listOf(0.0, 0.0, 10.0, 10.0)
        assertFalse(GeometryCoercion.isPointInBoundsFromFlat(LngLat(11.0, 5.0), bounds))
    }

    // endregion

    // region isGeometryInBounds

    @Test
    fun `isGeometryInBounds returns true for point inside`() {
        val point = Point.fromLngLat(LngLat(5.0, 5.0))
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertTrue(GeometryCoercion.isGeometryInBounds(point, bounds))
    }

    @Test
    fun `isGeometryInBounds returns false for point outside`() {
        val point = Point.fromLngLat(LngLat(15.0, 5.0))
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertFalse(GeometryCoercion.isGeometryInBounds(point, bounds))
    }

    @Test
    fun `isGeometryInBounds returns true for line inside bounds`() {
        val line = LineString.fromLngLats(listOf(LngLat(1.0, 1.0), LngLat(9.0, 9.0)))
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertTrue(GeometryCoercion.isGeometryInBounds(line, bounds))
    }

    @Test
    fun `isGeometryInBounds returns false for line partially outside`() {
        val line = LineString.fromLngLats(listOf(LngLat(1.0, 1.0), LngLat(15.0, 5.0)))
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertFalse(GeometryCoercion.isGeometryInBounds(line, bounds))
    }

    @Test
    fun `isGeometryInBounds returns true for polygon inside bounds`() {
        val polygon = Polygon.fromLngLats(
            listOf(
                listOf(
                    LngLat(1.0, 1.0),
                    LngLat(9.0, 1.0),
                    LngLat(9.0, 9.0),
                    LngLat(1.0, 9.0),
                    LngLat(1.0, 1.0),
                ),
            ),
        )
        val bounds = listOf(LngLat(0.0, 0.0), LngLat(10.0, 10.0))

        assertTrue(GeometryCoercion.isGeometryInBounds(polygon, bounds))
    }

    // endregion
}
