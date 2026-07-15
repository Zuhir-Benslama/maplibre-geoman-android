package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Change editing mode - allows editing vertices of polygons and lines
 */
class ChangeEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.CHANGE.name

    private var isEditing = false
    private var editingFeature: FeatureData? = null
    private var vertexMarkers = mutableListOf<VertexMarker>()

    /**
     * Wrapper holding a DOM marker and its vertex index
     */
    private data class VertexMarker(val index: Int, val domMarker: com.geoman.maplibre.geoman.adapter.DomMarker)

    override fun enable() {
        super.enable()
    }

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
        editingFeature = feature
        isEditing = true
        createVertexMarkers(feature)
        geomanInstance.scope.launch {
            fireChangeStartEvent(feature)
        }
    }

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        if (isEditing) {
            // While editing, clicks on vertex markers are handled by drag callbacks
            return
        } else {
            // Select a feature to edit by querying in-memory features
            val targetSources = setOf(
                GeomanCoreConstants.SOURCE_LINES,
                GeomanCoreConstants.SOURCE_POLYGONS,
                GeomanCoreConstants.SOURCE_CIRCLES,
            )

            var closestFeature: FeatureData? = null
            var closestDistance = Double.MAX_VALUE

            for (sourceName in targetSources) {
                val features = geomanInstance.features.getFeatures(sourceName)
                for ((_, feature) in features) {
                    val dist = distanceFromPointToFeature(feature, point)
                    if (dist < closestDistance && dist < 30.0) { // 30m hit tolerance
                        closestDistance = dist
                        closestFeature = feature
                    }
                }
            }

            if (closestFeature != null) {
                startEditing(closestFeature)
            }
        }
    }

    /**
     * Calculate distance from a screen point to a feature's geometry
     */
    private fun distanceFromPointToFeature(feature: FeatureData, point: LatLng): Double {
        val geometry = feature.geometry
        return when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.Point -> {
                val c = geometry.coordinates
                val featurePoint = LatLng(c[1], c[0])
                point.distanceTo(featurePoint)
            }

            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                geometry.coordinates.minOf { coord ->
                    val featurePoint = LatLng(coord[1], coord[0])
                    point.distanceTo(featurePoint)
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                if (geometry.coordinates.isNotEmpty()) {
                    val ring = geometry.coordinates[0]
                    ring.minOf { coord ->
                        val featurePoint = LatLng(coord[1], coord[0])
                        point.distanceTo(featurePoint)
                    }
                } else {
                    Double.MAX_VALUE
                }
            }

            else -> Double.MAX_VALUE
        }
    }

    private fun startEditing(feature: FeatureData) {
        editingFeature = feature
        isEditing = true

        createVertexMarkers(feature)

        geomanInstance.scope.launch {
            fireChangeStartEvent(feature)
        }
    }

    private fun finishEditing() {
        editingFeature?.let {
            geomanInstance.scope.launch {
                fireChangeEndEvent(it)
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

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
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
            val markerOptions = DomMarkerOptions(
                element = createVertexMarkerView(),
                anchor = com.geoman.maplibre.geoman.adapter.MarkerAnchor.CENTER,
                draggable = true,
            )

            val domMarker = geomanInstance.mapAdapter.createDomMarker(markerOptions, vertex.lngLat)
            domMarker.addToMap() // Actually add the marker to the map

            // Set drag callback to update geometry
            domMarker.onDrag = { newLngLat ->
                moveVertex(vertex.index, LatLng(newLngLat.latitude, newLngLat.longitude))
            }

            domMarker.onDragEnd = {
                // Re-sync the source to ensure map reflects final position
                geomanInstance.features.updateFeature(feature.sourceName, feature.id) { it }
            }

            vertexMarkers.add(VertexMarker(vertex.index, domMarker))
        }

        android.util.Log.d("ChangeEditor", "Created ${vertexMarkers.size} vertex markers")
    }

    /**
     * Create a small red circle view for vertex markers
     */
    private fun createVertexMarkerView(): android.view.View {
        val context = geomanInstance.mapView.context
        val size = (14 * context.resources.displayMetrics.density).toInt()
        val view = android.view.View(context)
        view.layoutParams = android.view.ViewGroup.LayoutParams(size, size)

        // Draw a red circle with white border
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.RED)
            setStroke((2 * context.resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
            setSize(size, size)
        }
        view.background = drawable

        return view
    }

    private fun clearVertexMarkers() {
        vertexMarkers.forEach { it.domMarker.remove() }
        vertexMarkers.clear()
    }

    /**
     * Move a vertex to a new position
     */
    private fun moveVertex(index: Int, newPoint: LatLng) {
        val feature = editingFeature ?: return
        val geometry = feature.geometry

        when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                val coords = geometry.coordinates.toMutableList()
                if (index in coords.indices) {
                    coords[index] = listOf(newPoint.longitude, newPoint.latitude)
                    val newGeometry = com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                    updateFeatureGeometry(feature, newGeometry)
                }
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                if (geometry.coordinates.isNotEmpty()) {
                    val rings = geometry.coordinates.map { ring -> ring.toMutableList() }
                    val exteriorRing = rings[0]
                    if (index in exteriorRing.indices) {
                        exteriorRing[index] = listOf(newPoint.longitude, newPoint.latitude)
                        val newGeometry = com.geoman.maplibre.geoman.types.geojson.Polygon(coordinates = rings)
                        updateFeatureGeometry(feature, newGeometry)
                    }
                }
            }

            else -> {
                // Unsupported geometry type for vertex movement
            }
        }
    }

    /**
     * Add a new vertex to the geometry
     */
    fun addVertex(segmentIndex: Int, newPoint: LatLng) {
        val feature = editingFeature ?: return
        val geometry = feature.geometry

        when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                val coords = geometry.coordinates.toMutableList()
                coords.add(segmentIndex + 1, listOf(newPoint.longitude, newPoint.latitude))
                val newGeometry = com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                updateFeatureGeometry(feature, newGeometry)
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                if (geometry.coordinates.isNotEmpty()) {
                    val rings = geometry.coordinates.map { ring -> ring.toMutableList() }
                    val exteriorRing = rings[0]
                    exteriorRing.add(segmentIndex + 1, listOf(newPoint.longitude, newPoint.latitude))
                    val newGeometry = com.geoman.maplibre.geoman.types.geojson.Polygon(coordinates = rings)
                    updateFeatureGeometry(feature, newGeometry)
                }
            }

            else -> {
                // Unsupported geometry type for adding vertices
            }
        }
    }

    /**
     * Remove a vertex from the geometry
     */
    fun removeVertex(index: Int) {
        val feature = editingFeature ?: return
        val geometry = feature.geometry

        when (geometry) {
            is com.geoman.maplibre.geoman.types.geojson.LineString -> {
                if (geometry.coordinates.size <= 2) return // Can't remove if only 2 points
                val coords = geometry.coordinates.toMutableList()
                coords.removeAt(index)
                val newGeometry = com.geoman.maplibre.geoman.types.geojson.LineString(coordinates = coords)
                updateFeatureGeometry(feature, newGeometry)
            }

            is com.geoman.maplibre.geoman.types.geojson.Polygon -> {
                if (geometry.coordinates.isNotEmpty()) {
                    val rings = geometry.coordinates.map { ring -> ring.toMutableList() }
                    val exteriorRing = rings[0]
                    if (exteriorRing.size <= 4) return // Can't remove if only 3 unique points + closing
                    exteriorRing.removeAt(index)
                    val newGeometry = com.geoman.maplibre.geoman.types.geojson.Polygon(coordinates = rings)
                    updateFeatureGeometry(feature, newGeometry)
                }
            }

            else -> {
                // Unsupported geometry type for vertex removal
            }
        }
    }

    private data class VertexMarkerData(val index: Int, val lngLat: LngLat)
}
