package com.geoman.maplibre.geoman.core.io

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.GeometryCollection
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.MultiLineString
import com.geoman.maplibre.geoman.types.geojson.MultiPoint
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Decodes GeoJSON documents from kotlinx JSON elements.
 *
 * Malformed structures yield `null` / empty results rather than exceptions so
 * batch imports can skip bad entries and report them individually.
 */
internal object GeoJsonDecoder {

    fun featureCollection(root: JsonObject): List<Pair<Int, JsonObject>>? = when {
        root.hasType("FeatureCollection") ->
            root["features"]
                ?.let { it as? JsonArray }
                ?.mapIndexedNotNull { index, element ->
                    (element as? JsonObject)?.let { index to it }
                }

        root.hasType("Feature") -> listOf(0 to root)

        else -> null
    }

    fun feature(element: JsonObject): Feature? {
        val geometryElement = element["geometry"] as? JsonObject ?: return null
        val geometry = geometry(geometryElement) ?: return null
        val id = (element["id"] as? JsonPrimitive)?.content
        val properties = properties(element["properties"])

        return Feature(id = id, geometry = geometry, properties = properties)
    }

    fun geometry(element: JsonObject): Geometry? {
        val type = (element["type"] as? JsonPrimitive)?.content ?: return null
        val coordinates = element["coordinates"] as? JsonArray ?: return null

        return when (type) {
            "Point" ->
                // GeoJSON spec: Point coordinates are a single [lng, lat] position
                decodePosition(coordinates)?.let { Point(coordinates = it) }

            "MultiPoint" -> MultiPoint(coordinates = decodePositions(coordinates))

            "LineString" -> LineString(coordinates = decodePositions(coordinates))

            "MultiLineString" -> MultiLineString(coordinates = decodeLines(coordinates))

            "Polygon" -> Polygon(coordinates = decodeLines(coordinates))

            "MultiPolygon" -> MultiPolygon(
                coordinates = coordinates.mapNotNull { ring -> (ring as? JsonArray)?.let { decodeLines(it) } },
            )

            "GeometryCollection" -> GeometryCollection(
                geometries = (element["geometries"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.mapNotNull { geometry(it) }
                    ?: emptyList(),
            )

            else -> null
        }
    }

    private fun JsonObject.hasType(expected: String): Boolean = (this["type"] as? JsonPrimitive)?.content == expected

    /** A single [longitude, latitude] position with at least two values. */
    private fun decodePosition(array: JsonArray): List<Double>? = array.mapNotNull { coordinate ->
        (coordinate as? JsonPrimitive)?.doubleOrNull
    }.takeIf { it.size >= 2 }

    private fun decodePositions(array: JsonArray): List<List<Double>> = array.mapNotNull { position ->
        (position as? JsonArray)?.let { decodePosition(it) }
    }

    private fun decodeLines(array: JsonArray): List<List<List<Double>>> = array.mapNotNull { line ->
        (line as? JsonArray)?.let { decodePositions(it) }
    }

    private fun properties(element: JsonElement?): Map<String, Any?> = when (element) {
        null, is JsonNull -> emptyMap()
        is JsonObject -> element.mapValues { (_, value) -> jsonValue(value) }
        else -> emptyMap()
    }

    private fun jsonValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null

        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            else -> element.doubleOrNull
        }

        is JsonArray -> element.map { jsonValue(it) }

        is JsonObject -> element.mapValues { (_, value) -> jsonValue(value) }
    }
}
