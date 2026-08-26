package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureSources
import com.geoman.maplibre.geoman.core.history.SplitChange
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.events.GmEditEvent
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
open class CutEditor(geoman: GeomanApi) : BaseEdit(geoman) {

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
        val features = queryFeaturesAt(clickPoint, listOf(FeatureSources.LINE))
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
        geoman.scope.launch { fireEditEvent({ GmEditEvent.CutStart(it) }, feature) }

        val sourceName = feature.sourceName
        val part1Id = createFeatureId(CUT_ID_PREFIX)
        val part2Id = createFeatureId(CUT_ID_PREFIX)

        val firstPart = coords.take(segmentIndex + 1) + cutPoint
        val secondPart = listOf(cutPoint) + coords.drop(segmentIndex + 1)

        // Add both parts BEFORE removing the original so a validator rejection
        // can never destroy the source line; roll back partial adds on failure
        val addedParts = try {
            listOf(
                addSplitPart(sourceName, part1Id, firstPart, feature.properties),
                addSplitPart(sourceName, part2Id, secondPart, feature.properties),
            )
        } catch (e: IllegalArgumentException) {
            GeomanLogger.e(TAG, "Cut aborted: split parts rejected by validation", e)
            return
        }

        val removed = geoman.features.removeFeature(sourceName, feature.id)
        if (removed == null) {
            // The original vanished mid-cut; drop the just-added parts instead
            // of leaving duplicates behind
            addedParts.forEach { geoman.features.removeFeature(it.sourceName, it.id) }
            return
        }

        geoman.history.record(
            SplitChange(
                sourceName = sourceName,
                original = removed.feature,
                parts = addedParts.map { it.feature },
            ),
        )

        // Fire CutEnd with a surviving result rather than the removed feature
        geoman.scope.launch { fireEditEvent({ GmEditEvent.CutEnd(it) }, addedParts.first()) }
    }

    private fun addSplitPart(
        sourceName: String,
        partId: String,
        coordinates: List<LngLat>,
        baseProperties: Map<String, Any?>,
    ): FeatureData = geoman.features.addGeoJsonFeature(
        Feature(
            id = partId,
            geometry = LineString.fromLngLats(coordinates),
            properties = baseProperties + (GeomanCoreConstants.FEATURE_ID_PROPERTY to partId),
        ),
        sourceName,
    )

    private companion object {
        const val TAG = "CutEditor"
        const val CUT_ID_PREFIX = "cut"
    }
}
