package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure snap-selection helpers in [SnapSelection]: the candidate
 * bounding box and the nearest-target selection used by [SnapHelper].
 */
class SnapSelectionTest {

    private fun pointData(id: String, source: String, lon: Double, lat: Double) = FeatureData(
        id = id,
        sourceName = source,
        feature = Feature(id = id, geometry = Point.fromLngLat(LngLat(lon, lat))),
    )

    private fun snapOf(feature: FeatureData): LngLat = (feature.geometry as Point).toLngLat()

    @Test
    fun `snapBounds covers one degree of latitude and cos-scaled longitude`() {
        val bounds = SnapSelection.snapBounds(LngLat(10.0, 20.0), 111195.0)

        // Moving one radius (≈111 km) north/south is almost exactly one degree
        // of latitude; east/west is cos(latitude)-scaled.
        assertEquals(19.0, bounds[0].latitude, 0.05)
        assertEquals(21.0, bounds[1].latitude, 0.05)
        assertEquals(10.0 - 111195.0 / (111195.0 * kotlin.math.cos(Math.toRadians(20.0))), bounds[0].longitude, 0.05)
        assertEquals(10.0 + 111195.0 / (111195.0 * kotlin.math.cos(Math.toRadians(20.0))), bounds[1].longitude, 0.05)
    }

    @Test
    fun `snapBounds keeps a point within the radius inside the box`() {
        val center = LngLat(50.0, -20.0)
        val radius = 2000.0
        val bounds = SnapSelection.snapBounds(center, radius)

        val inside = LngLat(center.longitude, center.latitude + 0.005)
        val minLon = bounds.minOf { it.longitude }
        val maxLon = bounds.maxOf { it.longitude }
        val minLat = bounds.minOf { it.latitude }
        val maxLat = bounds.maxOf { it.latitude }

        assertTrue(inside.longitude in minLon..maxLon)
        assertTrue(inside.latitude in minLat..maxLat)
    }

    @Test
    fun `snapBounds rejects a non-finite radius`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SnapSelection.snapBounds(LngLat(0.0, 0.0), Double.NaN)
        }
    }

    @Test
    fun `selectNearest returns the closest candidate within threshold`() {
        val near = pointData("near", "gm_markers", 10.0, 20.0)
        val far = pointData("far", "gm_markers", 12.0, 20.0)

        val result = SnapSelection.selectNearest(
            point = LngLat(10.0, 20.0),
            thresholdMeters = 500.0,
            candidates = listOf(far, near),
            snapOf = ::snapOf,
        )

        assertNotNull(result)
        assertEquals("near", result!!.feature.id)
        assertTrue(result.distanceMeters <= 500.0)
    }

    @Test
    fun `selectNearest returns null when everything is beyond threshold`() {
        val far = pointData("far", "gm_markers", 12.0, 20.0)

        assertNull(
            SnapSelection.selectNearest(
                point = LngLat(10.0, 20.0),
                thresholdMeters = 500.0,
                candidates = listOf(far),
                snapOf = ::snapOf,
            ),
        )
    }

    @Test
    fun `selectNearest skips features that do not snap`() {
        val passThrough = { _: FeatureData -> LngLat(10.0, 20.0) }

        val result = SnapSelection.selectNearest(
            point = LngLat(10.0, 20.0),
            thresholdMeters = 100.0,
            candidates = listOf(pointData("a", "gm_markers", 0.0, 0.0)),
            snapOf = passThrough,
        )

        assertNotNull(result)
        assertEquals("a", result!!.feature.id)
    }

    @Test
    fun `bounds-limiting excludes features outside the snap radius`() {
        val features = Features()
        val near = pointData("near", "gm_markers", 10.0, 20.0)
        val far = pointData("far", "gm_markers", 10.0, 21.0)
        features.addFeature(near)
        features.addFeature(far)

        val candidates = features.getFeaturesInBounds(
            bounds = SnapSelection.snapBounds(LngLat(10.0, 20.0), 1000.0),
            sourceNames = listOf("gm_markers"),
        )

        assertEquals(listOf("near"), candidates.map { it.id })
    }
}
