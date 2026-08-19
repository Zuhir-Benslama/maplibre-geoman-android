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

        val pointLngLat = LngLat(point.longitude, point.latitude)

        val allFeatures = sources.flatMap { source ->
            geoman.features.getFeatures(source).values.toList()
        }

        var nearestPoint: LngLat? = null
        var minDistance = Double.MAX_VALUE

        for (feature in allFeatures) {
            val snapped = snapToFeature(point, feature)
            if (snapped != null) {
                val distance = GeometryUtils.calculateDistance(pointLngLat, snapped)

                if (distance < minDistance) {
                    minDistance = distance
                    nearestPoint = snapped
                    snappedFeature = feature
                }
            }
        }

        if (nearestPoint != null && minDistance < pixelsToMeters(snapDistance, pointLngLat)) {
            snappedCoordinate = nearestPoint

            geoman.scope.launch {
                geoman.events.emit(GmHelperEvent.SnapStart(snappedFeature))
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
     * Check if a point is snappable
     */
    fun isSnappable(point: LatLng): Boolean = snap(point) != null

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
        if (guideAdded) return
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
                        "line-color" to "#E91E63",
                        "line-width" to 2.0,
                        "line-dasharray" to listOf(2.0, 2.0),
                    ),
                ),
            )
        }
        guideAdded = true
    }
}
