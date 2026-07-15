package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
 * MapLibre DOM marker implementation using SymbolLayer for SDK 11.x
 */
class MapLibreDomMarker(
    map: Any,
    private val options: DomMarkerOptions,
    initialLngLat: LngLat,
    private val mapView: android.view.ViewGroup,
    private val geoman: Geoman,
) : DomMarker(map) {

    private val mapLibreMap: MapLibreMap = map as? MapLibreMap
        ?: throw IllegalArgumentException("Expected MapLibreMap but got ${map::class.simpleName}")

    val id: String = "marker_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    val sourceName: String = GeomanCoreConstants.SOURCE_MARKERS

    private var view: View? = null
    private var isAdded = false
    private var isDraggingInternal = false
    private var dragStartLngLat: LngLat? = null
    private var currentLngLat: LngLat = initialLngLat

    init {
        createView()
    }

    private fun createView() {
        view = options.element ?: createDefaultMarkerView()

        if (options.draggable) {
            view?.setOnTouchListener { _, event ->
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
                                event.x + ((view?.left ?: 0).toFloat()),
                                event.y + ((view?.top ?: 0).toFloat()),
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
                        true
                    }

                    else -> false
                }
            }
        }
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

        // Create GeoJSON feature using org.json
        val featureJson = JSONObject().apply {
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
                    put("icon", iconId)
                },
            )
        }

        val featureCollection = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().put(featureJson))
        }

        val geoJsonSource: GeoJsonSource? = mapLibreMap.style?.getSourceAs(sourceName)
        if (geoJsonSource == null) {
            val newSource = GeoJsonSource(sourceName, featureCollection.toString())
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

        isAdded = true
        return this
    }

    private fun createMarkerBitmap(): Bitmap {
        val view = view ?: createDefaultMarkerView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(48),
            view.measuredHeight.coerceAtLeast(48),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun updateMarkerPosition() {
        if (!isAdded) return

        // Create GeoJSON feature using org.json
        val featureJson = JSONObject().apply {
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

        val featureCollection = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().put(featureJson))
        }

        val source = mapLibreMap.style?.getSourceAs<GeoJsonSource>(sourceName)
        source?.setGeoJson(featureCollection.toString())
    }

    override fun remove() {
        if (!isAdded) return

        mapLibreMap.style?.removeImage("marker-icon-$id")

        // Remove this marker's image from the source
        val source = mapLibreMap.style?.getSourceAs<GeoJsonSource>(sourceName)
        source?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")

        isAdded = false
    }

    override fun setDraggable(draggable: Boolean) {
        // Update draggable state
    }

    override fun isDragging(): Boolean = isDraggingInternal
}
