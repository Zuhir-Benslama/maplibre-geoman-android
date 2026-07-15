package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.core.GeomanCoreConstants

@Deprecated("Use GeomanCoreConstants instead", ReplaceWith("GeomanCoreConstants"))
object GeomanConstants {
    const val PREFIX = GeomanCoreConstants.GM_PREFIX
    const val EVENT_LOADED = GeomanCoreConstants.Events.LOADED
    const val EVENT_DESTROYED = GeomanCoreConstants.Events.DESTROYED

    const val FEATURE_PROPERTY_PREFIX = GeomanCoreConstants.FEATURE_PROPERTY_PREFIX
    const val FEATURE_ID_PROPERTY = GeomanCoreConstants.FEATURE_ID_PROPERTY

    const val SOURCE_MARKERS = GeomanCoreConstants.SOURCE_MARKERS
    const val SOURCE_LINES = GeomanCoreConstants.SOURCE_LINES
    const val SOURCE_POLYGONS = GeomanCoreConstants.SOURCE_POLYGONS
    const val SOURCE_CIRCLES = GeomanCoreConstants.SOURCE_CIRCLES
    const val SOURCE_RECTANGLES = GeomanCoreConstants.SOURCE_RECTANGLES
    const val SOURCE_EDIT = GeomanCoreConstants.SOURCE_EDIT
    const val SOURCE_HELPER = GeomanCoreConstants.SOURCE_HELPER
}
