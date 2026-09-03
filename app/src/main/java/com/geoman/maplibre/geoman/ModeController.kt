package com.geoman.maplibre.geoman

import android.view.MotionEvent
import com.geoman.maplibre.geoman.core.controls.GmControl
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.modes.draw.BaseDraw
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.modes.edit.ChangeEditor
import com.geoman.maplibre.geoman.modes.edit.DragEditor
import com.geoman.maplibre.geoman.modes.helpers.BaseHelper
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmModeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the lifecycle of enabled modes for [Geoman].
 *
 * Holds the action instance registry, the [activeModesFlow], and all
 * enable/disable/toggle bookkeeping together with the map interaction
 * dispatch that reads from the currently enabled actions. [Geoman] delegates
 * its public mode API here.
 *
 * Mode switching is synchronized on this instance to prevent races between
 * concurrent calls (e.g. rapid UI taps); [toggleMode] nests inside the same
 * monitor as [enableMode]/[disableMode] (reentrant).
 */
class ModeController(
    geoman: Geoman,
    private val options: GmOptions,
    private val events: GmEventBus,
    private val scope: CoroutineScope,
    private val control: () -> GmControl?,
    private val isDestroyed: () -> Boolean,
) {
    private companion object {
        const val TAG = "Geoman"
    }

    // Mode factory
    private val modeFactory = ModeFactory(geoman)

    // Action instances (modes) — guarded by `this` lock for atomic mode switching.
    // Keyed by a typed (ModeType, name) pair so mode names need not be
    // restricted to delimiter-free strings.
    private val actionInstances = ConcurrentHashMap<Pair<ModeType, String>, BaseAction>()

    // Single source of truth for the set of currently enabled modes
    private val _activeModesFlow = MutableStateFlow<List<Pair<ModeType, String>>>(emptyList())
    val activeModesFlow: StateFlow<List<Pair<ModeType, String>>> = _activeModesFlow.asStateFlow()

    /**
     * Build a stable map key for an action instance.
     */
    private fun modeKey(type: ModeType, name: String): Pair<ModeType, String> = type to name

    /**
     * Enable a mode. Disables other modes of the same type first.
     */
    fun enableMode(type: ModeType, name: String) {
        if (isDestroyed()) return

        val key = modeKey(type, name)

        synchronized(this) {
            // Disable other modes of the same type
            val keysToDisable = actionInstances.keys.filter {
                it.first == type && it != key
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
                    control()?.activeModes?.removeAll { active -> active.first == type }
                    control()?.activeModes?.add(type to name)

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
    fun disableMode(type: ModeType, name: String) {
        val key = modeKey(type, name)

        val action = synchronized(this) {
            actionInstances.remove(key)?.also {
                it.disable()
                control()?.activeModes?.remove(type to name)
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
    fun getEnabledModes(): List<Pair<ModeType, String>> = actionInstances.keys.map { it }

    /**
     * Disable all modes
     */
    fun disableAllModes() {
        val toDisable: List<BaseAction>
        synchronized(this) {
            toDisable = actionInstances.values.toList()
            actionInstances.clear()
            control()?.activeModes?.clear()
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
}
