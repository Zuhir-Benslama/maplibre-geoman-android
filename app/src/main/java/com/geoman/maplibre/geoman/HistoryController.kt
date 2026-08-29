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
 * [GeometryChange], original/parts swapping for [SplitChange]).
 */
class HistoryController(private val features: Features, private val history: ChangeTracker) {
    /**
     * Undo the most recent edit (geometry change or structural split).
     * Returns true when a change was restored.
     */
    fun undo(): Boolean {
        val entry = history.undo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.before)

            is SplitChange -> {
                // Restore the pre-cut state: drop the parts, re-add the original
                entry.parts.forEach { part ->
                    val partId = part.id ?: return@forEach
                    features.removeFeature(entry.sourceName, partId)
                }
                features.addGeoJsonFeature(entry.original, entry.sourceName)
            }
        }
        return true
    }

    /**
     * Re-apply the most recently undone edit. Returns true when a change was
     * restored.
     */
    fun redo(): Boolean {
        val entry = history.redo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.after)

            is SplitChange -> {
                // Re-apply the cut: remove the original, re-add the parts
                val originalId = entry.original.id
                if (originalId != null) {
                    features.removeFeature(entry.sourceName, originalId)
                }
                entry.parts.forEach { features.addGeoJsonFeature(it, entry.sourceName) }
            }
        }
        return true
    }

    private fun applyGeometry(sourceName: String, featureId: String, geometry: Geometry) {
        features.updateFeature(sourceName, featureId) { current ->
            current.copy(feature = current.feature.copy(geometry = geometry))
        }
    }
}
