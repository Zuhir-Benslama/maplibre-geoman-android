package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.adapter.MapLibreSource
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Source Update Manager
 * Handles automatic synchronization of GeoJSON sources with the map
 * Similar to the web version's source-update-manager.ts
 */
class SourceUpdateManager(private val geoman: Geoman) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val pendingUpdates = java.util.concurrent.ConcurrentHashMap<String, UpdateJob>()
    private val updateDelays = mapOf(
        "high" to 0L,
        "normal" to 50L,
        "low" to 200L,
    )

    /**
     * Update job data class
     */
    data class UpdateJob(
        val sourceId: String,
        val featureCollection: FeatureCollection,
        val priority: String = "normal",
        var job: Job? = null,
    )

    /**
     * Schedule a source update with debouncing
     */
    fun scheduleUpdate(sourceId: String, features: List<Feature>, priority: String = "normal") {
        val featureCollection = FeatureCollection(features = features)

        // Cancel existing update for this source
        pendingUpdates[sourceId]?.job?.cancel()

        // Schedule new update
        val delayMs = updateDelays[priority] ?: updateDelays["normal"]!!
        val job = scope.launch {
            delay(delayMs)
            executeUpdate(sourceId, featureCollection)
            pendingUpdates.remove(sourceId)
        }

        pendingUpdates[sourceId] = UpdateJob(sourceId, featureCollection, priority, job)
    }

    /**
     * Execute source update immediately
     */
    fun executeUpdate(sourceId: String, featureCollection: FeatureCollection) {
        try {
            // Get or create the source through the adapter
            val source = geoman.mapAdapter.getSource(sourceId)
            if (source is MapLibreSource) {
                source.setData(featureCollection)
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Failed to update source $sourceId: ${e.message}")
        }
    }

    /**
     * Execute source update with GeoJSON string
     */
    fun executeUpdate(sourceId: String, geoJsonString: String) {
        try {
            val featureCollection = json.decodeFromString<FeatureCollection>(geoJsonString)
            executeUpdate(sourceId, featureCollection)
        } catch (e: Exception) {
            android.util.Log.e("SourceUpdateManager", "Failed to update source $sourceId: ${e.message}")
        }
    }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }

    /**
     * Flush all pending updates immediately
     */
    fun flushAll() {
        pendingUpdates.values.forEach { update ->
            update.job?.cancel()
            executeUpdate(update.sourceId, update.featureCollection)
        }
        pendingUpdates.clear()
    }

    /**
     * Cancel all pending updates
     */
    fun cancelAll() {
        pendingUpdates.values.forEach { it.job?.cancel() }
        pendingUpdates.clear()
    }

    /**
     * Get pending update count
     */
    fun getPendingCount(): Int = pendingUpdates.size

    /**
     * Check if source has pending update
     */
    fun hasPendingUpdate(sourceId: String): Boolean = pendingUpdates.containsKey(sourceId)

    /**
     * Destroy the manager and cancel all updates
     */
    fun destroy() {
        cancelAll()
    }
}
