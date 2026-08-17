package com.geoman.maplibre.geoman.types.geojson

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GeoJSON Feature Collection
 */
@Serializable
data class FeatureCollection(val type: String = "FeatureCollection", val features: List<Feature>)

/**
 * GeoJSON Feature
 */
@Serializable
data class Feature(
    val type: String = "Feature",
    val id: String? = null,
    val geometry: Geometry,
    val properties: Map<String, @Contextual Any?> = emptyMap(),
)

/**
 * GeoJSON Geometry types
 */
@Serializable
sealed class Geometry {
    abstract val type: String
    abstract val coordinates: Any
}

/**
 * Point geometry
 */
@Serializable
@SerialName("Point")
data class Point(
    override val type: String = "Point",
    /** [longitude, latitude] */
    override val coordinates: List<Double>,
) : Geometry() {
    fun toLngLat(): LngLat {
        require(coordinates.size >= 2) { "Point coordinates must have at least 2 elements" }
        return LngLat(coordinates[0], coordinates[1])
    }

    companion object {
        fun fromLngLat(lngLat: LngLat): Point = Point(coordinates = listOf(lngLat.longitude, lngLat.latitude))
    }
}

/**
 * MultiPoint geometry
 */
@Serializable
@SerialName("MultiPoint")
data class MultiPoint(override val type: String = "MultiPoint", override val coordinates: List<List<Double>>) :
    Geometry()

/**
 * LineString geometry
 */
@Serializable
@SerialName("LineString")
data class LineString(override val type: String = "LineString", override val coordinates: List<List<Double>>) :
    Geometry() {
    fun toLngLats(): List<LngLat> = coordinates.map { LngLat(it[0], it[1]) }

    companion object {
        fun fromLngLats(points: List<LngLat>): LineString =
            LineString(coordinates = points.map { listOf(it.longitude, it.latitude) })
    }
}

/**
 * MultiLineString geometry
 */
@Serializable
@SerialName("MultiLineString")
data class MultiLineString(
    override val type: String = "MultiLineString",
    override val coordinates: List<List<List<Double>>>,
) : Geometry()

/**
 * Polygon geometry
 */
@Serializable
@SerialName("Polygon")
data class Polygon(override val type: String = "Polygon", override val coordinates: List<List<List<Double>>>) :
    Geometry() {
    /**
     * Get the exterior ring (first coordinate array)
     */
    fun getExteriorRing(): List<LngLat> {
        if (coordinates.isEmpty()) return emptyList()
        return coordinates[0].map { LngLat(it[0], it[1]) }
    }

    /**
     * Get interior rings (holes)
     */
    fun getInteriorRings(): List<List<LngLat>> {
        if (coordinates.size <= 1) return emptyList()
        return coordinates.drop(1).map { ring -> ring.map { LngLat(it[0], it[1]) } }
    }

    companion object {
        fun fromLngLats(rings: List<List<LngLat>>): Polygon {
            require(rings.isNotEmpty()) { "Polygon must have at least one ring" }
            return Polygon(
                coordinates = rings.map { ring ->
                    ring.map { listOf(it.longitude, it.latitude) }
                },
            )
        }
    }
}

/**
 * MultiPolygon geometry
 */
@Serializable
@SerialName("MultiPolygon")
data class MultiPolygon(
    override val type: String = "MultiPolygon",
    override val coordinates: List<List<List<List<Double>>>>,
) : Geometry()

/**
 * Geometry Collection
 */
@Serializable
@SerialName("GeometryCollection")
data class GeometryCollection(override val type: String = "GeometryCollection", val geometries: List<Geometry>) :
    Geometry() {
    override val coordinates: Any get() = geometries
}

/**
 * Longitude/Latitude coordinate
 */
@Serializable
data class LngLat(val longitude: Double, val latitude: Double) {
    fun toArray(): List<Double> = listOf(longitude, latitude)

    /**
     * Distance to another coordinate in meters (Haversine formula).
     * Delegates to [com.geoman.maplibre.geoman.utils.GeometryUtils.distance] so the
     * formula lives in exactly one place.
     */
    fun distanceTo(other: LngLat): Double = com.geoman.maplibre.geoman.utils.GeometryUtils.distance(this, other)
}

/**
 * Screen point (x, y in pixels)
 */
@Serializable
data class ScreenPoint(val x: Float, val y: Float) {
    fun toList(): List<Float> = listOf(x, y)
}

/**
 * LatLngBounds
 */
@Serializable
data class LatLngBounds(val northeast: LngLat, val southwest: LngLat) {
    fun contains(lngLat: LngLat): Boolean = lngLat.longitude in southwest.longitude..northeast.longitude &&
        lngLat.latitude in southwest.latitude..northeast.latitude

    companion object {
        fun from(lngLats: List<LngLat>): LatLngBounds {
            require(lngLats.isNotEmpty()) { "LatLngBounds requires at least one coordinate" }
            val minLon = lngLats.minOf { it.longitude }
            val maxLon = lngLats.maxOf { it.longitude }
            val minLat = lngLats.minOf { it.latitude }
            val maxLat = lngLats.maxOf { it.latitude }
            return LatLngBounds(
                northeast = LngLat(maxLon, maxLat),
                southwest = LngLat(minLon, minLat),
            )
        }
    }
}
