package com.geoman.maplibre.geoman.adapter

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.controls.GmControl
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * MapLibre Android SDK implementation of the base map adapter.
 *
 * The [MapEventSystem], [MapStyling], [MapViewport], [MapInteractionControl]
 * and [MapContentStore] contracts are delegated to focused MapLibre
 * implementations; the adapter itself only owns lifecycle/control wiring.
 */
class MapLibreAdapter private constructor(
    map: MapLibreMap,
    geoman: Geoman,
    private val mapView: MapView,
    private val contentStore: MapLibreContentStore,
    private val eventDispatcher: MapLibreEventDispatcher,
) : BaseMapAdapter<MapLibreMap>(map, geoman),
    MapEventSystem by eventDispatcher,
    MapStyling by MapLibreStyler(map),
    MapViewport by MapLibreViewport(map),
    MapInteractionControl by MapLibreInteractionManager(map),
    MapContentStore by contentStore {

    constructor(map: MapLibreMap, geoman: Geoman, mapView: MapView) : this(
        map,
        geoman,
        mapView,
        contentStore = MapLibreContentStore(map, geoman, mapView),
        eventDispatcher = MapLibreEventDispatcher(),
    )

    override val mapType: String = "maplibre"

    // Stored listener references for cleanup in removeControl()
    private var mapClickListener: MapLibreMap.OnMapClickListener? = null
    private var mapLongClickListener: MapLibreMap.OnMapLongClickListener? = null

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

    fun cleanup() {
        contentStore.cleanup()
        eventDispatcher.clearListeners()
    }

    /**
     * Drop cached sources/layers that reference a replaced style. Unlike
     * [cleanup], in-memory feature data and DOM markers are preserved; features
     * are re-synced to the new style afterwards via
     * [com.geoman.maplibre.geoman.Geoman.onStyleReloaded].
     */
    fun clearRenderingCache() {
        contentStore.clearRenderingCache()
    }
}
