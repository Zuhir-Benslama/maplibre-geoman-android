package com.geoman.maplibre.geoman.modes.helpers

import androidx.annotation.MainThread
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.LayerType
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureSources
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.events.GmHelperEvent
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch

/**
 * Snapping helper - snaps points to nearby vertices/segments
 */
class SnapHelper(mapGeoman: Geoman) : BaseHelper(mapGeoman) {

    override val modeName: String = HelperModeName.SNAP.name

    /** Concrete adapter needed for source/layer manipulation exposed on Geoman only. */
    private val adapter = mapGeoman.mapAdapter

    private companion object {
        /** Snap-guide line color (material pink). */
        const val SNAP_GUIDE_COLOR = "#E91E63"

        /** Feature sources whose geometry participates in snapping. */
        val SNAP_SOURCES = listOf(
            GeomanCoreConstants.SOURCE_MARKERS,
            GeomanCoreConstants.SOURCE_LINES,
            GeomanCoreConstants.SOURCE_POLYGONS,
            GeomanCoreConstants.SOURCE_CIRCLES,
            GeomanCoreConstants.SOURCE_RECTANGLES,
        )
    }

    private var snapDistance: Float = 20f // pixels
    private var snappedFeature: FeatureData? = null
    private var snappedCoordinate: LngLat? = null
    private var guideAdded = false

    override fun enable() {
        super.enable()
        snapDistance = geoman.options.helper.snapDistance
    }

    @MainThread
    override fun onMapClick(point: LngLat) {
        showSnapGuides(point)
    }

    override fun disable() {
        hideSnapGuides()
        guideAdded = false
        snappedFeature = null
        snappedCoordinate = null
        super.disable()
    }

    /**
     * Outcome of a snap lookup: the snapped coordinate, the feature it belongs
     * to, and the distance in meters from the query point.
     */
    private typealias SnapResult = SnapSelection.Target

    /**
     * Pure snap lookup: computes the nearest snap target without touching
     * helper state or emitting events.
     *
     * The search is limited to features whose bounding box intersects a box of
     * radius `snapDistance` around the click (see [SnapSelection.snapBounds]),
     * so the cost stays proportional to nearby features instead of the whole
     * store.
     */
    private fun findSnap(point: LngLat, sourceNames: List<String>?): SnapResult? {
        if (!enabled) return null

        val sources = sourceNames ?: SNAP_SOURCES

        // Convert the pixel snap radius to meters once and use it both to size
        // the candidate box and to filter candidate distances.
        val snapRadiusMeters = pixelsToMeters(snapDistance, point)
        if (snapRadiusMeters <= 0.0) return null

        val candidates = geoman.features.getFeaturesInBounds(
            bounds = SnapSelection.snapBounds(point, snapRadiusMeters),
            sourceNames = sources,
        )

        return SnapSelection.selectNearest(point, snapRadiusMeters, candidates) { feature ->
            snapToFeature(point, feature)
        }
    }

    /**
     * Snap a point to nearby features, recording the result as helper state and
     * firing [GmHelperEvent.SnapStart].
     */
    fun snap(point: LngLat, sourceNames: List<String>? = null): LngLat? {
        val result = findSnap(point, sourceNames) ?: return null

        snappedFeature = result.feature
        snappedCoordinate = result.point

        geoman.scope.launch {
            geoman.events.emit(GmHelperEvent.SnapStart(result.feature))
        }

        return result.point
    }

    /**
     * Snap a point to a specific feature
     */
    private fun snapToFeature(point: LngLat, feature: FeatureData): LngLat? {
        val geometry = feature.geometry

        return when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.Point -> {
                geometry.toLngLat()
            }

            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                val coords = geometry.toLngLats()
                GeometryUtils.nearestPointOnPolyline(
                    point,
                    coords,
                )
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                val ring = geometry.getExteriorRing()
                GeometryUtils.nearestPointOnPolyline(
                    point,
                    ring,
                )
            }

            else -> null
        }
    }

    /**
     * Get the currently snapped coordinate
     */
    fun getSnappedCoordinate(): LngLat? = snappedCoordinate

    /**
     * Get the currently snapped feature
     */
    fun getSnappedFeature(): FeatureData? = snappedFeature

    /**
     * Clear snap state
     */
    fun clearSnap() {
        snappedFeature?.let {
            geoman.scope.launch {
                geoman.events.emit(GmHelperEvent.SnapEnd(it))
            }
        }
        snappedFeature = null
        snappedCoordinate = null
        hideSnapGuides()
    }

    /**
     * Convert pixels to meters at the current zoom level by projecting a point
     * and measuring the ground distance of one pixel at that location
     */
    private fun pixelsToMeters(pixels: Float, point: LngLat): Double {
        val screenPoint = adapter.project(point)
        val onePixelRight = adapter.unproject(
            ScreenPoint(screenPoint.x + pixels, screenPoint.y),
        )
        return GeometryUtils.distance(point, onePixelRight)
    }

    /**
     * Check if a point is snappable. Side-effect free: unlike [snap], it does
     * not record state or emit events.
     */
    fun isSnappable(point: LngLat): Boolean = findSnap(point, null) != null

    /**
     * Show snap guides (visual indicators) from the press point to the snap target
     */
    fun showSnapGuides(point: LngLat) {
        if (!enabled) return

        val target = snap(point) ?: return
        ensureSnapGuidesLayer()

        val guide = FeatureCollection(
            features = listOf(
                com.geoman.maplibre.geoman.types.geojson.Feature(
                    geometry = LineString.fromLngLats(
                        listOf(point, target),
                    ),
                ),
            ),
        )
        adapter.getSource(FeatureSources.SNAP_GUIDES)?.setData(guide)
    }

    /**
     * Hide snap guides
     */
    fun hideSnapGuides() {
        if (!guideAdded) return
        val source = adapter.getSource(FeatureSources.SNAP_GUIDES) ?: return
        source.setData(FeatureCollection(features = emptyList()))
    }

    private fun ensureSnapGuidesLayer() {
        // Re-check existence on every call instead of trusting the cached
        // flag: a style reload destroys sources and layers, so an early
        // return would permanently break guides for the rest of the session
        if (adapter.getSource(FeatureSources.SNAP_GUIDES) == null) {
            adapter.addSource(
                FeatureSources.SNAP_GUIDES,
                FeatureCollection(features = emptyList()),
            )
        }
        if (adapter.getLayer(FeatureSources.SNAP_GUIDES + "_layer") == null) {
            adapter.addLayer(
                LayerOptions(
                    id = FeatureSources.SNAP_GUIDES + "_layer",
                    type = LayerType.LINE,
                    source = FeatureSources.SNAP_GUIDES,
                    paint = mapOf(
                        "line-color" to SNAP_GUIDE_COLOR,
                        "line-width" to 2.0,
                        "line-dasharray" to listOf(2.0, 2.0),
                    ),
                ),
            )
        }
        guideAdded = true
    }
}
