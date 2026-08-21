package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Debounced source updates.
 *
 * High-frequency feature changes (drag frames, vertex edits) each trigger a
 * source `setData` call; coalescing them into one update per debounce window
 * avoids flooding the map renderer. Callers schedule updates per source and
 * only the latest collection for that source is applied when the window fires.
 *
 * The actual apply step is injected as [applyUpdate], keeping this class free
 * of map dependencies and unit-testable with a virtual-time coroutine scope.
 */
class SourceUpdateManager(
    private val applyUpdate: (sourceName: String, collection: FeatureCollection) -> Unit,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 100L
    }

    private val pendingJobs = ConcurrentHashMap<String, Job>()
    private val latestCollections = ConcurrentHashMap<String, FeatureCollection>()

    /**
     * Schedule an update for [sourceName], replacing any previously scheduled
     * but not yet applied update for that source.
     */
    fun schedule(sourceName: String, collection: FeatureCollection) {
        latestCollections[sourceName] = collection
        pendingJobs.remove(sourceName)?.cancel()
        pendingJobs[sourceName] = scope.launch {
            delay(debounceMs)
            flush(sourceName)
        }
    }

    /**
     * Apply the latest scheduled update for [sourceName] immediately and cancel
     * its pending debounce job. No-op when nothing is scheduled.
     */
    fun flush(sourceName: String) {
        pendingJobs.remove(sourceName)?.cancel()
        latestCollections.remove(sourceName)?.let { applyUpdate(sourceName, it) }
    }

    /**
     * Flush every source with a pending update.
     */
    fun flushAll() {
        latestCollections.keys.toList().forEach { flush(it) }
    }

    /**
     * Cancel all pending debounced updates without applying them.
     */
    fun cancelPending() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        latestCollections.clear()
    }
}
