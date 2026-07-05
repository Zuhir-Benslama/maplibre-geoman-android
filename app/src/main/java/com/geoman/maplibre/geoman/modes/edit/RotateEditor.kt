package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rotate editing mode - allows rotating features around their centroid
 */
class RotateEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.ROTATE.name

    private var isRotating = false
    private var rotatingFeature: FeatureData? = null
    private var centroid: LngLat? = null
    private var rotationStartAngle: Double = 0.0
    private var initialRotation: Double = 0.0

    override fun enable() {
        super.enable()
    }

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
            val features = geomanInstance.mapAdapter.queryFeaturesByScreenCoordinates(
                geomanInstance.mapAdapter.project(LngLat(point.longitude, point.latitude)),
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

        centroid = calculateCentroid(feature)
        rotationStartAngle = calculateAngle(centroid!!, LngLat(startPoint.longitude, startPoint.latitude))
        initialRotation = 0.0

        geomanInstance.scope.launch {
            fireRotateStartEvent(feature)
        }
    }

    private fun updateRotation(point: LatLng) {
        if (!isRotating || rotatingFeature == null || centroid == null) return

        val currentAngle = calculateAngle(centroid!!, LngLat(point.longitude, point.latitude))
        val rotationDelta = currentAngle - rotationStartAngle
        rotateFeature(rotatingFeature!!, centroid!!, initialRotation + rotationDelta)
    }

    private fun finishRotation() {
        rotatingFeature?.let {
            geomanInstance.scope.launch {
                fireRotateEndEvent(it)
            }
        }

        isRotating = false
        rotatingFeature = null
        centroid = null
        rotationStartAngle = 0.0
        initialRotation = 0.0
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
                    LngLat(
                        coords.map { it.longitude }.average(),
                        coords.map { it.latitude }.average(),
                    )
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
        val dx = point.longitude - center.longitude
        val dy = point.latitude - center.latitude
        return Math.toDegrees(atan2(dy, dx))
    }

    private fun rotateFeature(feature: FeatureData, center: LngLat, angle: Double) {
        val geometry = feature.geometry
        val angleRad = Math.toRadians(angle)

        when (geometry) {
            is LineString -> {
                val newCoords = geometry.coordinates.map { coord ->
                    rotatePoint(LngLat(coord[0], coord[1]), center, angleRad)
                }
                val newGeometry = LineString(coordinates = newCoords.map { listOf(it.longitude, it.latitude) })
                updateFeatureGeometry(feature, newGeometry)
            }

            is Polygon -> {
                val newRings = geometry.coordinates.map { ring ->
                    ring.map { coord ->
                        rotatePoint(LngLat(coord[0], coord[1]), center, angleRad)
                    }
                }
                val newGeometry =
                    Polygon(coordinates = newRings.map { ring -> ring.map { listOf(it.longitude, it.latitude) } })
                updateFeatureGeometry(feature, newGeometry)
            }

            else -> {
                // Unsupported geometry type for rotation
            }
        }
    }

    private fun rotatePoint(point: LngLat, center: LngLat, angleRad: Double): LngLat {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        val dx = point.longitude - center.longitude
        val dy = point.latitude - center.latitude

        val rotatedDx = dx * cosA - dy * sinA
        val rotatedDy = dx * sinA + dy * cosA

        return LngLat(
            longitude = center.longitude + rotatedDx,
            latitude = center.latitude + rotatedDy,
        )
    }

    private fun updateFeatureGeometry(
        feature: FeatureData,
        newGeometry: com.geoman.maplibre.geoman.types.geojson.Geometry,
    ) {
        val updatedFeature = feature.copy(
            feature = feature.feature.copy(geometry = newGeometry),
        )

        geomanInstance.features.updateFeature(feature.sourceName, feature.id) {
            updatedFeature
        }
    }
}
