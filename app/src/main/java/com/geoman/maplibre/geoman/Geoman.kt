package com.geoman.maplibre.geoman

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.MapLibreAdapter
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
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
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.events.GmFeatureEvent
import com.geoman.maplibre.geoman.types.events.GmHelperEvent
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import com.geoman.maplibre.geoman.types.events.GmModeEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Constants - Deprecated: Use GeomanCoreConstants instead
 */
@Deprecated("Use GeomanCoreConstants instead", ReplaceWith("GeomanCoreConstants"))
object GeomanConstants {
    const val PREFIX = GeomanCoreConstants.GM_PREFIX
    const val EVENT_LOADED = GeomanCoreConstants.Events.LOADED
    const val EVENT_DESTROYED = GeomanCoreConstants.Events.DESTROYED

    // Feature properties prefix
    const val FEATURE_PROPERTY_PREFIX = GeomanCoreConstants.FEATURE_PROPERTY_PREFIX
    const val FEATURE_ID_PROPERTY = GeomanCoreConstants.FEATURE_ID_PROPERTY

    // Source names (legacy - kept for backward compatibility)
    const val SOURCE_MARKERS = "gm_markers"
    const val SOURCE_LINES = "gm_lines"
    const val SOURCE_POLYGONS = "gm_polygons"
    const val SOURCE_CIRCLES = "gm_circles"
    const val SOURCE_RECTANGLES = "gm_rectangles"
    const val SOURCE_EDIT = "gm_edit"
    const val SOURCE_HELPER = "gm_helper"
}

/**
 * Main Geoman class for MapLibre Android
 * 
 * Provides drawing, editing, and helper functionality for geographic features.
 * 
 * @param mapView The MapView instance
 * @param map The MapLibreMap instance
 * @param options Initial configuration options
 */
class Geoman(
    internal val mapView: MapView,
    private val map: MapLibreMap,
    options: GmOptionsData = GmOptionsData()
) {
    // Core components
    val options: GmOptions = GmOptions(options)
    val features: Features = Features()
    val events: GmEventBus = GmEventBus()
    
    // Map adapter
    private var _mapAdapter: BaseMapAdapter<MapLibreMap>? = null
    val mapAdapter: BaseMapAdapter<MapLibreMap>
        get() = _mapAdapter ?: throw IllegalStateException("Map adapter not initialized")
    
    // Control
    private var _control: GmControl? = null
    val control: GmControl
        get() = _control ?: throw IllegalStateException("Control not initialized")
    
    // Action instances (modes)
    private val actionInstances = mutableMapOf<String, BaseAction>()
    
    // State
    private val _loaded = MutableStateFlow(false)
    val loaded: Boolean get() = _loaded.value
    val loadedFlow: StateFlow<Boolean> = _loaded

    private val _destroyed = MutableStateFlow(false)
    val destroyed: Boolean get() = _destroyed.value

    // Coroutine scope - internal for use by modes
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Pending base map wait
    private var pendingBaseMapWait: kotlinx.coroutines.Job? = null
    
    init {
        initialize()
    }
    
    /**
     * Initialize Geoman
     */
    private fun initialize() {
        // Create map adapter
        _mapAdapter = MapLibreAdapter(map, this, mapView)
        
        // Create control
        _control = GmControl(this)
        
        // Wait for map to load
        waitForBaseMap()
    }
    
    /**
     * Wait for the base map to be loaded
     */
    private fun waitForBaseMap() {
        if (mapAdapter.isLoaded()) {
            init()
            return
        }
        
        pendingBaseMapWait = scope.launch {
            var attempts = 0
            val maxAttempts = 50 // 5 seconds total
            
            while (attempts < maxAttempts && !_destroyed.value) {
                if (mapAdapter.isLoaded()) {
                    init()
                    return@launch
                }
                delay(100)
                attempts++
            }
            
            if (!_destroyed.value) {
                android.util.Log.e("Geoman", "Map failed to load within timeout")
            }
        }
    }
    
    /**
     * Initialize Geoman after map is loaded
     */
    private fun init() {
        if (_destroyed.value) return

        // Initialize features with map adapter reference for rendering
        features.init(_mapAdapter)

        // Add controls
        scope.launch {
            addControls()
        }
    }
    
    /**
     * Add controls to the map
     */
    private suspend fun addControls() {
        if (options.settings.useControlsUi) {
            mapAdapter.addControl(control)
        }
        
        // Fire loaded event
        onMapLoad()
    }
    
    /**
     * Handle map load event
     */
    private suspend fun onMapLoad() {
        if (_loaded.value || _destroyed.value) return
        
        // Load default marker image
        try {
            val context = mapView.context
            val markerBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                android.R.drawable.ic_menu_mylocation
            )
            mapAdapter.loadImage("default-marker", markerBitmap)
        } catch (e: Exception) {
            android.util.Log.e("Geoman", "Failed to load default marker", e)
        }
        
        _loaded.value = true
        
        // Fire loaded event
        events.emit(GmMapEvent.Loaded)
    }
    
    /**
     * Enable a mode
     */
    fun enableMode(type: ModeType, name: String) {
        if (_destroyed.value) return

        val key = "${type.name}__$name"
        android.util.Log.d("Geoman", "enableMode called: $type.$name (key: $key)")

        // Disable other modes of the same type
        actionInstances.filter { it.key.startsWith("${type.name}__") }.forEach { (k, v) ->
            if (k != key) {
                v.disable()
                actionInstances.remove(k)
            }
        }

        // Create and enable the mode
        val action = createAction(type, name)
        action?.let {
            actionInstances[key] = it
            it.enable()

            // Also update control's active modes (for click handling)
            _control?.activeModes?.removeAll { it.first == type }
            _control?.activeModes?.add(type to name)
            android.util.Log.d("Geoman", "Mode enabled, activeModes now: ${_control?.activeModes}")

            // Fire event
            scope.launch {
                events.emit(GmModeEvent.Enable(name, type.name))
            }
        } ?: run {
            android.util.Log.e("Geoman", "Failed to create action for $type.$name")
        }
    }
    
    /**
     * Disable a mode
     */
    fun disableMode(type: ModeType, name: String) {
        val key = "${type.name}__$name"
        val action = actionInstances[key]

        action?.let {
            it.disable()
            actionInstances.remove(key)

            // Also remove from control's active modes
            _control?.activeModes?.remove(type to name)

            // Fire event
            scope.launch {
                events.emit(GmModeEvent.Disable(name, type.name))
            }
        }
    }
    
    /**
     * Toggle a mode
     */
    fun toggleMode(type: ModeType, name: String): Boolean {
        val key = "${type.name}__$name"
        return if (actionInstances.containsKey(key)) {
            disableMode(type, name)
            false
        } else {
            enableMode(type, name)
            true
        }
    }
    
    /**
     * Check if a mode is enabled
     */
    fun isModeEnabled(type: ModeType, name: String): Boolean {
        return actionInstances.containsKey("${type.name}__$name")
    }
    
    /**
     * Get all enabled modes
     */
    fun getEnabledModes(): List<Pair<ModeType, String>> {
        return actionInstances.keys.map { key ->
            val parts = key.split("__")
            val type = ModeType.valueOf(parts[0])
            val name = parts[1]
            type to name
        }
    }
    
    /**
     * Disable all modes
     */
    fun disableAllModes() {
        actionInstances.values.forEach { it.disable() }
        actionInstances.clear()
        // Also clear control's active modes
        _control?.activeModes?.clear()
        android.util.Log.d("Geoman", "disableAllModes called, activeModes cleared")
    }
    
    /**
     * Create an action instance based on type and name
     */
    private fun createAction(type: ModeType, name: String): BaseAction? {
        return when (type) {
            ModeType.DRAW -> createDrawAction(name)
            ModeType.EDIT -> createEditAction(name)
            ModeType.HELPER -> createHelperAction(name)
        }
    }
    
    private fun createDrawAction(name: String): BaseDraw? {
        return when (name) {
            DrawModeName.MARKER.name -> MarkerDrawer(this)
            DrawModeName.LINE.name -> LineDrawer(this)
            DrawModeName.POLYGON.name -> PolygonDrawer(this)
            DrawModeName.CIRCLE.name -> CircleDrawer(this)
            DrawModeName.RECTANGLE.name -> RectangleDrawer(this)
            else -> null
        }
    }
    
    private fun createEditAction(name: String): BaseEdit? {
        return when (name) {
            EditModeName.DRAG.name -> DragEditor(this)
            EditModeName.CHANGE.name -> ChangeEditor(this)
            EditModeName.ROTATE.name -> RotateEditor(this)
            EditModeName.CUT.name -> null // TODO: Implement CutEditor
            EditModeName.DELETE.name -> DeleteEditor(this)
            else -> null
        }
    }
    
    private fun createHelperAction(name: String): BaseAction? {
        return when (name) {
            HelperModeName.SNAP.name -> SnapHelper(this)
            else -> null
        }
    }
    
    /**
     * Handle draw mode click
     */
    fun handleDrawClick(modeName: String, point: LatLng) {
        val key = "${ModeType.DRAW.name}__$modeName"
        android.util.Log.d("Geoman", "handleDrawClick called: mode=$modeName, key=$key")
        android.util.Log.d("Geoman", "actionInstances keys: ${actionInstances.keys}")
        val action = actionInstances[key] as? BaseDraw
        if (action != null) {
            android.util.Log.d("Geoman", "Found action, calling onMapClick")
            action.onMapClick(point)
        } else {
            android.util.Log.e("Geoman", "No action found for key: $key")
        }
    }

    /**
     * Handle draw mode long press
     */
    fun handleDrawLongPress(modeName: String, point: LatLng) {
        val key = "${ModeType.DRAW.name}__$modeName"
        android.util.Log.d("Geoman", "handleDrawLongPress called: mode=$modeName, key=$key")
        val action = actionInstances[key] as? BaseDraw
        if (action != null) {
            android.util.Log.d("Geoman", "Found action, calling onMapLongClick")
            action.onMapLongClick(point)
        } else {
            android.util.Log.e("Geoman", "No action found for key: $key")
        }
    }

    /**
     * Start editing a specific feature directly (bypasses click selection)
     */
    fun startEditingFeature(feature: FeatureData) {
        val key = "${ModeType.EDIT.name}__${EditModeName.CHANGE.name}"
        val action = actionInstances[key] as? ChangeEditor
        action?.startEditingFeature(feature)
            ?: android.util.Log.w("Geoman", "ChangeEditor not enabled for startEditingFeature")
    }

    /**
     * Handle edit mode click
     */
    fun handleEditClick(modeName: String, point: LatLng) {
        val key = "${ModeType.EDIT.name}__$modeName"
        val action = actionInstances[key] as? BaseEdit
        action?.onMapClick(point)
    }

    /**
     * Handle helper mode click
     */
    fun handleHelperClick(modeName: String, @Suppress("UNUSED_PARAMETER") point: LatLng) {
        val key = "${ModeType.HELPER.name}__$modeName"
        @Suppress("UNUSED_VARIABLE")
        val action = actionInstances[key]
        // Helper actions may handle clicks differently
    }
    
    /**
     * Add a GeoJSON feature
     */
    fun addGeoJsonFeature(
        feature: Feature,
        sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS
    ): FeatureData {
        return features.addGeoJsonFeature(feature, sourceName)
    }
    
    /**
     * Add a GeoJSON feature collection
     */
    fun addFeatureCollection(collection: FeatureCollection) {
        collection.features.forEach { feature ->
            addGeoJsonFeature(feature)
        }
    }
    
    /**
     * Get a feature by ID
     */
    fun getFeature(sourceName: String, featureId: String): FeatureData? {
        return features.getFeature(sourceName, featureId)
    }
    
    /**
     * Get all features
     */
    fun getAllFeatures(): Map<String, Map<String, FeatureData>> {
        return features.getAllFeatures()
    }
    
    /**
     * Remove a feature
     */
    fun removeFeature(sourceName: String, featureId: String) {
        features.removeFeature(sourceName, featureId)
    }
    
    /**
     * Clear all features
     */
    fun clearAllFeatures() {
        features.clearAll()
    }
    
    /**
     * Wait for Geoman to be loaded
     */
    suspend fun waitForGeomanLoaded(): Geoman? {
        if (_loaded.value) return this
        if (_destroyed.value) return null

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                scope.launch {
                    var attempts = 0
                    while (attempts < 50 && !_destroyed.value) {
                        if (_loaded.value) {
                            continuation.resumeWith(Result.success(this@Geoman))
                            return@launch
                        }
                        delay(100)
                        attempts++
                    }
                    continuation.resumeWith(Result.success(null))
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Destroy the Geoman instance and clean up resources
     */
    fun destroy() {
        if (_destroyed.value) return
        _destroyed.value = true
        
        // Cancel pending operations
        pendingBaseMapWait?.cancel()
        
        // Disable all modes
        disableAllModes()
        
        // Remove controls
        scope.launch {
            if (options.settings.useControlsUi) {
                mapAdapter.removeControl(control)
            }
        }
        
        // Clean up map adapter
        if (_mapAdapter is MapLibreAdapter) {
            (_mapAdapter as MapLibreAdapter).cleanup()
        }
        
        // Remove event listeners
        events.removeAllListeners()
        
        // Cancel scope
        scope.cancel()
        
        // Fire destroyed event
        scope.launch {
            events.emit(GmMapEvent.Destroyed)
        }
    }
    
    // Convenience methods for draw modes
    fun enableDraw(mode: DrawModeName) = enableMode(ModeType.DRAW, mode.name)
    fun disableDraw(mode: DrawModeName) = disableMode(ModeType.DRAW, mode.name)
    fun toggleDraw(mode: DrawModeName) = toggleMode(ModeType.DRAW, mode.name)
    fun drawEnabled(mode: DrawModeName) = isModeEnabled(ModeType.DRAW, mode.name)
    
    // Convenience methods for edit modes
    fun enableEdit(mode: EditModeName) = enableMode(ModeType.EDIT, mode.name)
    fun disableEdit(mode: EditModeName) = disableMode(ModeType.EDIT, mode.name)
    fun toggleEdit(mode: EditModeName) = toggleMode(ModeType.EDIT, mode.name)
    fun editEnabled(mode: EditModeName) = isModeEnabled(ModeType.EDIT, mode.name)
    
    // Convenience methods for helper modes
    fun enableHelper(mode: HelperModeName) = enableMode(ModeType.HELPER, mode.name)
    fun disableHelper(mode: HelperModeName) = disableMode(ModeType.HELPER, mode.name)
    fun toggleHelper(mode: HelperModeName) = toggleMode(ModeType.HELPER, mode.name)
    fun helperEnabled(mode: HelperModeName) = isModeEnabled(ModeType.HELPER, mode.name)
}

/**
 * Base action class for all modes
 */
abstract class BaseAction(
    protected val geoman: Geoman
) {
    protected var enabled = false

    abstract val modeName: String
    abstract val modeType: ModeType

    open fun enable() {
        enabled = true
        android.util.Log.d("BaseAction", "enable() called for $modeName, now enabled=$enabled")
    }

    open fun disable() {
        android.util.Log.d("BaseAction", "disable() called for $modeName, was enabled=$enabled")
        enabled = false
    }

    open fun isEnabled(): Boolean = enabled
}
