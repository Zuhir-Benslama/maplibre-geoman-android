package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all draw modes
 */
abstract class BaseDraw(
    geoman: Geoman
) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.DRAW

    // Expose geoman to subclasses
    protected val geomanInstance: Geoman = geoman

    protected val temporaryFeatures = mutableListOf<FeatureData>()

    override fun disable() {
        android.util.Log.d("BaseDraw", "disable() called for $modeName, was enabled=$enabled")
        super.disable()
        // Clean up temporary features
        temporaryFeatures.forEach {
            geomanInstance.features.removeFeature(it.sourceName, it.id)
        }
        temporaryFeatures.clear()
    }

    abstract fun onMapClick(point: LatLng)
    abstract fun onMapLongClick(point: LatLng)
    abstract fun finishDrawing()

    protected suspend fun fireCreateEvent(feature: FeatureData?) {
        // Capture feature reference to avoid race condition
        val featureRef = feature ?: run {
            android.util.Log.w("BaseDraw", "fireCreateEvent called with null feature")
            return
        }
        
        android.util.Log.d("BaseDraw", "fireCreateEvent called: feature=${featureRef.id}, modeName=$modeName")
        geomanInstance.events.emit(GmDrawEvent.Create(modeName, featureRef))
        android.util.Log.d("BaseDraw", "fireCreateEvent: event emitted")
    }
}
