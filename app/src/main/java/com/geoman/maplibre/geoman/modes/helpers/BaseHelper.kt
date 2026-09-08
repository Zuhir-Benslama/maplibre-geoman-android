package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Base class for all helper modes.
 *
 * Helpers operate on the [GeomanApi] surface like edit/draw modes; helpers
 * that additionally need raw adapter source/layer access (e.g. [SnapHelper])
 * keep the concrete adapter on their own constructor.
 */
abstract class BaseHelper(geoman: GeomanApi) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.HELPER

    open fun onMapClick(point: LngLat) {
        GeomanLogger.d("BaseHelper", "Unhandled map click for ${this::class.simpleName} at $point")
    }
}
