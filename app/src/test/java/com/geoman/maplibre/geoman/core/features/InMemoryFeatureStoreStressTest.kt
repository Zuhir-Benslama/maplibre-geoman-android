package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Concurrency stress tests for [InMemoryFeatureStore]: parallel mutations must
 * not lose writes, corrupt the id/source index, or deadlock the monitor.
 */
class InMemoryFeatureStoreStressTest {

    private fun point(id: String, source: String, lon: Double = 0.0) = FeatureData(
        id = id,
        sourceName = source,
        feature = Feature(id = id, geometry = Point.fromLngLat(LngLat(lon, 0.0))),
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
            "concurrent mutations threw: ${errors.joinToString { it.message ?: "exception" }}",
            errors.isEmpty(),
        )
    }

    @Test
    fun `parallel adds across sources lose no features`() {
        val store = InMemoryFeatureStore()
        val threadCount = 8
        val iterations = 200

        runConcurrently(threadCount, iterations) { thread, i ->
            val source = "gm_source_${thread % 4}"
            val id = "feature-$thread-$i"
            store.add(point(id, source, lon = i.toDouble() + thread * 1000))
        }

        val expectedTotal = threadCount * iterations
        assertEquals(expectedTotal, store.getAllFeatures().values.sumOf { it.size })
        assertEquals(expectedTotal, store.featuresFlow.value.values.sumOf { it.size })
        assertEquals(4, store.allSourceNames().size)
        val snapshot = store.getAllFeatures()
        (0 until threadCount).forEach { thread ->
            (0 until iterations).forEach { i ->
                val id = "feature-$thread-$i"
                val source = "gm_source_${thread % 4}"
                assertEquals(
                    i.toDouble() + thread * 1000,
                    (snapshot[source]?.get(id)?.feature?.geometry as Point).toLngLat().longitude,
                    0.0,
                )
            }
        }
    }

    @Test
    fun `parallel add remove re-add cycles end with an intact store`() {
        val store = InMemoryFeatureStore()
        val threadCount = 6
        val iterations = 300

        runConcurrently(threadCount, iterations) { thread, i ->
            val id = "churn-$thread-$i"
            store.add(point(id, "gm_markers", lon = i.toDouble()))
            store.remove("gm_markers", id)
            store.add(point(id, "gm_markers", lon = i.toDouble() + 1))
        }

        // Every id's last operation was an add, so all must be present.
        assertEquals(threadCount * iterations, store.getAllFeatures()["gm_markers"]?.size ?: 0)
        assertEquals(1, store.allSourceNames().size)
        val snapshot = store.getAllFeatures()["gm_markers"].orEmpty()
        (0 until threadCount).forEach { thread ->
            (0 until iterations).forEach { i ->
                val feature = snapshot["churn-$thread-$i"]
                assertEquals(i.toDouble() + 1, (feature!!.feature.geometry as Point).toLngLat().longitude, 0.0)
            }
        }
    }

    @Test
    fun `parallel updates to the same feature never tear the geometry`() {
        val store = InMemoryFeatureStore()
        val id = "shared"
        store.add(point(id, "gm_lines", lon = 0.0))

        val threadCount = 8
        val iterations = 400
        runConcurrently(threadCount, iterations) { thread, _ ->
            val variant = 1000 + thread
            store.update("gm_lines", id) {
                point(id, "gm_lines", lon = variant.toDouble())
            }
        }

        val stored = store.getFeature("gm_lines", id) ?: error("feature was lost")
        val lon = (stored.feature.geometry as Point).toLngLat().longitude
        assertTrue("torn geometry lon=$lon", lon in (1000..1007).map { it.toDouble() })
    }

    @Test
    fun `parallel linking of many children to one parent keeps every link`() {
        val store = InMemoryFeatureStore()
        val parentId = "parent"
        store.add(point(parentId, "gm_markers"))

        val threadCount = 8
        val iterations = 100
        runConcurrently(threadCount, iterations) { thread, i ->
            val childId = "child-$thread-$i"
            store.add(point(childId, "gm_helpers"))
            store.setFeatureParent(childId, parentId)
        }

        val children = store.getChildFeatureIds(parentId)
        assertEquals(threadCount * iterations, children.size)
        children.forEach { childId ->
            assertEquals(parentId, store.getParentFeatureId(childId))
        }
    }
}
