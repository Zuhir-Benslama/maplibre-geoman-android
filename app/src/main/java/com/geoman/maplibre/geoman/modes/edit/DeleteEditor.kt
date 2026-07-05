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

    private var hoverFeature: FeatureData? = null

    override fun enable() {
        super.enable()
        // Could set cursor to indicate delete mode
    }

    override fun disable() {
        hoverFeature = null
        super.disable()
    }

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
        removeFromMap(feature)
    }

    private fun removeFromMap(feature: FeatureData) {
        // Remove associated layers and markers
        // This is a simplified implementation
        when (feature.sourceName) {
            GeomanCoreConstants.SOURCE_MARKERS -> {
                // Remove marker
            }

            GeomanCoreConstants.SOURCE_LINES -> {
                // Remove line layer
            }

            GeomanCoreConstants.SOURCE_POLYGONS -> {
                // Remove polygon layer
            }

            GeomanCoreConstants.SOURCE_CIRCLES -> {
                // Remove circle layer
            }

            GeomanCoreConstants.SOURCE_RECTANGLES -> {
                // Remove rectangle layer
            }
        }
    }

    /**
     * Preview delete by highlighting the feature
     */
    fun hoverOverFeature(feature: FeatureData?) {
        // Remove highlight from previous hover feature
        hoverFeature?.let { clearHighlight(it) }

        // Highlight new hover feature
        feature?.let { highlightFeature(it) }

        hoverFeature = feature
    }

    private fun highlightFeature(@Suppress("UNUSED_PARAMETER") feature: FeatureData) {
        // Change style to indicate feature will be deleted
        // e.g., change color to red
    }

    private fun clearHighlight(@Suppress("UNUSED_PARAMETER") feature: FeatureData) {
        // Restore original style
    }
}
