package com.geoman.maplibre.geoman.types

/**
 * Mode types for Geoman actions
 */
enum class ModeType {
    DRAW,
    EDIT,
    HELPER
}

/**
 * Draw mode names
 */
enum class DrawModeName {
    MARKER,
    LINE,
    POLYGON,
    CIRCLE,
    RECTANGLE,
    CIRCLE_MARKER
}

/**
 * Edit mode names
 */
enum class EditModeName {
    DRAG,
    CHANGE,
    ROTATE,
    CUT,
    DELETE
}

/**
 * Helper mode names
 */
enum class HelperModeName {
    SNAP,
    SHAPE_MARKERS,
    ZOOM_TO_FEATURES
}

/**
 * Combined mode name type
 */
sealed class ModeName {
    abstract val name: String
    abstract val type: ModeType
}

sealed class DrawMode(override val type: ModeType = ModeType.DRAW) : ModeName() {
    object Marker : DrawMode() { override val name = "marker" }
    object Line : DrawMode() { override val name = "line" }
    object Polygon : DrawMode() { override val name = "polygon" }
    object Circle : DrawMode() { override val name = "circle" }
    object Rectangle : DrawMode() { override val name = "rectangle" }
    object CircleMarker : DrawMode() { override val name = "circle_marker" }
}

sealed class EditMode(override val type: ModeType = ModeType.EDIT) : ModeName() {
    object Drag : EditMode() { override val name = "drag" }
    object Change : EditMode() { override val name = "change" }
    object Rotate : EditMode() { override val name = "rotate" }
    object Cut : EditMode() { override val name = "cut" }
    object Delete : EditMode() { override val name = "delete" }
}

sealed class HelperMode(override val type: ModeType = ModeType.HELPER) : ModeName() {
    object Snap : HelperMode() { override val name = "snap" }
    object ShapeMarkers : HelperMode() { override val name = "shape_markers" }
    object ZoomToFeatures : HelperMode() { override val name = "zoom_to_features" }
}

/**
 * Cursor types for map interaction
 */
enum class CursorType {
    DEFAULT,
    POINTER,
    GRAB,
    GRABBING,
    CROSSHAIR,
    MOVE,
    NOT_ALLOWED
}

/**
 * Map interaction types
 */
enum class MapInteraction {
    SCROLL,
    ZOOM,
    ROTATE,
    PITCH,
    DRAG_PAN,
    BOX_ZOOM,
    DOUBLE_CLICK_ZOOM,
    TOUCH_ZOOM,
    TOUCH_ROTATE,
    TOUCH_PITCH,
    DRAG_ROTATE,
    KEYBOARD
}
