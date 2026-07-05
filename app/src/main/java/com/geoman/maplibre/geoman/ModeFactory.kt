package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.modes.draw.BaseDraw
import com.geoman.maplibre.geoman.modes.draw.CircleDrawer
import com.geoman.maplibre.geoman.modes.draw.LineDrawer
import com.geoman.maplibre.geoman.modes.draw.MarkerDrawer
import com.geoman.maplibre.geoman.modes.draw.PolygonDrawer
import com.geoman.maplibre.geoman.modes.draw.RectangleDrawer
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.modes.edit.ChangeEditor
import com.geoman.maplibre.geoman.modes.edit.DeleteEditor
import com.geoman.maplibre.geoman.modes.edit.DragEditor
import com.geoman.maplibre.geoman.modes.edit.RotateEditor
import com.geoman.maplibre.geoman.modes.helpers.SnapHelper
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType

class ModeFactory(private val geoman: Geoman) {

    fun create(type: ModeType, name: String): BaseAction? = when (type) {
        ModeType.DRAW -> createDraw(name)
        ModeType.EDIT -> createEdit(name)
        ModeType.HELPER -> createHelper(name)
    }

    private fun createDraw(name: String): BaseDraw? = when (name) {
        DrawModeName.MARKER.name -> MarkerDrawer(geoman)
        DrawModeName.LINE.name -> LineDrawer(geoman)
        DrawModeName.POLYGON.name -> PolygonDrawer(geoman)
        DrawModeName.CIRCLE.name -> CircleDrawer(geoman)
        DrawModeName.RECTANGLE.name -> RectangleDrawer(geoman)
        else -> null
    }

    private fun createEdit(name: String): BaseEdit? = when (name) {
        EditModeName.DRAG.name -> DragEditor(geoman)
        EditModeName.CHANGE.name -> ChangeEditor(geoman)
        EditModeName.ROTATE.name -> RotateEditor(geoman)
        EditModeName.CUT.name -> null
        EditModeName.DELETE.name -> DeleteEditor(geoman)
        else -> null
    }

    private fun createHelper(name: String): BaseAction? = when (name) {
        HelperModeName.SNAP.name -> SnapHelper(geoman)
        else -> null
    }
}
