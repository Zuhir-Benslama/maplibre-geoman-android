package com.geoman.maplibre.geoman

/**
 * Logger interface for the Geoman library.
 *
 * Consumers set [delegate] to route logs through their app's logging framework
 * (e.g. Timber, NarsLogger). Falls back to [android.util.Log] if no delegate is set.
 */
object GeomanLogger {
    interface Delegate {
        fun d(tag: String, message: String)
        fun e(tag: String, message: String, throwable: Throwable? = null)
        fun w(tag: String, message: String, throwable: Throwable? = null)
    }

    private object DefaultDelegate : Delegate {
        override fun d(tag: String, message: String) {
            android.util.Log.d(tag, message)
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }

        override fun w(tag: String, message: String, throwable: Throwable?) {
            if (throwable != null) {
                android.util.Log.w(tag, message, throwable)
            } else {
                android.util.Log.w(tag, message)
            }
        }
    }

    var delegate: Delegate = DefaultDelegate

    fun d(tag: String, message: String) = delegate.d(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = delegate.e(tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = delegate.w(tag, message, throwable)
}
