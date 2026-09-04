package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.history.GeometryChange
import com.geoman.maplibre.geoman.core.history.SplitChange
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryControllerTest {

    private lateinit var features: Features
    private lateinit var history: ChangeTracker
    private lateinit var controller: HistoryController

    private fun line(vararg coords: Double) = LineString(
        coordinates = coords.toList().chunked(2),
    )

    private fun addLine(id: String, geometry: LineString) {
        features.addGeoJsonFeature(
            Feature(id = id, geometry = geometry),
            "gm_lines",
        )
    }

    @Before
    fun setUp() {
        features = Features()
        history = ChangeTracker()
        controller = HistoryController(features, history)
    }

    @Test
    fun `undo restores previous geometry`() {
        addLine("f1", line(0.0, 0.0, 1.0, 1.0))

        val before = line(0.0, 0.0, 1.0, 1.0)
        val after = line(5.0, 5.0, 6.0, 6.0)
        history.record(GeometryChange("gm_lines", "f1", before, after))
        features.updateFeature("gm_lines", "f1") {
            it.copy(feature = it.feature.copy(geometry = after))
        }

        val result = controller.undo()

        assertTrue(result)
        val restored = features.getFeature("gm_lines", "f1")
        assertNotNull(restored)
        assertEquals(before, restored!!.geometry)
    }

    @Test
    fun `redo re-applies the change`() {
        addLine("f1", line(0.0, 0.0, 1.0, 1.0))

        val before = line(0.0, 0.0, 1.0, 1.0)
        val after = line(5.0, 5.0, 6.0, 6.0)
        history.record(GeometryChange("gm_lines", "f1", before, after))
        features.updateFeature("gm_lines", "f1") {
            it.copy(feature = it.feature.copy(geometry = after))
        }
        controller.undo()

        val result = controller.redo()

        assertTrue(result)
        val restored = features.getFeature("gm_lines", "f1")
        assertNotNull(restored)
        assertEquals(after, restored!!.geometry)
    }

    @Test
    fun `undo on empty history returns false`() {
        assertFalse(controller.undo())
        assertFalse(controller.redo())
    }

    @Test
    fun `multiple undo and redo in sequence`() {
        addLine("f1", line(0.0, 0.0, 1.0, 1.0))

        val v1 = line(0.0, 0.0, 1.0, 1.0)
        val v2 = line(1.0, 1.0, 2.0, 2.0)
        val v3 = line(2.0, 2.0, 3.0, 3.0)

        history.record(GeometryChange("gm_lines", "f1", v1, v2))
        features.updateFeature("gm_lines", "f1") {
            it.copy(feature = it.feature.copy(geometry = v2))
        }
        history.record(GeometryChange("gm_lines", "f1", v2, v3))
        features.updateFeature("gm_lines", "f1") {
            it.copy(feature = it.feature.copy(geometry = v3))
        }

        // Undo twice
        assertTrue(controller.undo())
        assertEquals(v2, features.getFeature("gm_lines", "f1")?.geometry)
        assertTrue(controller.undo())
        assertEquals(v1, features.getFeature("gm_lines", "f1")?.geometry)

        // Redo once
        assertTrue(controller.redo())
        assertEquals(v2, features.getFeature("gm_lines", "f1")?.geometry)
    }

    @Test
    fun `split change undo removes parts and restores original`() {
        val originalFeature = Feature(id = "line-1", geometry = line(0.0, 0.0, 4.0, 0.0))
        addLine("line-1", originalFeature.geometry as LineString)

        val part1 = Feature(id = "cut-a", geometry = line(0.0, 0.0, 2.0, 0.0))
        val part2 = Feature(id = "cut-b", geometry = line(2.0, 0.0, 4.0, 0.0))

        // Add parts
        features.addGeoJsonFeature(part1, "gm_lines")
        features.addGeoJsonFeature(part2, "gm_lines")
        // Remove original
        features.removeFeature("gm_lines", "line-1")

        // Record the split
        history.record(
            SplitChange(
                sourceName = "gm_lines",
                original = originalFeature,
                parts = listOf(part1, part2),
            ),
        )

        val result = controller.undo()

        assertTrue(result)
        // Parts should be removed
        assertNull(features.getFeature("gm_lines", "cut-a"))
        assertNull(features.getFeature("gm_lines", "cut-b"))
        // Original should be restored
        val restored = features.getFeature("gm_lines", "line-1")
        assertNotNull(restored)
        assertEquals(originalFeature.geometry, restored!!.geometry)
    }

    @Test
    fun `split change redo removes original and adds parts`() {
        val originalFeature = Feature(id = "line-1", geometry = line(0.0, 0.0, 4.0, 0.0))
        addLine("line-1", originalFeature.geometry as LineString)

        val part1 = Feature(id = "cut-a", geometry = line(0.0, 0.0, 2.0, 0.0))
        val part2 = Feature(id = "cut-b", geometry = line(2.0, 0.0, 4.0, 0.0))

        // Record the split
        history.record(
            SplitChange(
                sourceName = "gm_lines",
                original = originalFeature,
                parts = listOf(part1, part2),
            ),
        )

        // Undo the split first (restores original, removes parts)
        controller.undo()

        // Redo the split (removes original, adds parts)
        val result = controller.redo()

        assertTrue(result)
        // Original should be removed
        assertNull(features.getFeature("gm_lines", "line-1"))
        // Parts should be added
        assertNotNull(features.getFeature("gm_lines", "cut-a"))
        assertNotNull(features.getFeature("gm_lines", "cut-b"))
    }
}
