package com.geoman.maplibre.geoman.core.markers

import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarkerManagerTest {

    private class FakeMarker : ManagedMarker {
        var position = LngLat(0.0, 0.0)
        var removed = false
        override var onClick: (() -> Unit)? = null

        override fun getLngLat(): LngLat = position

        override fun setLngLat(lngLat: LngLat) {
            position = lngLat
        }

        override fun remove() {
            removed = true
        }
    }

    private lateinit var manager: MarkerManager

    @Before
    fun setUp() {
        manager = MarkerManager()
    }

    @Test
    fun `add registers marker and attaches click handler`() {
        val marker = FakeMarker()
        var clicked = false

        manager.add("v1", marker) { clicked = true }

        assertTrue(manager.contains("v1"))
        assertSame(marker, manager.get("v1"))
        manager.get("v1")?.onClick?.invoke()
        assertTrue(clicked)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `add rejects duplicate ids`() {
        manager.add("v1", FakeMarker())
        manager.add("v1", FakeMarker())
    }

    @Test
    fun `updatePosition moves registered marker`() {
        val marker = FakeMarker()
        manager.add("v1", marker)

        val moved = manager.updatePosition("v1", LngLat(5.0, 6.0))

        assertTrue(moved)
        assertEquals(LngLat(5.0, 6.0), marker.position)
    }

    @Test
    fun `updatePosition returns false for unknown id`() {
        assertFalse(manager.updatePosition("missing", LngLat(1.0, 1.0)))
    }

    @Test
    fun `remove detaches handler removes from map and platform`() {
        val marker = FakeMarker()
        manager.add("v1", marker) { }

        val removed = manager.remove("v1")

        assertSame(marker, removed)
        assertTrue(marker.removed)
        assertNull(marker.onClick)
        assertFalse(manager.contains("v1"))
        assertEquals(0, manager.size)
    }

    @Test
    fun `remove returns null for unknown id`() {
        assertNull(manager.remove("missing"))
    }

    @Test
    fun `removeWhere removes only matching markers`() {
        repeat(4) { index -> manager.add("vertex_$index", FakeMarker()) }
        manager.add("midpoint_0", FakeMarker())

        val removedIds = manager.removeWhere { it.startsWith("midpoint_") }

        assertEquals(listOf("midpoint_0"), removedIds)
        assertEquals(4, manager.size)
        assertTrue(manager.contains("vertex_0"))
    }

    @Test
    fun `clear removes everything exactly once`() {
        val markers = (0 until 3).map { index -> "v$index" to FakeMarker() }
        markers.forEach { (id, marker) -> manager.add(id, marker) }

        manager.clear()

        assertEquals(0, manager.size)
        markers.forEach { (_, marker) -> assertTrue(marker.removed) }
    }

    @Test
    fun `setClickListener replaces existing handler`() {
        val marker = FakeMarker()
        manager.add("v1", marker) { }
        var replacedClicked = false

        val updated = manager.setClickListener("v1") { replacedClicked = true }

        assertTrue(updated)
        manager.get("v1")?.onClick?.invoke()
        assertTrue(replacedClicked)
    }

    @Test
    fun `ids exposes registered keys`() {
        manager.add("a", FakeMarker())
        manager.add("b", FakeMarker())

        assertEquals(setOf("a", "b"), manager.ids)
    }
}
