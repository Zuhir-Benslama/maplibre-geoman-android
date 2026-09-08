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
import com.geoman.maplibre.geoman.modes.draw.BaseDraw
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency stress tests for [ModeController]: parallel mode switching and
 * interaction dispatch must never corrupt bookkeeping, leave more than one
 * mode of a type enabled, or lose the mutual-exclusion invariant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeControllerStressTest {

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
                error("no DOM markers in stress test")

            override fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions?) = Unit

            override fun getContext(): android.content.Context = error("no context in stress test")
        }

        lateinit var controller: ModeController

        override fun enableMode(type: ModeType, name: String) = controller.enableMode(type, name)
        override fun disableMode(type: ModeType, name: String) = controller.disableMode(type, name)
        override fun toggleMode(type: ModeType, name: String): Boolean = controller.toggleMode(type, name)
        override fun isModeEnabled(type: ModeType, name: String): Boolean = controller.isModeEnabled(type, name)
    }

    /**
     * Fake action that asserts the same-type mutual-exclusion invariant from
     * inside enable(): at most one action per [modeType] may be registered.
     */
    private class AssertingAction(
        private val controller: ModeController,
        override val modeName: String,
        override val modeType: ModeType,
    ) : BaseAction(FakeGeoman()) {
        val enableCalls = AtomicInteger(0)
        val disableCalls = AtomicInteger(0)

        override fun enable() {
            enableCalls.incrementAndGet()
            super.enable()
            val sameType = controller.getEnabledModes().count { it.type == modeType }
            if (sameType != 1) {
                throw IllegalStateException("$modeType has $sameType enabled actions during enable()")
            }
        }

        override fun disable() {
            disableCalls.incrementAndGet()
            super.disable()
        }
    }

    private class RecordingDraw(override val modeName: String) : BaseDraw(FakeGeoman()) {
        val clicks = AtomicInteger(0)

        override fun onMapClick(point: LngLat) {
            clicks.incrementAndGet()
        }

        override fun onMapLongClick(point: LngLat) {
            clicks.incrementAndGet()
        }

        override fun finishDrawing() = Unit
    }

    private class FakeActionFactory(private val api: GeomanApi) : ModeActionFactory {
        val created = ConcurrentLinkedQueue<AssertingAction>()
        lateinit var controller: ModeController

        override fun create(type: ModeType, name: String): BaseAction? {
            val action = AssertingAction(controller, name, type)
            created.add(action)
            return action
        }
    }

    private lateinit var api: FakeGeoman

    @Before
    fun setUp() {
        GeomanLogger.delegate = object : GeomanLogger.Delegate {
            override fun d(tag: String, message: String) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        }
        api = FakeGeoman()
    }

    private fun newController(factory: ModeActionFactory): ModeController = ModeController(
        modeFactory = factory,
        events = api.events,
        scope = api.scope,
        isDestroyed = { false },
    )

    private fun runConcurrently(threadCount: Int, iterations: Int, action: (Int, Int) -> Unit) {
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = (0 until threadCount).map { thread ->
            Thread {
                try {
                    start.await()
                    for (i in 0 until iterations) action(thread, i)
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    done.countDown()
                }
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        assertTrue(
            "workers timed out",
            done.await(30, TimeUnit.SECONDS),
        )
        threads.forEach(Thread::join)
        assertTrue(
            "concurrent actions threw: ${errors.joinToString { it.message ?: "exception" }}",
            errors.isEmpty(),
        )
    }

    @Test
    fun `parallel mode switching keeps at most one action per type`() {
        val factory = FakeActionFactory(api)
        val controller = newController(factory)
        factory.controller = controller
        api.controller = controller

        val keys = listOf(
            ModeKey(ModeType.DRAW, "LINE"),
            ModeKey(ModeType.DRAW, "POLYGON"),
            ModeKey(ModeType.DRAW, "CIRCLE"),
            ModeKey(ModeType.EDIT, "DRAG"),
            ModeKey(ModeType.EDIT, "ROTATE"),
            ModeKey(ModeType.HELPER, "ZOOM_TO_FEATURES"),
        )

        runConcurrently(threadCount = 8, iterations = 300) { thread, i ->
            val key = keys[(thread + i) % keys.size]
            when (i % 3) {
                0 -> controller.enableMode(key.type, key.name)
                1 -> controller.toggleMode(key.type, key.name)
                else -> controller.disableMode(key.type, key.name)
            }
        }

        val enabled = controller.getEnabledModes()
        assertEquals(enabled.size, enabled.distinct().size)
        ModeType.entries.forEach { type ->
            assertTrue(
                "at most one $type enabled, found ${enabled.count { it.type == type }}",
                enabled.count { it.type == type } <= 1,
            )
        }
        assertEquals(enabled.toSet(), controller.activeModesFlow.value.toSet())
        // Every created action must have been enabled at least once during the storm.
        assertTrue(factory.created.isNotEmpty())
        factory.created.forEach { assertTrue(it.enableCalls.get() >= 1) }
    }

    @Test
    fun `parallel clicks while switching modes stay race-free`() {
        val clicks = AtomicInteger(0)
        val controller = newController(
            ModeActionFactory { type, name ->
                if (type == ModeType.DRAW) RecordingDraw(name) else null
            },
        )

        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        val errors = ConcurrentLinkedQueue<Throwable>()

        val switcher = Thread {
            try {
                start.await()
                repeat(500) { i ->
                    controller.enableMode(ModeType.DRAW, if (i % 2 == 0) "LINE" else "POLYGON")
                }
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                done.countDown()
            }
        }
        val clickers = (0 until 3).map { thread ->
            Thread {
                try {
                    start.await()
                    repeat(500) { i ->
                        val mode = if (i % 2 == 0) DrawModeName.LINE else DrawModeName.POLYGON
                        controller.handleDrawClick(mode, LngLat(2.0, 1.0))
                        controller.handleDrawLongPress(mode, LngLat(2.0, 1.0))
                        clicks.incrementAndGet()
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    done.countDown()
                }
            }
        }

        (listOf(switcher) + clickers).forEach(Thread::start)
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        (listOf(switcher) + clickers).forEach(Thread::join)
        assertTrue(errors.isEmpty())
        assertTrue(clicks.get() > 0)
    }

    @Test
    fun `concurrent typed dispatch with mode toggling never throws`() {
        val controller = newController(
            ModeActionFactory { type, name ->
                if (type == ModeType.DRAW) RecordingDraw(name) else null
            },
        )
        api.controller = controller

        runConcurrently(threadCount = 6, iterations = 250) { thread, i ->
            val drawMode = if ((thread + i) % 2 == 0) DrawModeName.LINE else DrawModeName.POLYGON
            controller.handleDrawClick(drawMode, LngLat(2.0, 1.0))
            controller.handleDrawLongPress(drawMode, LngLat(2.0, 1.0))
            controller.handleEditClick(EditModeName.CUT, LngLat(2.0, 1.0))
            controller.handleHelperClick(HelperModeName.SNAP, LngLat(2.0, 1.0))
            controller.toggleMode(ModeType.DRAW, drawMode.name)
            controller.toggleMode(ModeType.DRAW, "DOES_NOT_EXIST")
        }
    }
}
