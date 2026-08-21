package com.geoman.maplibre.geoman.core.markers

import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointClustererTest {

    @Test
    fun `empty input produces no clusters`() {
        assertTrue(PointClusterer().cluster(emptyList()).isEmpty())
    }

    @Test
    fun `single point becomes a single-member cluster`() {
        val clusters = PointClusterer().cluster(listOf("a" to LngLat(1.0, 2.0)))

        assertEquals(1, clusters.size)
        assertEquals(listOf("a"), clusters[0].featureIds)
        assertEquals(1, clusters[0].count)
        assertEquals(1.0, clusters[0].position.longitude, 1e-9)
        assertEquals(2.0, clusters[0].position.latitude, 1e-9)
    }

    @Test
    fun `nearby points merge into one cluster at the mean position`() {
        val clusters = PointClusterer().cluster(
            listOf(
                "a" to LngLat(0.0, 0.0),
                "b" to LngLat(0.1, 0.1),
                "c" to LngLat(0.2, 0.2),
            ),
        )

        assertEquals(1, clusters.size)
        assertEquals(listOf("a", "b", "c"), clusters[0].featureIds)
        assertEquals(3, clusters[0].count)
        assertEquals(0.1, clusters[0].position.longitude, 1e-9)
        assertEquals(0.1, clusters[0].position.latitude, 1e-9)
    }

    @Test
    fun `distant points stay in separate clusters`() {
        val clusters = PointClusterer().cluster(
            listOf(
                "a" to LngLat(0.0, 0.0),
                "b" to LngLat(10.0, 10.0),
            ),
        )

        assertEquals(2, clusters.size)
        assertEquals(listOf("a"), clusters.find { it.count == 1 && it.position.longitude == 0.0 }?.featureIds)
        assertEquals(listOf("b"), clusters.find { it.count == 1 && it.position.longitude == 10.0 }?.featureIds)
    }

    @Test
    fun `cell size controls the merge distance`() {
        val points = listOf(
            "a" to LngLat(0.0, 0.0),
            "b" to LngLat(0.4, 0.4),
        )

        // Default 0.5° grid: both fall into cell (0,0)
        assertEquals(1, PointClusterer().cluster(points).size)

        // 0.25° grid: separate cells
        assertEquals(2, PointClusterer(cellSizeDegrees = 0.25).cluster(points).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive cell size is rejected`() {
        PointClusterer(cellSizeDegrees = 0.0)
    }
}
