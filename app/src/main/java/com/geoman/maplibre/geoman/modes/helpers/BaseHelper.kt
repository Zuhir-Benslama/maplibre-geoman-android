package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.ModeType
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all helper modes.
 *
 * Helpers use the full platform adapter (fitBounds, unproject, source/layer
 * management), so unlike edit modes they require the concrete [Geoman].
 */
abstract class BaseHelper(protected override val geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.HELPER

    open fun onMapClick(point: LatLng) {
        GeomanLogger.d("BaseHelper", "Unhandled map click for ${this::class.simpleName} at $point")
    }
}
