package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.utils.GeometryUtils

/**
 * Pure snap-selection helpers used by [SnapHelper], extracted so the
 * candidate-limiting and nearest-point logic can be unit tested on the JVM
 * without a map adapter.
 *
 * The bounding-box trick keeps snap lookups independent of store size: instead
 * of scanning every stored feature, queries only consider features whose
 * bounding box intersects a box of radius [SnapSelection.snapBounds] around
 * the click point. For small radii this box contains every coordinate within
 * snap distance, so it is a conservative filter — no reachable candidate is
 * dropped.
 */
internal object SnapSelection {

    /** A snapped coordinate, the feature it belongs to, and its distance from the query point. */
    data class Target(val point: LngLat, val feature: FeatureData, val distanceMeters: Double)

    /**
     * Axis-aligned (longitude/latitude) box around [center] that contains every
     * point within [radiusMeters] of it. Computed from the four cardinal
     * destinations, so it tracks the geodesic circle at the given latitude.
     */
    fun snapBounds(center: LngLat, radiusMeters: Double): List<LngLat> {
        require(radiusMeters.isFinite() && radiusMeters >= 0.0) {
            "snap radius must be finite and non-negative, was $radiusMeters"
        }
        val north = GeometryUtils.calculateDestination(center, 0.0, radiusMeters)
        val south = GeometryUtils.calculateDestination(center, 180.0, radiusMeters)
        val east = GeometryUtils.calculateDestination(center, 90.0, radiusMeters)
        val west = GeometryUtils.calculateDestination(center, 270.0, radiusMeters)
        return listOf(
            LngLat(west.longitude, south.latitude),
            LngLat(east.longitude, north.latitude),
        )
    }

    /**
     * Nearest [Target] among [candidates] whose snapped coordinate is within
     * [thresholdMeters] of [point], or null when nothing snaps.
     */
    fun selectNearest(
        point: LngLat,
        thresholdMeters: Double,
        candidates: Iterable<FeatureData>,
        snapOf: (FeatureData) -> LngLat?,
    ): Target? {
        var nearestPoint: LngLat? = null
        var nearestFeature: FeatureData? = null
        var minDistance = Double.MAX_VALUE

        for (feature in candidates) {
            val snapped = snapOf(feature) ?: continue
            val distance = GeometryUtils.distance(point, snapped)
            if (distance < minDistance) {
                minDistance = distance
                nearestPoint = snapped
                nearestFeature = feature
            }
        }

        val point2 = nearestPoint ?: return null
        val feature = nearestFeature ?: return null
        if (minDistance >= thresholdMeters) return null
        return Target(point2, feature, minDistance)
    }
}
