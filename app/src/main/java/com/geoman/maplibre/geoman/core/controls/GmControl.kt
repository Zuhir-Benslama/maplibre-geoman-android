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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeType
import org.maplibre.android.geometry.LatLng

/**
 * Geoman control panel for map editing
 * Implements both traditional Android View and Jetpack Compose versions
 */
class GmControl(
    private val geoman: Geoman
) {
    private var controlView: View? = null
    // Make activeModes public so external code can see which modes are active
    val activeModes = mutableSetOf<Pair<ModeType, String>>()
    
    /**
     * Create the control panel UI using traditional Android Views
     */
    fun createControls(parent: ViewGroup): View {
        val context = parent.context
        
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
            background = context.getDrawable(android.R.drawable.dialog_holo_light_frame)
        }
        
        // Draw controls section
        val drawSection = createSection(layout.context, "Draw")
        drawSection.addView(createButton(layout.context, "Marker", Icons.Default.Place) {
            toggleMode(ModeType.DRAW, DrawModeName.MARKER.name)
        })
        drawSection.addView(createButton(layout.context, "Line", Icons.Default.Polyline) {
            toggleMode(ModeType.DRAW, DrawModeName.LINE.name)
        })
        drawSection.addView(createButton(layout.context, "Polygon", Icons.Default.CenterFocusStrong) {
            toggleMode(ModeType.DRAW, DrawModeName.POLYGON.name)
        })
        drawSection.addView(createButton(layout.context, "Circle", Icons.Default.Circle) {
            toggleMode(ModeType.DRAW, DrawModeName.CIRCLE.name)
        })
        drawSection.addView(createButton(layout.context, "Rectangle", Icons.Default.Square) {
            toggleMode(ModeType.DRAW, DrawModeName.RECTANGLE.name)
        })
        layout.addView(drawSection)
        
        // Edit controls section
        val editSection = createSection(layout.context, "Edit")
        editSection.addView(createButton(layout.context, "Drag", Icons.Default.PanTool) {
            toggleMode(ModeType.EDIT, EditModeName.DRAG.name)
        })
        editSection.addView(createButton(layout.context, "Change", Icons.Default.Edit) {
            toggleMode(ModeType.EDIT, EditModeName.CHANGE.name)
        })
        editSection.addView(createButton(layout.context, "Rotate", Icons.Default.Refresh) {
            toggleMode(ModeType.EDIT, EditModeName.ROTATE.name)
        })
        editSection.addView(createButton(layout.context, "Cut", Icons.Default.Remove) {
            toggleMode(ModeType.EDIT, EditModeName.CUT.name)
        })
        editSection.addView(createButton(layout.context, "Delete", Icons.Default.Remove) {
            toggleMode(ModeType.EDIT, EditModeName.DELETE.name)
        })
        layout.addView(editSection)
        
        // Helper controls section
        val helperSection = createSection(layout.context, "Helpers")
        helperSection.addView(createButton(layout.context, "Snap", Icons.Default.CenterFocusStrong) {
            toggleMode(ModeType.HELPER, HelperModeName.SNAP.name)
        })
        layout.addView(helperSection)
        
        controlView = layout
        return layout
    }
    
    private fun createSection(context: android.content.Context, title: String): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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
    
    private fun createButton(
        context: android.content.Context,
        label: String,
        @Suppress("UNUSED_PARAMETER") icon: ImageVector,
        onClick: () -> Unit
    ): View {
        return ImageButton(context).apply {
            @Suppress("DEPRECATION")
            setImageDrawable(android.graphics.drawable.BitmapDrawable())
            layoutParams = ViewGroup.LayoutParams(48, 48)
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
        android.util.Log.d("GmControl", "onMapClick called, activeModes: $activeModes")
        // Handle map click for active drawing modes
        activeModes.forEach { (type, name) ->
            android.util.Log.d("GmControl", "Forwarding click to $type.$name")
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
        android.util.Log.d("GmControl", "onMapLongClick called, activeModes: $activeModes")
        // Handle long press for finishing shapes
        activeModes.forEach { (type, name) ->
            if (type == ModeType.DRAW) {
                android.util.Log.d("GmControl", "Forwarding long click to $type.$name")
                geoman.handleDrawLongPress(name, point)
            }
        }
        return false
    }
    
    /**
     * Called for touch events
     */
    fun onTouchEvent(@Suppress("UNUSED_PARAMETER") event: MotionEvent): Boolean {
        // Handle touch events for dragging, etc.
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
 * Compose version of the Geoman control panel
 */
@Composable
fun GeomanControls(
    geoman: Geoman,
    modifier: Modifier = Modifier
) {
    var activeDrawMode by remember { mutableStateOf<DrawModeName?>(null) }
    var activeEditMode by remember { mutableStateOf<EditModeName?>(null) }
    var activeHelperMode by remember { mutableStateOf<HelperModeName?>(null) }
    
    Box(
        modifier = modifier
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(8.dp)
        ) {
            // Draw controls
            ControlSection(title = "Draw") {
                ControlButton(
                    icon = Icons.Default.Place,
                    contentDescription = "Marker",
                    isActive = activeDrawMode == DrawModeName.MARKER
                ) {
                    activeDrawMode = if (activeDrawMode == DrawModeName.MARKER) null else DrawModeName.MARKER
                    activeEditMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.DRAW, DrawModeName.MARKER.name)
                }
                ControlButton(
                    icon = Icons.Default.Polyline,
                    contentDescription = "Line",
                    isActive = activeDrawMode == DrawModeName.LINE
                ) {
                    activeDrawMode = if (activeDrawMode == DrawModeName.LINE) null else DrawModeName.LINE
                    activeEditMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
                }
                ControlButton(
                    icon = Icons.Default.CenterFocusStrong,
                    contentDescription = "Polygon",
                    isActive = activeDrawMode == DrawModeName.POLYGON
                ) {
                    activeDrawMode = if (activeDrawMode == DrawModeName.POLYGON) null else DrawModeName.POLYGON
                    activeEditMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.DRAW, DrawModeName.POLYGON.name)
                }
                ControlButton(
                    icon = Icons.Default.Circle,
                    contentDescription = "Circle",
                    isActive = activeDrawMode == DrawModeName.CIRCLE
                ) {
                    activeDrawMode = if (activeDrawMode == DrawModeName.CIRCLE) null else DrawModeName.CIRCLE
                    activeEditMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.DRAW, DrawModeName.CIRCLE.name)
                }
                ControlButton(
                    icon = Icons.Default.Square,
                    contentDescription = "Rectangle",
                    isActive = activeDrawMode == DrawModeName.RECTANGLE
                ) {
                    activeDrawMode = if (activeDrawMode == DrawModeName.RECTANGLE) null else DrawModeName.RECTANGLE
                    activeEditMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.DRAW, DrawModeName.RECTANGLE.name)
                }
            }
            
            // Edit controls
            ControlSection(title = "Edit") {
                ControlButton(
                    icon = Icons.Default.PanTool,
                    contentDescription = "Drag",
                    isActive = activeEditMode == EditModeName.DRAG
                ) {
                    activeEditMode = if (activeEditMode == EditModeName.DRAG) null else EditModeName.DRAG
                    activeDrawMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.EDIT, EditModeName.DRAG.name)
                }
                ControlButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Change",
                    isActive = activeEditMode == EditModeName.CHANGE
                ) {
                    activeEditMode = if (activeEditMode == EditModeName.CHANGE) null else EditModeName.CHANGE
                    activeDrawMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.EDIT, EditModeName.CHANGE.name)
                }
                ControlButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Rotate",
                    isActive = activeEditMode == EditModeName.ROTATE
                ) {
                    activeEditMode = if (activeEditMode == EditModeName.ROTATE) null else EditModeName.ROTATE
                    activeDrawMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.EDIT, EditModeName.ROTATE.name)
                }
                ControlButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Delete",
                    isActive = activeEditMode == EditModeName.DELETE
                ) {
                    activeEditMode = if (activeEditMode == EditModeName.DELETE) null else EditModeName.DELETE
                    activeDrawMode = null
                    activeHelperMode = null
                    geoman.enableMode(ModeType.EDIT, EditModeName.DELETE.name)
                }
            }
            
            // Helper controls
            ControlSection(title = "Helpers") {
                ControlButton(
                    icon = Icons.Default.CenterFocusStrong,
                    contentDescription = "Snap",
                    isActive = activeHelperMode == HelperModeName.SNAP
                ) {
                    activeHelperMode = if (activeHelperMode == HelperModeName.SNAP) null else HelperModeName.SNAP
                    activeDrawMode = null
                    activeEditMode = null
                    geoman.enableMode(ModeType.HELPER, HelperModeName.SNAP.name)
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        androidx.compose.material3.Text(
            text = title,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            content()
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
