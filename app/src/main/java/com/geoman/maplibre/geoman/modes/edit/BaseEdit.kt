package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all edit modes
 */
abstract class BaseEdit(geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.EDIT

    // Expose geoman to subclasses
    protected val geomanInstance: Geoman = geoman

    protected var selectedFeature: FeatureData? = null

    override fun disable() {
        super.disable()
        selectedFeature = null
    }

    abstract fun onMapClick(point: LatLng)

    protected suspend fun fireDragStartEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.DragStart(feature))
    }

    protected suspend fun fireDragEndEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.DragEnd(feature))
    }

    protected suspend fun fireChangeStartEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.ChangeStart(feature))
    }

    protected suspend fun fireChangeEndEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.ChangeEnd(feature))
    }

    protected suspend fun fireRotateStartEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.RotateStart(feature))
    }

    protected suspend fun fireRotateEndEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.RotateEnd(feature))
    }

    protected suspend fun fireCutStartEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.CutStart(feature))
    }

    protected suspend fun fireCutEndEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.CutEnd(feature))
    }

    protected suspend fun fireDeleteEvent(feature: FeatureData?) {
        geomanInstance.events.emit(GmEditEvent.Delete(feature))
    }
}
