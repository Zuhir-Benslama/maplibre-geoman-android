package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.Geometry
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all edit modes
 */
abstract class BaseEdit(geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.EDIT

    protected var selectedFeature: FeatureData? = null

    override fun disable() {
        super.disable()
        selectedFeature = null
    }

    abstract fun onMapClick(point: LatLng)

    /**
     * Fire an edit event with the given factory and feature.
     * Reduces boilerplate in subclasses.
     */
    protected suspend fun fireEditEvent(eventFactory: (FeatureData?) -> GmEditEvent, feature: FeatureData?) {
        geoman.events.emit(eventFactory(feature))
    }

    protected fun updateFeatureGeometry(feature: FeatureData, newGeometry: Geometry) {
        val updatedFeature = feature.copy(
            feature = feature.feature.copy(geometry = newGeometry),
        )
        geoman.features.updateFeature(feature.sourceName, feature.id) { updatedFeature }
    }
}
