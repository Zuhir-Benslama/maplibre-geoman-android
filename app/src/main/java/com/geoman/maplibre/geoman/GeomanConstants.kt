package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.core.GeomanCoreConstants

@Deprecated("Use GeomanCoreConstants instead", ReplaceWith("GeomanCoreConstants"))
object GeomanConstants {
    const val PREFIX = GeomanCoreConstants.GM_PREFIX
    const val EVENT_LOADED = GeomanCoreConstants.Events.LOADED
    const val EVENT_DESTROYED = GeomanCoreConstants.Events.DESTROYED

    const val FEATURE_PROPERTY_PREFIX = GeomanCoreConstants.FEATURE_PROPERTY_PREFIX
    const val FEATURE_ID_PROPERTY = GeomanCoreConstants.FEATURE_ID_PROPERTY

    const val SOURCE_MARKERS = "gm_markers"
    const val SOURCE_LINES = "gm_lines"
    const val SOURCE_POLYGONS = "gm_polygons"
    const val SOURCE_CIRCLES = "gm_circles"
    const val SOURCE_RECTANGLES = "gm_rectangles"
    const val SOURCE_EDIT = "gm_edit"
    const val SOURCE_HELPER = "gm_helper"
}
