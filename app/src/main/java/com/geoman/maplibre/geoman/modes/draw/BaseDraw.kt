package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.UUID
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
        val featuresToRemove = temporaryFeatures.toList()
        temporaryFeatures.clear()
        featuresToRemove.forEach {
            geoman.features.removeFeature(it.sourceName, it.id)
        }
    }

    abstract fun onMapClick(point: LatLng)
    abstract fun onMapLongClick(point: LatLng)
    abstract fun finishDrawing()

    /**
     * Generate a collision-free feature ID. Timestamp-based IDs collided when
     * two features were created within the same millisecond.
     */
    protected fun createFeatureId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    protected suspend fun fireCreateEvent(feature: FeatureData?) {
        val featureRef = feature ?: run {
            GeomanLogger.w("BaseDraw", "fireCreateEvent called with null feature")
            return
        }
        geoman.events.emit(GmDrawEvent.Create(modeName, featureRef))
    }
}
