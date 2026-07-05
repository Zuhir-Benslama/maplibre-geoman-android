package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.BaseAction
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmHelperEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Base class for all helper modes
 */
abstract class BaseHelper(geoman: Geoman) : BaseAction(geoman) {

    override val modeType: ModeType = ModeType.HELPER

    // Expose geoman to subclasses
    protected val geomanInstance: Geoman = geoman
}

/**
 * Snapping helper - snaps points to nearby vertices/segments
 */
class SnapHelper(geoman: Geoman) : BaseHelper(geoman) {

    override val modeName: String = HelperModeName.SNAP.name

    private var snapDistance: Float = 20f // pixels
    private var snappedFeature: FeatureData? = null
    private var snappedCoordinate: LngLat? = null

    override fun enable() {
        super.enable()
        snapDistance = geomanInstance.options.helper.snapDistance
    }

    override fun disable() {
        snappedFeature = null
        snappedCoordinate = null
        super.disable()
    }

    /**
     * Snap a point to nearby features
     */
    fun snap(point: LatLng, sourceNames: List<String>? = null): LngLat? {
        if (!enabled) return null

        val sources = sourceNames ?: listOf(
            GeomanCoreConstants.SOURCE_MARKERS,
            GeomanCoreConstants.SOURCE_LINES,
            GeomanCoreConstants.SOURCE_POLYGONS,
            GeomanCoreConstants.SOURCE_CIRCLES,
            GeomanCoreConstants.SOURCE_RECTANGLES,
        )

        geomanInstance.mapAdapter.project(LngLat(point.longitude, point.latitude))

        val allFeatures = sources.flatMap { source ->
            geomanInstance.features.getFeatures(source).values.toList()
        }

        var nearestPoint: LngLat? = null
        var minDistance = Double.MAX_VALUE

        for (feature in allFeatures) {
            val snapped = snapToFeature(point, feature)
            if (snapped != null) {
                val distance = GeometryUtils.calculateDistance(
                    LngLat(point.longitude, point.latitude),
                    snapped,
                )

                if (distance < minDistance) {
                    minDistance = distance
                    nearestPoint = snapped
                    snappedFeature = feature
                }
            }
        }

        if (nearestPoint != null && minDistance < pixelsToMeters(snapDistance)) {
            snappedCoordinate = nearestPoint

            geomanInstance.scope.launch {
                geomanInstance.events.emit(GmHelperEvent.SnapStart(snappedFeature))
            }

            return nearestPoint
        }

        return null
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
            geomanInstance.scope.launch {
                geomanInstance.events.emit(GmHelperEvent.SnapEnd(it))
            }
        }
        snappedFeature = null
        snappedCoordinate = null
    }

    /**
     * Convert pixels to meters (approximate at current zoom level)
     */
    private fun pixelsToMeters(pixels: Float): Double {
        // This is a rough approximation
        // A more accurate calculation would use the current zoom level
        val metersPerPixel = 1.0 // Simplified
        return pixels * metersPerPixel
    }

    /**
     * Check if a point is snappable
     */
    fun isSnappable(point: LatLng): Boolean = snap(point) != null

    /**
     * Show snap guides (visual indicators)
     */
    fun showSnapGuides(@Suppress("UNUSED_PARAMETER") point: LatLng) {
        // Draw visual guides showing snap targets
        // This would create temporary line layers
    }

    /**
     * Hide snap guides
     */
    fun hideSnapGuides() {
        // Remove temporary guide layers
    }
}
