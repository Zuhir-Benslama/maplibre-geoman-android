package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.core.GeomanCoreConstants

/**
 * Shape classification of a stored feature (web parity: FeatureData.gmShape).
 *
 * Shapes are derived from the source a feature lives in and travel through
 * GeoJSON export/import as the [GeomanCoreConstants.FEATURE_SHAPE_PROPERTY]
 * system property using the lowercase [tag].
 */
enum class FeatureShape(val sourceName: String, val tag: String) {
    POINT(GeomanCoreConstants.SOURCE_MARKERS, "point"),
    LINE(GeomanCoreConstants.SOURCE_LINES, "line"),
    POLYGON(GeomanCoreConstants.SOURCE_POLYGONS, "polygon"),
    CIRCLE(GeomanCoreConstants.SOURCE_CIRCLES, "circle"),
    RECTANGLE(GeomanCoreConstants.SOURCE_RECTANGLES, "rectangle"),
    CIRCLE_MARKER(GeomanCoreConstants.SOURCE_CIRCLE_MARKERS, "circle_marker"),
    ;

    companion object {
        fun fromSourceName(sourceName: String): FeatureShape? = entries.firstOrNull { it.sourceName == sourceName }

        fun fromTag(tag: String?): FeatureShape? = tag?.let { value -> entries.firstOrNull { it.tag == value } }
    }
}
