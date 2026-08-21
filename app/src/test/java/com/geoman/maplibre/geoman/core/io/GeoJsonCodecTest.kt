package com.geoman.maplibre.geoman.core.io

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonCodecTest {

    private fun pointData(id: String, lng: Double, lat: Double) = FeatureData(
        id = id,
        sourceName = "gm_markers",
        feature = com.geoman.maplibre.geoman.types.geojson.Feature(
            id = id,
            geometry = Point.fromLngLat(com.geoman.maplibre.geoman.types.geojson.LngLat(lng, lat)),
            properties = mapOf("name" to "test", "weight" to 2.5, "active" to true),
        ),
        properties = emptyMap(),
    )

    @Test
    fun `encode and decode round-trips a feature collection`() {
        val original = listOf(pointData("a", 1.0, 2.0), pointData("b", -3.0, 4.5))

        val json = GeoJsonCodec.encodeFeatureCollection(original)
        val result = GeoJsonCodec.decode(json, "gm_markers")

        assertTrue(result.isSuccess)
        assertEquals(2, result.features.size)
        assertEquals("a", result.features[0].id)
        assertEquals(1.0, (result.features[0].geometry as Point).coordinates[0], 1e-9)
        assertEquals(4.5, (result.features[1].geometry as Point).coordinates[1], 1e-9)
    }

    @Test
    fun `properties survive a round-trip`() {
        val json = GeoJsonCodec.encodeFeatureCollection(listOf(pointData("a", 1.0, 2.0)))

        val result = GeoJsonCodec.decode(json, "gm_markers")

        val properties = result.features[0].feature.properties
        assertEquals("test", properties["name"])
        assertEquals(true, properties["active"])
        assertEquals(2.5, properties["weight"])
    }

    @Test
    fun `polygon features round-trip with closed rings`() {
        val polygon = FeatureData(
            id = "poly",
            sourceName = "gm_polygons",
            feature = com.geoman.maplibre.geoman.types.geojson.Feature(
                id = "poly",
                geometry = Polygon(
                    coordinates = listOf(
                        listOf(
                            listOf(0.0, 0.0),
                            listOf(4.0, 0.0),
                            listOf(4.0, 4.0),
                            listOf(0.0, 0.0),
                        ),
                    ),
                ),
            ),
        )

        val result = GeoJsonCodec.decode(GeoJsonCodec.encodeFeatureCollection(listOf(polygon)), "gm_polygons")

        assertTrue(result.isSuccess)
        val decoded = result.features[0].geometry as Polygon
        assertEquals(listOf(0.0, 0.0), decoded.coordinates[0].first())
        assertEquals(listOf(0.0, 0.0), decoded.coordinates[0].last())
    }

    @Test
    fun `decoding a single feature document works`() {
        val json = GeoJsonCodec.encodeFeature(pointData("solo", 5.0, 6.0))

        val result = GeoJsonCodec.decode(json, "gm_lines")

        assertTrue(result.isSuccess)
        assertEquals(1, result.features.size)
        assertEquals("solo", result.features[0].id)
    }

    @Test
    fun `invalid json is reported without throwing`() {
        val result = GeoJsonCodec.decode("not json at all {", "gm_markers")

        assertFalse(result.isSuccess)
        assertEquals(1, result.errors.size)
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun `non-geojson documents are rejected`() {
        val result = GeoJsonCodec.decode("""{"type":"NotGeoJson"}""", "gm_markers")

        assertFalse(result.isSuccess)
        assertTrue(result.errors.any { it.message.contains("FeatureCollection or Feature") })
    }

    @Test
    fun `batch import reports per-feature errors and keeps valid features`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "good",
                  "geometry": {"type": "Point", "coordinates": [1.0, 2.0]},
                  "properties": {}
                },
                {
                  "type": "Feature",
                  "id": "bad-lat",
                  "geometry": {"type": "Point", "coordinates": [1.0, 200.0]},
                  "properties": {}
                },
                {
                  "type": "Feature",
                  "id": "unclosed",
                  "geometry": {"type": "Polygon", "coordinates": [[[0,0],[1,0],[1,1],[0,1]]]},
                  "properties": {}
                }
              ]
            }
        """.trimIndent()

        val result = GeoJsonCodec.decode(json, "gm_markers")

        assertFalse(result.isSuccess)
        assertEquals(1, result.features.size)
        assertEquals("good", result.features[0].id)
        assertEquals(2, result.errors.size)
        assertEquals(1, result.errors[0].index)
        assertEquals(2, result.errors[1].index)
        assertTrue(result.errors[1].message.contains("closed"))
    }

    @Test
    fun `malformed feature entries are reported by index`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {"type": "Feature"},
                {"type": "Feature", "id": "ok", "geometry": {"type": "Point", "coordinates": [0.0, 0.0]}}
              ]
            }
        """.trimIndent()

        val result = GeoJsonCodec.decode(json, "gm_markers")

        assertEquals(1, result.features.size)
        assertTrue(result.errors.any { it.index == 0 && it.message.contains("malformed") })
    }

    @Test
    fun `linestring geometries round-trip`() {
        val line = FeatureData(
            id = "line",
            sourceName = "gm_lines",
            feature = com.geoman.maplibre.geoman.types.geojson.Feature(
                id = "line",
                geometry = LineString(coordinates = listOf(listOf(0.0, 0.0), listOf(1.0, 1.0))),
            ),
        )

        val result = GeoJsonCodec.decode(GeoJsonCodec.encodeFeatureCollection(listOf(line)), "gm_lines")

        assertTrue(result.isSuccess)
        val decoded = result.features[0].geometry as LineString
        assertEquals(2, decoded.coordinates.size)
    }

    @Test
    fun `export embeds gm id and shape system properties`() {
        val polygon = FeatureData(
            id = "poly",
            sourceName = "gm_polygons",
            feature = com.geoman.maplibre.geoman.types.geojson.Feature(
                id = "poly",
                geometry = Polygon(
                    coordinates = listOf(
                        listOf(listOf(0.0, 0.0), listOf(4.0, 0.0), listOf(4.0, 4.0), listOf(0.0, 0.0)),
                    ),
                ),
            ),
            shape = com.geoman.maplibre.geoman.core.features.FeatureShape.POLYGON,
        )

        val json = GeoJsonCodec.encodeFeature(polygon)

        assertTrue(json.contains("__gm_id"))
        assertTrue(json.contains("poly"))
        assertTrue(json.contains("__gm_shape"))
        assertTrue(json.contains("polygon"))
    }

    @Test
    fun `import restores shape and strips system properties`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "tracked",
                  "geometry": {"type": "Point", "coordinates": [1.0, 2.0]},
                  "properties": {"__gm_id": "tracked", "__gm_shape": "circle_marker", "name": "kept"}
                }
              ]
            }
        """.trimIndent()

        val result = GeoJsonCodec.decode(json, "gm_markers")

        assertEquals(1, result.features.size)
        val imported = result.features[0]
        assertEquals(com.geoman.maplibre.geoman.core.features.FeatureShape.CIRCLE_MARKER, imported.shape)
        assertFalse(imported.feature.properties.containsKey("__gm_shape"))
        assertFalse(imported.properties.containsKey("__gm_id"))
        assertEquals("kept", imported.feature.properties["name"])
    }

    @Test
    fun `import tolerates unknown shape tags`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "odd",
                  "geometry": {"type": "Point", "coordinates": [1.0, 2.0]},
                  "properties": {"__gm_shape": "hypercube"}
                }
              ]
            }
        """.trimIndent()

        val result = GeoJsonCodec.decode(json, "gm_markers")

        assertEquals(1, result.features.size)
        assertNull(result.features[0].shape)
    }
}
