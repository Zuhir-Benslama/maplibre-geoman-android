package com.geoman.maplibre.geoman.core.options

import androidx.compose.ui.graphics.Color
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType

/**
 * Geoman options configuration
 */
data class GmOptionsData(
    val settings: SettingsOptions = SettingsOptions(),
    val drawOptions: DrawOptions = DrawOptions(),
    val editOptions: EditOptions = EditOptions(),
    val helperOptions: HelperOptions = HelperOptions(),
    val layerStyles: LayerStyles = LayerStyles(),
)

/**
 * General settings options
 */
data class SettingsOptions(
    val useControlsUi: Boolean = true,
    val showControlsOnMap: Boolean = true,
    val controlsPosition: ControlsPosition = ControlsPosition.TOP_LEFT,
    val enableSnap: Boolean = true,
    val snapDistance: Float = 20f,
    val enablePinning: Boolean = false,
    val preventMarkerRemoval: Boolean = false,
    val removeLayerBelowOtherLayers: Boolean = true,
    val hideMiddleMarkers: Boolean = false,
)

/**
 * Controls position on the map
 */
enum class ControlsPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/**
 * Draw mode options
 */
data class DrawOptions(
    val allowSelfIntersections: Boolean = false,
    val snappable: Boolean = true,
    val snapDistance: Float = 20f,
    val requireSnapToFinish: Boolean = false,
    val finishOn: FinishOn = FinishOn.DOUBLE_CLICK,
    val continueDrawing: Boolean = false,
    val markerIcon: String? = null,
    val cursorMarker: Boolean = true,
    val showTooltipOnPoint: Boolean = false,
    val tooltipText: String = "",
    val repeatMode: Boolean = false,
    val draggable: Boolean = true,
)

/**
 * When to finish drawing
 */
enum class FinishOn {
    DOUBLE_CLICK,
    SINGLE_CLICK,
    LONG_PRESS,
}

/**
 * Edit mode options
 */
data class EditOptions(
    val draggable: Boolean = true,
    val rotateable: Boolean = true,
    val removable: Boolean = true,
    val cuttable: Boolean = true,
    val changeable: Boolean = true,
    val allowSelfIntersections: Boolean = false,
    val preventMarkerRemoval: Boolean = false,
    val removeLayerBelowOtherLayers: Boolean = true,
    val hideMiddleMarkers: Boolean = false,
    val snapSegment: Boolean = true,
)

/**
 * Helper mode options
 */
data class HelperOptions(
    val snapEnabled: Boolean = true,
    val snapDistance: Float = 20f,
    val shapeMarkersEnabled: Boolean = true,
    val zoomToFeaturesEnabled: Boolean = false,
    val showSnapGuides: Boolean = true,
)

/**
 * Layer styles for different geometry types
 */
data class LayerStyles(
    val marker: MarkerStyle = MarkerStyle(),
    val line: LineStyle = LineStyle(),
    val polygon: PolygonStyle = PolygonStyle(),
    val circle: CircleStyle = CircleStyle(),
    val rectangle: RectangleStyle = RectangleStyle(),
    val circleMarker: CircleMarkerStyle = CircleMarkerStyle(),
    val editMarkers: EditMarkersStyle = EditMarkersStyle(),
)

/**
 * Marker style
 */
data class MarkerStyle(val color: Color = Color(0xFF3388FF), val opacity: Float = 1.0f, val size: Float = 24f)

/**
 * Line style
 */
data class LineStyle(
    val color: Color = Color(0xFF3388FF),
    val width: Float = 4f,
    val opacity: Float = 1.0f,
    val dashArray: List<Float>? = null,
)

/**
 * Polygon style
 */
data class PolygonStyle(
    val fillColor: Color = Color(0x4D3388FF),
    val color: Color = Color(0xFF3388FF),
    val width: Float = 4f,
    val opacity: Float = 1.0f,
    val fillOpacity: Float = 0.3f,
)

/**
 * Circle style
 */
data class CircleStyle(
    val fillColor: Color = Color(0x4D3388FF),
    val color: Color = Color(0xFF3388FF),
    val width: Float = 4f,
    val opacity: Float = 1.0f,
    val fillOpacity: Float = 0.3f,
)

/**
 * Rectangle style
 */
data class RectangleStyle(
    val fillColor: Color = Color(0x4D3388FF),
    val color: Color = Color(0xFF3388FF),
    val width: Float = 4f,
    val opacity: Float = 1.0f,
    val fillOpacity: Float = 0.3f,
)

/**
 * Circle marker style
 */
data class CircleMarkerStyle(
    val fillColor: Color = Color(0xFF3388FF),
    val color: Color = Color(0xFFFFFFFF),
    val radius: Float = 10f,
    val width: Float = 2f,
    val opacity: Float = 1.0f,
)

/**
 * Edit markers style
 */
data class EditMarkersStyle(
    val vertexMarkerColor: Color = Color(0xFF3388FF),
    val vertexMarkerRadius: Float = 6f,
    val middleMarkerColor: Color = Color(0xFF3388FF),
    val middleMarkerRadius: Float = 4f,
    val dragMarkerColor: Color = Color(0xFF3388FF),
    val rotationMarkerColor: Color = Color(0xFF3388FF),
)

/**
 * GmOptions class that manages the current state of options
 */
class GmOptions(initialData: GmOptionsData = GmOptionsData()) {
    private var _data = initialData
    val data: GmOptionsData get() = _data

    val settings: SettingsOptions get() = _data.settings
    val draw: DrawOptions get() = _data.drawOptions
    val edit: EditOptions get() = _data.editOptions
    val helper: HelperOptions get() = _data.helperOptions
    val layerStyles: LayerStyles get() = _data.layerStyles

    private val enabledModes = mutableSetOf<Pair<ModeType, String>>()

    /**
     * Update options
     */
    fun update(update: GmOptionsData.() -> GmOptionsData) {
        _data = _data.update()
    }

    /**
     * Enable a mode
     */
    fun enableMode(type: ModeType, name: String) {
        enabledModes.add(type to name)
    }

    /**
     * Disable a mode
     */
    fun disableMode(type: ModeType, name: String) {
        enabledModes.remove(type to name)
    }

    /**
     * Toggle a mode
     */
    fun toggleMode(type: ModeType, name: String): Boolean = if (isModeEnabled(type, name)) {
        disableMode(type, name)
        false
    } else {
        enableMode(type, name)
        true
    }

    /**
     * Check if a mode is enabled
     */
    fun isModeEnabled(type: ModeType, name: String): Boolean = enabledModes.contains(type to name)

    /**
     * Get all enabled modes
     */
    fun getEnabledModes(): List<Pair<ModeType, String>> = enabledModes.toList()

    /**
     * Disable all modes
     */
    fun disableAllModes() {
        enabledModes.clear()
    }

    // Convenience methods for draw modes
    fun enableDraw(mode: DrawModeName) = enableMode(ModeType.DRAW, mode.name)
    fun disableDraw(mode: DrawModeName) = disableMode(ModeType.DRAW, mode.name)
    fun drawEnabled(mode: DrawModeName) = isModeEnabled(ModeType.DRAW, mode.name)

    // Convenience methods for edit modes
    fun enableEdit(mode: EditModeName) = enableMode(ModeType.EDIT, mode.name)
    fun disableEdit(mode: EditModeName) = disableMode(ModeType.EDIT, mode.name)
    fun editEnabled(mode: EditModeName) = isModeEnabled(ModeType.EDIT, mode.name)

    // Convenience methods for helper modes
    fun enableHelper(mode: HelperModeName) = enableMode(ModeType.HELPER, mode.name)
    fun disableHelper(mode: HelperModeName) = disableMode(ModeType.HELPER, mode.name)
    fun helperEnabled(mode: HelperModeName) = isModeEnabled(ModeType.HELPER, mode.name)
}
