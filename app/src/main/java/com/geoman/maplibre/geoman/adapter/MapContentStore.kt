package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint

/**
 * GeoJSON source/layer surface plus feature query and DOM overlay factories
 * used by map adapters. Extends the minimal [FeatureStoreRenderer] contract so
 * feature hits and overlays stay coherent with the rendered sources.
 */
interface MapContentStore : FeatureStoreRenderer {
    /**
     * Query features by screen coordinates
     */
    fun queryFeaturesByScreenCoordinates(queryCoordinates: ScreenPoint, sourceNames: List<String>): List<FeatureData>

    /**
     * Remove a layer
     */
    fun removeLayer(layerId: String)

    /**
     * Iterate through all layers
     */
    fun eachLayer(callback: (MapLayer) -> Unit)

    /**
     * Create a DOM marker
     */
    fun createDomMarker(options: DomMarkerOptions, lngLat: LngLat): DomMarker

    /**
     * Create a popup
     */
    fun createPopup(options: PopupOptions, lngLat: LngLat? = null): Popup
}
