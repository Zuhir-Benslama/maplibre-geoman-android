package com.geoman.maplibre.geoman.adapter

import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import org.maplibre.android.maps.MapLibreMap
import java.util.concurrent.ConcurrentHashMap

/**
 * MapLibre implementation of the GeoJSON source/layer content store and DOM
 * overlay factories shared by the map adapter.
 */
class MapLibreContentStore(private val map: MapLibreMap, private val geoman: Geoman, private val mapView: ViewGroup) :
    MapContentStore {

    private val context: android.content.Context = mapView.context

    private val markers = ConcurrentHashMap.newKeySet<MapLibreDomMarker>()
    private val popups = ConcurrentHashMap.newKeySet<MapLibrePopup>()
    private val sources = ConcurrentHashMap<String, MapLibreSource>()
    private val layers = ConcurrentHashMap<String, MapLibreLayer>()

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
        layer.add()
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

    /**
     * Remove every managed map object; sources, layers and overlays are torn
     * down.
     */
    fun cleanup() {
        markers.forEach { it.remove() }
        markers.clear()
        popups.forEach { it.remove() }
        popups.clear()
        layers.values.forEach { it.remove() }
        layers.clear()
        sources.values.forEach { it.remove() }
        sources.clear()
    }

    /**
     * Drop cached sources/layers that reference a replaced style. In-memory
     * feature data and DOM markers are preserved; features are re-synced to
     * the new style afterwards via [com.geoman.maplibre.geoman.Geoman.onStyleReloaded].
     */
    fun clearRenderingCache() {
        sources.clear()
        layers.clear()
    }
}
