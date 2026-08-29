package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.GeomanLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MapLibre implementation of the map event listener system.
 */
class MapLibreEventDispatcher : MapEventSystem {

    // Thread-safe collection for event listeners
    private val eventListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()

    override fun fire(type: String, data: Any?) {
        eventListeners[type]?.forEach { listener ->
            try {
                listener(data)
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                GeomanLogger.e("MapLibreAdapter", "Error in event listener for $type", e)
            }
        }
    }

    override fun on(type: String, listener: (Any?) -> Unit) {
        eventListeners.getOrPut(type) { CopyOnWriteArrayList() }.add(listener)
    }

    override fun once(type: String, listener: (Any?) -> Unit) {
        val called = AtomicBoolean(false)
        val wrappedListener = object : (Any?) -> Unit {
            override fun invoke(data: Any?) {
                if (called.compareAndSet(false, true)) {
                    listener(data)
                    off(type, this)
                }
            }
        }
        on(type, wrappedListener)
    }

    override fun off(type: String, listener: (Any?) -> Unit) {
        val listeners = eventListeners[type] ?: return
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            eventListeners.remove(type, listeners)
        }
    }

    /**
     * Drop all registered event listeners.
     */
    fun clearListeners() {
        eventListeners.clear()
    }
}
