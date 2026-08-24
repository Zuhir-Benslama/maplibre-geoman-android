package com.geoman.maplibre.geoman.core.history

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Geometry

/**
 * A single geometry change: what a feature's geometry looked like before and
 * after an edit operation.
 */
data class GeometryChange(val sourceName: String, val featureId: String, val before: Geometry, val after: Geometry) :
    HistoryEntry

/**
 * A structural change that replaced one feature with several parts (a cut).
 * Undoing re-adds [original] and removes the parts; redoing reverses it.
 */
data class SplitChange(val sourceName: String, val original: Feature, val parts: List<Feature>) : HistoryEntry

/**
 * Undo/redo history entry for edits tracked by [ChangeTracker].
 */
sealed interface HistoryEntry

/**
 * Undo/redo history for feature edits.
 *
 * Editors record every applied change through [record]. Undoing returns an
 * entry describing the state to restore; redoing returns one describing the
 * state to re-apply. Applying changes back to the store is the caller's job.
 */
class ChangeTracker(private val maxHistory: Int = DEFAULT_MAX_HISTORY) {

    private companion object {
        const val DEFAULT_MAX_HISTORY = 100
    }

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Record an applied change. Clears the redo stack, since a new edit makes
     * any redoable state stale.
     */
    fun record(entry: HistoryEntry) {
        if (entry is GeometryChange && entry.before == entry.after) return
        if (entry is SplitChange && entry.parts.isEmpty()) return

        undoStack.addLast(entry)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /**
     * Pop the most recent entry; its "before" state undoes the edit.
     */
    fun undo(): HistoryEntry? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry
    }

    /**
     * Pop the most recently undone entry; its "after" state redoes the edit.
     */
    fun redo(): HistoryEntry? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry
    }

    /**
     * Drop all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
