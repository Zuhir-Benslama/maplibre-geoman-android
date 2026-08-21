package com.geoman.maplibre.geoman.core.history

import com.geoman.maplibre.geoman.types.geojson.Geometry

/**
 * A single geometry change: what a feature's geometry looked like before and
 * after an edit operation.
 */
data class GeometryChange(val sourceName: String, val featureId: String, val before: Geometry, val after: Geometry)

/**
 * Undo/redo history for geometry edits.
 *
 * Editors record every applied geometry change through [record]. Undoing
 * returns a change whose `before` holds the geometry to restore; redoing
 * returns one whose `after` holds the geometry to re-apply.
 *
 * Purely mechanical — applying changes back to the store is the caller's job.
 */
class ChangeTracker(private val maxHistory: Int = DEFAULT_MAX_HISTORY) {

    private companion object {
        const val DEFAULT_MAX_HISTORY = 100
    }

    private val undoStack = ArrayDeque<GeometryChange>()
    private val redoStack = ArrayDeque<GeometryChange>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Record an applied change. Clears the redo stack, since a new edit makes
     * any redoable state stale.
     */
    fun record(change: GeometryChange) {
        if (change.before == change.after) return

        undoStack.addLast(change)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /**
     * Pop the most recent change; restore `change.before` to undo the edit.
     */
    fun undo(): GeometryChange? {
        val change = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(change)
        return change
    }

    /**
     * Pop the most recently undone change; apply `change.after` to redo it.
     */
    fun redo(): GeometryChange? {
        val change = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(change)
        return change
    }

    /**
     * Drop all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
