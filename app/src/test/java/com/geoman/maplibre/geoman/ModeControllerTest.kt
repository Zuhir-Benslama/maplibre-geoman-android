package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.adapter.DomMarker
import com.geoman.maplibre.geoman.adapter.DomMarkerOptions
import com.geoman.maplibre.geoman.adapter.FitBoundsOptions
import com.geoman.maplibre.geoman.core.events.GmEventBus
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.Features
import com.geoman.maplibre.geoman.core.history.ChangeTracker
import com.geoman.maplibre.geoman.core.options.GmOptions
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.modes.edit.BaseEdit
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmModeEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM tests for [ModeController] mode lifecycle bookkeeping. The factory seam
 * ([ModeActionFactory]) lets us drive enable/disable/toggle and the dispatch
 * surface with fake actions and no Android map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeControllerTest {

    private class FakeAction(api: GeomanApi, override val modeName: String, override val modeType: ModeType) :
        BaseAction(api) {
        var enableCalls = 0
        var disableCalls = 0

        override fun enable() {
            enableCalls++
            super.enable()
        }

        override fun disable() {
            disableCalls++
            super.disable()
        }
    }

    /**
     * One-shot action: disables itself while being enabled (like ZoomToFitHelper).
     */
    private class SelfDisablingAction(api: GeomanApi, private val key: ModeKey) : BaseAction(api) {
        override val modeName = key.name
        override val modeType = key.type

        override fun enable() {
            geoman.disableMode(key.type, key.name)
        }
    }

    private class RecordingEdit(api: GeomanApi, override val modeName: String) : BaseEdit(api) {
        var clicks = 0

        override fun onMapClick(point: LngLat) {
            clicks++
        }
    }

    private class FakeGeoman : GeomanApi {
        override val features = Features()
        override val events = GmEventBus()
        override val history = ChangeTracker()
        override val options = GmOptions(GmOptionsData())
        override val scope = CoroutineScope(UnconfinedTestDispatcher())
        override val mapActions = object : EditorMapActions {
            override fun project(lngLat: LngLat): ScreenPoint = ScreenPoint(0f, 0f)

            override fun queryFeaturesByScreenCoordinates(
                point: ScreenPoint,
                sourceNames: List<String>,
            ): List<FeatureData> = emptyList()

            override fun createDomMarker(options: DomMarkerOptions, position: LngLat): DomMarker =
                error("no DOM markers in ModeControllerTest")

            override fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions?) = Unit

            override fun getContext(): android.content.Context = error("no context in ModeControllerTest")
        }

        lateinit var controller: ModeController

        override fun enableMode(type: ModeType, name: String) = controller.enableMode(type, name)
        override fun disableMode(type: ModeType, name: String) = controller.disableMode(type, name)
        override fun toggleMode(type: ModeType, name: String): Boolean = controller.toggleMode(type, name)
        override fun isModeEnabled(type: ModeType, name: String): Boolean = controller.isModeEnabled(type, name)
    }

    private class FakeActionFactory(private val api: GeomanApi) : ModeActionFactory {
        val overrides = mutableMapOf<ModeKey, BaseAction>()
        val created = mutableListOf<FakeAction>()

        override fun create(type: ModeType, name: String): BaseAction? {
            val key = ModeKey(type, name)
            overrides[key]?.let { return it }
            if (name !in knownNames) return null
            return FakeAction(api, name, type).also { created.add(it) }
        }
    }

    private lateinit var api: FakeGeoman
    private lateinit var factory: FakeActionFactory
    private lateinit var controller: ModeController
    private val destroyed = AtomicBoolean(false)

    private companion object {
        val knownNames = DrawModeName.entries.map { it.name } +
            EditModeName.entries.map { it.name } +
            HelperModeName.entries.map { it.name }
    }

    @Before
    fun setUp() {
        GeomanLogger.delegate = object : GeomanLogger.Delegate {
            override fun d(tag: String, message: String) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        }
        api = FakeGeoman()
        factory = FakeActionFactory(api)
        controller = ModeController(
            modeFactory = factory,
            events = api.events,
            scope = api.scope,
            isDestroyed = { destroyed.get() },
        )
        api.controller = controller
    }

    @Test
    fun `enable registers the action and publishes it in the flow`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertTrue(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertEquals(listOf(ModeKey(ModeType.DRAW, DrawModeName.LINE.name)), controller.getEnabledModes())
        assertEquals(listOf(ModeKey(ModeType.DRAW, DrawModeName.LINE.name)), controller.activeModesFlow.value)
    }

    @Test
    fun `re-enabling the same key disables the previous instance before replacing it`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        val first = factory.created.single()

        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertEquals(1, first.disableCalls)
        val second = factory.created.last()
        assertEquals(1, second.enableCalls)
        assertNotSame(first, second)
        assertEquals(1, controller.getEnabledModes().size)
        assertEquals(listOf(ModeKey(ModeType.DRAW, DrawModeName.LINE.name)), controller.activeModesFlow.value)
    }

    @Test
    fun `enabling a same-type mode disables the other modes of that type`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        controller.enableMode(ModeType.DRAW, DrawModeName.POLYGON.name)

        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertEquals(1, factory.created[0].disableCalls)
        assertTrue(controller.isModeEnabled(ModeType.DRAW, DrawModeName.POLYGON.name))
        assertEquals(1, controller.getEnabledModes().size)
    }

    @Test
    fun `different mode types can be enabled simultaneously`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        controller.enableMode(ModeType.HELPER, HelperModeName.SNAP.name)

        assertEquals(2, controller.getEnabledModes().size)
        assertTrue(
            controller.getEnabledModes().containsAll(
                listOf(
                    ModeKey(ModeType.DRAW, DrawModeName.LINE.name),
                    ModeKey(ModeType.HELPER, HelperModeName.SNAP.name),
                ),
            ),
        )
    }

    @Test
    fun `toggle turns a mode on then off again`() {
        assertTrue(controller.toggleMode(ModeType.DRAW, DrawModeName.LINE.name))
        assertTrue(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))

        assertFalse(controller.toggleMode(ModeType.DRAW, DrawModeName.LINE.name))
        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertEquals(emptyList<ModeKey>(), controller.getEnabledModes())
    }

    @Test
    fun `unknown mode name is ignored`() {
        controller.enableMode(ModeType.HELPER, "no-such-helper")

        assertFalse(controller.isModeEnabled(ModeType.HELPER, "no-such-helper"))
        assertTrue(factory.created.isEmpty())
        assertEquals(emptyList<ModeKey>(), controller.getEnabledModes())
        assertEquals(emptyList<ModeKey>(), controller.activeModesFlow.value)
    }

    @Test
    fun `enable fires an enable event with mode name and type`() {
        val enables = mutableListOf<GmModeEvent.Enable>()
        api.events.on(GmModeEvent.Enable("", "").type) { enables.add(it as GmModeEvent.Enable) }

        controller.enableMode(ModeType.EDIT, EditModeName.DRAG.name)

        assertEquals(listOf(GmModeEvent.Enable(EditModeName.DRAG.name, ModeType.EDIT.name)), enables)
    }

    @Test
    fun `disable fires a disable event and clears the flow`() {
        val disables = mutableListOf<GmModeEvent.Disable>()
        api.events.on(GmModeEvent.Disable("", "").type) { disables.add(it as GmModeEvent.Disable) }

        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        controller.disableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertEquals(listOf(GmModeEvent.Disable(DrawModeName.LINE.name, ModeType.DRAW.name)), disables)
        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertEquals(emptyList<ModeKey>(), controller.activeModesFlow.value)
    }

    @Test
    fun `disabling an inactive mode is a no-op`() {
        controller.disableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertEquals(emptyList<ModeKey>(), controller.activeModesFlow.value)
    }

    @Test
    fun `one-shot action that disables itself during enable is not registered`() {
        val key = ModeKey(ModeType.HELPER, HelperModeName.ZOOM_TO_FEATURES.name)
        factory.overrides[key] = SelfDisablingAction(api, key)
        val enables = mutableListOf<GmModeEvent.Enable>()
        api.events.on(GmModeEvent.Enable("", "").type) { enables.add(it as GmModeEvent.Enable) }

        controller.enableMode(ModeType.HELPER, HelperModeName.ZOOM_TO_FEATURES.name)

        assertFalse(controller.isModeEnabled(ModeType.HELPER, HelperModeName.ZOOM_TO_FEATURES.name))
        assertEquals(emptyList<ModeKey>(), controller.getEnabledModes())
        assertEquals(emptyList<ModeKey>(), controller.activeModesFlow.value)
        assertTrue(enables.isEmpty())
    }

    @Test
    fun `actions are disabled when released but stay enabled for other types`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        controller.enableMode(ModeType.HELPER, HelperModeName.SNAP.name)
        val line = factory.created[0]

        controller.disableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertEquals(1, line.disableCalls)
        assertTrue(controller.isModeEnabled(ModeType.HELPER, HelperModeName.SNAP.name))
    }

    @Test
    fun `disableAllModes disables every action and empties the flow`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        controller.enableMode(ModeType.EDIT, EditModeName.DRAG.name)

        controller.disableAllModes()

        assertTrue(factory.created.all { it.disableCalls == 1 })
        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertFalse(controller.isModeEnabled(ModeType.EDIT, EditModeName.DRAG.name))
        assertEquals(emptyList<ModeKey>(), controller.getEnabledModes())
        assertEquals(emptyList<ModeKey>(), controller.activeModesFlow.value)
    }

    @Test
    fun `enable after destroy is a no-op`() {
        destroyed.set(true)

        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)

        assertFalse(controller.isModeEnabled(ModeType.DRAW, DrawModeName.LINE.name))
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun `handleEditClick forwards to the enabled edit action`() {
        val key = ModeKey(ModeType.EDIT, EditModeName.CUT.name)
        val edit = RecordingEdit(api, EditModeName.CUT.name)
        factory.overrides[key] = edit
        controller.enableMode(ModeType.EDIT, EditModeName.CUT.name)

        controller.handleEditClick(EditModeName.CUT, LngLat(2.0, 1.0))

        assertEquals(1, edit.clicks)
    }

    @Test
    fun `dispatch surface is a no-op when the mode is not the expected action type`() {
        controller.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
        val point = LngLat(2.0, 1.0)

        // A fake action registered under a DRAW key is not a BaseDraw
        controller.handleDrawClick(DrawModeName.LINE, point)
        controller.handleDrawLongPress(DrawModeName.LINE, point)
        controller.handleHelperClick(HelperModeName.SNAP, point)
        controller.handleEditClick(EditModeName.CUT, point)
        // No ChangeEditor registered
        controller.startEditingFeature(
            FeatureData(
                id = "f",
                sourceName = "gm_lines",
                feature = Feature(
                    id = "f",
                    geometry = LineString(coordinates = listOf(listOf(0.0, 0.0), listOf(1.0, 1.0))),
                ),
            ),
        )
    }
}
