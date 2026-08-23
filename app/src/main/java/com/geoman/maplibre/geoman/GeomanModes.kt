package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType

/**
 * Typed convenience wrappers around [Geoman.enableMode]/[disableMode]/
 * [toggleMode]/[isModeEnabled], grouped per mode family. Declared as
 * extensions to keep the [Geoman] facade lean; import this file (or the
 * individual functions) at call sites.
 */

// Draw modes
fun Geoman.enableDraw(mode: DrawModeName) = enableMode(ModeType.DRAW, mode.name)
fun Geoman.disableDraw(mode: DrawModeName) = disableMode(ModeType.DRAW, mode.name)
fun Geoman.toggleDraw(mode: DrawModeName) = toggleMode(ModeType.DRAW, mode.name)
fun Geoman.drawEnabled(mode: DrawModeName) = isModeEnabled(ModeType.DRAW, mode.name)

// Edit modes
fun Geoman.enableEdit(mode: EditModeName) = enableMode(ModeType.EDIT, mode.name)
fun Geoman.disableEdit(mode: EditModeName) = disableMode(ModeType.EDIT, mode.name)
fun Geoman.toggleEdit(mode: EditModeName) = toggleMode(ModeType.EDIT, mode.name)
fun Geoman.editEnabled(mode: EditModeName) = isModeEnabled(ModeType.EDIT, mode.name)

// Helper modes
fun Geoman.enableHelper(mode: HelperModeName) = enableMode(ModeType.HELPER, mode.name)
fun Geoman.disableHelper(mode: HelperModeName) = disableMode(ModeType.HELPER, mode.name)
fun Geoman.toggleHelper(mode: HelperModeName) = toggleMode(ModeType.HELPER, mode.name)
fun Geoman.helperEnabled(mode: HelperModeName) = isModeEnabled(ModeType.HELPER, mode.name)
