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
            createButton(layout.context, "Marker") {
                toggleMode(ModeType.DRAW, DrawModeName.MARKER.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Line") {
                toggleMode(ModeType.DRAW, DrawModeName.LINE.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Polygon") {
                toggleMode(ModeType.DRAW, DrawModeName.POLYGON.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Circle") {
                toggleMode(ModeType.DRAW, DrawModeName.CIRCLE.name)
            },
        )
        drawSection.addView(
            createButton(layout.context, "Rectangle") {
                toggleMode(ModeType.DRAW, DrawModeName.RECTANGLE.name)
            },
        )
        layout.addView(drawSection)

        // Edit controls section
        val editSection = createSection(layout.context, "Edit")
        editSection.addView(
            createButton(layout.context, "Drag") {
                toggleMode(ModeType.EDIT, EditModeName.DRAG.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Change") {
                toggleMode(ModeType.EDIT, EditModeName.CHANGE.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Rotate") {
                toggleMode(ModeType.EDIT, EditModeName.ROTATE.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Cut") {
                toggleMode(ModeType.EDIT, EditModeName.CUT.name)
            },
        )
        editSection.addView(
            createButton(layout.context, "Delete") {
                toggleMode(ModeType.EDIT, EditModeName.DELETE.name)
            },
        )
        layout.addView(editSection)

        // Helper controls section
        val helperSection = createSection(layout.context, "Helpers")
        helperSection.addView(
            createButton(layout.context, "Snap") {
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

    private fun createButton(context: android.content.Context, label: String, onClick: () -> Unit): View =
        ImageButton(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(48, 48)
            setOnClickListener { onClick() }
            contentDescription = label
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

    private fun toggleMode(type: ModeType, name: String) {
        val wasEnabled = activeModes.any { it.first == type && it.second == name }

        if (wasEnabled) {
            activeModes.remove(type to name)
            geoman.disableMode(type, name)
        } else {
            // Disable all modes of the same type first
            activeModes.removeAll { it.first == type }
            activeModes.add(type to name)
            geoman.enableMode(type, name)
        }
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
                DrawModeName.entries.forEach { mode ->
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
                HelperModeName.entries.forEach { mode ->
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
