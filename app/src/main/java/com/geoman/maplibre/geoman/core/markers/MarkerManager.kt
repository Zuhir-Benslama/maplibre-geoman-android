package com.geoman.maplibre.geoman.core.markers

import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Minimal marker contract used by [MarkerManager].
 *
 * [com.geoman.maplibre.geoman.adapter.DomMarker] implements this directly,
 * and tests can supply lightweight fakes.
 */
interface ManagedMarker {
    fun getLngLat(): LngLat
    fun setLngLat(lngLat: LngLat)
    fun remove()
    var onClick: (() -> Unit)?
}

/**
 * Central lifecycle registry for DOM markers (vertex handles, midpoint
 * handles, labels).
 *
 * Markers are keyed by string ID so editors can replace or reposition them
 * without holding references that go stale across feature updates. All
 * mutation goes through the manager, guaranteeing that removal also detaches
 * click handlers and calls through to the platform marker's [ManagedMarker.remove].
 *
 * Thread safety: all state is guarded by `this`.
 */
class MarkerManager {

    private val markers = LinkedHashMap<String, ManagedMarker>()

    val size: Int get() = synchronized(this) { markers.size }

    val ids: Set<String> get() = synchronized(this) { markers.keys.toSet() }

    /**
     * Register a marker under [id], optionally attaching a click handler.
     *
     * @throws IllegalArgumentException when [id] is already registered
     */
    fun add(id: String, marker: ManagedMarker, onClick: (() -> Unit)? = null) {
        synchronized(this) {
            require(id !in markers) { "marker id already registered: $id" }
            marker.onClick = onClick
            markers[id] = marker
        }
    }

    fun get(id: String): ManagedMarker? = synchronized(this) { markers[id] }

    fun contains(id: String): Boolean = synchronized(this) { id in markers }

    /** Move a registered marker; returns false when the id is unknown. */
    fun updatePosition(id: String, lngLat: LngLat): Boolean = synchronized(this) {
        markers[id]?.let {
            it.setLngLat(lngLat)
            true
        } ?: false
    }

    /** Replace the click handler of a registered marker; returns false when unknown. */
    fun setClickListener(id: String, handler: (() -> Unit)?): Boolean = synchronized(this) {
        markers[id]?.let {
            it.onClick = handler
            true
        } ?: false
    }

    /**
     * Remove a marker from the map and the registry.
     *
     * @return the removed marker, or null when the id is unknown
     */
    fun remove(id: String): ManagedMarker? = synchronized(this) {
        markers.remove(id)?.also {
            it.onClick = null
            it.remove()
        }
    }

    /**
     * Remove every marker whose id matches [predicate]; returns removed ids.
     */
    fun removeWhere(predicate: (String) -> Boolean): List<String> {
        val toRemove = synchronized(this) { markers.keys.filter(predicate) }
        toRemove.forEach { remove(it) }
        return toRemove
    }

    /** Remove all markers from the map and the registry. */
    fun clear() {
        val all = synchronized(this) {
            val snapshot = markers.values.toList()
            markers.clear()
            snapshot
        }
        all.forEach {
            it.onClick = null
            it.remove()
        }
    }
}
