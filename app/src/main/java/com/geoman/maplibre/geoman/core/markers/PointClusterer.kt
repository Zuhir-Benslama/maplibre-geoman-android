package com.geoman.maplibre.geoman.core.markers

import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * A cluster of point features produced by [PointClusterer].
 *
 * [position] is the mean of member positions; [featureIds] preserves the
 * input order of members.
 */
data class PointCluster(val position: LngLat, val featureIds: List<String>, val count: Int = featureIds.size)

/**
 * Grid-based clustering for dense point features (circle markers, markers).
 *
 * Points are bucketed into square grid cells of [cellSizeDegrees]; points
 * falling into the same cell merge into a single [PointCluster] whose
 * position is the member mean. Purely geometric — the caller decides how to
 * render clusters (circle layer, symbol count, ...).
 *
 * Longitude wrap-around is not merged across the antimeridian edge; clusters
 * near ±180 stay split, matching typical tile-grid behavior.
 */
class PointClusterer(private val cellSizeDegrees: Double = DEFAULT_CELL_SIZE_DEGREES) {

    init {
        require(cellSizeDegrees > 0.0) { "cellSizeDegrees must be positive" }
    }

    /**
     * Cluster [points] (id to position). Single-member clusters are returned
     * as-is so callers can distinguish lone points via [PointCluster.count].
     */
    fun cluster(points: List<Pair<String, LngLat>>): List<PointCluster> {
        if (points.isEmpty()) return emptyList()

        val cells = LinkedHashMap<Long, MutableList<Pair<String, LngLat>>>()
        points.forEach { point ->
            cells.getOrPut(cellKey(point.second)) { mutableListOf() }.add(point)
        }

        return cells.values.map { members ->
            PointCluster(
                position = LngLat(
                    longitude = members.sumOf { it.second.longitude } / members.size,
                    latitude = members.sumOf { it.second.latitude } / members.size,
                ),
                featureIds = members.map { it.first },
            )
        }
    }

    private fun cellKey(position: LngLat): Long {
        val cellX = Math.floor(position.longitude / cellSizeDegrees).toLong()
        val cellY = Math.floor(position.latitude / cellSizeDegrees).toLong()
        // Pair two potentially negative grid coordinates into one stable key
        return cellX * HASH_PRIME + cellY
    }

    companion object {
        const val DEFAULT_CELL_SIZE_DEGREES = 0.5

        private const val HASH_PRIME = 1_000_003L
    }
}
