package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceUpdateManagerTest {

    private val applied = mutableListOf<Pair<String, FeatureCollection>>()

    private fun TestScope.manager(debounceMs: Long = 100) = SourceUpdateManager(
        applyUpdate = { sourceName, collection -> applied.add(sourceName to collection) },
        scope = this,
        debounceMs = debounceMs,
    )

    private fun collection(vararg ids: String) = FeatureCollection(
        features = ids.map { id ->
            com.geoman.maplibre.geoman.types.geojson.Feature(
                id = id,
                geometry = com.geoman.maplibre.geoman.types.geojson.Point.fromLngLat(
                    com.geoman.maplibre.geoman.types.geojson.LngLat(0.0, 0.0),
                ),
            )
        },
    )

    @Test
    fun `scheduled update applies after the debounce window`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        assertTrue(applied.isEmpty())

        advanceTimeBy(99)
        runCurrent()
        assertTrue(applied.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, applied.size)
        assertEquals("gm_lines", applied[0].first)
    }

    @Test
    fun `rapid schedules coalesce into a single apply of the latest collection`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        manager.schedule("gm_lines", collection("a", "b"))
        manager.schedule("gm_lines", collection("a", "b", "c"))

        advanceTimeBy(200)
        runCurrent()

        assertEquals(1, applied.size)
        assertEquals(listOf("a", "b", "c"), applied[0].second.features.mapNotNull { it.id })
    }

    @Test
    fun `flush applies immediately and cancels the pending job`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        manager.flush("gm_lines")

        assertEquals(1, applied.size)

        // The debounced job must not fire a second apply later
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, applied.size)
    }

    @Test
    fun `flush without pending updates is a no-op`() = runTest {
        val manager = manager()

        manager.flush("gm_lines")

        assertTrue(applied.isEmpty())
    }

    @Test
    fun `sources are debounced independently`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        manager.schedule("gm_polygons", collection("p"))

        advanceTimeBy(200)
        runCurrent()

        assertEquals(2, applied.size)
        assertEquals(setOf("gm_lines", "gm_polygons"), applied.map { it.first }.toSet())
    }

    @Test
    fun `cancelPending drops scheduled updates without applying them`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        manager.cancelPending()

        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(applied.isEmpty())
    }

    @Test
    fun `flushAll applies every pending source exactly once`() = runTest {
        val manager = manager()

        manager.schedule("gm_lines", collection("a"))
        manager.schedule("gm_polygons", collection("p"))
        manager.flushAll()

        assertEquals(2, applied.size)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, applied.size)
    }
}
