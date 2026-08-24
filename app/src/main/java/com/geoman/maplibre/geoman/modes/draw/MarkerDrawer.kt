package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.DrawModeName

/**
 * Marker drawing mode
 */
class MarkerDrawer(geoman: Geoman) : BaseMarkerDrawer(geoman) {

    override val modeName: String = DrawModeName.MARKER.name

    override val sourceName: String = GeomanCoreConstants.SOURCE_MARKERS

    override val markerType: String = "default"

    override val idPrefix: String = "marker"
}
