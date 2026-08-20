package com.geoman.maplibre.geoman.core.events

import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.events.GmEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Event bus for Geoman events.
 *
 * Uses Kotlin Flow for reactive event handling and a callback-based API
 * for imperative subscription. The callback API uses [CopyOnWriteArraySet]
 * so that [off] works correctly with any listener reference (including lambdas
 * wrapped in objects).
 */
class GmEventBus {
    private val _events = MutableSharedFlow<GmEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GmEvent> = _events.asSharedFlow()

    private val eventListeners = ConcurrentHashMap<String, CopyOnWriteArraySet<(GmEvent) -> Unit>>()

    private companion object {
        const val TAG = "GmEventBus"
    }

    /**
     * Emit an event to all listeners (suspending).
     */
    suspend fun emit(event: GmEvent) {
        _events.emit(event)
        notifyListeners(event)
    }

    /**
     * Emit an event to all listeners (non-suspending, for use in destroy/cleanup paths).
     */
    fun tryEmit(event: GmEvent): Boolean {
        val result = _events.tryEmit(event)
        notifyListeners(event)
        return result
    }

    private fun notifyListeners(event: GmEvent) {
        eventListeners[event.type]?.forEach { listener ->
            try {
                listener(event)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                GeomanLogger.e(TAG, "Error in event listener for ${event.type}", e)
            }
        }
    }

    /**
     * Subscribe to a specific event type.
     */
    fun on(eventType: String, listener: (GmEvent) -> Unit) {
        eventListeners.getOrPut(eventType) { CopyOnWriteArraySet() }.add(listener)
    }

    /**
     * Subscribe to a specific event type, firing only once.
     *
     * Thread-safe: uses [java.util.concurrent.atomic.AtomicBoolean] to ensure
     * the listener is invoked at most once even under concurrent emissions.
     */
    fun once(eventType: String, listener: (GmEvent) -> Unit) {
        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        val wrappedListener = object : (GmEvent) -> Unit {
            override fun invoke(event: GmEvent) {
                if (called.compareAndSet(false, true)) {
                    listener(event)
                    off(eventType, this)
                }
            }
        }
        on(eventType, wrappedListener)
    }

    /**
     * Unsubscribe from an event type.
     *
     * Because listeners are stored in a [CopyOnWriteArraySet], removal uses
     * reference equality which works for object-instance listeners and for
     * listeners registered via [once]. For raw lambda listeners, keep a
     * reference and pass it here.
     */
    fun off(eventType: String, listener: (GmEvent) -> Unit) {
        val listeners = eventListeners[eventType] ?: return
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            eventListeners.remove(eventType)
        }
    }

    /**
     * Clear all event listeners.
     */
    fun removeAllListeners() {
        eventListeners.clear()
    }

    /**
     * Clear listeners for a specific event type.
     */
    fun removeAllListeners(eventType: String) {
        eventListeners.remove(eventType)
    }
}
