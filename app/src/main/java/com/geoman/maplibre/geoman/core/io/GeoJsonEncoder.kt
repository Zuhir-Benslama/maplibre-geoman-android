package com.geoman.maplibre.geoman.core.io

import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_ID_PROPERTY
import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_SHAPE_PROPERTY
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.GeometryCollection
import com.geoman.maplibre.geoman.types.geojson.MultiPoint
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Encodes features and geometries as kotlinx JSON elements.
 *
 * Hand-rolled element conversion is used because feature properties hold
 * arbitrary `Any?` values that polymorphic serialization cannot handle.
 */
internal object GeoJsonEncoder {

    val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

    fun featureCollection(features: List<FeatureData>): JsonObject = JsonObject(
        mapOf(
            "type" to JsonPrimitive("FeatureCollection"),
            "features" to JsonArray(features.map { feature(it) }),
        ),
    )

    fun feature(feature: FeatureData): JsonObject = JsonObject(
        buildMap {
            put("type", JsonPrimitive("Feature"))
            feature.feature.id?.let { put("id", JsonPrimitive(it)) }
            put("geometry", geometry(feature.feature.geometry))
            put("properties", propertiesWithSystemEntries(feature))
        },
    )

    fun geometry(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry): JsonObject = when (geometry) {
        is Point ->
            // GeoJSON spec: Point coordinates are a single [lng, lat] position
            geometryObject("Point", position(geometry.coordinates))

        is MultiPoint -> geometryObject("MultiPoint", positions(geometry.coordinates))

        is com.geoman.maplibre.geoman.types.geojson.LineString ->
            geometryObject("LineString", positions(geometry.coordinates))

        is com.geoman.maplibre.geoman.types.geojson.MultiLineString ->
            geometryObject("MultiLineString", lines(geometry.coordinates))

        is Polygon -> geometryObject("Polygon", lines(geometry.coordinates))

        is MultiPolygon -> geometryObject("MultiPolygon", JsonArray(geometry.coordinates.map { lines(it) }))

        is GeometryCollection -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("GeometryCollection"),
                "geometries" to JsonArray(geometry.geometries.map { geometry(it) }),
            ),
        )
    }

    fun jsonValue(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { jsonValue(it) })
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to jsonValue(item) })
        else -> JsonPrimitive(value.toString())
    }

    private fun geometryObject(type: String, coordinates: JsonElement) = JsonObject(
        mapOf(
            "type" to JsonPrimitive(type),
            "coordinates" to coordinates,
        ),
    )

    private fun position(position: List<Double>) = JsonArray(position.map { JsonPrimitive(it) })

    private fun positions(positions: List<List<Double>>) = JsonArray(positions.map { position(it) })

    private fun lines(lines: List<List<List<Double>>>) = JsonArray(lines.map { positions(it) })

    /**
     * User properties merged with Geoman system entries (`__gm_id`,
     * `__gm_shape`) so exports round-trip shape tracking like the web version.
     */
    private fun propertiesWithSystemEntries(feature: FeatureData): JsonObject {
        val entries = LinkedHashMap<String, JsonElement>()
        entries[FEATURE_ID_PROPERTY] = jsonValue(feature.id)
        feature.shape?.let { entries[FEATURE_SHAPE_PROPERTY] = jsonValue(it.tag) }
        feature.feature.properties.forEach { (key, value) -> entries[key] = jsonValue(value) }
        return JsonObject(entries)
    }
}
