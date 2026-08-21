package com.geoman.maplibre.geoman.types

/**
 * Mode types for Geoman actions
 */
enum class ModeType {
    DRAW,
    EDIT,
    HELPER,
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
    CIRCLE_MARKER,
}

/**
 * Edit mode names
 */
enum class EditModeName {
    DRAG,
    CHANGE,
    ROTATE,
    CUT,
    DELETE,
}

/**
 * Helper mode names
 *
 * Note: shape markers are not a mode; midpoint handles are rendered by
 * [com.geoman.maplibre.geoman.modes.edit.ChangeEditor] and toggled via
 * `helperOptions.shapeMarkersEnabled`.
 */
enum class HelperModeName {
    SNAP,
    ZOOM_TO_FEATURES,
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
    NOT_ALLOWED,
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
    KEYBOARD,
}
