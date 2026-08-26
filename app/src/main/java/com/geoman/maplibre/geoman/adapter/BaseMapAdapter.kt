package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.CursorType
import com.geoman.maplibre.geoman.types.MapInteraction
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import com.geoman.maplibre.geoman.utils.GeometryUtils

/**
 * Minimal source/layer surface [com.geoman.maplibre.geoman.core.features.Features]
 * needs to render features. Extracted as an interface so the feature store can
 * be unit-tested against a fake renderer without a live map.
 */
interface FeatureStoreRenderer {
    fun getSource(sourceId: String): MapSource?
    fun addSource(sourceId: String, geoJson: FeatureCollection): MapSource
    fun getLayer(layerId: String): MapLayer?
    fun addLayer(options: LayerOptions): MapLayer
}

/**
 * Base map adapter interface for MapLibre
 * Abstracts map operations to allow different map implementations
 */
abstract class BaseMapAdapter<TMap>(protected val map: TMap, val geoman: Geoman) : FeatureStoreRenderer {
    abstract val mapType: String

    /**
     * Check if the map is loaded
     */
    abstract fun isLoaded(): Boolean

    /**
     * Get the map container view
     */
    abstract fun getContainer(): ViewGroup

    /**
     * Get the map canvas/surface
     */
    abstract fun getCanvas(): Any?

    /**
     * Add a control to the map
     */
    abstract fun addControl(control: GmControl)

    /**
     * Remove a control from the map
     */
    abstract fun removeControl(control: GmControl)

    /**
     * Load an image for use in markers/icons
     */
    abstract suspend fun loadImage(id: String, image: Bitmap)

    /**
     * Remove a loaded image
     */
    abstract fun removeImage(id: String)

    /**
     * Get the current map bounds
     */
    abstract fun getBounds(): LatLngBounds

    /**
     * Fit the map to bounds
     */
    abstract fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions? = null)

    /**
     * Set the map cursor
     */
    abstract fun setCursor(cursor: CursorType)

    /**
     * Disable map interactions
     */
    abstract fun disableMapInteractions(interactionTypes: List<MapInteraction>)

    /**
     * Enable map interactions
     */
    abstract fun enableMapInteractions(interactionTypes: List<MapInteraction>)

    /**
     * Enable/disable drag pan
     */
    abstract fun setDragPan(enabled: Boolean)

    /**
     * Query features by screen coordinates
     */
    abstract fun queryFeaturesByScreenCoordinates(
        queryCoordinates: ScreenPoint,
        sourceNames: List<String>,
    ): List<FeatureData>

    /**
     * Add a GeoJSON source
     */
    abstract override fun addSource(sourceId: String, geoJson: FeatureCollection): MapSource

    /**
     * Get a source by ID
     */
    abstract override fun getSource(sourceId: String): MapSource?

    /**
     * Add a layer
     */
    abstract override fun addLayer(options: LayerOptions): MapLayer

    /**
     * Get a layer by ID
     */
    abstract override fun getLayer(layerId: String): MapLayer?

    /**
     * Remove a layer
     */
    abstract fun removeLayer(layerId: String)

    /**
     * Iterate through all layers
     */
    abstract fun eachLayer(callback: (MapLayer) -> Unit)

    /**
     * Create a DOM marker
     */
    abstract fun createDomMarker(options: DomMarkerOptions, lngLat: LngLat): DomMarker

    /**
     * Create a popup
     */
    abstract fun createPopup(options: PopupOptions, lngLat: LngLat? = null): Popup

    /**
     * Project lngLat to screen coordinates
     */
    abstract fun project(position: LngLat): ScreenPoint

    /**
     * Unproject screen coordinates to lngLat
     */
    abstract fun unproject(point: ScreenPoint): LngLat

    /**
     * Convert coordinate bounds to screen bounds
     */
    abstract fun coordBoundsToScreenBounds(bounds: LatLngBounds): Pair<ScreenPoint, ScreenPoint>

    /**
     * Fire a map event
     */
    abstract fun fire(type: String, data: Any? = null)

    /**
     * Add an event listener
     */
    abstract fun on(type: String, listener: (Any?) -> Unit)

    /**
     * Add a one-time event listener
     */
    abstract fun once(type: String, listener: (Any?) -> Unit)

    /**
     * Remove an event listener
     */
    abstract fun off(type: String, listener: (Any?) -> Unit)

    /**
     * Calculate distance between two points in meters
     */
    open fun getDistance(lngLat1: LngLat, lngLat2: LngLat): Double = GeometryUtils.distance(lngLat1, lngLat2)

    /**
     * Get the nearest point on a line to a given point
     */
    abstract fun getEuclideanNearestLngLat(lineCoordinates: List<LngLat>, point: LngLat): LngLat
}

/**
 * Fit bounds options
 */
data class FitBoundsOptions(
    val padding: Float = 0f,
    val bearing: Double? = null,
    val pitch: Double? = null,
    val offset: ScreenPoint? = null,
    val maxZoom: Int? = null,
    val duration: Long = 1000L,
)

/**
 * Layer options
 */
data class LayerOptions(
    val id: String,
    val type: LayerType,
    val source: String,
    val sourceLayer: String? = null,
    val paint: Map<String, Any> = emptyMap(),
    val layout: Map<String, Any> = emptyMap(),
    val filter: List<Any>? = null,
    val minZoom: Float? = null,
    val maxZoom: Float? = null,
)

enum class LayerType {
    FILL,
    LINE,
    CIRCLE,
    SYMBOL,
    FILL_EXTRUSION,
    RASTER,
    HEATMAP,
}

/**
 * Dom marker options
 */
data class DomMarkerOptions(
    val element: View? = null,
    val anchor: MarkerAnchor = MarkerAnchor.CENTER,
    val draggable: Boolean = false,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
)

enum class MarkerAnchor {
    CENTER,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/**
 * Popup options
 */
data class PopupOptions(
    val content: String = "",
    val closeButton: Boolean = true,
    val closeOnClick: Boolean = true,
    val anchor: MarkerAnchor = MarkerAnchor.BOTTOM,
    val offset: ScreenPoint? = null,
    val maxWidth: Float = 240f,
    val className: String = "",
)

/**
 * Map source interface
 */
interface MapSource {
    val sourceId: String
    fun setData(geoJson: FeatureCollection)
    fun getData(): FeatureCollection?
    fun remove()
}

/**
 * Map layer interface
 */
interface MapLayer {
    val layerId: String
    fun setPaintProperty(name: String, value: Any)
    fun setLayoutProperty(name: String, value: Any)
    fun remove()
}

/**
 * Dom marker interface
 */
abstract class DomMarker(protected val map: Any) : com.geoman.maplibre.geoman.core.markers.ManagedMarker {
    abstract override fun getLngLat(): LngLat
    abstract override fun setLngLat(lngLat: LngLat)
    abstract fun getElement(): View
    abstract fun addToMap(): DomMarker
    abstract override fun remove()
    abstract fun setDraggable(draggable: Boolean)
    abstract fun isDragging(): Boolean

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((LngLat) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    /**
     * Invoked when a non-draggable marker's view is tapped.
     */
    override var onClick: (() -> Unit)? = null
}

/**
 * Popup interface
 */
abstract class Popup(protected val map: Any) {
    abstract fun getLngLat(): LngLat?
    abstract fun setLngLat(lngLat: LngLat): Popup
    abstract fun getContent(): String
    abstract fun setContent(content: String): Popup
    abstract fun addToMap(): Popup
    abstract fun remove()
    abstract fun isOpen(): Boolean
    abstract fun close(): Popup
}
