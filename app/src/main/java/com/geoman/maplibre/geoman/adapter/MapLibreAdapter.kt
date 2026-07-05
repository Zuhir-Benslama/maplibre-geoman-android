package com.geoman.maplibre.geoman.adapter

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.CursorType
import com.geoman.maplibre.geoman.types.MapInteraction
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.GeoJsonFeatureData
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Projection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MapLibre Android SDK implementation of the base map adapter
 */
class MapLibreAdapter(map: MapLibreMap, geoman: Geoman, private val mapView: MapView) :
    BaseMapAdapter<MapLibreMap>(map, geoman) {

    override val mapType: String = "maplibre"

    private val context: android.content.Context = mapView.context

    // Thread-safe collections for event listeners and map objects
    private val eventListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()
    private val markers = ConcurrentHashMap.newKeySet<MapLibreDomMarker>()
    private val popups = ConcurrentHashMap.newKeySet<MapLibrePopup>()
    private val sources = ConcurrentHashMap<String, MapLibreSource>()
    private val layers = ConcurrentHashMap<String, MapLibreLayer>()

    override fun isLoaded(): Boolean = try {
        // Check if map style is loaded
        map.style != null
    } catch (e: Exception) {
        false
    }

    override fun getContainer(): ViewGroup = mapView

    override fun getCanvas(): Any? = mapView.renderView

    override fun addControl(control: GmControl) {
        android.util.Log.d("Geoman", "addControl called, registering click listeners")
        // SDK 11.x uses addOnMapClickListener instead of setOnMapClickListener
        map.addOnMapClickListener { point: LatLng ->
            android.util.Log.d("Geoman", "Map click received: $point, activeModes: ${control.activeModes}")
            val result = control.onMapClick(point)
            android.util.Log.d("Geoman", "Map click handled, result: $result")
            false
        }
        map.addOnMapLongClickListener { point: LatLng ->
            android.util.Log.d("Geoman", "Map long click received: $point, activeModes: ${control.activeModes}")
            val result = control.onMapLongClick(point)
            android.util.Log.d("Geoman", "Map long click handled, result: $result")
            false
        }
        mapView.renderView.setOnTouchListener { _, event ->
            control.onTouchEvent(event as MotionEvent)
            false
        }
    }

    override fun removeControl(control: GmControl) {
        // Controls are removed when detached
        control.onDetach()
    }

    override suspend fun loadImage(id: String, image: android.graphics.Bitmap) {
        map.style?.addImage(id, image)
    }

    override fun removeImage(id: String) {
        try {
            map.style?.removeImage(id)
        } catch (e: Exception) {
            // Style may not be available
        }
    }

    override fun getBounds(): LatLngBounds {
        val projection = map.projection
        val visibleRegion = projection.visibleRegion

        val northeast = LngLat(
            visibleRegion.farRight!!.longitude,
            visibleRegion.farRight!!.latitude,
        )
        val southwest = LngLat(
            visibleRegion.nearLeft!!.longitude,
            visibleRegion.nearLeft!!.latitude,
        )

        return LatLngBounds(northeast = northeast, southwest = southwest)
    }

    override fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions?) {
        val latLngBounds = org.maplibre.android.geometry.LatLngBounds.from(
            bounds.northeast.latitude,
            bounds.northeast.longitude,
            bounds.southwest.latitude,
            bounds.southwest.longitude,
        )

        val cameraUpdate = if (options != null) {
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                latLngBounds,
                options.padding.toInt(),
            )
        } else {
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(latLngBounds, 100)
        }

        map.animateCamera(cameraUpdate)
    }

    override fun setCursor(cursor: CursorType) {
        // On Android, cursor is handled by the system
        // This is a no-op for touch devices
    }

    override fun disableMapInteractions(interactionTypes: List<MapInteraction>) {
        interactionTypes.forEach { interaction ->
            when (interaction) {
                MapInteraction.SCROLL -> map.uiSettings.isScrollGesturesEnabled = false

                MapInteraction.ZOOM -> {
                    map.uiSettings.isZoomGesturesEnabled = false
                    map.uiSettings.isDoubleTapGesturesEnabled = false
                }

                MapInteraction.ROTATE -> map.uiSettings.isRotateGesturesEnabled = false

                MapInteraction.PITCH -> map.uiSettings.isTiltGesturesEnabled = false

                MapInteraction.DRAG_PAN -> map.uiSettings.isScrollGesturesEnabled = false

                MapInteraction.BOX_ZOOM -> map.uiSettings.isZoomGesturesEnabled = false

                MapInteraction.DOUBLE_CLICK_ZOOM -> map.uiSettings.isDoubleTapGesturesEnabled = false

                MapInteraction.TOUCH_ZOOM -> map.uiSettings.isZoomGesturesEnabled = false

                MapInteraction.TOUCH_ROTATE -> map.uiSettings.isRotateGesturesEnabled = false

                MapInteraction.TOUCH_PITCH -> map.uiSettings.isTiltGesturesEnabled = false

                MapInteraction.DRAG_ROTATE -> map.uiSettings.isRotateGesturesEnabled = false

                MapInteraction.KEYBOARD -> {
                    // Keyboard interactions not applicable on mobile
                }
            }
        }
    }

    override fun enableMapInteractions(interactionTypes: List<MapInteraction>) {
        interactionTypes.forEach { interaction ->
            when (interaction) {
                MapInteraction.SCROLL -> map.uiSettings.isScrollGesturesEnabled = true

                MapInteraction.ZOOM -> {
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isDoubleTapGesturesEnabled = true
                }

                MapInteraction.ROTATE -> map.uiSettings.isRotateGesturesEnabled = true

                MapInteraction.PITCH -> map.uiSettings.isTiltGesturesEnabled = true

                MapInteraction.DRAG_PAN -> map.uiSettings.isScrollGesturesEnabled = true

                MapInteraction.BOX_ZOOM -> map.uiSettings.isZoomGesturesEnabled = true

                MapInteraction.DOUBLE_CLICK_ZOOM -> map.uiSettings.isDoubleTapGesturesEnabled = true

                MapInteraction.TOUCH_ZOOM -> map.uiSettings.isZoomGesturesEnabled = true

                MapInteraction.TOUCH_ROTATE -> map.uiSettings.isRotateGesturesEnabled = true

                MapInteraction.TOUCH_PITCH -> map.uiSettings.isTiltGesturesEnabled = true

                MapInteraction.DRAG_ROTATE -> map.uiSettings.isRotateGesturesEnabled = true

                MapInteraction.KEYBOARD -> {
                    // Keyboard interactions not applicable on mobile
                }
            }
        }
    }

    override fun setDragPan(enabled: Boolean) {
        map.uiSettings.isScrollGesturesEnabled = enabled
    }

    override fun queryFeaturesByScreenCoordinates(
        queryCoordinates: ScreenPoint,
        sourceNames: List<String>,
    ): List<FeatureData> {
        // Query rendered features at point
        val features = mutableListOf<FeatureData>()

        // Check markers first
        markers.forEach { marker ->
            val markerPoint = project(marker.getLngLat())
            val dx = markerPoint.x - queryCoordinates.x
            val dy = markerPoint.y - queryCoordinates.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < 20) { // 20px hit tolerance
                val featureData = FeatureData(
                    id = marker.id,
                    sourceName = marker.sourceName,
                    feature = Feature(
                        geometry = com.geoman.maplibre.geoman.types.geojson.Point.fromLngLat(marker.getLngLat()),
                    ),
                )
                if (sourceNames.contains(marker.sourceName)) {
                    features.add(featureData)
                }
            }
        }

        // Query vector tile features if using vector sources
        sources.forEach { (sourceId, source) ->
            if (sourceNames.contains(sourceId)) {
                source.getFeaturesAtPoint(queryCoordinates)?.forEach { geoJsonFeature ->
                    features.add(
                        FeatureData(
                            id = geoJsonFeature.id ?: "",
                            sourceName = sourceId,
                            feature = geoJsonFeature,
                        ),
                    )
                }
            }
        }

        return features
    }

    override fun queryGeoJsonFeatures(
        queryCoordinates: ScreenPoint,
        sourceNames: List<String>,
    ): List<GeoJsonFeatureData> {
        val features = mutableListOf<GeoJsonFeatureData>()

        sources.forEach { (sourceId, source) ->
            if (sourceNames.contains(sourceId)) {
                source.getFeaturesAtPoint(queryCoordinates)?.forEach { feature ->
                    features.add(
                        GeoJsonFeatureData(
                            id = feature.id ?: "",
                            sourceName = sourceId,
                            feature = feature,
                        ),
                    )
                }
            }
        }

        return features
    }

    override fun addSource(sourceId: String, geoJson: FeatureCollection): MapSource {
        val source = MapLibreSource(geoman, sourceId, geoJson, map)
        sources[sourceId] = source
        return source
    }

    override fun getSource(sourceId: String): MapSource? = sources[sourceId]

    override fun addLayer(options: LayerOptions): MapLayer {
        val layer = MapLibreLayer(geoman, options, map)
        layers[options.id] = layer
        return layer
    }

    override fun getLayer(layerId: String): MapLayer? = layers[layerId]

    override fun removeLayer(layerId: String) {
        layers[layerId]?.remove()
        layers.remove(layerId)
    }

    override fun eachLayer(callback: (MapLayer) -> Unit) {
        layers.values.forEach(callback)
    }

    override fun createDomMarker(options: DomMarkerOptions, lngLat: LngLat): DomMarker {
        val marker = MapLibreDomMarker(map, options, lngLat, mapView, geoman)
        markers.add(marker)
        return marker
    }

    override fun createPopup(options: PopupOptions, lngLat: LngLat?): Popup {
        val popup = MapLibrePopup(map, context, options, lngLat)
        if (lngLat != null) {
            popups.add(popup)
        }
        return popup
    }

    override fun project(position: LngLat): ScreenPoint {
        val latLng = LatLng(position.latitude, position.longitude)
        val point = map.projection.toScreenLocation(latLng)
        return ScreenPoint(point.x, point.y)
    }

    override fun unproject(point: ScreenPoint): LngLat {
        val screenPoint = PointF(point.x, point.y)
        val latLng = map.projection.fromScreenLocation(screenPoint)
        return LngLat(latLng.longitude, latLng.latitude)
    }

    override fun coordBoundsToScreenBounds(bounds: LatLngBounds): Pair<ScreenPoint, ScreenPoint> {
        val sw = project(bounds.southwest)
        val ne = project(bounds.northeast)
        return sw to ne
    }

    override fun fire(type: String, data: Any?) {
        eventListeners[type]?.forEach { listener ->
            try {
                listener(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun on(type: String, listener: (Any?) -> Unit) {
        eventListeners.getOrPut(type) { CopyOnWriteArrayList() }.add(listener)
    }

    override fun once(type: String, listener: (Any?) -> Unit) {
        val wrappedListener = object : (Any?) -> Unit {
            var called = false
            override fun invoke(data: Any?) {
                if (!called) {
                    called = true
                    listener(data)
                    off(type, this)
                }
            }
        }
        on(type, wrappedListener)
    }

    override fun off(type: String, listener: (Any?) -> Unit) {
        eventListeners[type]?.remove(listener)
        if (eventListeners[type]?.isEmpty() == true) {
            eventListeners.remove(type)
        }
    }

    override fun getEuclideanNearestLngLat(lineCoordinates: List<LngLat>, point: LngLat): LngLat {
        var closestPoint = lineCoordinates.first()
        var minDistance = Double.MAX_VALUE

        for (i in 0 until lineCoordinates.size - 1) {
            val start = lineCoordinates[i]
            val end = lineCoordinates[i + 1]

            val nearest = getNearestPointOnSegment(start, end, point)
            val distance = getDistance(nearest, point)

            if (distance < minDistance) {
                minDistance = distance
                closestPoint = nearest
            }
        }

        return closestPoint
    }

    private fun getNearestPointOnSegment(start: LngLat, end: LngLat, point: LngLat): LngLat {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude

        if (dx == 0.0 && dy == 0.0) {
            return start
        }

        val t = (
            (point.longitude - start.longitude) * dx +
                (point.latitude - start.latitude) * dy
            ) /
            (dx * dx + dy * dy)

        val clampedT = t.coerceIn(0.0, 1.0)

        return LngLat(
            start.longitude + clampedT * dx,
            start.latitude + clampedT * dy,
        )
    }

    fun cleanup() {
        markers.forEach { it.remove() }
        markers.clear()
        popups.forEach { it.remove() }
        popups.clear()
        layers.values.forEach { it.remove() }
        layers.clear()
        sources.values.forEach { it.remove() }
        sources.clear()
        eventListeners.clear()
    }
}
