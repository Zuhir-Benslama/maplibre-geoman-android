package com.geoman.maplibre.geoman.modes.draw

import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.modes.edit.EditModeTestBase
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interaction tests for draw modes against a fake [com.geoman.maplibre.geoman.GeomanApi]:
 * each mode stores a feature in its source, fires a [GmDrawEvent.Create], and
 * disables itself once finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerInteractionTest : EditModeTestBase() {

    private fun single(sourceName: String): Feature {
        val stored = geoman.features.getFeatures(sourceName)
        assertEquals(1, stored.size)
        return requireNotNull(stored.values.single().feature)
    }

    private fun assertDisabled(modeName: String) {
        assertTrue(
            "$modeName should have been disabled after finishing",
            geoman.disabledModes.any { it == ModeKey(ModeType.DRAW, modeName) },
        )
    }

    @Test
    fun `marker drawer stores a default point and disables`() {
        val drawer = MarkerDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 20.0, latitude = 10.0))

        val feature = single(GeomanCoreConstants.SOURCE_MARKERS)
        assertEquals("default", feature.properties["markerType"])
        val point = feature.geometry as Point
        assertEquals(listOf(20.0, 10.0), point.coordinates)
        assertNotNull(create)
        assertEquals(DrawModeName.MARKER.name, create!!.shape)
        assertDisabled(DrawModeName.MARKER.name)
    }

    @Test
    fun `circle marker drawer stores a circle point and disables`() {
        val drawer = CircleMarkerDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 20.0, latitude = 10.0))

        val feature = single(GeomanCoreConstants.SOURCE_CIRCLE_MARKERS)
        assertEquals("circle", feature.properties["markerType"])
        val point = feature.geometry as Point
        assertEquals(listOf(20.0, 10.0), point.coordinates)
        assertNotNull(create)
        assertEquals(DrawModeName.CIRCLE_MARKER.name, create!!.shape)
        assertDisabled(DrawModeName.CIRCLE_MARKER.name)
    }

    @Test
    fun `line drawer builds a two-point line and finishes on long press`() {
        val drawer = LineDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 0.0, latitude = 0.0))
        drawer.onMapClick(LngLat(longitude = 2.0, latitude = 1.0))
        drawer.onMapLongClick(LngLat(longitude = 2.0, latitude = 1.0))

        val feature = single(GeomanCoreConstants.SOURCE_LINES)
        val line = feature.geometry as LineString
        assertEquals(listOf(listOf(0.0, 0.0), listOf(2.0, 1.0)), line.coordinates)
        assertEquals("line", feature.properties["shapeType"])
        assertNotNull(create)
        assertEquals(DrawModeName.LINE.name, create!!.shape)
        assertDisabled(DrawModeName.LINE.name)
    }

    @Test
    fun `polygon drawer builds a closed ring and finishes on long press`() {
        val drawer = PolygonDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 0.0, latitude = 0.0))
        drawer.onMapClick(LngLat(longitude = 2.0, latitude = 1.0))
        drawer.onMapClick(LngLat(longitude = 4.0, latitude = 1.0))
        drawer.onMapLongClick(LngLat(longitude = 4.0, latitude = 1.0))

        val feature = single(GeomanCoreConstants.SOURCE_POLYGONS)
        val polygon = feature.geometry as Polygon
        val ring = polygon.coordinates.single()
        assertEquals(listOf(listOf(0.0, 0.0), listOf(2.0, 1.0), listOf(4.0, 1.0), listOf(0.0, 0.0)), ring)
        assertEquals(ring.first(), ring.last())
        assertNotNull(create)
        assertEquals(DrawModeName.POLYGON.name, create!!.shape)
        assertDisabled(DrawModeName.POLYGON.name)
    }

    @Test
    fun `circle drawer stores a circle with positive radius and disables`() {
        val drawer = CircleDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 5.0, latitude = 0.0))
        drawer.onMapClick(LngLat(longitude = 5.0, latitude = 1.0))

        val feature = single(GeomanCoreConstants.SOURCE_CIRCLES)
        val polygon = feature.geometry as Polygon
        val ring = polygon.coordinates.single()
        assertTrue(ring.size >= 4)
        assertEquals(ring.first(), ring.last())
        val radius = feature.properties["radius"] as Double
        assertTrue("expected positive radius, was $radius", radius > 0.0)
        assertNotNull(create)
        assertEquals(DrawModeName.CIRCLE.name, create!!.shape)
        assertDisabled(DrawModeName.CIRCLE.name)
    }

    @Test
    fun `rectangle drawer stores a closed rectangle and disables`() {
        val drawer = RectangleDrawer(geoman)
        var create: GmDrawEvent.Create? = null
        geoman.events.on(GmDrawEvent.Create("", null).type) { create = it as GmDrawEvent.Create }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 0.0, latitude = 1.0))
        drawer.onMapClick(LngLat(longitude = 2.0, latitude = 0.0))

        val feature = single(GeomanCoreConstants.SOURCE_RECTANGLES)
        val polygon = feature.geometry as Polygon
        val ring = polygon.coordinates.single()
        assertEquals(
            listOf(listOf(0.0, 1.0), listOf(2.0, 1.0), listOf(2.0, 0.0), listOf(0.0, 0.0), listOf(0.0, 1.0)),
            ring,
        )
        assertEquals(ring.first(), ring.last())
        assertNotNull(create)
        assertEquals(DrawModeName.RECTANGLE.name, create!!.shape)
        assertDisabled(DrawModeName.RECTANGLE.name)
    }

    @Test
    fun `unfinished path does not fire create nor disable`() {
        val drawer = LineDrawer(geoman)
        var createFired = false
        geoman.events.on(GmDrawEvent.Create("", null).type) { createFired = true }

        drawer.enable()
        drawer.onMapClick(LngLat(longitude = 0.0, latitude = 0.0))
        drawer.onMapLongClick(LngLat(longitude = 0.0, latitude = 0.0))

        assertTrue(geoman.features.getFeatures(GeomanCoreConstants.SOURCE_LINES).isEmpty())
        assertTrue(geoman.disabledModes.isEmpty())
    }
}
