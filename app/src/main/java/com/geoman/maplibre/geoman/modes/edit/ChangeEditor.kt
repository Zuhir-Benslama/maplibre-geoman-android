package com.geoman.maplibre.geoman.modes.edit

import androidx.annotation.MainThread
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureSources
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Change editing mode - allows editing vertices of polygons and lines
 */
open class ChangeEditor(geoman: GeomanApi) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.CHANGE.name

    private var isEditing = false
    private var editingFeature: FeatureData? = null
    private var vertexMarkers = mutableListOf<VertexMarker>()
    private var midpointMarkers = mutableListOf<MidpointMarker>()

    /**
     * Wrapper holding a DOM marker and its vertex index
     */
    private data class VertexMarker(val index: Int, val domMarker: com.geoman.maplibre.geoman.adapter.DomMarker)

    /**
     * Wrapper holding a clickable midpoint marker and the segment it splits
     */
    private data class MidpointMarker(
        val segmentIndex: Int,
        val domMarker: com.geoman.maplibre.geoman.adapter.DomMarker,
    )

    @MainThread
    override fun disable() {
        if (isEditing) {
            finishEditing()
        }
        clearVertexMarkers()
        super.disable()
    }

    /**
     * Start editing a specific feature directly (bypassing click selection)
     */
    fun startEditingFeature(feature: FeatureData) {
        startEditing(feature)
    }

    @MainThread
    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        if (isEditing) {
            // While editing, clicks on vertex markers are handled by drag callbacks
            return
        }

        // Select a feature via the shared screen-space hit-testing seam used by
        // the other editors, so selection tolerance follows the map zoom level
        val targetSources = FeatureSources.EDITABLE_WITHOUT_MARKERS

        val clickPoint = LngLat(point.longitude, point.latitude)
        queryFeaturesAt(clickPoint, targetSources).firstOrNull()?.let { startEditing(it) }
    }

    private fun startEditing(feature: FeatureData) {
        editingFeature = feature
        isEditing = true

        createVertexMarkers(feature)

        geoman.scope.launch {
            fireEditEvent({ GmEditEvent.ChangeStart(it) }, feature)
        }
    }

    private fun finishEditing() {
        editingFeature?.let { stale ->
            val current = refreshFeature(stale) ?: stale
            geoman.scope.launch {
                fireEditEvent({ GmEditEvent.ChangeEnd(it) }, current)
            }
        }

        isEditing = false
        editingFeature = null
        clearVertexMarkers()
    }

    private fun createVertexMarkers(feature: FeatureData) {
        clearVertexMarkers()

        val geometry = feature.geometry
        val vertices = when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                geometry.coordinates.mapIndexed { index, coord ->
                    VertexMarkerData(index, LngLat(coord[0], coord[1]))
                }
            }

            is Polygon -> {
                if (geometry.coordinates.isNotEmpty()) {
                    val ring = geometry.coordinates[0]
                    // Exclude the closing coordinate (duplicate of first)
                    ring.dropLast(1).mapIndexed { index, coord ->
                        VertexMarkerData(index, LngLat(coord[0], coord[1]))
                    }
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

        vertices.forEach { vertex ->
            val domMarker = createDraggableMarker(vertex.lngLat) { newLngLat ->
                moveVertex(vertex.index, LatLng(newLngLat.latitude, newLngLat.longitude))
            }
            vertexMarkers.add(VertexMarker(vertex.index, domMarker))
        }

        createMidpointMarkers(geometry)

        GeomanLogger.d("ChangeEditor", "Created ${vertexMarkers.size} vertex markers")
    }

    /**
     * Shape markers: clickable midpoints on every segment. Tapping one inserts
     * a new vertex at the midpoint (web Geoman's shape_markers behavior).
     * Toggled via `helperOptions.shapeMarkersEnabled`.
     */
    private fun createMidpointMarkers(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry) {
        if (!geoman.options.helper.shapeMarkersEnabled) return

        val midpoints: List<MidpointData> = when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                val coords = geometry.coordinates
                (0 until coords.size - 1).mapNotNull { i ->
                    val mid = EditorGeometry.midpoint(coords[i], coords[i + 1])
                    MidpointData(i, LngLat(mid[0], mid[1]))
                }
            }

            is Polygon -> {
                val ring = geometry.coordinates.firstOrNull() ?: return
                val unique = ring.dropLast(1)
                if (unique.size < 2) return

                // Segments between consecutive vertices plus the wrap-around
                // closing segment; segmentIndex matches addVertex() indexing
                (unique.indices).map { i ->
                    val a = unique[i]
                    val b = unique[(i + 1) % unique.size]
                    val mid = EditorGeometry.midpoint(a, b)
                    MidpointData(i, LngLat(mid[0], mid[1]))
                }
            }

            else -> emptyList()
        }

        midpoints.forEach { data ->
            val domMarker = createClickableMarker(data.lngLat) {
                addVertex(data.segmentIndex, LatLng(data.lngLat.latitude, data.lngLat.longitude))
            }
            midpointMarkers.add(MidpointMarker(data.segmentIndex, domMarker))
        }
    }

    private fun clearVertexMarkers() {
        vertexMarkers.forEach { it.domMarker.remove() }
        vertexMarkers.clear()
        midpointMarkers.forEach { it.domMarker.remove() }
        midpointMarkers.clear()
    }

    /**
     * Move a vertex to a new position
     */
    private fun moveVertex(index: Int, newPoint: LatLng) {
        val feature = editingFeature ?: return
        val coord = listOf(newPoint.longitude, newPoint.latitude)

        editingFeature = updateFeatureGeometry(feature) { geometry ->
            when (geometry) {
                is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                    val coords = geometry.coordinates.toMutableList()
                    if (index in coords.indices) {
                        coords[index] = coord
                    }
                    com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                }

                is Polygon -> Polygon(
                    coordinates = EditorGeometry.movePolygonVertex(geometry.coordinates, index, coord),
                )

                else -> geometry
            }
        }
    }

    /**
     * Add a new vertex to the geometry.
     *
     * @param segmentIndex index of the segment to split (0-based, between vertex i and i+1)
     */
    fun addVertex(segmentIndex: Int, newPoint: LatLng) {
        val feature = editingFeature ?: return
        val coord = listOf(newPoint.longitude, newPoint.latitude)

        val updated = updateFeatureGeometry(feature) { geometry ->
            when (geometry) {
                is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                    val coords = geometry.coordinates.toMutableList()
                    if (segmentIndex in 0 until coords.size - 1) {
                        coords.add(segmentIndex + 1, coord)
                    }
                    com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                }

                is Polygon -> {
                    if (geometry.coordinates.isEmpty()) {
                        geometry
                    } else {
                        val rings = geometry.coordinates.map { ring -> ring.toMutableList() }
                        val exteriorRing = rings[0]
                        if (segmentIndex in 0 until exteriorRing.size - 1) {
                            exteriorRing.add(segmentIndex + 1, coord)
                        }
                        Polygon(coordinates = rings)
                    }
                }

                else -> geometry
            }
        } ?: return

        editingFeature = updated
        createVertexMarkers(updated)
    }

    /**
     * Remove a vertex from the geometry.
     *
     * @param index index of the vertex to remove
     */
    fun removeVertex(index: Int) {
        val feature = editingFeature ?: return

        val updated = updateFeatureGeometry(feature) { geometry ->
            when (geometry) {
                is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                    if (geometry.coordinates.size <= 2) {
                        geometry // Can't remove if only 2 points
                    } else {
                        val coords = geometry.coordinates.toMutableList()
                        if (index in coords.indices) {
                            coords.removeAt(index)
                        }
                        com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                    }
                }

                is Polygon -> removePolygonVertex(geometry, index)

                else -> geometry
            }
        } ?: return

        editingFeature = updated
        createVertexMarkers(updated)
    }

    private fun removePolygonVertex(geometry: Polygon, index: Int): Polygon =
        Polygon(coordinates = EditorGeometry.removePolygonVertex(geometry.coordinates, index))

    private data class VertexMarkerData(val index: Int, val lngLat: LngLat)

    private data class MidpointData(val segmentIndex: Int, val lngLat: LngLat)
}
