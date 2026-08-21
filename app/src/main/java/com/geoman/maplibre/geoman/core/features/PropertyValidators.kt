package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.GeometryCollection
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.MultiLineString
import com.geoman.maplibre.geoman.types.geojson.MultiPoint
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlin.math.abs

/**
 * Result of validating a GeoJSON feature.
 */
data class ValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Structural validation for incoming GeoJSON features.
 *
 * Mirrors the web version's `validators.ts`: catches corrupted data (non-finite
 * coordinates, latitudes outside the valid range, unclosed polygon rings,
 * malformed IDs) before it enters the feature store.
 *
 * Longitudes are tolerated slightly beyond ±180 so features that legitimately
 * cross the antimeridian (e.g. generated with unwrapped longitudes) are not
 * rejected; anything beyond [MAX_LONGITUDE] is treated as garbage.
 */
object PropertyValidators {

    private const val MAX_ID_LENGTH = 128
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 540.0

    /**
     * Validate a complete feature: ID and geometry.
     */
    fun validateFeature(feature: Feature): ValidationResult =
        ValidationResult(validateFeatureId(feature.id) + validateGeometry(feature.geometry))

    /**
     * Feature IDs must be non-blank and reasonably sized.
     */
    fun validateFeatureId(id: String?): List<String> {
        val errors = mutableListOf<String>()
        when {
            id == null -> errors.add("id: must not be null")

            id.isBlank() -> errors.add("id: must not be blank")

            id.length > MAX_ID_LENGTH ->
                errors.add("id: exceeds maximum length of $MAX_ID_LENGTH characters")
        }
        return errors
    }

    /**
     * Validate a single [longitude, latitude] pair.
     *
     * Non-finite values and out-of-range latitudes are errors. Longitudes are
     * only rejected far beyond ±180 (see class doc).
     */
    fun validateCoordinate(longitude: Double, latitude: Double): List<String> {
        val errors = mutableListOf<String>()
        if (!longitude.isFinite()) {
            errors.add("coordinate: longitude must be finite, was $longitude")
        } else if (abs(longitude) > MAX_LONGITUDE) {
            errors.add("coordinate: longitude $longitude is too far out of range")
        }
        if (!latitude.isFinite()) {
            errors.add("coordinate: latitude must be finite, was $latitude")
        } else if (abs(latitude) > MAX_LATITUDE) {
            errors.add("coordinate: latitude $latitude is out of range [-90, 90]")
        }
        return errors
    }

    /**
     * Validate every coordinate in a geometry plus shape-specific structural
     * rules (minimum positions, closed rings for polygons).
     */
    fun validateGeometry(geometry: Geometry): List<String> = when (geometry) {
        is Point -> validatePosition(geometry.coordinates)

        is MultiPoint -> validatePositions(geometry.coordinates)

        is LineString -> buildList {
            if (geometry.coordinates.size < 2) {
                add("linestring: requires at least 2 positions, has ${geometry.coordinates.size}")
            }
            addAll(validatePositions(geometry.coordinates))
        }

        is MultiLineString -> geometry.coordinates.flatMapIndexed { index, line ->
            validateLine(line, "multilinestring line $index")
        }

        is Polygon -> geometry.coordinates.flatMapIndexed { ringIndex, ring ->
            validateRing(ring, "polygon ring $ringIndex")
        }

        is MultiPolygon -> geometry.coordinates.flatMapIndexed { polygonIndex, rings ->
            rings.flatMapIndexed { ringIndex, ring ->
                validateRing(ring, "multipolygon $polygonIndex ring $ringIndex")
            }
        }

        is GeometryCollection -> geometry.geometries.flatMap { validateGeometry(it) }
    }

    private fun validateLine(coordinates: List<List<Double>>, label: String): List<String> = buildList {
        if (coordinates.size < 2) {
            add("$label: requires at least 2 positions, has ${coordinates.size}")
        }
        addAll(validatePositions(coordinates))
    }

    private fun validateRing(ring: List<List<Double>>, label: String): List<String> = buildList {
        if (ring.size < 4) {
            add("$label: requires at least 4 positions, has ${ring.size}")
        } else if (ring.first() != ring.last()) {
            add("$label: is not closed (first != last)")
        }
        addAll(validatePositions(ring))
    }

    private fun validatePosition(position: List<Double>): List<String> = if (position.size < 2) {
        listOf("coordinate: position requires [longitude, latitude], got ${position.size} values")
    } else {
        validateCoordinate(position[0], position[1])
    }

    private fun validatePositions(coordinates: List<List<Double>>): List<String> =
        coordinates.flatMapIndexed { index, position ->
            validatePosition(position).map { "position $index: $it" }
        }
}
