package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.CursorType
import com.geoman.maplibre.geoman.types.MapInteraction
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import com.geoman.maplibre.geoman.utils.GeometryUtils
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MapLibre Android SDK implementation of the base map adapter
 */
class MapLibreAdapter(map: MapLibreMap, geoman: Geoman, private val mapView: MapView) :
    BaseMapAdapter<MapLibreMap>(map, geoman) {

    override val mapType: String = "maplibre"

    private companion object {
        const val TAG = "MapLibreAdapter"
        const val HIT_TOLERANCE_PX = 20.0
        const val DEFAULT_CAMERA_PADDING = 100
    }

    private val context: android.content.Context = mapView.context

    // Thread-safe collections for event listeners and map objects
    private val eventListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()

    // Stored listener references for cleanup in removeControl()
    private var mapClickListener: MapLibreMap.OnMapClickListener? = null
    private var mapLongClickListener: MapLibreMap.OnMapLongClickListener? = null
    private val markers = ConcurrentHashMap.newKeySet<MapLibreDomMarker>()
    private val popups = ConcurrentHashMap.newKeySet<MapLibrePopup>()
    private val sources = ConcurrentHashMap<String, MapLibreSource>()
    private val layers = ConcurrentHashMap<String, MapLibreLayer>()

    override fun isLoaded(): Boolean = try {
        // Check if map style is loaded
        map.style != null
    } catch (_: IllegalStateException) {
        false
    }

    override fun getContainer(): ViewGroup = mapView

    override fun getCanvas(): Any? = mapView.renderView

    override fun addControl(control: GmControl) {
        GeomanLogger.d("Geoman", "addControl called, registering click listeners")
        val clickListener = MapLibreMap.OnMapClickListener { point: LatLng ->
            GeomanLogger.d("Geoman") { "Map click received: $point, activeModes: ${control.activeModes}" }
            val result = control.onMapClick(point)
            GeomanLogger.d("Geoman") { "Map click handled, result: $result" }
            false
        }
        mapClickListener = clickListener
        map.addOnMapClickListener(clickListener)

        val longClickListener = MapLibreMap.OnMapLongClickListener { point: LatLng ->
            GeomanLogger.d("Geoman") { "Map long click received: $point, activeModes: ${control.activeModes}" }
            val result = control.onMapLongClick(point)
            GeomanLogger.d("Geoman") { "Map long click handled, result: $result" }
            false
        }
        mapLongClickListener = longClickListener
        map.addOnMapLongClickListener(longClickListener)

        val touchListener = View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            control.onTouchEvent(event)
        }
        mapView.renderView.setOnTouchListener(touchListener)
    }

    override fun removeControl(control: GmControl) {
        control.onDetach()
        mapClickListener?.let { map.removeOnMapClickListener(it) }
        mapClickListener = null
        mapLongClickListener?.let { map.removeOnMapLongClickListener(it) }
        mapLongClickListener = null
        mapView.renderView.setOnTouchListener(null)
    }

    override suspend fun loadImage(id: String, image: Bitmap) {
        map.style?.addImage(id, image)
    }

    override fun removeImage(id: String) {
        try {
            map.style?.removeImage(id)
        } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
            GeomanLogger.d("MapLibreAdapter", "Failed to remove image $id: ${e.message}")
        }
    }

    override fun getBounds(): LatLngBounds {
        val projection = map.projection
        val visibleRegion = projection.visibleRegion

        val farRight = visibleRegion.farRight
        val nearLeft = visibleRegion.nearLeft

        val northeast = LngLat(
            farRight?.longitude ?: 0.0,
            farRight?.latitude ?: 0.0,
        )
        val southwest = LngLat(
            nearLeft?.longitude ?: 0.0,
            nearLeft?.latitude ?: 0.0,
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
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(latLngBounds, DEFAULT_CAMERA_PADDING)
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
        // Hit-test the in-memory features of the requested sources against the
        // projected geometry. DOM markers (vertex/drag handles) are deliberately
        // excluded: they are interaction affordances, not features, and their
        // generated IDs have no counterpart in the feature store.
        val features = mutableListOf<FeatureData>()

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
        val popup = MapLibrePopup(map, context, options, lngLat, mapView)
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
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                GeomanLogger.e("MapLibreAdapter", "Error in event listener for $type", e)
            }
        }
    }

    override fun on(type: String, listener: (Any?) -> Unit) {
        eventListeners.getOrPut(type) { CopyOnWriteArrayList() }.add(listener)
    }

    override fun once(type: String, listener: (Any?) -> Unit) {
        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        val wrappedListener = object : (Any?) -> Unit {
            override fun invoke(data: Any?) {
                if (called.compareAndSet(false, true)) {
                    listener(data)
                    off(type, this)
                }
            }
        }
        on(type, wrappedListener)
    }

    override fun off(type: String, listener: (Any?) -> Unit) {
        val listeners = eventListeners[type] ?: return
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            eventListeners.remove(type, listeners)
        }
    }

    override fun getEuclideanNearestLngLat(lineCoordinates: List<LngLat>, point: LngLat): LngLat {
        require(lineCoordinates.isNotEmpty()) { "lineCoordinates must not be empty" }
        var closestPoint = lineCoordinates.first()
        var minDistance = Double.MAX_VALUE

        for (i in 0 until lineCoordinates.size - 1) {
            val nearest = GeometryUtils.nearestPointOnSegment(
                point,
                lineCoordinates[i],
                lineCoordinates[i + 1],
            )
            val dist = getDistance(nearest, point)

            if (dist < minDistance) {
                minDistance = dist
                closestPoint = nearest
            }
        }

        return closestPoint
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

    /**
     * Drop cached sources/layers that reference a replaced style.
     * Unlike [cleanup], in-memory feature data and DOM markers are preserved;
     * features are re-synced to the new style afterwards via
     * [com.geoman.maplibre.geoman.Geoman.onStyleReloaded].
     */
    fun clearRenderingCache() {
        sources.clear()
        layers.clear()
    }
}
