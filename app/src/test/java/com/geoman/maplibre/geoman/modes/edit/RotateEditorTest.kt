package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotateEditorTest : EditModeTestBase() {

    private fun rounded(coords: List<List<Double>>): List<List<Double>> = coords.map { coord ->
        coord.map {
            Math.round(it * 1e9) / 1e9
        }
    }

    @Test
    fun `rotating a line applies the pointer delta around the centroid`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        var rotateStart = false
        var rotateEnd: GmEditEvent.RotateEnd? = null
        geoman.events.on(GmEditEvent.RotateStart().type) { rotateStart = true }
        geoman.events.on(GmEditEvent.RotateEnd().type) { rotateEnd = it as GmEditEvent.RotateEnd }

        val editor = RotateEditor(geoman)
        editor.enable()

        // Click on the centroid starts the rotation (pointer angle 0)
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))
        assertTrue(rotateStart)

        // Pointer straight north of the centroid -> +90°
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 1.0))

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(listOf(listOf(5.0, -5.0), listOf(5.0, 5.0)), rounded(stored.coordinates))

        editor.disable()
        assertNotNull(rotateEnd)
        assertEquals("line", rotateEnd!!.feature?.id)
    }

    @Test
    fun `rotation with an unchanged pointer angle is a no-op`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        val editor = RotateEditor(geoman)
        editor.enable()

        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)), stored.coordinates)
        assertFalse(geoman.history.canUndo)
    }

    @Test
    fun `point features are ignored because they have no orientation`() {
        val point = FeatureData(
            id = "p",
            sourceName = "gm_markers",
            feature = Feature(
                id = "p",
                geometry = Point.fromLngLat(LngLat(5.0, 5.0)),
            ),
        )
        geoman.features.addFeature(point)
        geoman.mapActions.queryResult = listOf(point)

        var rotateStart = false
        geoman.events.on(GmEditEvent.RotateStart().type) { rotateStart = true }

        val editor = RotateEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 5.0))

        assertFalse(rotateStart)
        assertFalse(geoman.history.canUndo)
    }

    @Test
    fun `rotating a polygon keeps the ring closed`() {
        val ring = listOf(
            listOf(0.0, 0.0),
            listOf(4.0, 0.0),
            listOf(4.0, 4.0),
            listOf(0.0, 4.0),
            listOf(0.0, 0.0),
        )
        val polygon = FeatureData(
            id = "square",
            sourceName = "gm_polygons",
            feature = Feature(id = "square", geometry = Polygon(coordinates = listOf(ring))),
        )
        geoman.features.addFeature(polygon)
        geoman.mapActions.queryResult = listOf(polygon)

        val editor = RotateEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 2.0, latitude = 2.0)) // start at the centroid
        editor.onMapClick(LngLat(longitude = 2.0, latitude = 3.0)) // pointer north -> positive rotation

        val storedRing = (geoman.features.getFeature("gm_polygons", "square")!!.geometry as Polygon).coordinates[0]
        assertEquals(storedRing.first(), storedRing.last())
        // The ring geometry changed relative to the source polygon
        assertTrue(storedRing != ring)
    }

    @Test
    fun `rotation is ignored while disabled`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        val editor = RotateEditor(geoman)
        editor.enable()
        editor.disable()
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)), stored.coordinates)
        assertFalse(geoman.history.canUndo)
    }
}
