package com.geoman.maplibre.geoman.adapter

/**
 * Map event listener contract for map adapters.
 */
interface MapEventSystem {
    /**
     * Fire a map event
     */
    fun fire(type: String, data: Any? = null)

    /**
     * Add an event listener
     */
    fun on(type: String, listener: (Any?) -> Unit)

    /**
     * Add a one-time event listener
     */
    fun once(type: String, listener: (Any?) -> Unit)

    /**
     * Remove an event listener
     */
    fun off(type: String, listener: (Any?) -> Unit)
}
