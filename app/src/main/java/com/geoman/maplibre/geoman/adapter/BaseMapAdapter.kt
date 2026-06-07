package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.CursorType
import com.geoman.maplibre.geoman.types.MapInteraction
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.GeoJsonFeatureData
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint

/**
 * Base map adapter interface for MapLibre
 * Abstracts map operations to allow different map implementations
 */
abstract class BaseMapAdapter<TMap>(
    protected val map: TMap,
    val geoman: Geoman
) {
    abstract val mapType: String
    
    /**
     * Check if the map is loaded
     */
    abstract fun isLoaded(): Boolean
    
    /**
     * Get the map container view
     */
    abstract fun getContainer(): android.view.ViewGroup
    
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
    abstract suspend fun loadImage(id: String, image: android.graphics.Bitmap)
    
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
        sourceNames: List<String>
    ): List<FeatureData>
    
    /**
     * Query GeoJSON features by screen coordinates
     */
    abstract fun queryGeoJsonFeatures(
        queryCoordinates: ScreenPoint,
        sourceNames: List<String>
    ): List<GeoJsonFeatureData>
    
    /**
     * Add a GeoJSON source
     */
    abstract fun addSource(sourceId: String, geoJson: FeatureCollection): MapSource
    
    /**
     * Get a source by ID
     */
    abstract fun getSource(sourceId: String): MapSource?
    
    /**
     * Add a layer
     */
    abstract fun addLayer(options: LayerOptions): MapLayer
    
    /**
     * Get a layer by ID
     */
    abstract fun getLayer(layerId: String): MapLayer?
    
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
    open fun getDistance(lngLat1: LngLat, lngLat2: LngLat): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lngLat2.latitude - lngLat1.latitude)
        val dLon = Math.toRadians(lngLat2.longitude - lngLat1.longitude)
        val lat1 = Math.toRadians(lngLat1.latitude)
        val lat2 = Math.toRadians(lngLat2.latitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Get the nearest point on a line to a given point
     */
    abstract fun getEuclideanNearestLngLat(
        lineCoordinates: List<LngLat>,
        point: LngLat
    ): LngLat
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
    val duration: Long = 1000L
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
    val maxZoom: Float? = null
)

enum class LayerType {
    FILL,
    LINE,
    CIRCLE,
    SYMBOL,
    FILL_EXTRUSION,
    RASTER,
    HEATMAP
}

/**
 * Dom marker options
 */
data class DomMarkerOptions(
    val element: android.view.View? = null,
    val anchor: MarkerAnchor = MarkerAnchor.CENTER,
    val draggable: Boolean = false,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f
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
    BOTTOM_RIGHT
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
    val className: String = ""
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
abstract class DomMarker(
    protected val map: Any
) {
    abstract fun getLngLat(): LngLat
    abstract fun setLngLat(lngLat: LngLat)
    abstract fun getElement(): android.view.View
    abstract fun addToMap(): DomMarker
    abstract fun remove()
    abstract fun setDraggable(draggable: Boolean)
    abstract fun isDragging(): Boolean

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((LngLat) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
}

/**
 * Popup interface
 */
abstract class Popup(
    protected val map: Any
) {
    abstract fun getLngLat(): LngLat?
    abstract fun setLngLat(lngLat: LngLat): Popup
    abstract fun getContent(): String
    abstract fun setContent(content: String): Popup
    abstract fun addToMap(): Popup
    abstract fun remove()
    abstract fun isOpen(): Boolean
    abstract fun close(): Popup
}
