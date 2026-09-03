package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon

/**
 * Polygon drawing mode
 */
class PolygonDrawer(geoman: Geoman) : BasePathDrawer(geoman) {

    override val modeName: String = DrawModeName.POLYGON.name

    override val sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS

    override val shapeType: String = "polygon"

    override val idPrefix: String = "polygon"

    override val minFinishPoints: Int = 3

    override val minRenderPoints: Int = 3

    override fun buildGeometry(coordinates: List<LngLat>): Geometry {
        val closed = buildList(coordinates.size + 1) {
            addAll(coordinates)
            coordinates.firstOrNull()?.let(::add)
        }
        return Polygon.fromLngLats(listOf(closed))
    }
}
