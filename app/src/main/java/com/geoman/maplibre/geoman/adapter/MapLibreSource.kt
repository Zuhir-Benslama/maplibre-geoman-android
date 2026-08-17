package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import com.geoman.maplibre.geoman.types.geojson.Feature as GeoJsonFeature

/**
 * MapLibre source implementation
 * Actually creates and updates GeoJsonSource on the MapLibre map.
 */
class MapLibreSource(
    private val geoman: Geoman,
    override val sourceId: String,
    private var geoJson: FeatureCollection? = null,
    private val map: MapLibreMap,
) : MapSource {

    private var maplibreSource: GeoJsonSource? = null

    override fun setData(geoJson: FeatureCollection) {
        this.geoJson = geoJson
        val geoJsonString = featureCollectionToJson(geoJson)

        if (maplibreSource != null) {
            // Update existing source
            try {
                maplibreSource?.setGeoJson(geoJsonString)
            } catch (e: Exception) {
                GeomanLogger.w("MapLibreSource", "Error updating source: ${e.message}")
            }
        } else {
            // Create new source and add to map
            try {
                maplibreSource = GeoJsonSource(sourceId, geoJsonString)
                map.style?.addSource(maplibreSource!!)
                GeomanLogger.d("MapLibreSource", "Created source: $sourceId with ${geoJson.features.size} features")
            } catch (e: Exception) {
                // Source may already exist, try to update
                GeomanLogger.w("MapLibreSource", "Error creating source: ${e.message}, trying update")
                try {
                    map.style?.removeSource(sourceId)
                    maplibreSource = GeoJsonSource(sourceId, geoJsonString)
                    map.style?.addSource(maplibreSource!!)
                } catch (e2: Exception) {
                    GeomanLogger.e("MapLibreSource", "Failed to create/update source: ${e2.message}")
                }
            }
        }
    }

    override fun getData(): FeatureCollection? = geoJson

    override fun remove() {
        try {
            map.style?.removeSource(sourceId)
        } catch (e: Exception) {
            // Source may not exist
        }
        maplibreSource = null
    }

    /**
     * Convert FeatureCollection to JSON string using org.json
     */
    private fun featureCollectionToJson(fc: FeatureCollection): String {
        val obj = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray(fc.features.map { featureToJson(it) }))
        }
        return obj.toString()
    }

    /**
     * Convert Feature to JSON string using org.json
     */
    private fun featureToJson(feature: GeoJsonFeature): JSONObject = JSONObject().apply {
        put("type", "Feature")
        feature.id?.let { put("id", it) }
        put("geometry", geometryToJson(feature.geometry))
        val props = JSONObject()
        feature.properties.forEach { (k, v) ->
            if (v != null) props.put(k, v.toString()) else props.put(k, JSONObject.NULL)
        }
        put("properties", props)
    }

    /**
     * Convert Geometry to JSON using org.json
     */
    private fun geometryToJson(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry): JSONObject =
        when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.Point -> {
                JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray(geometry.coordinates))
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", JSONArray(geometry.coordinates.map { JSONArray(it) }))
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                JSONObject().apply {
                    put("type", "Polygon")
                    val rings = JSONArray()
                    geometry.coordinates.forEach { ring ->
                        rings.put(JSONArray(ring.map { JSONArray(it) }))
                    }
                    put("coordinates", rings)
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.MultiPolygon -> {
                JSONObject().apply {
                    put("type", "MultiPolygon")
                    val polygons = JSONArray()
                    geometry.coordinates.forEach { polygon ->
                        val rings = JSONArray()
                        polygon.forEach { ring ->
                            rings.put(JSONArray(ring.map { JSONArray(it) }))
                        }
                        polygons.put(rings)
                    }
                    put("coordinates", polygons)
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.MultiPoint -> {
                JSONObject().apply {
                    put("type", "MultiPoint")
                    put("coordinates", JSONArray(geometry.coordinates.map { JSONArray(it) }))
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.MultiLineString -> {
                JSONObject().apply {
                    put("type", "MultiLineString")
                    val lines = JSONArray()
                    geometry.coordinates.forEach { line ->
                        lines.put(JSONArray(line.map { JSONArray(it) }))
                    }
                    put("coordinates", lines)
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.GeometryCollection -> {
                JSONObject().apply {
                    put("type", "GeometryCollection")
                    put("geometries", JSONArray(geometry.geometries.map { geometryToJson(it) }))
                }
            }
        }

    /**
     * Get features at a screen point by hit-testing the in-memory features of this
     * source against the projected geometry. Points within [HIT_TOLERANCE_PX] pixels
     * of the click are returned, so lines/polygons can be selected by clicking their
     * outline as well as their interior.
     */
    fun getFeaturesAtPoint(point: ScreenPoint): List<GeoJsonFeature>? = geoman.features.getFeatures(sourceId).values
        .map { it.feature }
        .filter { featureIntersectsPoint(it.geometry, point) }

    private fun featureIntersectsPoint(
        geometry: com.geoman.maplibre.geoman.types.geojson.Geometry,
        point: ScreenPoint,
    ): Boolean = when (geometry) {
        is com.geoman.maplibre.geoman.types.geojson.Point -> {
            distancePx(toScreen(geometry.toLngLat()), point) <= HIT_TOLERANCE_PX
        }

        is com.geoman.maplibre.geoman.types.geojson.MultiPoint -> {
            geometry.coordinates.any { coord ->
                distancePx(toScreen(LngLat(coord[0], coord[1])), point) <= HIT_TOLERANCE_PX
            }
        }

        is com.geoman.maplibre.geoman.types.geojson.LineString -> {
            polylineWithinTolerance(geometry.toLngLats(), point)
        }

        is com.geoman.maplibre.geoman.types.geojson.MultiLineString -> {
            geometry.coordinates.any { line ->
                polylineWithinTolerance(line.map { LngLat(it[0], it[1]) }, point)
            }
        }

        is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
            polygonWithinTolerance(geometry.coordinates, point)
        }

        is com.geoman.maplibre.geoman.types.geojson.MultiPolygon -> {
            geometry.coordinates.any { polygon -> polygonWithinTolerance(polygon, point) }
        }

        else -> false
    }

    private fun polygonWithinTolerance(rings: List<List<List<Double>>>, point: ScreenPoint): Boolean {
        if (rings.isEmpty()) return false

        val exterior = rings[0].map { toScreen(LngLat(it[0], it[1])) }
        if (isPointInScreenPolygon(point, exterior)) return true

        // Clicking the outline should also select the polygon
        return rings.any { ring ->
            polylineWithinTolerance(ring.map { LngLat(it[0], it[1]) }, point)
        }
    }

    private fun polylineWithinTolerance(coordinates: List<LngLat>, point: ScreenPoint): Boolean {
        if (coordinates.size < 2) return false
        val screenCoords = coordinates.map { toScreen(it) }
        for (i in 0 until screenCoords.size - 1) {
            if (pointToSegmentDistancePx(point, screenCoords[i], screenCoords[i + 1]) <= HIT_TOLERANCE_PX) {
                return true
            }
        }
        return false
    }

    private fun isPointInScreenPolygon(point: ScreenPoint, polygon: List<ScreenPoint>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val yi = polygon[i].y
            val yj = polygon[j].y
            val xi = polygon[i].x
            val xj = polygon[j].x
            if ((yi > point.y) != (yj > point.y) &&
                point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun toScreen(lngLat: LngLat): ScreenPoint {
        val latLng = org.maplibre.android.geometry.LatLng(lngLat.latitude, lngLat.longitude)
        val point = map.projection.toScreenLocation(latLng)
        return ScreenPoint(point.x, point.y)
    }

    private fun distancePx(a: ScreenPoint, b: ScreenPoint): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }

    private fun pointToSegmentDistancePx(point: ScreenPoint, start: ScreenPoint, end: ScreenPoint): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y

        if (dx == 0f && dy == 0f) {
            return distancePx(point, start)
        }

        val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0f, 1f)
        return distancePx(point, ScreenPoint(start.x + clamped * dx, start.y + clamped * dy))
    }

    companion object {
        private const val HIT_TOLERANCE_PX = 20.0
    }
}
