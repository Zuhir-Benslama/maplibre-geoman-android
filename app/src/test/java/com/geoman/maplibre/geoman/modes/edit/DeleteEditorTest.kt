package com.geoman.maplibre.geoman.modes.edit

import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteEditorTest : EditModeTestBase() {

    @Test
    fun `delete removes the clicked feature and fires the Delete event`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(4.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        var deleted: GmEditEvent.Delete? = null
        geoman.events.on(GmEditEvent.Delete().type) { deleted = it as GmEditEvent.Delete }

        val editor = DeleteEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 0.0, latitude = 2.0))

        assertNull(geoman.features.getFeature("gm_lines", "line"))
        assertNotNull(deleted)
        assertEquals("line", deleted!!.feature?.id)
    }

    @Test
    fun `delete with no hit leaves the store untouched and fires no event`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(4.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = emptyList()

        var deleted = false
        geoman.events.on(GmEditEvent.Delete().type) { deleted = true }

        val editor = DeleteEditor(geoman)
        editor.enable()
        editor.onMapClick(LngLat(longitude = 2.0, latitude = 2.0))

        assertNotNull(geoman.features.getFeature("gm_lines", "line"))
        assertFalse(deleted)
    }

    @Test
    fun `delete is ignored while disabled`() {
        val line = lineData("line", listOf(listOf(0.0, 0.0), listOf(4.0, 0.0)))
        geoman.features.addFeature(line)
        geoman.mapActions.queryResult = listOf(line)

        var deleted = false
        geoman.events.on(GmEditEvent.Delete().type) { deleted = true }

        val editor = DeleteEditor(geoman)
        editor.enable()
        editor.disable()
        editor.onMapClick(LngLat(longitude = 0.0, latitude = 2.0))

        assertNotNull(geoman.features.getFeature("gm_lines", "line"))
        assertFalse(deleted)
    }
}
