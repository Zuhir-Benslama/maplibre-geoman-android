package com.geoman.maplibre.geoman.modes.edit

import android.view.MotionEvent
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.MarkerAnchor
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Drag editing mode - drags features by selecting them with a tap and then
 * dragging the handle that appears. The handle is a draggable DOM marker whose
 * callbacks drive the feature translation.
 */
class DragEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.DRAG.name

    private var isDragging = false
    private var dragStartPoint: LatLng? = null
    private var dragHandle: DomMarker? = null

    override fun enable() {
        super.enable()
    }

    override fun disable() {
        if (isDragging) {
            finishDrag()
        }
        dragHandle?.remove()
        dragHandle = null
        super.disable()
    }

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val clickPoint = LngLat(point.longitude, point.latitude)
        val features = geoman.mapAdapter.queryFeaturesByScreenCoordinates(
            geoman.mapAdapter.project(clickPoint),
            DRAG_SOURCES,
        )

        if (features.isNotEmpty()) {
            selectFeature(features.first())
            startDrag(point)
        } else {
            selectedFeature = null
        }
    }

    /**
     * Start dragging a feature.
     * Creates a draggable handle at the press point that drives the drag.
     */
    fun startDrag(point: LatLng) {
        if (!enabled || selectedFeature == null || isDragging) return

        val feature = selectedFeature ?: return
        isDragging = true
        dragStartPoint = point

        dragHandle?.remove()
        dragHandle = geoman.mapAdapter.createDomMarker(
            DomMarkerOptions(
                draggable = true,
                anchor = MarkerAnchor.CENTER,
            ),
            LngLat(point.longitude, point.latitude),
        ).also { handle ->
            handle.onDragStart = {
                geoman.scope.launch {
                    fireEditEvent({ GmEditEvent.DragStart(it) }, feature)
                }
            }
            handle.onDrag = { newLngLat ->
                dragTo(LatLng(newLngLat.latitude, newLngLat.longitude))
            }
            handle.onDragEnd = {
                finishDrag()
            }
            handle.addToMap()
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
        val feature = selectedFeature ?: return
        moveFeature(feature, deltaLon, deltaLat)

        dragStartPoint = point
    }

    /**
     * Finish dragging
     */
    fun finishDrag() {
        if (!isDragging) return

        isDragging = false
        dragStartPoint = null

        dragHandle?.remove()
        dragHandle = null

        selectedFeature?.let {
            geoman.scope.launch {
                fireEditEvent({ GmEditEvent.DragEnd(it) }, it)
            }
        }
    }

    /**
     * Consume touch events while a drag is in progress so the map does not pan
     * underneath the drag handle.
     */
    fun onTouchEvent(@Suppress("UNUSED_PARAMETER") event: MotionEvent): Boolean = isDragging

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

    private companion object {
        val DRAG_SOURCES = listOf(
            GeomanCoreConstants.SOURCE_MARKERS,
            GeomanCoreConstants.SOURCE_LINES,
            GeomanCoreConstants.SOURCE_POLYGONS,
            GeomanCoreConstants.SOURCE_CIRCLES,
            GeomanCoreConstants.SOURCE_RECTANGLES,
        )
    }
}
