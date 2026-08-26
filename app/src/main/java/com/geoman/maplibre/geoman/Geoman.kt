package com.geoman.maplibre.geoman

import android.content.Context
import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.MapLibreAdapter
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.history.GeometryChange
import com.geoman.maplibre.geoman.core.history.SplitChange
import com.geoman.maplibre.geoman.core.io.GeoJsonCodec
import com.geoman.maplibre.geoman.core.io.ImportResult
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.modes.draw.BaseDraw
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.modes.edit.ChangeEditor
import com.geoman.maplibre.geoman.modes.edit.DragEditor
import com.geoman.maplibre.geoman.modes.helpers.BaseHelper
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.events.GmFeatureEvent
import com.geoman.maplibre.geoman.types.events.GmHelperEvent
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import com.geoman.maplibre.geoman.types.events.GmModeEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Main Geoman class for MapLibre Android
 *
 * Provides drawing, editing, and helper functionality for geographic features.
 *
 * @param mapView The MapView instance
 * @param map The MapLibreMap instance
 * @param options Initial configuration options
 */
class Geoman(internal val mapView: MapView, private val map: MapLibreMap, options: GmOptionsData = GmOptionsData()) :
    GeomanApi {

    private companion object {
        const val TAG = "Geoman"
        const val MODE_KEY_DELIMITER = "__"
        const val STYLE_LOAD_TIMEOUT_MS = 10_000L
        const val GEOMAN_LOADED_TIMEOUT_MS = 5_000L
    }

    // Core components
    override val options: GmOptions = GmOptions(options)
    override val features: Features = Features(this)
    override val events: GmEventBus = GmEventBus()
    override val history: ChangeTracker = ChangeTracker()

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
    private val actionInstances = ConcurrentHashMap<String, BaseAction>()

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
        if (throwable is CancellationException) throw throwable
        GeomanLogger.e(TAG, "Uncaught coroutine exception", throwable as? Exception ?: Exception(throwable))
    }
    override val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    /** Adapter-backed slice used by edit modes; safe to call after init. */
    override val mapActions: EditorMapActions = object : EditorMapActions {
        override fun project(lngLat: LngLat): ScreenPoint = mapAdapter.project(lngLat)

        override fun queryFeaturesByScreenCoordinates(
            point: ScreenPoint,
            sourceNames: List<String>,
        ): List<FeatureData> = mapAdapter.queryFeaturesByScreenCoordinates(point, sourceNames)

        override fun createDomMarker(options: DomMarkerOptions, position: LngLat): DomMarker =
            mapAdapter.createDomMarker(options, position)

        override fun getContext(): android.content.Context = mapView.context
    }

    // Pending base map wait
    private var pendingBaseMapWait: Job? = null

    init {
        createAdapterAndControl()
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
     * Create the map adapter and control, then wait for the base map style.
     */
    private fun createAdapterAndControl() {
        _mapAdapter = MapLibreAdapter(map, this, mapView)
        _control = GmControl(this)
        waitForBaseMap()
    }

    /**
     * Wait for the base map style to be loaded
     */
    private fun waitForBaseMap() {
        if (mapAdapter.isLoaded()) {
            onBaseMapReady()
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
                onBaseMapReady()
            }
        }
    }

    /**
     * Wire up features and controls once the base map style is ready.
     */
    private fun onBaseMapReady() {
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
            val markerBitmap = BitmapFactory.decodeResource(
                context.resources,
                android.R.drawable.ic_menu_mylocation,
            )
            mapAdapter.loadImage("default-marker", markerBitmap)
        } catch (e: IllegalArgumentException) {
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

                // A one-shot action may have disabled itself during enable()
                // (e.g. ZoomToFitHelper). In that case disableMode() already
                // cleaned up bookkeeping, so only refresh it when the action
                // is still registered.
                if (actionInstances[key] === it) {
                    _control?.activeModes?.removeAll { active -> active.first == type }
                    _control?.activeModes?.add(type to name)

                    options.enableMode(type, name)
                    _activeModesFlow.value = getEnabledModes()
                }
            }
        }

        // Fire event outside the lock to avoid holding it during coroutine dispatch
        when (actionInstances[key]) {
            null -> GeomanLogger.d(TAG, "Mode $type.$name disabled itself during enable()")

            else -> scope.launch {
                events.emit(GmModeEvent.Enable(name, type.name))
            }
        }
    }

    /**
     * Disable a mode.
     */
    override fun disableMode(type: ModeType, name: String) {
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
     * Toggle a mode. The enabled check and the enable/disable act happen under
     * one lock so concurrent calls cannot both observe the same prior state
     * (monitor locks are reentrant, so nesting with [enableMode] is safe).
     */
    fun toggleMode(type: ModeType, name: String): Boolean = synchronized(this) {
        val key = modeKey(type, name)
        if (actionInstances.containsKey(key)) {
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
    fun handleEditTouch(modeName: String, event: MotionEvent): Boolean {
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
     * Export every stored feature as a pretty-printed GeoJSON FeatureCollection.
     */
    fun exportGeoJson(): String = GeoJsonCodec.encodeFeatureCollection(
        features.getAllFeatures().values.flatMap {
            it.values
        },
    )

    /**
     * Import a GeoJSON FeatureCollection (or single Feature) document.
     *
     * Structurally valid features are added to [sourceName]; invalid ones are
     * reported per index in the returned [ImportResult] without aborting the
     * rest of the batch.
     */
    fun importGeoJson(json: String, sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS): ImportResult {
        val result = GeoJsonCodec.decode(json, sourceName)
        result.features.forEach { featureData ->
            features.addGeoJsonFeature(featureData.feature, sourceName)
        }
        return result
    }

    /**
     * Undo the most recent edit (geometry change or structural split).
     * Returns true when a change was restored.
     */
    fun undo(): Boolean {
        val entry = history.undo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.before)

            is SplitChange -> {
                // Restore the pre-cut state: drop the parts, re-add the original
                entry.parts.forEach { part ->
                    val partId = part.id ?: return@forEach
                    features.removeFeature(entry.sourceName, partId)
                }
                features.addGeoJsonFeature(entry.original, entry.sourceName)
            }
        }
        return true
    }

    /**
     * Re-apply the most recently undone edit. Returns true when a change was
     * restored.
     */
    fun redo(): Boolean {
        val entry = history.redo() ?: return false
        when (entry) {
            is GeometryChange -> applyGeometry(entry.sourceName, entry.featureId, entry.after)

            is SplitChange -> {
                // Re-apply the cut: remove the original, re-add the parts
                val originalId = entry.original.id
                if (originalId != null) {
                    features.removeFeature(entry.sourceName, originalId)
                }
                entry.parts.forEach { features.addGeoJsonFeature(it, entry.sourceName) }
            }
        }
        return true
    }

    private fun applyGeometry(sourceName: String, featureId: String, geometry: Geometry) {
        features.updateFeature(sourceName, featureId) { current ->
            current.copy(feature = current.feature.copy(geometry = geometry))
        }
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalStateException) { // SwallowedException: returns null on timeout/error
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
        features.shutdown()

        if (options.settings.useControlsUi) {
            mapAdapter.removeControl(control)
        }

        (_mapAdapter as? MapLibreAdapter)?.cleanup()

        events.tryEmit(GmMapEvent.Destroyed)
        events.removeAllListeners()

        scope.cancel()
    }
}
