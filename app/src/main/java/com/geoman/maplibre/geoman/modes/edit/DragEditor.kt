package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Drag editing mode - allows dragging features
 */
class DragEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.DRAG.name

    private var isDragging = false
    private var dragStartPoint: LatLng? = null

    override fun enable() {
        super.enable()
        // Set up drag listeners
    }

    override fun disable() {
        if (isDragging) {
            finishDrag()
        }
        super.disable()
    }

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        // Find feature at click point
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
            selectFeature(features.first())
        } else {
            selectedFeature = null
        }
    }

    /**
     * Start dragging a feature
     */
    fun startDrag(point: LatLng) {
        if (!enabled || selectedFeature == null || isDragging) return

        isDragging = true
        dragStartPoint = point

        geomanInstance.scope.launch {
            fireDragStartEvent(selectedFeature)
        }
    }

    /**
     * Continue dragging
     */
    fun dragTo(point: LatLng) {
        if (!enabled || !isDragging || selectedFeature == null) return

        val startPoint = dragStartPoint ?: return

        // Calculate offset
        val deltaLon = point.longitude - startPoint.longitude
        val deltaLat = point.latitude - startPoint.latitude

        // Move the feature
        moveFeature(selectedFeature!!, deltaLon, deltaLat)

        dragStartPoint = point
    }

    /**
     * Finish dragging
     */
    fun finishDrag() {
        if (!isDragging) return

        isDragging = false
        dragStartPoint = null

        geomanInstance.scope.launch {
            fireDragEndEvent(selectedFeature)
        }
    }

    private fun selectFeature(feature: FeatureData) {
        selectedFeature = feature
    }

    private fun moveFeature(feature: FeatureData, deltaLon: Double, deltaLat: Double) {
        val geometry = feature.geometry

        when (geometry) {
            is Point -> {
                val coords = geometry.coordinates
                val newLngLat = LngLat(coords[0] + deltaLon, coords[1] + deltaLat)
                val newGeometry = Point.fromLngLat(newLngLat)
                updateFeatureGeometry(feature, newGeometry)
            }

            is LineString -> {
                val newCoords = geometry.coordinates.map { coord ->
                    listOf(coord[0] + deltaLon, coord[1] + deltaLat)
                }
                val newGeometry = LineString(coordinates = newCoords)
                updateFeatureGeometry(feature, newGeometry)
            }

            is Polygon -> {
                val newRings = geometry.coordinates.map { ring ->
                    ring.map { coord ->
                        listOf(coord[0] + deltaLon, coord[1] + deltaLat)
                    }
                }
                val newGeometry = Polygon(coordinates = newRings)
                updateFeatureGeometry(feature, newGeometry)
            }

            else -> {
                // Unsupported geometry type
            }
        }
    }

    private fun updateFeatureGeometry(
        feature: FeatureData,
        newGeometry: com.geoman.maplibre.geoman.types.geojson.Geometry,
    ) {
        val updatedFeature = feature.copy(
            feature = feature.feature.copy(geometry = newGeometry),
        )

        geomanInstance.features.updateFeature(feature.sourceName, feature.id) {
            updatedFeature
        }
    }
}
