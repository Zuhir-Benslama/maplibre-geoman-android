package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Delete editing mode - allows deleting features by clicking on them
 */
class DeleteEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.DELETE.name

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val features = geomanInstance.mapAdapter.queryFeaturesByScreenCoordinates(
            geomanInstance.mapAdapter.project(LngLat(point.longitude, point.latitude)),
            listOf(
                GeomanCoreConstants.SOURCE_MARKERS,
                GeomanCoreConstants.SOURCE_LINES,
                GeomanCoreConstants.SOURCE_POLYGONS,
                GeomanCoreConstants.SOURCE_CIRCLES,
                GeomanCoreConstants.SOURCE_RECTANGLES,
            ),
        )

        if (features.isNotEmpty()) {
            deleteFeature(features.first())
        }
    }

    /**
     * Delete a feature
     */
    private fun deleteFeature(feature: FeatureData) {
        geomanInstance.scope.launch {
            fireDeleteEvent(feature)
        }

        geomanInstance.features.removeFeature(feature.sourceName, feature.id)
    }
}
