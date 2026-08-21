package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private fun pointFeature(lng: Double, lat: Double, id: String? = null) = Feature(
        id = id,
        geometry = Point.fromLngLat(LngLat(lng, lat)),
    )

    private fun featureData(sourceName: String, lng: Double, lat: Double, id: String? = null) = FeatureData(
        id = id ?: "id_${sourceName}_$lng,$lat",
        sourceName = sourceName,
        feature = pointFeature(lng, lat),
    )

    @Test
    fun `addFeature stores feature under its source`() {
        val features = Features()
        val data = featureData("gm_markers", 1.0, 2.0)

        features.addFeature(data)

        assertEquals(data, features.getFeature("gm_markers", data.id))
        assertEquals(1, features.getFeatures("gm_markers").size)
    }

    @Test
    fun `addGeoJsonFeature generates an id when missing`() {
        val features = Features()

        val added = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_lines")

        assertTrue(added.id.isNotBlank())
        assertEquals(added.id, added.feature.id)
        assertNotNull(features.getFeature("gm_lines", added.id))
    }

    @Test
    fun `addGeoJsonFeature keeps an existing id`() {
        val features = Features()

        val added = features.addGeoJsonFeature(pointFeature(1.0, 2.0, id = "custom"), "gm_lines")

        assertEquals("custom", added.id)
    }

    @Test
    fun `addGeoJsonFeature derives shape from source name`() {
        val features = Features()

        val marker = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        val polygon = features.addGeoJsonFeature(
            com.geoman.maplibre.geoman.types.geojson.Feature(
                id = null,
                geometry = com.geoman.maplibre.geoman.types.geojson.Polygon(
                    coordinates = listOf(
                        listOf(listOf(0.0, 0.0), listOf(4.0, 0.0), listOf(4.0, 4.0), listOf(0.0, 0.0)),
                    ),
                ),
            ),
            "gm_polygons",
        )

        assertEquals(FeatureShape.POINT, marker.shape)
        assertEquals(FeatureShape.POLYGON, polygon.shape)
    }

    @Test
    fun `deepCopy shares no property collections with the original`() {
        val original = featureData("gm_markers", 1.0, 2.0).copy(
            properties = mapOf("key" to "value"),
        )

        val copy = original.deepCopy()

        assertEquals(original, copy)
        assertTrue(copy.properties !== original.properties)
    }

    @Test
    fun `addGeoJsonFeature rejects invalid features and stores nothing`() {
        val features = Features()
        val invalid = Feature(
            id = "bad",
            geometry = Point.fromLngLat(LngLat(Double.NaN, 2.0)),
        )

        try {
            features.addGeoJsonFeature(invalid, "gm_markers")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("finite"))
        }

        assertTrue(features.getFeatures("gm_markers").isEmpty())
    }

    @Test
    fun `updateFeature replaces stored state and reports no-op for unknown ids`() {
        val features = Features()
        val data = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_polygons")

        var updateRan = false
        features.updateFeature("gm_polygons", data.id) { existing ->
            updateRan = true
            existing.copy(properties = mapOf("touched" to true))
        }

        assertTrue(updateRan)
        assertEquals(true, features.getFeature("gm_polygons", data.id)?.properties?.get("touched"))

        var unknownUpdateRan = false
        features.updateFeature("gm_polygons", "missing") {
            unknownUpdateRan = true
            it
        }
        assertFalse(unknownUpdateRan)
    }

    @Test
    fun `removeFeature drops the feature and prunes empty sources`() {
        val features = Features()
        val a = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_circles")
        val b = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_circles")

        val removed = features.removeFeature("gm_circles", a.id)

        assertEquals(a, removed)
        assertNull(features.getFeature("gm_circles", a.id))
        assertNotNull(features.getFeature("gm_circles", b.id))

        features.removeFeature("gm_circles", b.id)
        assertTrue(features.getAllFeatures().isEmpty())
    }

    @Test
    fun `clearSource removes only the requested source`() {
        val features = Features()
        features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_lines")
        features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_polygons")

        features.clearSource("gm_lines")

        assertTrue(features.getFeatures("gm_lines").isEmpty())
        assertEquals(1, features.getFeatures("gm_polygons").size)
    }

    @Test
    fun `clearAll removes everything`() {
        val features = Features()
        features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_lines")
        features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_polygons")

        features.clearAll()

        assertTrue(features.getAllFeatures().isEmpty())
    }

    @Test
    fun `featuresFlow emits snapshot after mutations`() = runBlocking {
        val features = Features()
        val initial = features.featuresFlow.first()

        assertEquals(emptyMap<String, Map<String, FeatureData>>(), initial)

        val data = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")

        val snapshot = features.featuresFlow.value
        assertEquals(data, snapshot["gm_markers"]?.get(data.id))
    }

    @Test
    fun `returned collections are snapshots unaffected by later mutations`() {
        val features = Features()
        val data = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        val snapshot = features.getFeatures("gm_markers")

        features.removeFeature("gm_markers", data.id)

        assertEquals(1, snapshot.size)
        assertNotNull(snapshot[data.id])
        assertTrue(features.getFeatures("gm_markers").isEmpty())
    }

    @Test
    fun `getFeaturesInBounds filters by bbox intersection`() {
        val features = Features()
        val inside = features.addGeoJsonFeature(pointFeature(5.0, 5.0), "gm_markers")
        features.addGeoJsonFeature(pointFeature(50.0, 50.0), "gm_markers")

        val result = features.getFeaturesInBounds(
            bounds = listOf(LngLat(4.0, 4.0), LngLat(6.0, 6.0)),
            sourceNames = listOf("gm_markers"),
        )

        assertEquals(listOf(inside), result)
    }

    @Test
    fun `getFeaturesInBounds requires non-empty bounds`() {
        val features = Features()

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            features.getFeaturesInBounds(emptyList())
        }
    }

    @Test
    fun `setFeatureParent links and unlinks features`() {
        val features = Features()
        val parent = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")
        val child = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_markers")

        features.setFeatureParent(child.id, parent.id)

        assertEquals(parent.id, features.getParentFeatureId(child.id))
        assertEquals(setOf(child.id), features.getChildFeatureIds(parent.id))

        features.setFeatureParent(child.id, null)

        assertNull(features.getParentFeatureId(child.id))
        assertTrue(features.getChildFeatureIds(parent.id).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setFeatureParent rejects unknown child`() {
        val features = Features()
        val parent = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")

        features.setFeatureParent("missing", parent.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setFeatureParent rejects unknown parent`() {
        val features = Features()
        val child = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_markers")

        features.setFeatureParent(child.id, "missing")
    }

    @Test
    fun `setFeatureParent rejects cyclic links`() {
        val features = Features()
        val a = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")
        val b = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_markers")
        features.setFeatureParent(b.id, a.id)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            features.setFeatureParent(a.id, b.id)
        }
    }

    @Test
    fun `getDescendantFeatureIds walks the tree breadth-first`() {
        val features = Features()
        val root = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")
        val childA = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_markers")
        val childB = features.addGeoJsonFeature(pointFeature(3.0, 3.0), "gm_markers")
        val grandChild = features.addGeoJsonFeature(pointFeature(4.0, 4.0), "gm_markers")
        features.setFeatureParent(childA.id, root.id)
        features.setFeatureParent(childB.id, root.id)
        features.setFeatureParent(grandChild.id, childA.id)

        assertEquals(setOf(childA.id, childB.id, grandChild.id), features.getDescendantFeatureIds(root.id))
        assertEquals(setOf(grandChild.id), features.getDescendantFeatureIds(childA.id))
        assertTrue(features.getDescendantFeatureIds(grandChild.id).isEmpty())
    }

    @Test
    fun `removeFeature cascades to descendants across sources`() {
        val features = Features()
        val parent = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")
        val child = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_markers")
        val grandChild = features.addGeoJsonFeature(pointFeature(3.0, 3.0), "gm_lines")
        val unrelated = features.addGeoJsonFeature(pointFeature(4.0, 4.0), "gm_markers")
        features.setFeatureParent(child.id, parent.id)
        features.setFeatureParent(grandChild.id, child.id)

        features.removeFeature("gm_polygons", parent.id)

        assertNull(features.getFeature("gm_markers", child.id))
        assertNull(features.getFeature("gm_lines", grandChild.id))
        assertNotNull(features.getFeature("gm_markers", unrelated.id))
        assertNull(features.getParentFeatureId(child.id))
        assertNull(features.getParentFeatureId(grandChild.id))
    }

    @Test
    fun `clearAll clears the relationship registry`() {
        val features = Features()
        val parent = features.addGeoJsonFeature(pointFeature(1.0, 1.0), "gm_polygons")
        val child = features.addGeoJsonFeature(pointFeature(2.0, 2.0), "gm_markers")
        features.setFeatureParent(child.id, parent.id)

        features.clearAll()

        assertNull(features.getParentFeatureId(child.id))
        assertTrue(features.getChildFeatureIds(parent.id).isEmpty())
    }
}
