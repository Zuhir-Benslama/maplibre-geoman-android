package com.geoman.maplibre.geoman.core.features

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.FeatureStoreRenderer
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.LayerType
import com.geoman.maplibre.geoman.core.options.LayerStyles
import com.geoman.maplibre.geoman.utils.runCatchingRethrowCancellation

/**
 * Creates the rendering layers that visualize stored features for each source,
 * resolving stroke/fill colors and widths from the Geoman options'
 * [LayerStyles] with per-source fallback defaults. Extracted from
 * [Features] to keep the store free of style-mapping concerns.
 */
internal class FeatureLayerStyler(private val geoman: Geoman?) {

    private companion object {
        const val RGB_MASK = 0xFFFFFF
        const val DEFAULT_CIRCLE_MARKER_RADIUS = 10.0f
        const val DEFAULT_LINE_COLOR = "#3498db"
        const val DEFAULT_POLYGON_COLOR = "#8e44ad"
        const val DEFAULT_CIRCLE_COLOR = "#e74c3c"
        const val DEFAULT_FALLBACK_COLOR = "#2ecc71"
        const val DEFAULT_STROKE_WIDTH = 2f
        const val DEFAULT_LINE_WIDTH = 3f
    }

    fun addRenderingLayersForSource(sourceName: String, target: FeatureStoreRenderer) {
        val layerId = when (sourceName) {
            FeatureSources.MARKER -> "${sourceName}_symbol"
            FeatureSources.CIRCLE_MARKER -> "${sourceName}_circle"
            FeatureSources.LINE -> "${sourceName}_line"
            else -> "${sourceName}_stroke"
        }

        if (target.getLayer(layerId) != null) return

        runCatchingRethrowCancellation(
            onError = { GeomanLogger.w("Features", "Error adding layer $layerId", it) },
        ) {
            target.addLayer(buildLayerOptions(sourceName, layerId))
        }
    }

    private fun buildLayerOptions(sourceName: String, layerId: String): LayerOptions {
        if (sourceName == FeatureSources.MARKER) {
            return LayerOptions(
                id = layerId,
                type = LayerType.SYMBOL,
                source = sourceName,
                layout = mapOf(
                    "icon-image" to "default-marker",
                    "icon-size" to 0.5f,
                    "icon-allow-overlap" to true,
                ),
            )
        }

        if (sourceName == FeatureSources.CIRCLE_MARKER) {
            val styles = geoman?.options?.layerStyles
            val circleMarkerStyle = styles?.circleMarker
            val fillColor = resolveLineColor(styles, sourceName)
                ?: circleMarkerStyle?.fillColor?.let { toHex(it) }
                ?: resolveDefaults(sourceName).color

            return LayerOptions(
                id = layerId,
                type = LayerType.CIRCLE,
                source = sourceName,
                paint = mapOf<String, Any>(
                    "circle-radius" to (circleMarkerStyle?.radius ?: DEFAULT_CIRCLE_MARKER_RADIUS),
                    "circle-color" to fillColor,
                    "circle-opacity" to (circleMarkerStyle?.opacity ?: 1.0f),
                    "circle-stroke-width" to (circleMarkerStyle?.width ?: 2.0f),
                    "circle-stroke-color" to (circleMarkerStyle?.color?.let { toHex(it) } ?: "#FFFFFF"),
                ),
            )
        }

        val (defaultColor, defaultWidth) = resolveDefaults(sourceName)
        val layerStyles = geoman?.options?.layerStyles
        val color = resolveLineColor(layerStyles, sourceName) ?: defaultColor
        val width = resolveLineWidth(layerStyles, sourceName) ?: defaultWidth

        return LayerOptions(
            id = layerId,
            type = LayerType.LINE,
            source = sourceName,
            paint = mapOf(
                "line-color" to color,
                "line-width" to width,
            ),
        )
    }

    private data class LayerDefaults(val color: String, val width: Float)

    private fun resolveDefaults(sourceName: String) = when (sourceName) {
        FeatureSources.LINE -> LayerDefaults(DEFAULT_LINE_COLOR, DEFAULT_LINE_WIDTH)
        FeatureSources.POLYGON -> LayerDefaults(DEFAULT_POLYGON_COLOR, DEFAULT_STROKE_WIDTH)
        FeatureSources.CIRCLE -> LayerDefaults(DEFAULT_CIRCLE_COLOR, DEFAULT_STROKE_WIDTH)
        FeatureSources.CIRCLE_MARKER -> LayerDefaults(DEFAULT_LINE_COLOR, DEFAULT_STROKE_WIDTH)
        else -> LayerDefaults(DEFAULT_FALLBACK_COLOR, DEFAULT_STROKE_WIDTH)
    }

    private fun resolveLineColor(styles: LayerStyles?, sourceName: String): String? {
        val color = when (sourceName) {
            FeatureSources.LINE -> styles?.line?.color
            FeatureSources.POLYGON -> styles?.polygon?.color
            FeatureSources.CIRCLE -> styles?.circle?.color
            FeatureSources.CIRCLE_MARKER -> styles?.circleMarker?.fillColor
            else -> styles?.rectangle?.color
        }
        return color?.let { toHex(it) }
    }

    private fun resolveLineWidth(styles: LayerStyles?, sourceName: String): Float? = when (sourceName) {
        FeatureSources.LINE -> styles?.line?.width
        FeatureSources.POLYGON -> styles?.polygon?.width
        FeatureSources.CIRCLE -> styles?.circle?.width
        FeatureSources.CIRCLE_MARKER -> styles?.circleMarker?.width
        else -> styles?.rectangle?.width
    }

    private fun toHex(color: Color): String = String.format("#%06X", color.toArgb() and RGB_MASK)
}
