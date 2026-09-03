package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LngLat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * Shared multi-click lifecycle for path drawing modes (polyline/polygon):
 * every click appends a vertex and updates a stable in-progress feature; a
 * long press finishes and fires Create.
 *
 * Subclasses only describe what makes them distinct: target source, shape
 * property, ID prefix, the minimum clicks required to start rendering
 * ([minRenderPoints]) and to finish ([minFinishPoints]), and how the
 * accumulated vertices become a geometry.
 */
abstract class BasePathDrawer(geoman: Geoman) : BaseDraw(geoman) {

    /** Source the finished path feature is added to. */
    protected abstract val sourceName: String

    /** Value stored under the "shapeType" property. */
    protected abstract val shapeType: String

    /** Prefix for generated feature IDs. */
    protected abstract val idPrefix: String

    /** Minimum accumulated clicks before the mode may finish. */
    protected abstract val minFinishPoints: Int

    /** Minimum accumulated clicks before an in-progress feature is created/updated. */
    protected abstract val minRenderPoints: Int

    /** Build the final geometry from the accumulated vertices. */
    protected abstract fun buildGeometry(coordinates: List<LngLat>): Geometry

    // Guarded by `this` — accessed from map click handlers (main thread)
    // and disable() (any thread via ModeController).
    private val coordinates = mutableListOf<LngLat>()
    private var currentFeature: FeatureData? = null

    override fun onMapClick(point: LatLng): Unit = synchronized(this) {
        if (!enabled) return

        coordinates.add(LngLat(point.longitude, point.latitude))

        // Update or create the feature (kept stable across clicks)
        updateFeature()
    }

    override fun onMapLongClick(point: LatLng): Unit = synchronized(this) {
        if (!enabled || coordinates.size < minFinishPoints) return

        finishDrawing()
    }

    override fun finishDrawing(): Unit = synchronized(this) {
        if (coordinates.size >= minFinishPoints && currentFeature != null) {
            // Capture the feature before launching coroutine to avoid race condition
            val featureToFire = currentFeature
            geoman.scope.launch {
                fireCreateEvent(featureToFire)
            }
        }

        coordinates.clear()
        currentFeature = null
        geoman.disableMode(modeType, modeName)
    }

    override fun disable() {
        // Snapshot-then-clear under the lock to avoid races with click handlers
        synchronized(this) {
            currentFeature?.let {
                geoman.features.removeFeature(sourceName, it.id)
            }
            currentFeature = null
            coordinates.clear()
        }
        super.disable()
    }

    private fun updateFeature() {
        if (coordinates.size < minRenderPoints) return

        val geometry = buildGeometry(coordinates)
        val existing = currentFeature

        if (existing != null) {
            val updated = existing.copy(feature = existing.feature.copy(geometry = geometry))
            geoman.features.updateFeature(sourceName, existing.id) { updated }
            currentFeature = updated
        } else {
            val featureId = createFeatureId(idPrefix)
            val feature = Feature(
                id = featureId,
                geometry = geometry,
                properties = mapOf(
                    GeomanCoreConstants.FEATURE_ID_PROPERTY to featureId,
                    "shapeType" to shapeType,
                ),
            )
            currentFeature = geoman.features.addGeoJsonFeature(feature, sourceName)
        }
    }
}
