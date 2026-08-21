package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.history.GeometryChange
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

    /**
     * Apply [transform] to the *current* geometry of the feature, as stored in
     * [com.geoman.maplibre.geoman.core.features.Features].
     *
     * Editors keep long-lived references to features which go stale as soon as
     * the store is updated; reading through the store here prevents edits from
     * being computed against outdated geometry. Returns the updated
     * [FeatureData] so callers can refresh their reference, or `null` if the
     * feature no longer exists in the store.
     */
    protected fun updateFeatureGeometry(feature: FeatureData, transform: (Geometry) -> Geometry): FeatureData? {
        val current = geoman.features.getFeature(feature.sourceName, feature.id) ?: return null
        val newGeometry = transform(current.geometry)
        if (newGeometry == current.geometry) return current

        geoman.history.record(
            GeometryChange(
                sourceName = current.sourceName,
                featureId = current.id,
                before = current.geometry,
                after = newGeometry,
            ),
        )

        val updated = current.copy(feature = current.feature.copy(geometry = newGeometry))
        geoman.features.updateFeature(current.sourceName, current.id) { updated }
        return updated
    }

    /**
     * Fetch the latest state of [feature] from the store, for firing events with
     * up-to-date data.
     */
    protected fun refreshFeature(feature: FeatureData): FeatureData? =
        geoman.features.getFeature(feature.sourceName, feature.id)
}
