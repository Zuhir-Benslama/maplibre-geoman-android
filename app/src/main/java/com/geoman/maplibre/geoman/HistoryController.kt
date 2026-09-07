package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.history.GeometryChange
import com.geoman.maplibre.geoman.core.history.SplitChange
import com.geoman.maplibre.geoman.types.geojson.Geometry

/**
 * Owns the undo/redo history stack for [Geoman].
 *
 * [undo] and [redo] pop the appropriate entry from the [ChangeTracker] and
 * replay the corresponding [Features] mutation (geometry restore for
 * [GeometryChange], original/parts swapping for [SplitChange]). Every
 * mutation runs under a dedicated monitor so concurrent undo/redo calls can
 * never interleave two history entries' store writes.
 */
class HistoryController(private val features: Features, private val history: ChangeTracker) {
    private companion object {
        const val TAG = "Geoman"
    }

    private val lock = Any()

    /**
     * Undo the most recent edit (geometry change or structural split).
     * Returns true when a change was actually restored.
     */
    fun undo(): Boolean = synchronized(lock) {
        val entry = history.undo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.before)
            is SplitChange -> undoSplit(entry)
        }
    }

    /**
     * Re-apply the most recently undone edit. Returns true when a change was
     * actually re-applied.
     */
    fun redo(): Boolean = synchronized(lock) {
        val entry = history.redo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.after)
            is SplitChange -> redoSplit(entry)
        }
    }

    /**
     * Restore [geometry] for [featureId]. Returns false when the feature no
     * longer exists (its geometry cannot be restored).
     */
    private fun applyGeometry(sourceName: String, featureId: String, geometry: Geometry): Boolean =
        features.updateFeature(sourceName, featureId) { current ->
            current.copy(feature = current.feature.copy(geometry = geometry))
        }

    /**
     * Restore the pre-cut state: drop the parts, re-add the original. Returns
     * false when a part could not be located (the original is still re-added,
     * but the undo is reported as incomplete).
     */
    private fun undoSplit(entry: SplitChange): Boolean {
        var allPartsRemoved = true
        entry.parts.forEach { part ->
            val partId = part.id
            if (partId == null || features.removeFeature(entry.sourceName, partId) == null) {
                GeomanLogger.w(TAG, "undo: could not remove split part $partId while restoring $entry")
                allPartsRemoved = false
            }
        }
        features.addGeoJsonFeature(entry.original, entry.sourceName)
        return allPartsRemoved
    }

    /**
     * Re-apply the cut: remove the original, re-add the parts. Returns false
     * when the original was already gone, since re-adding the parts would
     * duplicate it.
     */
    private fun redoSplit(entry: SplitChange): Boolean {
        val originalId = entry.original.id
        if (originalId == null || features.removeFeature(entry.sourceName, originalId) == null) {
            GeomanLogger.w(TAG, "redo: original ${entry.original.id} not found; cannot re-apply cut")
            return false
        }
        entry.parts.forEach { features.addGeoJsonFeature(it, entry.sourceName) }
        return true
    }
}
