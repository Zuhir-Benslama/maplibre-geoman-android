package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.adapter.FeatureStoreRenderer
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.utils.generateFeatureId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Feature data class holding feature information
 */
data class FeatureData(
    val id: String,
    val sourceName: String,
    val feature: Feature,
    val properties: Map<String, Any?> = emptyMap(),
    val shape: FeatureShape? = null,
) {
    val geometry: Geometry get() = feature.geometry

    /**
     * A copy that shares no mutable collections with the original: both
     * property-map containers are recreated, so later mutation of either
     * instance's maps cannot leak into the other. Leaf values are deeply
     * copied where possible (lists and maps) to prevent mutation through
     * shared references.
     */
    fun deepCopy(): FeatureData = copy(
        feature = feature.copy(properties = feature.properties.mapValues { deepCopyValue(it.value) }),
        properties = properties.mapValues { deepCopyValue(it.value) },
    )

    private fun deepCopyValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.mapValues { deepCopyValue(it.value) }
        is List<*> -> value.map { deepCopyValue(it) }
        else -> value
    }
}

/**
 * Source names for different feature types
 */
object FeatureSources {
    const val MARKER = GeomanCoreConstants.SOURCE_MARKERS
    const val LINE = GeomanCoreConstants.SOURCE_LINES
    const val POLYGON = GeomanCoreConstants.SOURCE_POLYGONS
    const val CIRCLE = GeomanCoreConstants.SOURCE_CIRCLES
    const val RECTANGLE = GeomanCoreConstants.SOURCE_RECTANGLES
    const val CIRCLE_MARKER = GeomanCoreConstants.SOURCE_CIRCLE_MARKERS
    const val EDIT = GeomanCoreConstants.SOURCE_EDIT
    const val HELPER = GeomanCoreConstants.SOURCE_HELPER
    const val SNAP_GUIDES = "gm_snap_guides"

    /** All user-editable feature source names, used by editors for hit-testing. */
    val ALL_EDITABLE: List<String> = listOf(MARKER, CIRCLE_MARKER, LINE, POLYGON, CIRCLE, RECTANGLE)

    /** Non-marker editable sources (used by editors that exclude markers from selection). */
    val EDITABLE_WITHOUT_MARKERS: List<String> = listOf(LINE, POLYGON, CIRCLE, RECTANGLE)
}

/**
 * Features manager for handling GeoJSON features.
 *
 * Storage and parent-child relations live in an [InMemoryFeatureStore], which
 * exposes the query surface through [FeatureStore] and is delegated to here.
 * The store guards every mutation with a single monitor; map adapter calls are
 * performed *outside* that lock to avoid holding it during I/O or re-entrant
 * callbacks. The query surface ([getAllFeatures], [getFeatures] and friends) is
 * provided via interface delegation and therefore never falls out of sync with
 * the store's own snapshot semantics.
 *
 * Rendering: source creation is applied synchronously (layers must exist
 * before the next frame); subsequent updates for an existing source are
 * coalesced through [SourceUpdateManager] so drag-frame edits produce one
 * `setData` per debounce window instead of one per frame. Call
 * [flushPendingUpdates] when the final state must land immediately, and
 * [shutdown] on teardown.
 *
 * [updateScope] must be main-confined whenever a renderer is attached:
 * MapLibre style/source/layer mutations are main-thread-only. Geoman passes
 * its own `Dispatchers.Main` scope so debounced updates coalesce on the UI
 * thread; tests pass a test dispatcher and use a fake renderer.
 */
class Features(
    private val geoman: Geoman? = null,
    updateScope: CoroutineScope? = null,
    debounceMs: Long = SourceUpdateManager.DEFAULT_DEBOUNCE_MS,
    private val store: InMemoryFeatureStore = InMemoryFeatureStore(),
) : FeatureStore by store {

    private val styler = FeatureLayerStyler(geoman)

    @Volatile
    private var renderer: FeatureStoreRenderer? = null

    private val updateScope: CoroutineScope = updateScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ownsUpdateScope = updateScope == null

    private val updateManager = SourceUpdateManager(
        applyUpdate = { sourceName, collection -> applySourceUpdate(sourceName, collection) },
        scope = this.updateScope,
        debounceMs = debounceMs,
    )

    /**
     * Initialize with the renderer used for map syncs. Used by tests.
     */
    fun init(renderer: FeatureStoreRenderer?) {
        this.renderer = renderer
    }

    fun addFeature(featureData: FeatureData) {
        val sourceName = store.add(featureData)
        syncSourceToMap(sourceName)
    }

    /**
     * Add a GeoJSON feature after structural validation.
     *
     * A missing feature ID is generated before validation, so only explicitly
     * supplied invalid IDs (blank, oversized) are rejected.
     *
     * @throws IllegalArgumentException when [PropertyValidators.validateFeature]
     * reports errors (non-finite coordinates, out-of-range latitudes, unclosed
     * polygon rings, malformed IDs). The feature is not stored in that case.
     */
    fun addGeoJsonFeature(feature: Feature, sourceName: String = FeatureSources.POLYGON): FeatureData {
        val featureId = feature.id ?: generateFeatureId("feature")
        val resolved = feature.copy(id = featureId)

        val result = PropertyValidators.validateFeature(resolved)
        require(result.isValid) {
            "Invalid GeoJSON feature: ${result.errors.joinToString("; ")}"
        }

        val featureData = FeatureData(
            id = featureId,
            sourceName = sourceName,
            feature = resolved,
            properties = feature.properties.toMutableMap(),
            shape = FeatureShape.fromSourceName(sourceName),
        )
        addFeature(featureData)
        return featureData
    }

    fun updateFeature(sourceName: String, featureId: String, update: (FeatureData) -> FeatureData) {
        if (store.update(sourceName, featureId, update)) {
            syncSourceToMap(sourceName)
        }
    }

    /**
     * Remove a feature; its descendants (transitive children) are removed
     * with it. Returns the removed feature, or null when unknown.
     */
    fun removeFeature(sourceName: String, featureId: String): FeatureData? {
        val removal = store.remove(sourceName, featureId)
        (removal.sourcesToSync + sourceName).distinct().forEach { syncSourceToMap(it) }
        return removal.removed
    }

    fun clearSource(sourceName: String) {
        store.clearSource(sourceName)
        syncSourceToMap(sourceName)
    }

    fun clearAll() {
        store.clearAll().forEach { syncSourceToMap(it) }
    }

    fun reSyncAll() {
        store.allSourceNames().forEach { syncSourceToMap(it) }
    }

    /**
     * Link [childId] as a child of [parentId] (web parity: helper features
     * belonging to a shape). Pass `null` to clear the link. Both features must
     * exist; linking may not create a cycle.
     *
     * @throws IllegalArgumentException on unknown ids or cyclic links
     */
    fun setFeatureParent(childId: String, parentId: String?) {
        store.setFeatureParent(childId, parentId)
    }

    /**
     * Apply every pending debounced source update immediately.
     */
    fun flushPendingUpdates() {
        updateManager.flushAll()
    }

    /**
     * Drop every scheduled-but-not-yet-applied source update without applying
     * it. Used during teardown so a dying map is not forced through the final
     * coalesced sync.
     */
    fun discardPendingUpdates() {
        updateManager.cancelPending()
    }

    /**
     * Flush pending updates and stop the internal update scope. The store
     * remains usable for in-memory queries, but no further map syncs occur.
     */
    fun shutdown() {
        updateManager.flushAll()
        if (ownsUpdateScope) {
            updateScope.cancel()
        }
        renderer = null
    }

    private fun buildSourceCollection(sourceName: String): FeatureCollection {
        val sourceFeatures = store.getFeatures(sourceName)
        return FeatureCollection(
            features = sourceFeatures.values.map { it.feature }.toList(),
        )
    }

    private fun syncSourceToMap(sourceName: String) {
        val target = renderer ?: return
        val collection = buildSourceCollection(sourceName)

        // Source creation must be synchronous so layers are registered before
        // the next frame; existing sources get coalesced debounced updates.
        if (target.getSource(sourceName) == null) {
            applySourceUpdate(sourceName, collection)
        } else {
            updateManager.schedule(sourceName, collection)
        }
    }

    private fun applySourceUpdate(sourceName: String, collection: FeatureCollection) {
        val target = renderer ?: return

        val existingSource = target.getSource(sourceName)
        if (existingSource != null) {
            existingSource.setData(collection)
        } else {
            target.addSource(sourceName, collection)
            styler.addRenderingLayersForSource(sourceName, target)
        }
    }
}
