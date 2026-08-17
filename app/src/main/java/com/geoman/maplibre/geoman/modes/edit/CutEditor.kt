package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.utils.GeometryUtils
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Cut editing mode - splits a line feature into two features at the click point.
 * Non-line geometries are ignored (they fire no events).
 */
class CutEditor(geoman: Geoman) : BaseEdit(geoman) {

    override val modeName: String = EditModeName.CUT.name

    override fun onMapClick(point: LatLng) {
        if (!enabled) return

        val feature = findLineAt(point) ?: return
        val geometry = feature.geometry as? LineString ?: return
        val coords = geometry.toLngLats()
        if (coords.size < 2) return

        val clickPoint = LngLat(point.longitude, point.latitude)
        val segmentIndex = findSplitSegment(coords, clickPoint)
        val cutPoint = GeometryUtils.nearestPointOnPolyline(
            clickPoint,
            listOf(coords[segmentIndex], coords[segmentIndex + 1]),
        )
        splitFeature(feature, coords, segmentIndex, cutPoint)
    }

    private fun findLineAt(point: LatLng): FeatureData? {
        val clickPoint = LngLat(point.longitude, point.latitude)
        val features = geomanInstance.mapAdapter.queryFeaturesByScreenCoordinates(
            geomanInstance.mapAdapter.project(clickPoint),
            listOf(GeomanCoreConstants.SOURCE_LINES),
        )
        return features.firstOrNull()
    }

    private fun findSplitSegment(coords: List<LngLat>, clickPoint: LngLat): Int {
        var bestIndex = 0
        var bestDist = Double.MAX_VALUE

        for (i in 0 until coords.size - 1) {
            val nearest = GeometryUtils.nearestPointOnPolyline(
                clickPoint,
                listOf(coords[i], coords[i + 1]),
            )
            val dist = GeometryUtils.distance(clickPoint, nearest)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun splitFeature(feature: FeatureData, coords: List<LngLat>, segmentIndex: Int, cutPoint: LngLat) {
        geomanInstance.scope.launch { fireCutStartEvent(feature) }

        val sourceName = feature.sourceName
        val now = System.currentTimeMillis()
        val part1Id = "cut_${now}_1"
        val part2Id = "cut_${now}_2"

        val firstPart = coords.take(segmentIndex + 1) + cutPoint
        val secondPart = listOf(cutPoint) + coords.drop(segmentIndex + 1)

        geomanInstance.features.removeFeature(sourceName, feature.id)
        addSplitPart(sourceName, part1Id, firstPart, feature.properties)
        addSplitPart(sourceName, part2Id, secondPart, feature.properties)

        geomanInstance.scope.launch { fireCutEndEvent(feature) }
    }

    private fun addSplitPart(
        sourceName: String,
        partId: String,
        coordinates: List<LngLat>,
        baseProperties: Map<String, Any?>,
    ) {
        geomanInstance.features.addGeoJsonFeature(
            Feature(
                id = partId,
                geometry = LineString.fromLngLats(coordinates),
                properties = baseProperties + (GeomanCoreConstants.FEATURE_ID_PROPERTY to partId),
            ),
            sourceName,
        )
    }
}
