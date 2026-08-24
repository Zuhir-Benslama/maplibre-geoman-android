package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Rotate editing mode - allows rotating features around their centroid
 */
open class RotateEditor(geoman: GeomanApi) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.ROTATE.name

    private var isRotating = false
    private var rotatingFeature: FeatureData? = null
    private var centroid: LngLat? = null

    // Bearing from the centroid to the pointer at the previous frame; deltas
    // are computed frame-to-frame and normalized so crossing the ±180°
    // bearing discontinuity produces a small signed step instead of a ~360° spin
    private var lastPointerAngle: Double = 0.0
    private var totalRotation: Double = 0.0

    override fun disable() {
        if (isRotating) {
            finishRotation()
        }
        super.disable()
    }

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        if (isRotating) {
            updateRotation(point)
        } else {
            val features = queryFeaturesAt(
                LngLat(point.longitude, point.latitude),
                listOf(
                    GeomanCoreConstants.SOURCE_LINES,
                    GeomanCoreConstants.SOURCE_POLYGONS,
                    GeomanCoreConstants.SOURCE_CIRCLES,
                    GeomanCoreConstants.SOURCE_RECTANGLES,
                ),
            )

            if (features.isNotEmpty()) {
                startRotation(features.first(), point)
            }
        }
    }

    private fun startRotation(feature: FeatureData, startPoint: LatLng) {
        rotatingFeature = feature
        isRotating = true

        val c = calculateCentroid(feature)
        centroid = c
        lastPointerAngle = calculateAngle(c, LngLat(startPoint.longitude, startPoint.latitude))
        totalRotation = 0.0

        geoman.scope.launch {
            fireEditEvent({ GmEditEvent.RotateStart(it) }, feature)
        }
    }

    private fun updateRotation(point: LatLng) {
        val feature = rotatingFeature ?: return
        val c = centroid ?: return

        val currentAngle = calculateAngle(c, LngLat(point.longitude, point.latitude))
        val frameDelta = GeometryUtils.normalizeAngleDegrees(currentAngle - lastPointerAngle)
        if (frameDelta == 0.0) return

        lastPointerAngle = currentAngle
        totalRotation += frameDelta

        // Apply only the normalized frame delta to the current stored geometry;
        // applying the cumulative angle to already-rotated coordinates would compound
        rotateFeature(feature, c, frameDelta)
    }

    private fun finishRotation() {
        rotatingFeature?.let { stale ->
            val current = refreshFeature(stale) ?: stale
            geoman.scope.launch {
                fireEditEvent({ GmEditEvent.RotateEnd(it) }, current)
            }
        }

        isRotating = false
        rotatingFeature = null
        centroid = null
        lastPointerAngle = 0.0
        totalRotation = 0.0
    }

    private fun calculateCentroid(feature: FeatureData): LngLat {
        val geometry = feature.geometry

        return when (geometry) {
            is Point -> geometry.toLngLat()

            is LineString -> {
                val coords = geometry.toLngLats()
                if (coords.isEmpty()) {
                    LngLat(0.0, 0.0)
                } else {
                    GeometryUtils.calculateCentroid(coords)
                }
            }

            is Polygon -> {
                val ring = geometry.getExteriorRing()
                GeometryUtils.calculateCentroid(ring)
            }

            else -> LngLat(0.0, 0.0)
        }
    }

    private fun calculateAngle(center: LngLat, point: LngLat): Double {
        // Scale longitude by cos(latitude) so the angle is measured in
        // approximately equirectangular (metre-like) space
        val scale = cos(Math.toRadians(center.latitude)).coerceAtLeast(1e-6)
        val dx = (point.longitude - center.longitude) * scale
        val dy = point.latitude - center.latitude
        return Math.toDegrees(atan2(dy, dx))
    }

    private fun rotateFeature(feature: FeatureData, center: LngLat, angle: Double) {
        val angleRad = Math.toRadians(angle)

        updateFeatureGeometry(feature) { geometry ->
            when (geometry) {
                is LineString -> LineString(
                    coordinates = geometry.coordinates.map { coord ->
                        rotatePoint(LngLat(coord[0], coord[1]), center, angleRad)
                            .let { listOf(it.longitude, it.latitude) }
                    },
                )

                is Polygon -> Polygon(
                    coordinates = geometry.coordinates.map { ring ->
                        ring.map { coord ->
                            rotatePoint(LngLat(coord[0], coord[1]), center, angleRad)
                                .let { listOf(it.longitude, it.latitude) }
                        }
                    },
                )

                else -> geometry
            }
        }
    }

    private fun rotatePoint(point: LngLat, center: LngLat, angleRad: Double): LngLat =
        EditorGeometry.rotatePoint(point, center, angleRad)
}
