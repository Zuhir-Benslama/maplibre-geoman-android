package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.GeomanApi
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Interaction tests for edit modes against a fake [GeomanApi]: no Android map,
 * real store/history/event wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorInteractionTest {

    private class FakeDomMarker(initialPosition: LngLat) : DomMarker(Any()) {
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

    private class FakeMapActions : com.geoman.maplibre.geoman.EditorMapActions {
        var queryResult: List<FeatureData> = emptyList()
        val markers = mutableListOf<FakeDomMarker>()

        override fun project(lngLat: LngLat): ScreenPoint = ScreenPoint(0f, 0f)

        override fun queryFeaturesByScreenCoordinates(
            point: ScreenPoint,
            sourceNames: List<String>,
        ): List<FeatureData> = queryResult

        override fun createDomMarker(options: DomMarkerOptions, position: LngLat): DomMarker =
            FakeDomMarker(position).also { markers.add(it) }

        override fun getContext(): android.content.Context =
            throw UnsupportedOperationException("No Android context in unit tests")
    }

    private class FakeGeoman : GeomanApi {
        override val features = Features()
        override val events = GmEventBus()
        override val history = ChangeTracker()
        override val options = GmOptions(GmOptionsData())
        override val scope = CoroutineScope(UnconfinedTestDispatcher())
        override val mapActions = FakeMapActions()

        val disabledModes = mutableListOf<Pair<ModeType, String>>()

        override fun disableMode(type: ModeType, name: String) {
            disabledModes.add(type to name)
        }
    }

    private lateinit var geoman: FakeGeoman

    /**
     * Overrides the Android-view-bound marker seams so handles are created
     * directly through the fake map actions.
     */
    private class TestableChangeEditor(geoman: GeomanApi) : ChangeEditor(geoman) {
        override fun createDraggableMarker(position: LngLat, onDrag: (LngLat) -> Unit): DomMarker = geoman.mapActions
            .createDomMarker(DomMarkerOptions(draggable = true), position)
            .also {
                it.onDrag = onDrag
                it.addToMap()
            }

        override fun createClickableMarker(position: LngLat, onClick: () -> Unit): DomMarker = geoman.mapActions
            .createDomMarker(DomMarkerOptions(), position)
            .also {
                it.onClick = onClick
                it.addToMap()
            }
    }

    @Before
    fun setUp() {
        geoman = FakeGeoman()
        GeomanLogger.delegate = object : GeomanLogger.Delegate {
            override fun d(tag: String, message: String) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        }
    }

    private fun lineData(id: String, coords: List<List<Double>>) = FeatureData(
        id = id,
        sourceName = "gm_lines",
        feature = Feature(id = id, geometry = LineString(coordinates = coords)),
    )

    // ------------------------------------------------------------------
    // DragEditor
    // ------------------------------------------------------------------

    @Test
    fun `drag frames accumulate onto current stored geometry`() {
        val original = lineData("line", listOf(listOf(0.0, 0.0), listOf(1.0, 1.0)))
        geoman.features.addFeature(original)

        val editor = DragEditor(geoman)
        editor.enable()
        geoman.mapActions.queryResult = listOf(original)

        editor.onMapClick(org.maplibre.android.geometry.LatLng(0.5, 0.5))
        assertEquals(1, geoman.mapActions.markers.size)

        val handle = geoman.mapActions.markers.single()
        handle.onDrag?.invoke(LngLat(0.2, 0.1))
        handle.onDrag?.invoke(LngLat(0.4, 0.3))

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(
            listOf(listOf(-0.1, -0.2), listOf(0.9, 0.8)),
            stored.coordinates.map { coord ->
                listOf(
                    Math.round(coord[0] * 1e9) / 1e9,
                    Math.round(coord[1] * 1e9) / 1e9,
                )
            },
        )

        // Two drag frames -> two recorded changes; undoing both restores origin
        val second = geoman.history.undo() as com.geoman.maplibre.geoman.core.history.GeometryChange
        val first = geoman.history.undo() as com.geoman.maplibre.geoman.core.history.GeometryChange
        assertEquals(
            listOf(listOf(0.0, 0.0), listOf(1.0, 1.0)),
            (first.before as LineString).coordinates,
        )
        val secondBefore = (second.before as LineString).coordinates
        assertEquals(-0.3, secondBefore[0][0], 1e-9)
        assertEquals(-0.4, secondBefore[0][1], 1e-9)
    }

    @Test
    fun `drag end fires event with refreshed feature`() {
        val original = lineData("line", listOf(listOf(0.0, 0.0), listOf(1.0, 1.0)))
        geoman.features.addFeature(original)

        var endedWith: FeatureData? = null
        geoman.events.on(GmEditEvent.DragEnd().type) {
            endedWith = (it as GmEditEvent.DragEnd).feature
        }

        val editor = DragEditor(geoman)
        editor.enable()
        geoman.mapActions.queryResult = listOf(original)
        editor.onMapClick(org.maplibre.android.geometry.LatLng(0.5, 0.5))

        geoman.mapActions.markers.single().onDragEnd?.invoke()

        assertEquals("line", endedWith?.id)
    }

    @Test
    fun `click without a hit does not create a drag handle`() {
        val editor = DragEditor(geoman)
        editor.enable()
        geoman.mapActions.queryResult = emptyList()

        editor.onMapClick(org.maplibre.android.geometry.LatLng(0.5, 0.5))

        assertTrue(geoman.mapActions.markers.isEmpty())
    }

    // ------------------------------------------------------------------
    // ChangeEditor
    // ------------------------------------------------------------------

    @Test
    fun `editing a line creates vertex and midpoint handles`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(2.0, 0.0), listOf(4.0, 0.0)))
        geoman.features.addFeature(line)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(line)

        // 3 vertices + 2 segment midpoints
        assertEquals(5, geoman.mapActions.markers.size)
        assertTrue(geoman.mapActions.markers.all { it.added })
    }

    @Test
    fun `dragging a vertex marker moves that vertex in the store`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(2.0, 0.0)))
        geoman.features.addFeature(line)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(line)

        geoman.mapActions.markers[1].onDrag?.invoke(LngLat(5.0, 6.0))

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(listOf(5.0, 6.0), stored.coordinates[1])
        assertTrue(geoman.history.canUndo)
    }

    @Test
    fun `moving polygon vertex zero keeps the ring closed`() {
        val square = FeatureData(
            id = "square",
            sourceName = "gm_polygons",
            feature = Feature(
                id = "square",
                geometry = Polygon(
                    coordinates = listOf(
                        listOf(
                            listOf(0.0, 0.0),
                            listOf(4.0, 0.0),
                            listOf(4.0, 4.0),
                            listOf(0.0, 4.0),
                            listOf(0.0, 0.0),
                        ),
                    ),
                ),
            ),
        )
        geoman.features.addFeature(square)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(square)

        // Vertex handles exclude the closing coordinate: 4 vertices + 4 midpoints
        assertEquals(8, geoman.mapActions.markers.size)
        geoman.mapActions.markers[0].onDrag?.invoke(LngLat(1.0, 1.0))

        val ring = (geoman.features.getFeature("gm_polygons", "square")!!.geometry as Polygon).coordinates[0]
        assertEquals(listOf(1.0, 1.0), ring.first())
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun `clicking a midpoint inserts a vertex at that segment`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(2.0, 0.0)))
        geoman.features.addFeature(line)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(line)

        // Marker layout: vertex0, vertex1, midpoint0
        geoman.mapActions.markers[2].onClick?.invoke()

        val stored = geoman.features.getFeature("gm_lines", "line")!!.geometry as LineString
        assertEquals(3, stored.coordinates.size)
        assertEquals(listOf(1.0, 0.0), stored.coordinates[1])
        // Handles were recreated after insertion: initial 3 + new 3 vertices + 2 midpoints
        assertEquals(8, geoman.mapActions.markers.size)
    }

    @Test
    fun `shapeMarkersEnabled false suppresses midpoint handles`() {
        geoman.options.update { copy(helperOptions = helperOptions.copy(shapeMarkersEnabled = false)) }
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(2.0, 0.0)))
        geoman.features.addFeature(line)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(line)

        assertEquals(2, geoman.mapActions.markers.size)
    }

    @Test
    fun `disable removes all handles`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(2.0, 0.0)))
        geoman.features.addFeature(line)

        val editor = TestableChangeEditor(geoman)
        editor.enable()
        editor.startEditingFeature(line)

        editor.disable()

        assertTrue(geoman.mapActions.markers.all { it.removed })
    }
}
