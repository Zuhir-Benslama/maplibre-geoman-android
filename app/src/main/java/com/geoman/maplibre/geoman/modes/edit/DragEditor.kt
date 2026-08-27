package com.geoman.maplibre.geoman.modes.edit

import android.view.MotionEvent
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.MarkerAnchor
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureSources
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
open class DragEditor(geoman: GeomanApi) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.DRAG.name

    private var isDragging = false
    private var dragStartPoint: LatLng? = null
    private var dragHandle: DomMarker? = null

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
        val features = queryFeaturesAt(clickPoint, DRAG_SOURCES)

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
        dragHandle = createDomMarkerAt(
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
        val feature = selectedFeature ?: return

        // Calculate offset
        val deltaLon = point.longitude - startPoint.longitude
        val deltaLat = point.latitude - startPoint.latitude

        // Apply the incremental delta to the *current* stored geometry so that
        // successive drag frames accumulate instead of overwriting each other
        selectedFeature = moveFeature(feature, deltaLon, deltaLat)

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

        selectedFeature?.let { stale ->
            val current = refreshFeature(stale) ?: stale
            geoman.scope.launch {
                fireEditEvent({ GmEditEvent.DragEnd(it) }, current)
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

    private fun moveFeature(feature: FeatureData, deltaLon: Double, deltaLat: Double): FeatureData? =
        updateFeatureGeometry(feature) { geometry ->
            when (geometry) {
                is Point -> Point(
                    coordinates = EditorGeometry.translatePoint(geometry.coordinates, deltaLon, deltaLat),
                )

                is LineString -> LineString(
                    coordinates = EditorGeometry.translateLine(geometry.coordinates, deltaLon, deltaLat),
                )

                is Polygon -> Polygon(
                    coordinates = EditorGeometry.translatePolygonRings(geometry.coordinates, deltaLon, deltaLat),
                )

                else -> geometry
            }
        }

    private companion object {
        val DRAG_SOURCES = FeatureSources.ALL_EDITABLE
    }
}
