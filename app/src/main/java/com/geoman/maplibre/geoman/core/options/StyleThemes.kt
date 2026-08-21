package com.geoman.maplibre.geoman.core.options

import androidx.compose.ui.graphics.Color

private const val DARK_STROKE_LONG = 0xFF00E5FF
private const val DARK_FILL_LONG = 0x5500E5FF
private const val DARK_MARKER_LONG = 0xFFFFC400
private const val DARK_VERTEX_LONG = 0xFFFFFFFF

/**
 * Named style themes for map layers.
 */
enum class StyleTheme {
    /** Tuned for light basemaps (library defaults). */
    LIGHT,

    /** High-contrast palette for dark/satellite basemaps. */
    DARK,
}

/**
 * Preset [LayerStyles] per [StyleTheme].
 *
 * Host apps can use these as a starting point and override individual
 * entries via `gmOptions.update { copy(layerStyles = ...) }`.
 */
object StyleThemes {

    val light: LayerStyles = LayerStyles()

    val dark: LayerStyles = LayerStyles(
        marker = MarkerStyle(color = Color(DARK_MARKER_LONG)),
        line = LineStyle(color = Color(DARK_STROKE_LONG)),
        polygon = PolygonStyle(fillColor = Color(DARK_FILL_LONG), color = Color(DARK_STROKE_LONG)),
        circle = CircleStyle(fillColor = Color(DARK_FILL_LONG), color = Color(DARK_STROKE_LONG)),
        rectangle = RectangleStyle(fillColor = Color(DARK_FILL_LONG), color = Color(DARK_STROKE_LONG)),
        circleMarker = CircleMarkerStyle(fillColor = Color(DARK_STROKE_LONG), color = Color(DARK_VERTEX_LONG)),
        editMarkers = EditMarkersStyle(
            vertexMarkerColor = Color(DARK_VERTEX_LONG),
            middleMarkerColor = Color(DARK_VERTEX_LONG),
            dragMarkerColor = Color(DARK_MARKER_LONG),
            rotationMarkerColor = Color(DARK_MARKER_LONG),
        ),
    )

    /** Resolve [theme] to its preset styles. */
    fun resolve(theme: StyleTheme): LayerStyles = when (theme) {
        StyleTheme.LIGHT -> light
        StyleTheme.DARK -> dark
    }
}
