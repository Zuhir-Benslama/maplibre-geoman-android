package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.GeometryCollection
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.MultiPoint
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyValidatorsTest {

    private fun feature(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry, id: String? = "id") =
        Feature(id = id, geometry = geometry)

    // region validateFeature / validateFeatureId

    @Test
    fun `valid point feature passes`() {
        val result = PropertyValidators.validateFeature(
            feature(Point.fromLngLat(com.geoman.maplibre.geoman.types.geojson.LngLat(10.0, 20.0))),
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `null blank and oversized ids are rejected`() {
        assertFalse(PropertyValidators.validateFeatureId(null).isEmpty())
        assertFalse(PropertyValidators.validateFeatureId("   ").isEmpty())
        assertFalse(PropertyValidators.validateFeatureId("x".repeat(129)).isEmpty())
        assertTrue(PropertyValidators.validateFeatureId("ok-id").isEmpty())
    }

    // endregion

    // region validateCoordinate

    @Test
    fun `non-finite coordinates are rejected`() {
        assertFalse(PropertyValidators.validateCoordinate(Double.NaN, 0.0).isEmpty())
        assertFalse(PropertyValidators.validateCoordinate(0.0, Double.POSITIVE_INFINITY).isEmpty())
    }

    @Test
    fun `latitudes beyond plus minus 90 are rejected`() {
        assertFalse(PropertyValidators.validateCoordinate(0.0, 90.5).isEmpty())
        assertFalse(PropertyValidators.validateCoordinate(0.0, -91.0).isEmpty())
        assertTrue(PropertyValidators.validateCoordinate(0.0, 90.0).isEmpty())
    }

    @Test
    fun `longitudes slightly beyond plus minus 180 are tolerated for antimeridian data`() {
        assertTrue(PropertyValidators.validateCoordinate(185.0, 0.0).isEmpty())
        assertTrue(PropertyValidators.validateCoordinate(-190.0, 0.0).isEmpty())
    }

    @Test
    fun `absurd longitudes are rejected`() {
        assertFalse(PropertyValidators.validateCoordinate(541.0, 0.0).isEmpty())
        assertFalse(PropertyValidators.validateCoordinate(-1000.0, 0.0).isEmpty())
    }

    // endregion

    // region validateGeometry

    @Test
    fun `linestring requires at least two positions`() {
        val short = LineString(coordinates = listOf(listOf(0.0, 0.0)))

        val errors = PropertyValidators.validateGeometry(short)

        assertTrue(errors.any { it.contains("at least 2 positions") })
    }

    @Test
    fun `unclosed polygon ring is rejected`() {
        val open = Polygon(
            coordinates = listOf(
                listOf(
                    listOf(0.0, 0.0),
                    listOf(4.0, 0.0),
                    listOf(4.0, 4.0),
                    listOf(0.0, 4.0),
                ),
            ),
        )

        val errors = PropertyValidators.validateGeometry(open)

        assertTrue(errors.any { it.contains("not closed") })
    }

    @Test
    fun `closed polygon ring passes`() {
        val closed = Polygon(
            coordinates = listOf(
                listOf(
                    listOf(0.0, 0.0),
                    listOf(4.0, 0.0),
                    listOf(4.0, 4.0),
                    listOf(0.0, 4.0),
                    listOf(0.0, 0.0),
                ),
            ),
        )

        assertTrue(PropertyValidators.validateGeometry(closed).isEmpty())
    }

    @Test
    fun `invalid coordinate inside a polygon is reported with its position`() {
        val polygon = Polygon(
            coordinates = listOf(
                listOf(
                    listOf(0.0, 0.0),
                    listOf(4.0, Double.NaN),
                    listOf(4.0, 4.0),
                    listOf(0.0, 0.0),
                ),
            ),
        )

        val errors = PropertyValidators.validateGeometry(polygon)

        assertTrue(errors.any { it.startsWith("position 1:") && it.contains("finite") })
    }

    @Test
    fun `multipoint validates each position`() {
        val multi = MultiPoint(coordinates = listOf(listOf(0.0, 0.0), listOf(Double.NaN, 0.0)))

        val errors = PropertyValidators.validateGeometry(multi)

        assertTrue(errors.any { it.startsWith("position 1:") })
    }

    @Test
    fun `multipolygon rings are validated`() {
        val multi = MultiPolygon(
            coordinates = listOf(
                listOf(
                    listOf(
                        listOf(0.0, 0.0),
                        listOf(1.0, 0.0),
                        listOf(1.0, 1.0),
                        listOf(0.0, 1.0),
                    ),
                ),
            ),
        )

        val errors = PropertyValidators.validateGeometry(multi)

        assertTrue(errors.any { it.contains("multipolygon 0 ring 0") && it.contains("not closed") })
    }

    @Test
    fun `geometry collection recurses into child geometries`() {
        val collection = GeometryCollection(
            geometries = listOf(
                Point.fromLngLat(com.geoman.maplibre.geoman.types.geojson.LngLat(0.0, 95.0)),
            ),
        )

        val errors = PropertyValidators.validateGeometry(collection)

        assertTrue(errors.any { it.contains("latitude") })
    }

    // endregion

    // region validateFeature aggregation

    @Test
    fun `validateFeature aggregates id and geometry errors`() {
        val result = PropertyValidators.validateFeature(feature(LineString(coordinates = emptyList()), id = null))

        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
    }

    // endregion
}
