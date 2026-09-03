package com.geoman.maplibre.geoman

import android.content.Context
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CoroutineScope

/**
 * The slice of map functionality edit modes need. Extracted from
 * [BaseMapAdapter] so editors can be exercised on the JVM against fakes;
 * production delegates to the real adapter.
 */
interface EditorMapActions {
    fun project(lngLat: LngLat): ScreenPoint

    fun queryFeaturesByScreenCoordinates(point: ScreenPoint, sourceNames: List<String>): List<FeatureData>

    fun createDomMarker(options: DomMarkerOptions, position: LngLat): DomMarker

    fun getContext(): Context
}

/**
 * Abstraction over [Geoman] consumed by mode classes.
 *
 * Modes depend on this interface rather than the concrete class so their
 * store/event/history interactions are testable without an Android map.
 * Map-bound operations go through [mapActions]; helpers that need the full
 * adapter still receive the concrete [Geoman].
 */
interface GeomanApi {
    val features: Features
    val events: GmEventBus
    val history: ChangeTracker
    val options: GmOptions
    val scope: CoroutineScope
    val mapActions: EditorMapActions

    /** Enable a mode. Disables other modes of the same type first. */
    fun enableMode(type: ModeType, name: String)

    /** Disable a mode. */
    fun disableMode(type: ModeType, name: String)

    /** Check whether a mode is currently enabled. */
    fun isModeEnabled(type: ModeType, name: String): Boolean
}
