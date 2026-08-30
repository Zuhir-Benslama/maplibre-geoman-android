package com.geoman.maplibre.geoman.adapter

import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
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
 * Base map adapter interface for MapLibre.
 * Abstracts map operations to allow different map implementations.
 *
 * The abstract surface is split across the cohesive [MapStyling],
 * [MapViewport], [MapInteractionControl] and [MapContentStore] contracts;
 * concrete adapters implement them (optionally via delegates) and only the
 * lifecycle/control entry points plus geometry helpers stay declared here.
 */
abstract class BaseMapAdapter<TMap>(protected val map: TMap, val geoman: Geoman) :
    FeatureStoreRenderer,
    MapStyling,
    MapViewport,
    MapInteractionControl,
    MapContentStore {

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
     * Calculate distance between two points in meters
     */
    open fun getDistance(lngLat1: LngLat, lngLat2: LngLat): Double = GeometryUtils.distance(lngLat1, lngLat2)

    /**
     * Get the nearest point on a line to a given point
     */
    open fun getEuclideanNearestLngLat(lineCoordinates: List<LngLat>, point: LngLat): LngLat {
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
