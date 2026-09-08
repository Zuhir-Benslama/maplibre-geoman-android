package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.EditorMapActions
import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.FitBoundsOptions
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before

/**
 * Shared fake [GeomanApi] harness for edit-mode interaction tests: no Android
 * map, real store/history/event wiring, and query seams overridable per test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class EditModeTestBase {

    class FakeDomMarker(initialPosition: LngLat) : DomMarker(Any()) {
        var position = initialPosition
        var added = false
        var removed = false

        override fun getLngLat(): LngLat = position

        override fun setLngLat(lngLat: LngLat) {
            position = lngLat
        }

        override fun getElement(): android.view.View = throw AssertionError("views are not used on the JVM")

        override fun addToMap(): DomMarker {
            added = true
            return this
        }

        override fun remove() {
            removed = true
        }

        override fun setDraggable(draggable: Boolean) = Unit

        override fun isDragging(): Boolean = false
    }

    class FakeMapActions : EditorMapActions {
        var queryResult: List<FeatureData> = emptyList()
        var fittedBounds: LatLngBounds? = null
        val markers = mutableListOf<FakeDomMarker>()

        override fun project(lngLat: LngLat): ScreenPoint = ScreenPoint(0f, 0f)

        override fun queryFeaturesByScreenCoordinates(
            point: ScreenPoint,
            sourceNames: List<String>,
        ): List<FeatureData> = queryResult

        override fun createDomMarker(options: DomMarkerOptions, position: LngLat): DomMarker =
            FakeDomMarker(position).also { markers.add(it) }

        override fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions?) {
            fittedBounds = bounds
        }

        override fun getContext(): android.content.Context =
            throw UnsupportedOperationException("No Android context in unit tests")
    }

    class FakeGeoman : GeomanApi {
        override val features = Features()
        override val events = GmEventBus()
        override val history = ChangeTracker()
        override val options = GmOptions(GmOptionsData())
        override val scope = CoroutineScope(UnconfinedTestDispatcher())
        override val mapActions = FakeMapActions()

        val disabledModes = mutableListOf<ModeKey>()
        val enabledModes = mutableListOf<ModeKey>()
        override fun enableMode(type: ModeType, name: String) {
            enabledModes.add(ModeKey(type, name))
        }

        override fun disableMode(type: ModeType, name: String) {
            disabledModes.add(ModeKey(type, name))
        }

        override fun toggleMode(type: ModeType, name: String): Boolean {
            val key = ModeKey(type, name)
            return if (enabledModes.contains(key)) {
                disabledModes.add(key)
                enabledModes.remove(key)
                false
            } else {
                enabledModes.add(key)
                true
            }
        }

        override fun isModeEnabled(type: ModeType, name: String): Boolean = enabledModes.contains(ModeKey(type, name))
    }

    protected lateinit var geoman: FakeGeoman

    @Before
    fun setUp() {
        geoman = FakeGeoman()
        GeomanLogger.delegate = object : GeomanLogger.Delegate {
            override fun d(tag: String, message: String) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        }
    }

    protected fun lineData(id: String, coords: List<List<Double>>) = FeatureData(
        id = id,
        sourceName = "gm_lines",
        feature = Feature(id = id, geometry = LineString(coordinates = coords)),
    )
}
