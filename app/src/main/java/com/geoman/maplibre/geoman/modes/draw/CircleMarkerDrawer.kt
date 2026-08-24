package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName

/**
 * Circle marker drawing mode: places a point rendered as a circle via the
 * map's circle layer (as opposed to [MarkerDrawer], which renders an icon).
 */
class CircleMarkerDrawer(geoman: Geoman) : BaseMarkerDrawer(geoman) {

    override val modeName: String = DrawModeName.CIRCLE_MARKER.name

    override val sourceName: String = GeomanCoreConstants.SOURCE_CIRCLE_MARKERS

    override val markerType: String = "circle"

    override val idPrefix: String = "circle_marker"
}
