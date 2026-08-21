package com.geoman.maplibre.geoman.core.controls

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType
import org.maplibre.android.geometry.LatLng

/**
 * Geoman control panel for map editing
 * Implements both traditional Android View and Jetpack Compose versions
 */
class GmControl(private val geoman: Geoman) {
    private var controlView: View? = null

    val activeModes: MutableSet<Pair<ModeType, String>> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * Create the control panel UI using traditional Android Views
     */
    fun createControls(parent: ViewGroup): View {
        val context = parent.context

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(16, 16, 16, 16)
            background =
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    context,
                    android.R.drawable.dialog_holo_light_frame,
                )
        }

        // Draw controls section
        val drawSection = createSection(layout.context, "Draw")
        drawSection.addView(
            createButton(layout.context, "Marker", android.R.drawable.ic_menu_myplaces) {
                toggleMode(ModeType.DRAW, DrawModeName.MARKER.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Line", android.R.drawable.ic_menu_edit) {
                toggleMode(ModeType.DRAW, DrawModeName.LINE.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Polygon", android.R.drawable.ic_menu_mapmode) {
                toggleMode(ModeType.DRAW, DrawModeName.POLYGON.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Circle", android.R.drawable.ic_menu_compass) {
                toggleMode(ModeType.DRAW, DrawModeName.CIRCLE.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Rectangle", android.R.drawable.ic_menu_gallery) {
                toggleMode(ModeType.DRAW, DrawModeName.RECTANGLE.name)
            },
        )
        layout.addView(drawSection)

        // Edit controls section
        val editSection = createSection(layout.context, "Edit")
        editSection.addView(
            createButton(layout.context, "Drag", android.R.drawable.ic_menu_directions) {
                toggleMode(ModeType.EDIT, EditModeName.DRAG.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Change", android.R.drawable.ic_menu_manage) {
                toggleMode(ModeType.EDIT, EditModeName.CHANGE.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Rotate", android.R.drawable.ic_menu_rotate) {
                toggleMode(ModeType.EDIT, EditModeName.ROTATE.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Cut", android.R.drawable.ic_menu_crop) {
                toggleMode(ModeType.EDIT, EditModeName.CUT.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Delete", android.R.drawable.ic_menu_delete) {
                toggleMode(ModeType.EDIT, EditModeName.DELETE.name)
            },
        )
        layout.addView(editSection)

        // Helper controls section
        val helperSection = createSection(layout.context, "Helpers")
        helperSection.addView(
            createButton(layout.context, "Snap", android.R.drawable.ic_menu_zoom) {
                toggleMode(ModeType.HELPER, HelperModeName.SNAP.name)
            },
        )
        layout.addView(helperSection)

        controlView = layout
        return layout
    }

    private fun createSection(context: android.content.Context, title: String): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val titleView = android.widget.TextView(context).apply {
            text = title
            textSize = 12f
            setPadding(8, 8, 8, 4)
        }
        layout.addView(titleView)

        return layout
    }

    private fun createButton(context: android.content.Context, label: String, iconRes: Int, onClick: () -> Unit): View {
        val sizePx = (48 * context.resources.displayMetrics.density).toInt()
        return ImageButton(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            setImageResource(iconRes)
            setOnClickListener { onClick() }
            contentDescription = label
        }
    }

    /**
     * Remove controls
     */
    fun removeControls() {
        controlView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        controlView = null
    }

    /**
     * Called when map is clicked
     */
    fun onMapClick(point: LatLng): Boolean {
        GeomanLogger.d("GmControl", "onMapClick called, activeModes: $activeModes")
        // Handle map click for active drawing modes
        activeModes.forEach { (type, name) ->
            GeomanLogger.d("GmControl", "Forwarding click to $type.$name")
            when (type) {
                ModeType.DRAW -> geoman.handleDrawClick(name, point)
                ModeType.EDIT -> geoman.handleEditClick(name, point)
                ModeType.HELPER -> geoman.handleHelperClick(name, point)
            }
        }
        return false
    }

    /**
     * Called when map is long clicked
     */
    fun onMapLongClick(point: LatLng): Boolean {
        GeomanLogger.d("GmControl", "onMapLongClick called, activeModes: $activeModes")
        // Handle long press for finishing shapes
        activeModes.forEach { (type, name) ->
            if (type == ModeType.DRAW) {
                GeomanLogger.d("GmControl", "Forwarding long click to $type.$name")
                geoman.handleDrawLongPress(name, point)
            }
        }
        return false
    }

    /**
     * Called for touch events.
     * Forwards to the active DragEditor, which consumes events while a drag handle
     * is being moved so the map does not pan underneath it.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val dragMode = activeModes.firstOrNull { it.first == ModeType.EDIT && it.second == EditModeName.DRAG.name }
        if (dragMode != null) {
            return geoman.handleEditTouch(dragMode.second, event)
        }
        return false
    }

    /**
     * Called when control is detached
     */
    fun onDetach() {
        removeControls()
    }

    /**
     * Toggle a mode. State bookkeeping ([activeModes], options, flow) is owned
     * by [Geoman.enableMode]/[Geoman.disableMode]; do not mutate it here.
     */
    private fun toggleMode(type: ModeType, name: String) {
        geoman.toggleMode(type, name)
    }
}

/**
 * Compose version of the Geoman control panel.
 * Reads the enabled modes directly from [Geoman.activeModesFlow] so the UI can
 * never drift from the actual mode state (drawers that finish themselves, etc.).
 */
@Composable
fun GeomanControls(geoman: Geoman, modifier: Modifier = Modifier) {
    val activeModes by geoman.activeModesFlow.collectAsState()
    val isActive: (ModeType, String) -> Boolean = { type, name ->
        activeModes.any { it.first == type && it.second == name }
    }

    Box(
        modifier = modifier
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(8.dp),
        ) {
            ControlSection(title = "Draw") {
                supportedDrawModes.forEach { mode ->
                    ControlButton(
                        icon = mode.icon(),
                        contentDescription = mode.name,
                        isActive = isActive(ModeType.DRAW, mode.name),
                    ) {
                        geoman.toggleMode(ModeType.DRAW, mode.name)
                    }
                }
            }

            ControlSection(title = "Edit") {
                EditModeName.entries.forEach { mode ->
                    ControlButton(
                        icon = mode.icon(),
                        contentDescription = mode.name,
                        isActive = isActive(ModeType.EDIT, mode.name),
                    ) {
                        geoman.toggleMode(ModeType.EDIT, mode.name)
                    }
                }
            }

            ControlSection(title = "Helpers") {
                supportedHelperModes.forEach { mode ->
                    ControlButton(
                        icon = mode.icon(),
                        contentDescription = mode.name,
                        isActive = isActive(ModeType.HELPER, mode.name),
                    ) {
                        geoman.toggleMode(ModeType.HELPER, mode.name)
                    }
                }
            }
        }
    }
}

// Modes with a working implementation in ModeFactory. Enum entries without
// one (SHAPE_MARKERS) are hidden from the panel so users can't toggle
// buttons that silently do nothing.
private val supportedDrawModes = listOf(
    DrawModeName.MARKER,
    DrawModeName.CIRCLE_MARKER,
    DrawModeName.LINE,
    DrawModeName.POLYGON,
    DrawModeName.CIRCLE,
    DrawModeName.RECTANGLE,
)

private val supportedHelperModes = listOf(
    HelperModeName.SNAP,
    HelperModeName.ZOOM_TO_FEATURES,
)

private fun DrawModeName.icon(): ImageVector = when (this) {
    DrawModeName.MARKER, DrawModeName.CIRCLE_MARKER -> Icons.Default.Place
    DrawModeName.LINE -> Icons.Default.Polyline
    DrawModeName.POLYGON -> Icons.Default.CenterFocusStrong
    DrawModeName.CIRCLE -> Icons.Default.Circle
    DrawModeName.RECTANGLE -> Icons.Default.Square
}

private fun EditModeName.icon(): ImageVector = when (this) {
    EditModeName.DRAG -> Icons.Default.PanTool
    EditModeName.CHANGE -> Icons.Default.Edit
    EditModeName.ROTATE -> Icons.Default.Refresh
    EditModeName.CUT, EditModeName.DELETE -> Icons.Default.Remove
}

private fun HelperModeName.icon(): ImageVector = when (this) {
    HelperModeName.SNAP, HelperModeName.ZOOM_TO_FEATURES -> Icons.Default.CenterFocusStrong
    HelperModeName.SHAPE_MARKERS -> Icons.Default.Place
}

@Composable
private fun ControlSection(title: String, content: @Composable () -> Unit) {
    Column {
        androidx.compose.material3.Text(
            text = title,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            content()
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, contentDescription: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp),
        )
    }
}
