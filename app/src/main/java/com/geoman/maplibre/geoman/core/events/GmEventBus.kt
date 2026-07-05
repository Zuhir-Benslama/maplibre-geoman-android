package com.geoman.maplibre.geoman.core.events

import com.geoman.maplibre.geoman.types.events.GmEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for Geoman events
 * Uses Kotlin Flow for reactive event handling
 */
class GmEventBus {
    private val _events = MutableSharedFlow<GmEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GmEvent> = _events.asSharedFlow()

    private val eventListeners = mutableMapOf<String, MutableList<(GmEvent) -> Unit>>()

    /**
     * Emit an event to all listeners
     */
    suspend fun emit(event: GmEvent) {
        _events.emit(event)
        eventListeners[event.type]?.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }

    /**
     * Subscribe to a specific event type
     */
    fun on(eventType: String, listener: (GmEvent) -> Unit) {
        eventListeners.getOrPut(eventType) { mutableListOf() }.add(listener)
    }

    /**
     * Subscribe to a specific event type (once)
     */
    fun once(eventType: String, listener: (GmEvent) -> Unit) {
        val wrappedListener = object : (GmEvent) -> Unit {
            var called = false
            override fun invoke(event: GmEvent) {
                if (!called) {
                    called = true
                    listener(event)
                    off(eventType, this)
                }
            }
        }
        on(eventType, wrappedListener)
    }

    /**
     * Unsubscribe from an event type
     */
    fun off(eventType: String, listener: (GmEvent) -> Unit) {
        eventListeners[eventType]?.remove(listener)
        if (eventListeners[eventType]?.isEmpty() == true) {
            eventListeners.remove(eventType)
        }
    }

    /**
     * Clear all event listeners
     */
    fun removeAllListeners() {
        eventListeners.clear()
    }

    /**
     * Clear listeners for a specific event type
     */
    fun removeAllListeners(eventType: String) {
        eventListeners.remove(eventType)
    }
}
