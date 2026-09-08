package com.geoman.maplibre.geoman.modes.helpers

import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.modes.edit.EditModeTestBase
import com.geoman.maplibre.geoman.types.HelperModeName
import com.geoman.maplibre.geoman.types.ModeKey
import com.geoman.maplibre.geoman.types.ModeType
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ZoomToFitHelper]: fit the map to every stored feature, then
 * disable itself; skip fitting when there is nothing to fit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZoomToFitHelperTest : EditModeTestBase() {

    private fun point(id: String, lon: Double, lat: Double) = Feature(
        id = id,
        geometry = Point.fromLngLat(LngLat(lon, lat)),
    )

    private fun assertSelfDisabled() {
        assertTrue(
            "helper should disable itself after fitting",
            geoman.disabledModes.contains(ModeKey(ModeType.HELPER, HelperModeName.ZOOM_TO_FEATURES.name)),
        )
    }

    @Test
    fun `enable fits the map to the stored features and disables`() {
        geoman.features.addGeoJsonFeature(point("p1", 1.0, 2.0), GeomanCoreConstants.SOURCE_MARKERS)
        geoman.features.addGeoJsonFeature(point("p2", 5.0, 6.0), GeomanCoreConstants.SOURCE_LINES)

        ZoomToFitHelper(geoman).enable()

        val bounds = geoman.mapActions.fittedBounds
        assertTrue("expected a fitBounds call", bounds != null)
        assertEquals(1.0, bounds!!.southwest.longitude, 0.0)
        assertEquals(5.0, bounds.northeast.longitude, 0.0)
        assertEquals(2.0, bounds.southwest.latitude, 0.0)
        assertEquals(6.0, bounds.northeast.latitude, 0.0)
        assertSelfDisabled()
    }

    @Test
    fun `enable with no stored features skips fitting but still disables`() {
        ZoomToFitHelper(geoman).enable()

        assertNull(geoman.mapActions.fittedBounds)
        assertSelfDisabled()
    }
}
