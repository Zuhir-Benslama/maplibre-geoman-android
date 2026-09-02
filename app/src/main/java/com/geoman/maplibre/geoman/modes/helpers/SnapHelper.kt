package com.geoman.maplibre.geoman.modes.helpers

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
import org.maplibre.android.geometry.LatLng

/**
 * Snapping helper - snaps points to nearby vertices/segments
 */
class SnapHelper(geoman: Geoman) : BaseHelper(geoman) {

    override val modeName: String = HelperModeName.SNAP.name

    private companion object {
        /** Snap-guide line color (material pink). */
        const val SNAP_GUIDE_COLOR = "#E91E63"
    }

    private var snapDistance: Float = 20f // pixels
    private var snappedFeature: FeatureData? = null
    private var snappedCoordinate: LngLat? = null
    private var guideAdded = false

    override fun enable() {
        super.enable()
        snapDistance = geoman.options.helper.snapDistance
    }

    override fun onMapClick(point: LatLng) {
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
    private data class SnapResult(val point: LngLat, val feature: FeatureData, val distanceMeters: Double)

    /**
     * Pure snap lookup: computes the nearest snap target without touching
     * helper state or emitting events.
     */
    private fun findSnap(point: LatLng, sourceNames: List<String>?): SnapResult? {
        if (!enabled) return null

        val sources = sourceNames ?: listOf(
            GeomanCoreConstants.SOURCE_MARKERS,
            GeomanCoreConstants.SOURCE_LINES,
            GeomanCoreConstants.SOURCE_POLYGONS,
            GeomanCoreConstants.SOURCE_CIRCLES,
            GeomanCoreConstants.SOURCE_RECTANGLES,
        )

        val pointLngLat = LngLat(point.longitude, point.latitude)

        val allFeatures = sources.flatMap { source ->
            geoman.features.getFeatures(source).values.toList()
        }

        var nearestPoint: LngLat? = null
        var nearestFeature: FeatureData? = null
        var minDistance = Double.MAX_VALUE

        for (feature in allFeatures) {
            val snapped = snapToFeature(point, feature)
            if (snapped != null) {
                val distance = GeometryUtils.distance(pointLngLat, snapped)

                if (distance < minDistance) {
                    minDistance = distance
                    nearestPoint = snapped
                    nearestFeature = feature
                }
            }
        }

        val candidate = nearestPoint ?: return null
        val target = nearestFeature ?: return null
        if (minDistance >= pixelsToMeters(snapDistance, pointLngLat)) return null

        return SnapResult(candidate, target, minDistance)
    }

    /**
     * Snap a point to nearby features, recording the result as helper state and
     * firing [GmHelperEvent.SnapStart].
     */
    fun snap(point: LatLng, sourceNames: List<String>? = null): LngLat? {
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
    private fun snapToFeature(point: LatLng, feature: FeatureData): LngLat? {
        val geometry = feature.geometry

        return when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.Point -> {
                geometry.toLngLat()
            }

            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                val coords = geometry.toLngLats()
                GeometryUtils.nearestPointOnPolyline(
                    LngLat(point.longitude, point.latitude),
                    coords,
                )
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                val ring = geometry.getExteriorRing()
                GeometryUtils.nearestPointOnPolyline(
                    LngLat(point.longitude, point.latitude),
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
        val screenPoint = geoman.mapAdapter.project(point)
        val onePixelRight = geoman.mapAdapter.unproject(
            ScreenPoint(screenPoint.x + pixels, screenPoint.y),
        )
        return GeometryUtils.distance(point, onePixelRight)
    }

    /**
     * Check if a point is snappable. Side-effect free: unlike [snap], it does
     * not record state or emit events.
     */
    fun isSnappable(point: LatLng): Boolean = findSnap(point, null) != null

    /**
     * Show snap guides (visual indicators) from the press point to the snap target
     */
    fun showSnapGuides(point: LatLng) {
        if (!enabled) return

        val target = snap(point) ?: return
        ensureSnapGuidesLayer()

        val guide = FeatureCollection(
            features = listOf(
                com.geoman.maplibre.geoman.types.geojson.Feature(
                    geometry = LineString.fromLngLats(
                        listOf(LngLat(point.longitude, point.latitude), target),
                    ),
                ),
            ),
        )
        geoman.mapAdapter.getSource(FeatureSources.SNAP_GUIDES)?.setData(guide)
    }

    /**
     * Hide snap guides
     */
    fun hideSnapGuides() {
        if (!guideAdded) return
        val source = geoman.mapAdapter.getSource(FeatureSources.SNAP_GUIDES) ?: return
        source.setData(FeatureCollection(features = emptyList()))
    }

    private fun ensureSnapGuidesLayer() {
        // Re-check existence on every call instead of trusting the cached
        // flag: a style reload destroys sources and layers, so an early
        // return would permanently break guides for the rest of the session
        if (geoman.mapAdapter.getSource(FeatureSources.SNAP_GUIDES) == null) {
            geoman.mapAdapter.addSource(
                FeatureSources.SNAP_GUIDES,
                FeatureCollection(features = emptyList()),
            )
        }
        if (geoman.mapAdapter.getLayer(FeatureSources.SNAP_GUIDES + "_layer") == null) {
            geoman.mapAdapter.addLayer(
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
