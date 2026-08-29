package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.types.MapInteraction

/**
 * Interaction controls contract for map adapters.
 */
interface MapInteractionControl {
    /**
     * Disable map interactions
     */
    fun disableMapInteractions(interactionTypes: List<MapInteraction>)

    /**
     * Enable map interactions
     */
    fun enableMapInteractions(interactionTypes: List<MapInteraction>)

    /**
     * Enable/disable drag pan
     */
    fun setDragPan(enabled: Boolean)
}
