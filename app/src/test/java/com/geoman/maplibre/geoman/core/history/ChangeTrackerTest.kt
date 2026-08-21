package com.geoman.maplibre.geoman.core.history

import com.geoman.maplibre.geoman.types.geojson.LineString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeTrackerTest {

    private fun line(vararg coords: Double) = LineString(
        coordinates = coords.toList().chunked(2),
    )

    private fun change(before: LineString, after: LineString) = GeometryChange(
        sourceName = "gm_lines",
        featureId = "f1",
        before = before,
        after = after,
    )

    @Test
    fun `record then undo restores the previous geometry`() {
        val tracker = ChangeTracker()
        val original = line(0.0, 0.0, 1.0, 1.0)
        val moved = line(5.0, 5.0, 6.0, 6.0)

        tracker.record(change(original, moved))

        assertTrue(tracker.canUndo)
        assertEquals(original, tracker.undo()?.before)
        assertFalse(tracker.canUndo)
    }

    @Test
    fun `undo followed by redo returns the applied change`() {
        val tracker = ChangeTracker()
        val original = line(0.0, 0.0, 1.0, 1.0)
        val moved = line(5.0, 5.0, 6.0, 6.0)

        tracker.record(change(original, moved))
        tracker.undo()

        assertTrue(tracker.canRedo)
        assertEquals(moved, tracker.redo()?.after)
        assertFalse(tracker.canRedo)
    }

    @Test
    fun `multiple changes undo in reverse order`() {
        val tracker = ChangeTracker()
        val v1 = line(0.0, 0.0)
        val v2 = line(1.0, 1.0)
        val v3 = line(2.0, 2.0)

        tracker.record(change(v1, v2))
        tracker.record(change(v2, v3))

        assertEquals(v2, tracker.undo()?.before)
        assertEquals(v1, tracker.undo()?.before)
    }

    @Test
    fun `recording after an undo clears the redo stack`() {
        val tracker = ChangeTracker()
        val v1 = line(0.0, 0.0)
        val v2 = line(1.0, 1.0)
        val v3 = line(2.0, 2.0)

        tracker.record(change(v1, v2))
        tracker.undo()
        tracker.record(change(v2, v3))

        assertFalse(tracker.canRedo)
        assertNull(tracker.redo())
    }

    @Test
    fun `no-op changes are not recorded`() {
        val tracker = ChangeTracker()
        val same = line(0.0, 0.0)

        tracker.record(change(same, same))

        assertFalse(tracker.canUndo)
    }

    @Test
    fun `history is capped at maxHistory entries`() {
        val tracker = ChangeTracker(maxHistory = 3)

        repeat(5) { i ->
            tracker.record(change(line(i.toDouble(), 0.0), line((i + 1).toDouble(), 0.0)))
        }

        // Only the last 3 changes remain; the oldest is 2 -> 3
        var count = 0
        while (tracker.canUndo) {
            val change = tracker.undo()!!
            if (count == 2) {
                assertEquals(2.0, (change.before as LineString).coordinates[0][0], 1e-9)
            }
            count++
        }
        assertEquals(3, count)
    }

    @Test
    fun `clear drops all history`() {
        val tracker = ChangeTracker()
        tracker.record(change(line(0.0, 0.0), line(1.0, 1.0)))
        tracker.undo()

        tracker.clear()

        assertFalse(tracker.canUndo)
        assertFalse(tracker.canRedo)
    }

    @Test
    fun `undo on empty history returns null`() {
        assertNull(ChangeTracker().undo())
        assertNull(ChangeTracker().redo())
    }
}
