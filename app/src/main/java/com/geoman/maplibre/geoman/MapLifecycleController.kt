package com.geoman.maplibre.geoman

import android.graphics.BitmapFactory
import com.geoman.maplibre.geoman.adapter.BaseMapAdapter
import com.geoman.maplibre.geoman.adapter.MapLibreAdapter
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Owns the base-map style-load lifecycle for [Geoman].
 *
 * Waits for the map style, wires up features and controls once it arrives,
 * loads the default marker image, tracks the [loaded] state exposed to
 * [Geoman.waitForGeomanLoaded], and restores rendering after a style swap
 * ([onStyleReloaded]).
 */
class MapLifecycleController(
    private val mapView: MapView,
    private val mapAdapter: () -> BaseMapAdapter<MapLibreMap>,
    private val control: () -> GmControl?,
    private val features: Features,
    private val events: GmEventBus,
    private val options: GmOptions,
    private val scope: CoroutineScope,
    private val isDestroyed: () -> Boolean = { false },
) {
    private companion object {
        const val TAG = "Geoman"
        const val STYLE_LOAD_TIMEOUT_MS = 10_000L
    }

    private val _loaded = MutableStateFlow(false)
    val loaded: Boolean get() = _loaded.value
    val loadedFlow: StateFlow<Boolean> = _loaded

    // Pending base map wait
    private var pendingBaseMapWait: Job? = null

    /**
     * Wait for the base map style to be loaded
     */
    fun waitForBaseMap() {
        if (mapAdapter().isLoaded()) {
            onBaseMapReady()
            return
        }

        pendingBaseMapWait = scope.launch {
            withTimeoutOrNull(STYLE_LOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    // lateinit because the listener unregisters itself; the variable
                    // is always assigned before the listener can possibly fire.
                    lateinit var listener: MapView.OnDidFinishLoadingStyleListener
                    listener = MapView.OnDidFinishLoadingStyleListener {
                        if (continuation.isActive) {
                            // Remove on the happy path too: invokeOnCancellation only
                            // runs on cancellation, so leaving the listener registered
                            // after a successful style load would keep a strong
                            // reference to the owning Geoman instance (via the coroutine
                            // frame) for the lifetime of the MapView.
                            mapView.removeOnDidFinishLoadingStyleListener(listener)
                            continuation.resumeWith(Result.success(Unit))
                        }
                    }
                    mapView.addOnDidFinishLoadingStyleListener(listener)
                    continuation.invokeOnCancellation {
                        mapView.removeOnDidFinishLoadingStyleListener(listener)
                    }
                }
            }

            if (!isDestroyed()) {
                onBaseMapReady()
            }
        }
    }

    /**
     * Wire up features and controls once the base map style is ready.
     */
    private fun onBaseMapReady() {
        if (isDestroyed()) return

        features.init(mapAdapter())

        scope.launch {
            addControls()
        }
    }

    /**
     * Add controls to the map
     */
    private suspend fun addControls() {
        if (options.settings.useControlsUi) {
            control()?.let {
                mapAdapter().addControl(it)
            }
        }
        onMapLoad()
    }

    /**
     * Handle map load event
     */
    private suspend fun onMapLoad() {
        if (_loaded.value || isDestroyed()) return

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
            mapAdapter().loadImage("default-marker", markerBitmap)
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
        if (isDestroyed()) return
        (mapAdapter() as? MapLibreAdapter)?.clearRenderingCache()
        features.reSyncAll()
        scope.launch {
            loadMarkerImage()
        }
    }

    /**
     * Cancel a still-pending base map style wait during teardown.
     */
    fun cancelPendingBaseMapWait() {
        pendingBaseMapWait?.cancel()
    }
}
