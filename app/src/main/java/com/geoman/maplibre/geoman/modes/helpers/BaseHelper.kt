package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.ModeType
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all helper modes
 */
abstract class BaseHelper(geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.HELPER

    open fun onMapClick(point: LatLng) {
        GeomanLogger.d("BaseHelper", "Unhandled map click for ${this::class.simpleName} at $point")
    }
}
