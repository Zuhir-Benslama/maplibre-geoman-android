package com.geoman.maplibre.geoman.adapter

import android.view.View
import com.geoman.maplibre.geoman.types.geojson.LngLat

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
