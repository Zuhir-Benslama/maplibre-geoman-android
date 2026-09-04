package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.utils.generateFeatureId

abstract class BaseAction(protected open val geoman: GeomanApi) {
    @Volatile protected var enabled = false

    abstract val modeName: String
    abstract val modeType: ModeType

    open fun enable() {
        enabled = true
        GeomanLogger.d("BaseAction", "enable() called for $modeName, now enabled=$enabled")
    }

    open fun disable() {
        GeomanLogger.d("BaseAction", "disable() called for $modeName, was enabled=$enabled")
        enabled = false
    }

    open fun isEnabled(): Boolean = enabled

    /**
     * Generate a collision-free feature ID. Timestamp-based IDs collided when
     * two features were created within the same millisecond.
     */
    protected fun createFeatureId(prefix: String): String = generateFeatureId(prefix)
}
