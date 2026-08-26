package com.geoman.maplibre.geoman.types.geojson

import com.geoman.maplibre.geoman.core.features.FeatureData

/**
 * GeoJSON Feature data class — alias for [FeatureData] for backward compatibility.
 * Prefer using [FeatureData] directly in new code.
 */
@Deprecated("Use FeatureData directly", ReplaceWith("FeatureData"))
typealias GeoJsonFeatureData = FeatureData
