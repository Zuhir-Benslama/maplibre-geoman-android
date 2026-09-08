package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Base class for all draw modes
 */
abstract class BaseDraw(geoman: GeomanApi) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.DRAW

    abstract fun onMapClick(point: LngLat)
    abstract fun onMapLongClick(point: LngLat)
    abstract fun finishDrawing()

    protected suspend fun fireCreateEvent(feature: FeatureData?) {
        val featureRef = feature ?: run {
            GeomanLogger.w("BaseDraw", "fireCreateEvent called with null feature")
            return
        }
        geoman.events.emit(GmDrawEvent.Create(modeName, featureRef))
    }
}
