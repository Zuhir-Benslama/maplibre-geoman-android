package com.geoman.maplibre.geoman

import android.view.MotionEvent
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.modes.draw.BaseDraw
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.modes.edit.ChangeEditor
import com.geoman.maplibre.geoman.modes.edit.DragEditor
import com.geoman.maplibre.geoman.modes.helpers.BaseHelper
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmModeEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the lifecycle of enabled modes for [Geoman].
 *
 * Holds the action instance registry, the [activeModesFlow], and all
 * enable/disable/toggle bookkeeping together with the map interaction
 * dispatch that reads from the currently enabled actions. [Geoman] delegates
 * its public mode API here.
 *
 * The action registry ([actionInstances]) is the single source of truth for
 * which modes are enabled; the [activeModesFlow] is derived from it and every
 * other consumer reads through us. There is no separate mirror (e.g. in
 * GmControl or GmOptions) left to drift.
 *
 * Mode switching is synchronized on this instance to prevent races between
 * concurrent calls (e.g. rapid UI taps); [toggleMode] nests inside the same
 * monitor as [enableMode]/[disableMode] (reentrant).
 */
class ModeController(
    private val modeFactory: ModeActionFactory,
    private val events: GmEventBus,
    private val scope: CoroutineScope,
    private val isDestroyed: () -> Boolean,
) {
    private companion object {
        const val TAG = "Geoman"
    }

    // Action instances (modes) — guarded by `this` lock for atomic mode switching.
    // Keyed by a typed (ModeType, name) pair so mode names need not be
    // restricted to delimiter-free strings.
    private val actionInstances = ConcurrentHashMap<ModeKey, BaseAction>()

    // Derived from actionInstances whenever it changes; readers of the UI can
    // observe mode state without touching the map.
    private val _activeModesFlow = MutableStateFlow<List<ModeKey>>(emptyList())
    val activeModesFlow: StateFlow<List<ModeKey>> = _activeModesFlow.asStateFlow()

    /**
     * Build a stable map key for an action instance.
     */
    private fun modeKey(type: ModeType, name: String): ModeKey = ModeKey(type, name)

    /**
     * Enable a mode. Disables other modes of the same type first.
     */
    fun enableMode(type: ModeType, name: String) {
        if (isDestroyed()) return

        val key = modeKey(type, name)

        val enabled = synchronized(this) {
            // Disable other modes of the same type
            val keysToDisable = actionInstances.keys.filter {
                it.type == type && it != key
            }
            keysToDisable.forEach { k ->
                actionInstances[k]?.disable()
                actionInstances.remove(k)
            }

            // Create and enable the mode
            val action = modeFactory.create(type, name)
            action?.let {
                // A previous instance of the same key is replaced, not left
                // running: disable it so it stops consuming map events.
                actionInstances[key]?.disable()
                actionInstances[key] = it
                it.enable()

                // A one-shot action may have disabled itself during enable()
                // (e.g. ZoomToFitHelper). In that case disableMode() already
                // cleaned up bookkeeping, so only refresh the flow when the
                // action is still registered.
                if (actionInstances[key] === it) {
                    _activeModesFlow.value = getEnabledModes()
                    true
                } else {
                    false
                }
            } ?: false
        }

        // Fire the event outside the lock to avoid holding it during coroutine
        // dispatch; whether it fires is decided from the committed state above.
        if (enabled) {
            scope.launch {
                events.emit(GmModeEvent.Enable(name, type.name))
            }
        } else {
            GeomanLogger.d(TAG, "Mode $type.$name disabled itself during enable()")
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
    fun getEnabledModes(): List<ModeKey> = synchronized(this) { actionInstances.keys.toList() }

    /**
     * Disable all modes
     */
    fun disableAllModes() {
        val toDisable: List<BaseAction>
        synchronized(this) {
            toDisable = actionInstances.values.toList()
            actionInstances.clear()
            _activeModesFlow.value = emptyList()
        }
        // Disable actions outside the lock to avoid holding it during mode cleanup
        toDisable.forEach { it.disable() }
    }

    /**
     * Handle draw mode click
     */
    fun handleDrawClick(mode: DrawModeName, point: LngLat) {
        val key = modeKey(ModeType.DRAW, mode.name)
        val action = actionInstances[key] as? BaseDraw
        action?.onMapClick(point)
    }

    /**
     * Handle draw mode long press
     */
    fun handleDrawLongPress(mode: DrawModeName, point: LngLat) {
        val key = modeKey(ModeType.DRAW, mode.name)
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
    fun handleEditClick(mode: EditModeName, point: LngLat) {
        val key = modeKey(ModeType.EDIT, mode.name)
        val action = actionInstances[key] as? BaseEdit
        action?.onMapClick(point)
    }

    /**
     * Handle edit mode touch events (currently used by DragEditor to prevent the
     * map from panning while a drag handle is being moved)
     */
    fun handleEditTouch(mode: EditModeName, event: MotionEvent): Boolean {
        val key = modeKey(ModeType.EDIT, mode.name)
        val action = actionInstances[key] as? DragEditor
        return action?.onTouchEvent(event) ?: false
    }

    /**
     * Handle helper mode click
     */
    fun handleHelperClick(mode: HelperModeName, point: LngLat) {
        val key = modeKey(ModeType.HELPER, mode.name)
        (actionInstances[key] as? BaseHelper)?.onMapClick(point)
    }
}
