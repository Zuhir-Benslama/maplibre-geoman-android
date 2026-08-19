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
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.modes.edit.ChangeEditor
import com.geoman.maplibre.geoman.modes.edit.DragEditor
import com.geoman.maplibre.geoman.modes.helpers.BaseHelper
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Main Geoman class for MapLibre Android
 *
 * Provides drawing, editing, and helper functionality for geographic features.
 *
 * @param mapView The MapView instance
 * @param map The MapLibreMap instance
 * @param options Initial configuration options
 */
class Geoman(internal val mapView: MapView, private val map: MapLibreMap, options: GmOptionsData = GmOptionsData()) {

    private companion object {
        const val TAG = "Geoman"
        const val MODE_KEY_DELIMITER = "__"
        const val STYLE_LOAD_TIMEOUT_MS = 10_000L
        const val GEOMAN_LOADED_TIMEOUT_MS = 5_000L
    }

    // Core components
    val options: GmOptions = GmOptions(options)
    val features: Features = Features(this)
    val events: GmEventBus = GmEventBus()

    // Map adapter
    @Volatile
    private var _mapAdapter: BaseMapAdapter<MapLibreMap>? = null
    val mapAdapter: BaseMapAdapter<MapLibreMap>
        get() = _mapAdapter ?: throw IllegalStateException("Map adapter not initialized")

    // Control
    @Volatile
    private var _control: GmControl? = null
    val control: GmControl
        get() = _control ?: throw IllegalStateException("Control not initialized")

    // Mode factory
    private val modeFactory = ModeFactory(this)

    // Action instances (modes) — guarded by `this` lock for atomic mode switching
    private val actionInstances = java.util.concurrent.ConcurrentHashMap<String, BaseAction>()

    // Single source of truth for the set of currently enabled modes
    private val _activeModesFlow = MutableStateFlow<List<Pair<ModeType, String>>>(emptyList())
    val activeModesFlow: StateFlow<List<Pair<ModeType, String>>> = _activeModesFlow.asStateFlow()

    // State
    private val _loaded = MutableStateFlow(false)
    val loaded: Boolean get() = _loaded.value
    val loadedFlow: StateFlow<Boolean> = _loaded

    private val _destroyed = MutableStateFlow(false)
    val destroyed: Boolean get() = _destroyed.value

    // Coroutine scope with exception handler to prevent silent coroutine failures
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        GeomanLogger.e(TAG, "Uncaught coroutine exception", throwable as? Exception ?: Exception(throwable))
    }
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    // Pending base map wait
    private var pendingBaseMapWait: kotlinx.coroutines.Job? = null

    init {
        initialize()
    }

    /**
     * Build a stable map key for an action instance.
     */
    private fun modeKey(type: ModeType, name: String): String = "${type.name}$MODE_KEY_DELIMITER$name"

    /**
     * Parse a mode key back into its type and name components.
     */
    private fun parseModeKey(key: String): Pair<ModeType, String>? {
        val parts = key.split(MODE_KEY_DELIMITER)
        if (parts.size != 2) return null
        return try {
            ModeType.valueOf(parts[0]) to parts[1]
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Initialize Geoman
     */
    private fun initialize() {
        _mapAdapter = MapLibreAdapter(map, this, mapView)
        _control = GmControl(this)
        waitForBaseMap()
    }

    /**
     * Wait for the base map style to be loaded
     */
    private fun waitForBaseMap() {
        if (mapAdapter.isLoaded()) {
            init()
            return
        }

        pendingBaseMapWait = scope.launch {
            withTimeoutOrNull(STYLE_LOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val listener = MapView.OnDidFinishLoadingStyleListener {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Unit))
                        }
                    }
                    mapView.addOnDidFinishLoadingStyleListener(listener)
                    continuation.invokeOnCancellation {
                        mapView.removeOnDidFinishLoadingStyleListener(listener)
                    }
                }
            }

            if (!_destroyed.value) {
                init()
            }
        }
    }

    /**
     * Initialize Geoman after map is loaded
     */
    private fun init() {
        if (_destroyed.value) return

        features.init(_mapAdapter)

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
        onMapLoad()
    }

    /**
     * Handle map load event
     */
    private suspend fun onMapLoad() {
        if (_loaded.value || _destroyed.value) return

        loadMarkerImage()

        _loaded.value = true
        events.emit(GmMapEvent.Loaded)
    }

    /**
     * Load the default marker image into the current style.
     */
    private suspend fun loadMarkerImage() {
        try {
            val context = mapView.context
            val markerBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                android.R.drawable.ic_menu_mylocation,
            )
            mapAdapter.loadImage("default-marker", markerBitmap)
        } catch (e: Exception) {
            GeomanLogger.e(TAG, "Failed to load default marker", e)
        }
    }

    /**
     * Restore rendering after the base map style has been replaced.
     * Style-bound sources and layers were destroyed by the style swap, so
     * cached references are dropped, the default marker image is reloaded,
     * and in-memory features are re-synced.
     */
    fun onStyleReloaded() {
        if (_destroyed.value) return
        (_mapAdapter as? MapLibreAdapter)?.clearRenderingCache()
        features.reSyncAll()
        scope.launch {
            loadMarkerImage()
        }
    }

    /**
     * Enable a mode. Disables other modes of the same type first.
     *
     * Mode switching is synchronized to prevent races between concurrent
     * calls (e.g. rapid UI taps).
     */
    fun enableMode(type: ModeType, name: String) {
        if (_destroyed.value) return

        val key = modeKey(type, name)

        synchronized(this) {
            // Disable other modes of the same type
            val keysToDisable = actionInstances.keys.filter {
                it.startsWith("${type.name}$MODE_KEY_DELIMITER") && it != key
            }
            keysToDisable.forEach { k ->
                actionInstances[k]?.disable()
                actionInstances.remove(k)
            }

            // Create and enable the mode
            val action = modeFactory.create(type, name)
            action?.let {
                actionInstances[key] = it
                it.enable()

                _control?.activeModes?.removeAll { it.first == type }
                _control?.activeModes?.add(type to name)

                options.enableMode(type, name)
                _activeModesFlow.value = getEnabledModes()
            }
        }

        // Fire event outside the lock to avoid holding it during coroutine dispatch
        actionInstances[key]?.let {
            scope.launch {
                events.emit(GmModeEvent.Enable(name, type.name))
            }
        } ?: run {
            GeomanLogger.e(TAG, "Failed to create action for $type.$name")
        }
    }

    /**
     * Disable a mode.
     */
    fun disableMode(type: ModeType, name: String) {
        val key = modeKey(type, name)

        val action = synchronized(this) {
            actionInstances.remove(key)?.also {
                it.disable()
                _control?.activeModes?.remove(type to name)
                options.disableMode(type, name)
                _activeModesFlow.value = getEnabledModes()
            }
        }

        action?.let {
            scope.launch {
                events.emit(GmModeEvent.Disable(name, type.name))
            }
        }
    }

    /**
     * Toggle a mode
     */
    fun toggleMode(type: ModeType, name: String): Boolean {
        val key = modeKey(type, name)
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
    fun isModeEnabled(type: ModeType, name: String): Boolean = actionInstances.containsKey(modeKey(type, name))

    /**
     * Get all enabled modes
     */
    fun getEnabledModes(): List<Pair<ModeType, String>> = actionInstances.keys.mapNotNull { parseModeKey(it) }

    /**
     * Disable all modes
     */
    fun disableAllModes() {
        val toDisable: List<BaseAction>
        synchronized(this) {
            toDisable = actionInstances.values.toList()
            actionInstances.clear()
            _control?.activeModes?.clear()
            options.disableAllModes()
            _activeModesFlow.value = emptyList()
        }
        // Disable actions outside the lock to avoid holding it during mode cleanup
        toDisable.forEach { it.disable() }
    }

    /**
     * Handle draw mode click
     */
    fun handleDrawClick(modeName: String, point: LatLng) {
        val key = modeKey(ModeType.DRAW, modeName)
        val action = actionInstances[key] as? BaseDraw
        action?.onMapClick(point)
    }

    /**
     * Handle draw mode long press
     */
    fun handleDrawLongPress(modeName: String, point: LatLng) {
        val key = modeKey(ModeType.DRAW, modeName)
        val action = actionInstances[key] as? BaseDraw
        action?.onMapLongClick(point)
    }

    /**
     * Start editing a specific feature directly (bypasses click selection)
     */
    fun startEditingFeature(feature: FeatureData) {
        val key = modeKey(ModeType.EDIT, EditModeName.CHANGE.name)
        val action = actionInstances[key] as? ChangeEditor
        action?.startEditingFeature(feature)
            ?: GeomanLogger.w(TAG, "ChangeEditor not enabled for startEditingFeature")
    }

    /**
     * Handle edit mode click
     */
    fun handleEditClick(modeName: String, point: LatLng) {
        val key = modeKey(ModeType.EDIT, modeName)
        val action = actionInstances[key] as? BaseEdit
        action?.onMapClick(point)
    }

    /**
     * Handle edit mode touch events (currently used by DragEditor to prevent the
     * map from panning while a drag handle is being moved)
     */
    fun handleEditTouch(modeName: String, event: android.view.MotionEvent): Boolean {
        val key = modeKey(ModeType.EDIT, modeName)
        val action = actionInstances[key] as? DragEditor
        return action?.onTouchEvent(event) ?: false
    }

    /**
     * Handle helper mode click
     */
    fun handleHelperClick(modeName: String, point: LatLng) {
        val key = modeKey(ModeType.HELPER, modeName)
        (actionInstances[key] as? BaseHelper)?.onMapClick(point)
    }

    /**
     * Add a GeoJSON feature
     */
    fun addGeoJsonFeature(feature: Feature, sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS): FeatureData =
        features.addGeoJsonFeature(feature, sourceName)

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
    fun getFeature(sourceName: String, featureId: String): FeatureData? = features.getFeature(sourceName, featureId)

    /**
     * Get all features
     */
    fun getAllFeatures(): Map<String, Map<String, FeatureData>> = features.getAllFeatures()

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
            withTimeoutOrNull(GEOMAN_LOADED_TIMEOUT_MS) {
                _loaded.first { it }
                this@Geoman
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Destroy the Geoman instance and clean up resources
     */
    fun destroy() {
        if (_destroyed.value) return
        _destroyed.value = true

        pendingBaseMapWait?.cancel()
        disableAllModes()

        if (options.settings.useControlsUi) {
            mapAdapter.removeControl(control)
        }

        (_mapAdapter as? MapLibreAdapter)?.cleanup()

        events.tryEmit(GmMapEvent.Destroyed)
        events.removeAllListeners()

        scope.cancel()
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
