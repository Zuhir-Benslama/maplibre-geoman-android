package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.types.MapInteraction
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre implementation of map interaction toggles.
 */
class MapLibreInteractionManager(private val map: MapLibreMap) : MapInteractionControl {

    override fun disableMapInteractions(interactionTypes: List<MapInteraction>) {
        interactionTypes.forEach { toggleInteraction(it, enabled = false) }
    }

    override fun enableMapInteractions(interactionTypes: List<MapInteraction>) {
        interactionTypes.forEach { toggleInteraction(it, enabled = true) }
    }

    override fun setDragPan(enabled: Boolean) {
        map.uiSettings.isScrollGesturesEnabled = enabled
    }

    private fun toggleInteraction(interaction: MapInteraction, enabled: Boolean) {
        val settings = map.uiSettings
        when (interaction) {
            MapInteraction.SCROLL -> settings.isScrollGesturesEnabled = enabled

            MapInteraction.ZOOM -> {
                settings.isZoomGesturesEnabled = enabled
                settings.isDoubleTapGesturesEnabled = enabled
            }

            MapInteraction.ROTATE -> settings.isRotateGesturesEnabled = enabled

            MapInteraction.PITCH -> settings.isTiltGesturesEnabled = enabled

            MapInteraction.DRAG_PAN -> settings.isScrollGesturesEnabled = enabled

            MapInteraction.BOX_ZOOM -> settings.isZoomGesturesEnabled = enabled

            MapInteraction.DOUBLE_CLICK_ZOOM -> settings.isDoubleTapGesturesEnabled = enabled

            MapInteraction.TOUCH_ZOOM -> settings.isZoomGesturesEnabled = enabled

            MapInteraction.TOUCH_ROTATE -> settings.isRotateGesturesEnabled = enabled

            MapInteraction.TOUCH_PITCH -> settings.isTiltGesturesEnabled = enabled

            MapInteraction.DRAG_ROTATE -> settings.isRotateGesturesEnabled = enabled

            MapInteraction.KEYBOARD -> Unit
        }
    }
}
