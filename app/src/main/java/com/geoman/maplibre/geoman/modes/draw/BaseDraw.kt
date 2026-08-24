package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base class for all draw modes
 */
abstract class BaseDraw(geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.DRAW

    // Thread-safe list for temporary features that may be accessed from both
    // map click handlers (main thread) and disable() (any thread).
    protected val temporaryFeatures = CopyOnWriteArrayList<FeatureData>()

    override fun disable() {
        super.disable()
        // Drain one-by-one, popping each feature out of the tracking list
        // BEFORE removing it from the map. A snapshot-then-clear left a window
        // where a concurrent onMapClick could add a feature that was then
        // wiped from tracking but orphaned on the map.
        while (true) {
            val feature = temporaryFeatures.removeFirstOrNull() ?: break
            geoman.features.removeFeature(feature.sourceName, feature.id)
        }
    }

    abstract fun onMapClick(point: LatLng)
    abstract fun onMapLongClick(point: LatLng)
    abstract fun finishDrawing()

    protected suspend fun fireCreateEvent(feature: FeatureData?) {
        val featureRef = feature ?: run {
            GeomanLogger.w("BaseDraw", "fireCreateEvent called with null feature")
            return
        }
        geoman.events.emit(GmDrawEvent.Create(modeName, featureRef))
    }
}
