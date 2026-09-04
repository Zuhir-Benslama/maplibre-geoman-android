package com.geoman.maplibre.geoman

import android.view.MotionEvent
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.MapLibreAdapter
import com.geoman.maplibre.geoman.adapter.MapLibreDomMarker
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.io.GeoJsonCodec
import com.geoman.maplibre.geoman.core.io.ImportResult
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Main Geoman class for MapLibre Android
 *
 * Provides drawing, editing, and helper functionality for geographic features.
 * This type is a thin facade that owns the core components ([features],
 * [events], [history], [options]) and coordinates three cohesive collaborators:
 *
 *  - [ModeController]  — mode enable/disable bookkeeping and interaction dispatch
 *  - [HistoryController] — undo/redo replay over the history stack
 *  - [MapLifecycleController] — base map style-load lifecycle and loaded state
 *
 * The function count reflects this class's role as the library's public API
 * root: every member is a mandated entry point or [GeomanApi] override that
 * callers invoke as `geoman.<method>`. Implementation complexity has been
 * extracted into the collaborators above, so the remaining surface is API
 * breadth rather than god-class logic.
 *
 * @param mapView The MapView instance
 * @param map The MapLibreMap instance
 * @param options Initial configuration options
 */
@Suppress("TooManyFunctions")
class Geoman(internal val mapView: MapView, private val map: MapLibreMap, options: GmOptionsData = GmOptionsData()) :
    GeomanApi {

    private companion object {
        const val TAG = "Geoman"
        const val GEOMAN_LOADED_TIMEOUT_MS = 5_000L
    }

    // Core components
    override val options: GmOptions = GmOptions(options)

    // Coroutine scope with exception handler to prevent silent coroutine failures.
    // Declared before [features] so the feature store's debounced map syncs run
    // on the main thread (MapLibre style mutations are main-thread-only).
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) throw throwable
        GeomanLogger.e(TAG, "Uncaught coroutine exception", throwable as? Exception ?: Exception(throwable))
    }
    override val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    override val features: Features = Features(this, updateScope = scope)
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

    // State
    private val _destroyed = MutableStateFlow(false)
    val destroyed: Boolean get() = _destroyed.value

    // Delegated collaboration
    private val modeController = ModeController(
        geoman = this,
        options = this.options,
        events = events,
        scope = scope,
        control = { _control },
        isDestroyed = { _destroyed.value },
    )
    private val historyController = HistoryController(features, history)
    private val mapLifecycle = MapLifecycleController(
        mapView = mapView,
        mapAdapter = { mapAdapter },
        control = { _control },
        features = features,
        events = events,
        options = this.options,
        scope = scope,
        isDestroyed = { _destroyed.value },
    )

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

    // Single source of truth for the set of currently enabled modes
    val activeModesFlow: StateFlow<List<ModeKey>> = modeController.activeModesFlow

    val loaded: Boolean get() = mapLifecycle.loaded
    val loadedFlow: StateFlow<Boolean> = mapLifecycle.loadedFlow

    init {
        _mapAdapter = MapLibreAdapter(map, this, mapView)
        _control = GmControl(this)
        mapLifecycle.waitForBaseMap()
    }

    /**
     * Enable a mode. Disables other modes of the same type first.
     */
    override fun enableMode(type: ModeType, name: String) {
        modeController.enableMode(type, name)
    }

    /**
     * Disable a mode.
     */
    override fun disableMode(type: ModeType, name: String) {
        modeController.disableMode(type, name)
    }

    /**
     * Toggle a mode.
     */
    fun toggleMode(type: ModeType, name: String): Boolean = modeController.toggleMode(type, name)

    /**
     * Check if a mode is enabled
     */
    override fun isModeEnabled(type: ModeType, name: String): Boolean = modeController.isModeEnabled(type, name)

    /**
     * Get all enabled modes
     */
    fun getEnabledModes(): List<ModeKey> = modeController.getEnabledModes()

    /**
     * Disable all modes
     */
    fun disableAllModes() {
        modeController.disableAllModes()
    }

    /**
     * Handle draw mode click
     */
    fun handleDrawClick(modeName: String, point: LatLng) {
        modeController.handleDrawClick(modeName, point)
    }

    /**
     * Handle draw mode long press
     */
    fun handleDrawLongPress(modeName: String, point: LatLng) {
        modeController.handleDrawLongPress(modeName, point)
    }

    /**
     * Start editing a specific feature directly (bypasses click selection)
     */
    fun startEditingFeature(feature: FeatureData) {
        modeController.startEditingFeature(feature)
    }

    /**
     * Handle edit mode click
     */
    fun handleEditClick(modeName: String, point: LatLng) {
        modeController.handleEditClick(modeName, point)
    }

    /**
     * Handle edit mode touch events (currently used by DragEditor to prevent the
     * map from panning while a drag handle is being moved)
     */
    fun handleEditTouch(modeName: String, event: MotionEvent): Boolean = modeController.handleEditTouch(modeName, event)

    /**
     * Handle helper mode click
     */
    fun handleHelperClick(modeName: String, point: LatLng) {
        modeController.handleHelperClick(modeName, point)
    }

    /**
     * Add a GeoJSON feature
     */
    fun addGeoJsonFeature(feature: Feature, sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS): FeatureData =
        features.addGeoJsonFeature(feature, sourceName)

    /**
     * Add a GeoJSON feature collection.
     *
     * Each feature is added to [sourceName]; features missing an ID get one
     * generated before validation.
     *
     * @throws IllegalArgumentException if any feature is structurally invalid.
     */
    fun addFeatureCollection(collection: FeatureCollection, sourceName: String = GeomanCoreConstants.SOURCE_POLYGONS) {
        collection.features.forEach { feature ->
            addGeoJsonFeature(feature, sourceName)
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
    fun undo(): Boolean = historyController.undo()

    /**
     * Re-apply the most recently undone edit. Returns true when a change was
     * restored.
     */
    fun redo(): Boolean = historyController.redo()

    /**
     * Restore rendering after the base map style has been replaced.
     */
    fun onStyleReloaded() {
        mapLifecycle.onStyleReloaded()
    }

    /**
     * Wait for Geoman to be loaded
     *
     * @return this once the map is loaded, or null if destroyed or the load
     *   exceeds [GEOMAN_LOADED_TIMEOUT_MS] (withTimeoutOrNull returns null).
     */
    @Suppress("RethrowCaughtException") // Cancelling a suspended wait must propagate, not return null.
    suspend fun waitForGeomanLoaded(): Geoman? = try {
        if (loaded) {
            this
        } else if (destroyed) {
            null
        } else {
            withTimeoutOrNull(GEOMAN_LOADED_TIMEOUT_MS) {
                loadedFlow.first { it }
                this@Geoman
            }
        }
    } catch (e: CancellationException) {
        throw e
    }

    /**
     * Destroy the Geoman instance and clean up resources
     */
    fun destroy() {
        if (destroyed) return
        _destroyed.value = true

        mapLifecycle.cancelPendingBaseMapWait()
        disableAllModes()
        // Drop scheduled debounced syncs instead of forcing the last coalesced
        // update onto a map that is about to be torn down.
        features.discardPendingUpdates()
        features.shutdown()

        if (options.settings.useControlsUi) {
            mapAdapter.removeControl(control)
        }

        (_mapAdapter as? MapLibreAdapter)?.cleanup()
        MapLibreDomMarker.cleanupForMap(map)

        events.tryEmit(GmMapEvent.Destroyed)
        events.removeAllListeners()

        scope.cancel()
    }
}
