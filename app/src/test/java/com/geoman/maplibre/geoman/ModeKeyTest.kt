package com.geoman.maplibre.geoman

import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModeKeyTest {

    @Test
    fun `ModeKey equality by type and name`() {
        val key1 = ModeKey(ModeType.DRAW, "marker")
        val key2 = ModeKey(ModeType.DRAW, "marker")
        val key3 = ModeKey(ModeType.DRAW, "line")
        val key4 = ModeKey(ModeType.EDIT, "marker")

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertNotEquals(key1, key4)
    }

    @Test
    fun `ModeKey hashCode is consistent`() {
        val key1 = ModeKey(ModeType.DRAW, "marker")
        val key2 = ModeKey(ModeType.DRAW, "marker")

        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `ModeKey works as map key`() {
        val map = mutableMapOf<ModeKey, Int>()
        map[ModeKey(ModeType.DRAW, "marker")] = 1
        map[ModeKey(ModeType.DRAW, "line")] = 2

        assertEquals(1, map[ModeKey(ModeType.DRAW, "marker")])
        assertEquals(2, map[ModeKey(ModeType.DRAW, "line")])
        assertEquals(2, map.size)
    }

    @Test
    fun `ModeKey destructuring`() {
        val key = ModeKey(ModeType.EDIT, "drag")
        val (type, name) = key

        assertEquals(ModeType.EDIT, type)
        assertEquals("drag", name)
    }
}
