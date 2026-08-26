package com.geoman.maplibre.geoman.modes.edit

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.MarkerAnchor
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.history.GeometryChange
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all edit modes
 */
abstract class BaseEdit(geoman: GeomanApi) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.EDIT

    protected var selectedFeature: FeatureData? = null

    override fun disable() {
        super.disable()
        selectedFeature = null
    }

    abstract fun onMapClick(point: LatLng)

    /**
     * Query features under a geographic point. Seam over the map adapter so
     * tests can substitute hit-testing.
     */
    protected open fun queryFeaturesAt(lngLat: LngLat, sources: List<String>): List<FeatureData> =
        geoman.mapActions.queryFeaturesByScreenCoordinates(geoman.mapActions.project(lngLat), sources)

    /**
     * Create a platform DOM marker. Seam over the map adapter so tests can
     * substitute fake markers.
     */
    protected open fun createDomMarkerAt(options: DomMarkerOptions, position: LngLat): DomMarker =
        geoman.mapActions.createDomMarker(options, position)

    /**
     * Draggable vertex-style handle with the default red circle view.
     */
    protected open fun createDraggableMarker(position: LngLat, onDrag: (LngLat) -> Unit): DomMarker {
        val marker = createDomMarkerAt(
            DomMarkerOptions(
                element = createHandleView(handleColor = HANDLE_COLOR_VERTEX, strokeDp = 2f),
                anchor = MarkerAnchor.CENTER,
                draggable = true,
            ),
            position,
        )
        marker.onDrag = onDrag
        marker.addToMap()
        return marker
    }

    /**
     * Clickable midpoint-style handle with the default blue circle view.
     */
    protected open fun createClickableMarker(position: LngLat, onClick: () -> Unit): DomMarker {
        val marker = createDomMarkerAt(
            DomMarkerOptions(
                element = createHandleView(handleColor = HANDLE_COLOR_MIDPOINT, strokeDp = 1.5f),
                anchor = MarkerAnchor.CENTER,
                draggable = false,
            ),
            position,
        )
        marker.onClick = onClick
        marker.addToMap()
        return marker
    }

    /**
     * Build the circular handle view. Requires the platform [Geoman] for a
     * context; tests override the marker seams above and never reach this.
     */
    private fun createHandleView(handleColor: Int, strokeDp: Float): View {
        val context = geoman.mapActions.getContext()
        val size = (HANDLE_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val view = View(context)
        view.layoutParams = ViewGroup.LayoutParams(size, size)

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(handleColor)
            setStroke((strokeDp * context.resources.displayMetrics.density).toInt(), Color.WHITE)
            setSize(size, size)
        }
        view.background = drawable

        return view
    }

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

    private companion object {
        const val HANDLE_SIZE_DP = 14f
        const val HANDLE_COLOR_VERTEX = Color.RED
        const val HANDLE_COLOR_MIDPOINT = Color.BLUE
    }
}
