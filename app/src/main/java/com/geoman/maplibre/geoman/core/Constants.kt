package com.geoman.maplibre.geoman.core

/**
 * Geoman core constants
 * Matches the web version's constants.ts structure
 */
object GeomanCoreConstants {
    /** Geoman prefix for all internal properties and IDs */
    const val GM_PREFIX = "gm"

    /** System prefix for internal use */
    const val GM_SYSTEM_PREFIX = "__$GM_PREFIX"

    /** Feature property prefix */
    const val FEATURE_PROPERTY_PREFIX = "${GM_SYSTEM_PREFIX}_"

    /** Feature ID property name */
    const val FEATURE_ID_PROPERTY = "${FEATURE_PROPERTY_PREFIX}id"

    /** Load timeout in milliseconds */
    const val LOAD_TIMEOUT = 60000L

    /** Source names for feature types */
    const val SOURCE_MARKERS = "${GM_PREFIX}_markers"
    const val SOURCE_LINES = "${GM_PREFIX}_lines"
    const val SOURCE_POLYGONS = "${GM_PREFIX}_polygons"
    const val SOURCE_CIRCLES = "${GM_PREFIX}_circles"
    const val SOURCE_RECTANGLES = "${GM_PREFIX}_rectangles"
    const val SOURCE_EDIT = "${GM_PREFIX}_edit"
    const val SOURCE_HELPER = "${GM_PREFIX}_helper"

    /** Source names - order matters for layer rendering */
    object Sources {
        const val STANDBY = "${GM_PREFIX}_standby"
        const val MAIN = "${GM_PREFIX}_main"
        const val TEMPORARY = "${GM_PREFIX}_temporary"
        const val INTERNAL = "${GM_PREFIX}_internal"

        val ALL = listOf(MAIN, TEMPORARY, INTERNAL)
    }

    /** Event names */
    object Events {
        const val LOADED = "${GM_PREFIX}:loaded"
        const val DESTROYED = "${GM_PREFIX}:destroyed"
    }
}
