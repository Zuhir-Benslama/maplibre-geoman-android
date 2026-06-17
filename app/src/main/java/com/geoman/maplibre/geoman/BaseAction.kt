package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.types.ModeType

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
