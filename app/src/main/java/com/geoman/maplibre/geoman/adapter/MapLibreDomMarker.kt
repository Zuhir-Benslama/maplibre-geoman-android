package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre DOM marker implementation using SymbolLayer for SDK 11.x.
 *
 * The marker is rendered as a symbol-layer icon AND its [View] is placed as an
 * overlay on the map container so it can receive touch events (e.g. for dragging).
 */
@Suppress("TooManyFunctions")
class MapLibreDomMarker(
    map: Any,
    private val options: DomMarkerOptions,
    initialLngLat: LngLat,
    private val mapView: ViewGroup,
    private val geoman: Geoman,
) : DomMarker(map) {

    private val mapLibreMap: MapLibreMap = map as? MapLibreMap
        ?: throw IllegalArgumentException("Expected MapLibreMap but got ${map::class.simpleName}")

    val id: String = "marker_${java.util.UUID.randomUUID()}"
    val sourceName: String = GeomanCoreConstants.SOURCE_MARKERS

    private var view: View? = null
    private var isAdded = false
    private var isDraggingInternal = false
    private var dragStartLngLat: LngLat? = null
    private var currentLngLat: LngLat = initialLngLat
    private var draggable = options.draggable
    private var cameraMoveListener: MapLibreMap.OnCameraMoveListener? = null

    companion object {
        private val markersByMap =
            java.util.concurrent.ConcurrentHashMap<MapLibreMap, MutableMap<String, MapLibreDomMarker>>()

        private fun markersFor(map: MapLibreMap): MutableMap<String, MapLibreDomMarker> = markersByMap.getOrPut(map) {
            java.util.Collections.synchronizedMap(linkedMapOf())
        }

        private fun rebuildSource(mapLibreMap: MapLibreMap, sourceName: String) {
            val map = markersByMap[mapLibreMap] ?: return
            val featuresArray = JSONArray()
            synchronized(map) {
                map.values.forEach { marker ->
                    featuresArray.put(marker.buildFeatureJson())
                }
            }
            val featureCollection = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", featuresArray)
            }
            val source = mapLibreMap.style?.getSourceAs<GeoJsonSource>(sourceName)
            source?.setGeoJson(featureCollection.toString())
        }

        fun cleanupForMap(mapLibreMap: MapLibreMap) {
            val markers = markersByMap.remove(mapLibreMap) ?: return
            synchronized(markers) {
                markers.values.forEach { it.remove() }
            }
        }
    }

    init {
        createView()
    }

    private fun createView() {
        view = options.element ?: createDefaultMarkerView()
        if (draggable) {
            attachDragListener()
        } else {
            attachClickListener()
        }
    }

    private fun attachClickListener() {
        view?.setOnClickListener { onClick?.invoke() }
    }

    private fun removeClickListener() {
        view?.setOnClickListener(null)
    }

    private fun attachDragListener() {
        view?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingInternal = true
                    dragStartLngLat = currentLngLat
                    onDragStart?.invoke()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingInternal) {
                        val screenPoint = PointF(
                            event.x + (view?.x ?: 0f),
                            event.y + (view?.y ?: 0f),
                        )
                        val newLatLng = mapLibreMap.projection.fromScreenLocation(screenPoint)
                        val newLngLat = LngLat(newLatLng.longitude, newLatLng.latitude)
                        currentLngLat = newLngLat
                        updateMarkerPosition()
                        onDrag?.invoke(newLngLat)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingInternal) {
                        isDraggingInternal = false
                        onDragEnd?.invoke()
                    }
                    v.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun removeDragListener() {
        view?.setOnTouchListener(null)
    }

    private fun createDefaultMarkerView(): View {
        val context = mapView.context
        val markerView = View(context)
        markerView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        markerView.setBackgroundResource(android.R.drawable.ic_dialog_map)
        markerView.minimumWidth = 48
        markerView.minimumHeight = 48
        return markerView
    }

    override fun getLngLat(): LngLat = currentLngLat

    override fun setLngLat(lngLat: LngLat) {
        currentLngLat = lngLat
        if (isAdded) {
            updateMarkerPosition()
        }
    }

    override fun getElement(): View = view ?: createDefaultMarkerView()

    override fun addToMap(): DomMarker {
        if (isAdded) return this

        val iconId = "marker-icon-$id"
        val iconBitmap = createMarkerBitmap()

        mapLibreMap.style?.addImage(iconId, iconBitmap)

        markersFor(mapLibreMap)[id] = this

        val geoJsonSource: GeoJsonSource? = mapLibreMap.style?.getSourceAs(sourceName)
        if (geoJsonSource == null) {
            val emptyCollection = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", JSONArray())
            }
            val newSource = GeoJsonSource(sourceName, emptyCollection.toString())
            mapLibreMap.style?.addSource(newSource)
        }

        val layerId = "$sourceName-layer"
        var layer = mapLibreMap.style?.getLayerAs<SymbolLayer>(layerId)
        if (layer == null) {
            layer = SymbolLayer(layerId, sourceName).apply {
                withProperties(
                    PropertyValue("icon-image", "{icon}"),
                    PropertyValue("icon-allow-overlap", true),
                    PropertyValue("icon-ignore-placement", true),
                    PropertyValue("text-allow-overlap", true),
                    PropertyValue("text-ignore-placement", true),
                )
            }
            mapLibreMap.style?.addLayer(layer)
        }

        // Place the DOM view as an overlay so it can receive touch events.
        view?.let { markerView ->
            (markerView.parent as? ViewGroup)?.removeView(markerView)
            mapView.addView(markerView)
        }
        updateViewPosition()

        val cameraListener = MapLibreMap.OnCameraMoveListener {
            if (isAdded) updateViewPosition()
        }
        cameraMoveListener = cameraListener
        mapLibreMap.addOnCameraMoveListener(cameraListener)

        isAdded = true
        rebuildSource(mapLibreMap, sourceName)
        return this
    }

    private fun buildFeatureJson(): JSONObject = JSONObject().apply {
        put("type", "Feature")
        put("id", id)
        put(
            "geometry",
            JSONObject().apply {
                put("type", "Point")
                put(
                    "coordinates",
                    JSONArray().apply {
                        put(currentLngLat.longitude)
                        put(currentLngLat.latitude)
                    },
                )
            },
        )
        put(
            "properties",
            JSONObject().apply {
                put(GeomanCoreConstants.FEATURE_ID_PROPERTY, id)
                put("icon", "marker-icon-$id")
            },
        )
    }

    private fun createMarkerBitmap(): Bitmap {
        val markerView = view ?: createDefaultMarkerView()
        markerView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

        val bitmap = createBitmap(
            markerView.measuredWidth.coerceAtLeast(48),
            markerView.measuredHeight.coerceAtLeast(48),
        )
        val canvas = Canvas(bitmap)
        markerView.draw(canvas)
        return bitmap
    }

    /**
     * Rebuild the symbol source and reposition the overlay for the current coordinate.
     */
    private fun updateMarkerPosition() {
        if (!isAdded) return
        updateViewPosition()
        rebuildSource(mapLibreMap, sourceName)
    }

    private fun updateViewPosition() {
        val markerView = view ?: return
        val point = mapLibreMap.projection.toScreenLocation(
            LatLng(currentLngLat.latitude, currentLngLat.longitude),
        )
        val offset = anchorOffset(markerView)
        markerView.x = point.x + offset.x
        markerView.y = point.y + offset.y
    }

    private fun anchorOffset(markerView: View): PointF {
        val width = if (markerView.width > 0) markerView.width.toFloat() else markerView.measuredWidth.toFloat()
        val height = if (markerView.height > 0) markerView.height.toFloat() else markerView.measuredHeight.toFloat()
        return when (options.anchor) {
            MarkerAnchor.CENTER -> PointF(-width / 2f, -height / 2f)
            MarkerAnchor.TOP -> PointF(-width / 2f, 0f)
            MarkerAnchor.BOTTOM -> PointF(-width / 2f, -height)
            MarkerAnchor.LEFT -> PointF(0f, -height / 2f)
            MarkerAnchor.RIGHT -> PointF(-width, -height / 2f)
            MarkerAnchor.TOP_LEFT -> PointF(0f, 0f)
            MarkerAnchor.TOP_RIGHT -> PointF(-width, 0f)
            MarkerAnchor.BOTTOM_LEFT -> PointF(0f, -height)
            MarkerAnchor.BOTTOM_RIGHT -> PointF(-width, -height)
        }
    }

    override fun remove() {
        if (!isAdded) return

        mapLibreMap.style?.removeImage("marker-icon-$id")
        markersByMap[mapLibreMap]?.let { markers ->
            synchronized(markers) {
                markers.remove(id)
                if (markers.isEmpty()) {
                    markersByMap.remove(mapLibreMap)
                }
            }
        }

        cameraMoveListener?.let { mapLibreMap.removeOnCameraMoveListener(it) }
        cameraMoveListener = null

        view?.let { (it.parent as? ViewGroup)?.removeView(it) }

        rebuildSource(mapLibreMap, sourceName)
        isAdded = false
    }

    override fun setDraggable(draggable: Boolean) {
        if (this.draggable == draggable) return
        this.draggable = draggable
        if (draggable) {
            removeClickListener()
            attachDragListener()
        } else {
            removeDragListener()
            attachClickListener()
        }
    }

    override fun isDragging(): Boolean = isDraggingInternal
}
