package com.geoman.maplibre.geoman.modes.edit

import androidx.annotation.MainThread
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureSources
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.launch

/**
 * Delete editing mode - allows deleting features by clicking on them
 */
open class DeleteEditor(geoman: GeomanApi) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.DELETE.name

    @MainThread
    override fun onMapClick(point: LngLat) {
        if (!enabled) return

        val features = queryFeaturesAt(
            point,
            FeatureSources.ALL_EDITABLE,
        )

        if (features.isNotEmpty()) {
            deleteFeature(features.first())
        }
    }

    /**
     * Delete a feature
     */
    private fun deleteFeature(feature: FeatureData) {
        // Remove first so the Delete event unambiguously carries the deleted
        // feature; event delivery stays async via the scope.
        geoman.features.removeFeature(feature.sourceName, feature.id)
        geoman.scope.launch {
            fireEditEvent({ GmEditEvent.Delete(it) }, feature)
        }
    }
}
