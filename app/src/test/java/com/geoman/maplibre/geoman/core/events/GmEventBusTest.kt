package com.geoman.maplibre.geoman.core.events

import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.events.GmEvent
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GmEventBusTest {

    private val bus = GmEventBus()

    @Before
    fun setUp() {
        // Route logs away from android.util.Log, which is unmocked on the JVM
        GeomanLogger.delegate = object : GeomanLogger.Delegate {
            override fun d(tag: String, message: String) = Unit
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
            override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        }
    }

    @Test
    fun `listener registered with on receives emitted event`() {
        var received: GmEditEvent? = null
        bus.on(GmEditEvent.Delete().type) { received = it as GmEditEvent }

        val event = GmEditEvent.Delete()
        bus.tryEmit(event)

        assertEquals(event, received)
    }

    @Test
    fun `listeners of other event types are not invoked`() {
        var called = false
        bus.on("gm:other") { called = true }

        bus.tryEmit(GmMapEvent.Loaded)

        assertTrue(!called)
    }

    @Test
    fun `off unregisters a listener`() {
        var count = 0
        val listener: (GmMapEvent.Loaded) -> Unit = { count++ }
        val adapter: (GmEvent) -> Unit = { listener(it as GmMapEvent.Loaded) }

        bus.on(GmMapEvent.Loaded.type, adapter)
        bus.tryEmit(GmMapEvent.Loaded)
        bus.off(GmMapEvent.Loaded.type, adapter)
        bus.tryEmit(GmMapEvent.Loaded)

        assertEquals(1, count)
    }

    @Test
    fun `once fires at most once and unregisters itself`() {
        var count = 0
        bus.once(GmMapEvent.Loaded.type) { count++ }

        bus.tryEmit(GmMapEvent.Loaded)
        bus.tryEmit(GmMapEvent.Loaded)
        bus.tryEmit(GmMapEvent.Loaded)

        assertEquals(1, count)
    }

    @Test
    fun `throwing listener does not prevent other listeners from running`() {
        var secondCalled = false
        bus.on(GmMapEvent.Loaded.type) { error("boom") }
        bus.on(GmMapEvent.Loaded.type) { secondCalled = true }

        bus.tryEmit(GmMapEvent.Loaded)

        assertTrue(secondCalled)
    }

    @Test
    fun `removeAllListeners clears every event type`() {
        var count = 0
        bus.on(GmMapEvent.Loaded.type) { count++ }
        bus.on(GmMapEvent.Destroyed.type) { count++ }

        bus.removeAllListeners()
        bus.tryEmit(GmMapEvent.Loaded)
        bus.tryEmit(GmMapEvent.Destroyed)

        assertEquals(0, count)
    }

    @Test
    fun `removeAllListeners with type clears only that type`() {
        var loadedCount = 0
        var destroyedCount = 0
        bus.on(GmMapEvent.Loaded.type) { loadedCount++ }
        bus.on(GmMapEvent.Destroyed.type) { destroyedCount++ }

        bus.removeAllListeners(GmMapEvent.Loaded.type)
        bus.tryEmit(GmMapEvent.Loaded)
        bus.tryEmit(GmMapEvent.Destroyed)

        assertEquals(0, loadedCount)
        assertEquals(1, destroyedCount)
    }

    @Test
    fun `events flow exposes emissions to collectors`() = runBlocking {
        val subscribed = CompletableDeferred<Unit>()
        val received = CompletableDeferred<GmEvent>()
        val job = launch {
            bus.events
                .onSubscription { subscribed.complete(Unit) }
                .collect { received.complete(it) }
        }

        // Wait until the collector is actually subscribed before emitting,
        // since the SharedFlow has no replay buffer
        withTimeoutOrNull(1_000) { subscribed.await() }
        bus.tryEmit(GmMapEvent.Loaded)

        assertEquals(GmMapEvent.Loaded, withTimeoutOrNull(1_000) { received.await() })
        job.cancel()
    }

    @Test
    fun `suspend emit notifies callback listeners`() = runBlocking {
        var received: GmMapEvent? = null
        bus.on(GmMapEvent.Destroyed.type) { received = it as? GmMapEvent }

        bus.emit(GmMapEvent.Destroyed)

        assertNotNull(received)
    }
}
