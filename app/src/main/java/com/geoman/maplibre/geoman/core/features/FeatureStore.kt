package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only surface of the feature store.
 *
 * Implementations guard all mutable state with a single monitor and return
 * immutable snapshots, so callers never observe a store mid-mutation. The
 * `Features` facade exposes this interface via delegation while keeping the
 * mutating operations (which must also trigger map re-rendering) on itself.
 */
interface FeatureStore {

    /** Snapshot of all sources and their features, emitted after each mutation. */
    val featuresFlow: StateFlow<Map<String, Map<String, FeatureData>>>

    /** Snapshot of every source and its features. */
    fun getAllFeatures(): Map<String, Map<String, FeatureData>>

    /** Snapshot of the features stored under [sourceName]; empty when unknown. */
    fun getFeatures(sourceName: String): Map<String, FeatureData>

    /** The stored feature, or null when unknown. */
    fun getFeature(sourceName: String, featureId: String): FeatureData?

    /**
     * Features whose geometry intersects [bounds]. Pass [sourceNames] to
     * restrict the search, or null to search every known source.
     *
     * @throws IllegalArgumentException when [bounds] is empty.
     */
    fun getFeaturesInBounds(bounds: List<LngLat>, sourceNames: List<String>? = null): List<FeatureData>

    /** The registered parent of [featureId], or null when it has none. */
    fun getParentFeatureId(featureId: String): String?

    /** Direct children of [parentId]; empty when it has none. */
    fun getChildFeatureIds(parentId: String): Set<String>

    /** All descendants of [parentId], breadth-first, excluding the parent itself. */
    fun getDescendantFeatureIds(parentId: String): Set<String>
}
