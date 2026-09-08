package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.history.SplitChange
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutEditorTest : EditModeTestBase() {

    @Test
    fun `cut splits a line at the nearest point on the clicked segment`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        var cutStart = false
        var cutEnd: GmEditEvent.CutEnd? = null
        geoman.events.on(GmEditEvent.CutStart().type) { cutStart = true }
        geoman.events.on(GmEditEvent.CutEnd().type) { cutEnd = it as GmEditEvent.CutEnd }

        val editor = CutEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))

        val parts = geoman.features.getFeatures("gm_lines").values.filter { it.id.startsWith("cut") }
        assertEquals(2, parts.size)
        val coords = parts.map { (it.geometry as LineString).coordinates }
        assertTrue(coords.contains(listOf(listOf(0.0, 0.0), listOf(5.0, 0.0))))
        assertTrue(coords.contains(listOf(listOf(5.0, 0.0), listOf(10.0, 0.0))))
        assertNull(geoman.features.getFeature("gm_lines", "line"))
        assertTrue(geoman.history.canUndo)
        assertTrue(geoman.history.undo() is SplitChange)
        assertTrue(cutStart)
        assertNotNull(cutEnd)
        val endFeature = cutEnd!!.feature!!
        assertEquals(endFeature, parts.first { it.id == endFeature.id })
    }

    @Test
    fun `cut at an endpoint aborts and leaves the source line intact`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        val editor = CutEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 0.0, latitude = 0.0))

        assertNotNull(geoman.features.getFeature("gm_lines", "line"))
        assertTrue(geoman.features.getFeatures("gm_lines").values.none { it.id.startsWith("cut") })
        assertFalse(geoman.history.canUndo)
    }

    @Test
    fun `cut ignores a hit that is not a line`() {
        val polygon = polygonData(
            id = "poly",
            ring = listOf(
                listOf(0.0, 0.0),
                listOf(4.0, 0.0),
                listOf(4.0, 4.0),
                listOf(0.0, 4.0),
                listOf(0.0, 0.0),
            ),
        )
        geoman.features.addFeature(polygon)
        geoman.mapActions.queryResult = listOf(polygon)

        val editor = CutEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 2.0, latitude = 2.0))

        assertNotNull(geoman.features.getFeature("gm_polygons", "poly"))
        assertFalse(geoman.history.canUndo)
    }

    @Test
    fun `cut does nothing when nothing is hit`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = emptyList()

        val editor = CutEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 5.0))

        assertNotNull(geoman.features.getFeature("gm_lines", "line"))
        assertFalse(geoman.history.canUndo)
    }

    @Test
    fun `cut is ignored while disabled`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(10.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        val editor = CutEditor(geoman)
        editor.enable()
        editor.disable()
        editor.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))

        assertNotNull(geoman.features.getFeature("gm_lines", "line"))
        assertFalse(geoman.history.canUndo)
        assertTrue(geoman.features.getFeatures("gm_lines").values.none { it.id.startsWith("cut") })
    }

    private fun polygonData(id: String, ring: List<List<Double>>) = FeatureData(
        id = id,
        sourceName = "gm_polygons",
        feature = Feature(id = id, geometry = Polygon(coordinates = listOf(ring))),
    )
}
