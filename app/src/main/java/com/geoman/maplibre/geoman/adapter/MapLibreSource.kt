package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
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
    private val map: MapLibreMap
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
                android.util.Log.w("MapLibreSource", "Error updating source: ${e.message}")
            }
        } else {
            // Create new source and add to map
            try {
                maplibreSource = GeoJsonSource(sourceId, geoJsonString)
                map.style?.addSource(maplibreSource!!)
                android.util.Log.d("MapLibreSource", "Created source: $sourceId with ${geoJson.features.size} features")
            } catch (e: Exception) {
                // Source may already exist, try to update
                android.util.Log.w("MapLibreSource", "Error creating source: ${e.message}, trying update")
                try {
                    map.style?.removeSource(sourceId)
                    maplibreSource = GeoJsonSource(sourceId, geoJsonString)
                    map.style?.addSource(maplibreSource!!)
                } catch (e2: Exception) {
                    android.util.Log.e("MapLibreSource", "Failed to create/update source: ${e2.message}")
                }
            }
        }
    }

    override fun getData(): FeatureCollection? {
        return geoJson
    }

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
    private fun featureToJson(feature: GeoJsonFeature): JSONObject {
        return JSONObject().apply {
            put("type", "Feature")
            feature.id?.let { put("id", it) }
            put("geometry", geometryToJson(feature.geometry))
            val props = JSONObject()
            feature.properties.forEach { (k, v) ->
                if (v != null) props.put(k, v.toString()) else props.put(k, JSONObject.NULL)
            }
            put("properties", props)
        }
    }

    /**
     * Convert Geometry to JSON using org.json
     */
    private fun geometryToJson(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry): JSONObject {
        return when (geometry) {
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
    }

    /**
     * Get features at a screen point
     * Note: ChangeEditor now uses in-memory feature lookup instead.
     */
    fun getFeaturesAtPoint(@Suppress("UNUSED_PARAMETER") point: ScreenPoint): List<GeoJsonFeature>? {
        return emptyList()
    }
}
