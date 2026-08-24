package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

class EditorGeometryTest {

    private companion object {
        // Closed square: [A,B,C,D,A]
        val CLOSED_SQUARE = listOf(
            listOf(0.0, 0.0),
            listOf(4.0, 0.0),
            listOf(4.0, 4.0),
            listOf(0.0, 4.0),
            listOf(0.0, 0.0),
        )
        val RINGS = listOf(CLOSED_SQUARE)
    }

    // region movePolygonVertex

    @Test
    fun `moving first vertex also moves the closing coordinate`() {
        val moved = EditorGeometry.movePolygonVertex(RINGS, 0, listOf(1.0, 1.0))

        assertEquals(listOf(1.0, 1.0), moved[0].first())
        assertEquals(listOf(1.0, 1.0), moved[0].last())
        // Middle vertices untouched
        assertEquals(listOf(4.0, 0.0), moved[0][1])
        assertEquals(listOf(4.0, 4.0), moved[0][2])
    }

    @Test
    fun `moving the closing coordinate also moves the first vertex`() {
        val lastIndex = CLOSED_SQUARE.size - 1
        val moved = EditorGeometry.movePolygonVertex(RINGS, lastIndex, listOf(2.0, 3.0))

        assertEquals(listOf(2.0, 3.0), moved[0].first())
        assertEquals(listOf(2.0, 3.0), moved[0].last())
    }

    @Test
    fun `moving a middle vertex keeps closure intact`() {
        val moved = EditorGeometry.movePolygonVertex(RINGS, 2, listOf(9.0, 9.0))

        assertEquals(moved[0].first(), moved[0].last())
        assertEquals(listOf(9.0, 9.0), moved[0][2])
    }

    @Test
    fun `moving an out-of-range vertex is a no-op`() {
        assertEquals(RINGS, EditorGeometry.movePolygonVertex(RINGS, 99, listOf(1.0, 1.0)))
        assertEquals(RINGS, EditorGeometry.movePolygonVertex(RINGS, -1, listOf(1.0, 1.0)))
    }

    @Test
    fun `moving a vertex on empty rings is a no-op`() {
        assertEquals(
            emptyList<List<List<Double>>>(),
            EditorGeometry.movePolygonVertex(emptyList(), 0, listOf(1.0, 1.0)),
        )
    }

    @Test
    fun `moving vertices does not mutate the input rings`() {
        val original = RINGS.map { it.map { coord -> coord.toList() } }
        EditorGeometry.movePolygonVertex(RINGS, 0, listOf(7.0, 7.0))

        assertEquals(original, RINGS)
    }

    // endregion

    // region removePolygonVertex

    @Test
    fun `removing first vertex re-closes the ring around the new first point`() {
        // [A,B,C,D,A] -> remove A -> [B,C,D] -> re-close -> [B,C,D,B]
        val updated = EditorGeometry.removePolygonVertex(RINGS, 0)

        assertEquals(
            listOf(
                listOf(4.0, 0.0),
                listOf(4.0, 4.0),
                listOf(0.0, 4.0),
                listOf(4.0, 0.0),
            ),
            updated[0],
        )
    }

    @Test
    fun `removing a middle vertex keeps the ring closed`() {
        val updated = EditorGeometry.removePolygonVertex(RINGS, 1)

        assertEquals(updated[0].first(), updated[0].last())
        assertEquals(4, updated[0].size)
    }

    @Test
    fun `cannot remove below three unique points plus closing coordinate`() {
        // [A,B,C,A]: removing any vertex would leave only 2 unique points
        val triangle = listOf(
            listOf(0.0, 0.0),
            listOf(4.0, 0.0),
            listOf(4.0, 4.0),
            listOf(0.0, 0.0),
        )

        assertEquals(listOf(triangle), EditorGeometry.removePolygonVertex(listOf(triangle), 0))
        assertEquals(listOf(triangle), EditorGeometry.removePolygonVertex(listOf(triangle), 1))
    }

    @Test
    fun `closing coordinate can never be removed directly`() {
        val lastIndex = CLOSED_SQUARE.size - 1

        assertEquals(RINGS, EditorGeometry.removePolygonVertex(RINGS, lastIndex))
    }

    @Test
    fun `out-of-range removal is a no-op`() {
        assertEquals(RINGS, EditorGeometry.removePolygonVertex(RINGS, 99))
        assertEquals(RINGS, EditorGeometry.removePolygonVertex(RINGS, -1))
    }

    // endregion

    // region rotatePoint

    @Test
    fun `quarter rotation about origin maps east to north`() {
        val rotated = EditorGeometry.rotatePoint(LngLat(1.0, 0.0), LngLat(0.0, 0.0), PI / 2)

        assertEquals(0.0, rotated.longitude, 1e-9)
        assertEquals(1.0, rotated.latitude, 1e-9)
    }

    @Test
    fun `full rotation returns the original point`() {
        val rotated = EditorGeometry.rotatePoint(LngLat(3.0, 2.0), LngLat(0.0, 0.0), 2 * PI)

        assertEquals(3.0, rotated.longitude, 1e-9)
        assertEquals(2.0, rotated.latitude, 1e-9)
    }

    @Test
    fun `rotation preserves distance in projected space`() {
        val center = LngLat(10.0, 10.0)
        val point = LngLat(13.0, 14.0)
        val angle = 0.7

        // The rotation operates on equirectangular coordinates, so the
        // invariant to verify is Euclidean distance in that space
        val scale = cos(Math.toRadians(center.latitude))
        fun project(p: LngLat) = Pair((p.longitude - center.longitude) * scale, p.latitude - center.latitude)

        val (px, py) = project(point)
        val rotated = EditorGeometry.rotatePoint(point, center, angle)
        val (rx, ry) = project(rotated)

        assertEquals(sqrt(px * px + py * py), sqrt(rx * rx + ry * ry), 1e-9)
    }

    @Test
    fun `rotating the center itself is a no-op`() {
        val center = LngLat(5.0, 5.0)
        val rotated = EditorGeometry.rotatePoint(center, center, 1.234)

        assertEquals(center.longitude, rotated.longitude, 1e-12)
        assertEquals(center.latitude, rotated.latitude, 1e-12)
    }

    // endregion

    // region translate

    @Test
    fun `translating a point offsets both axes`() {
        val moved = EditorGeometry.translatePoint(listOf(1.0, 2.0), 3.0, -1.0)

        assertEquals(listOf(4.0, 1.0), moved)
    }

    @Test
    fun `translating a line moves every position`() {
        val moved = EditorGeometry.translateLine(
            listOf(listOf(0.0, 0.0), listOf(1.0, 1.0)),
            2.0,
            2.0,
        )

        assertEquals(listOf(listOf(2.0, 2.0), listOf(3.0, 3.0)), moved)
    }

    @Test
    fun `translating polygon rings keeps them closed`() {
        val moved = EditorGeometry.translatePolygonRings(RINGS, 1.5, -2.5)

        assertEquals(moved[0].first(), moved[0].last())
        assertEquals(listOf(1.5, -2.5), moved[0].first())
    }

    @Test
    fun `successive drag frames accumulate like a single combined translate`() {
        // DragEditor applies per-frame deltas to the current stored geometry;
        // two sequential translates must equal one translate of the summed delta
        val first = EditorGeometry.translatePolygonRings(RINGS, 1.0, 0.5)
        val twice = EditorGeometry.translatePolygonRings(first, 1.0, 0.5)
        val combined = EditorGeometry.translatePolygonRings(RINGS, 2.0, 1.0)

        assertEquals(combined, twice)
    }

    @Test
    fun `translating does not mutate the input rings`() {
        val original = RINGS.map { it.map { coord -> coord.toList() } }
        EditorGeometry.translatePolygonRings(RINGS, 9.0, 9.0)

        assertEquals(original, RINGS)
    }

    @Test
    fun `midpoint averages both axes`() {
        val mid = EditorGeometry.midpoint(listOf(0.0, 0.0), listOf(4.0, 2.0))

        assertEquals(listOf(2.0, 1.0), mid)
    }

    @Test
    fun `midpoint of a point with itself is the point`() {
        val mid = EditorGeometry.midpoint(listOf(3.5, -2.5), listOf(3.5, -2.5))

        assertEquals(listOf(3.5, -2.5), mid)
    }

    @Test
    fun `midpoint across the antimeridian stays on the dateline`() {
        // Naive averaging would place the midpoint at longitude 0
        val mid = EditorGeometry.midpoint(listOf(179.5, 10.0), listOf(-179.5, 10.0))

        assertEquals(180.0, Math.abs(mid[0]), 1e-9)
        assertEquals(10.0, mid[1], 1e-9)
    }

    @Test
    fun `antimeridian midpoint is symmetric in argument order`() {
        val ab = EditorGeometry.midpoint(listOf(170.0, 0.0), listOf(-170.0, 4.0))
        val ba = EditorGeometry.midpoint(listOf(-170.0, 4.0), listOf(170.0, 0.0))

        assertEquals(ab, ba)
    }

    // endregion
}
