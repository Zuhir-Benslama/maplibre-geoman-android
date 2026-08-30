package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat

/**
 * Line drawing mode
 */
class LineDrawer(geoman: Geoman) : BasePathDrawer(geoman) {

    override val modeName: String = DrawModeName.LINE.name

    override val sourceName: String = GeomanCoreConstants.SOURCE_LINES

    override val shapeType: String = "line"

    override val idPrefix: String = "line"

    override val minFinishPoints: Int = 2

    // The validator requires at least 2 positions per LineString; creating the
    // in-progress feature on the first click previously produced an invalid
    // single-point line that was rejected by addGeoJsonFeature.
    override val minRenderPoints: Int = 2

    override fun buildGeometry(coordinates: List<LngLat>): Geometry = LineString.fromLngLats(coordinates)
}
