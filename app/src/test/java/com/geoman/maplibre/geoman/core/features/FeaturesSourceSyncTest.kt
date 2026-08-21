package com.geoman.maplibre.geoman.core.features

import com.geoman.maplibre.geoman.adapter.FeatureStoreRenderer
import com.geoman.maplibre.geoman.adapter.LayerOptions
import com.geoman.maplibre.geoman.adapter.MapLayer
import com.geoman.maplibre.geoman.adapter.MapSource
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.FeatureCollection
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesSourceSyncTest {

    private class FakeRenderer : FeatureStoreRenderer {
        val sources = LinkedHashMap<String, FeatureCollection>()
        val appliedUpdates = mutableListOf<Pair<String, Int>>()
        val createdLayers = mutableListOf<String>()
        var createCount = 0

        override fun getSource(sourceId: String): MapSource? =
            if (sources.containsKey(sourceId)) FakeSource(sourceId) else null

        override fun addSource(sourceId: String, geoJson: FeatureCollection): MapSource {
            createCount++
            sources[sourceId] = geoJson
            return FakeSource(sourceId)
        }

        override fun getLayer(layerId: String): MapLayer? = if (createdLayers.contains(layerId)) FakeLayer else null

        override fun addLayer(options: LayerOptions): MapLayer {
            createdLayers.add(options.id)
            return FakeLayer
        }

        fun recordSetData(sourceName: String, collection: FeatureCollection) {
            appliedUpdates.add(sourceName to collection.features.size)
            sources[sourceName] = collection
        }

        private inner class FakeSource(private val id: String) : MapSource {
            override val sourceId: String = id
            override fun setData(geoJson: FeatureCollection) = recordSetData(id, geoJson)
            override fun getData(): FeatureCollection? = sources[id]
            override fun remove() = Unit
        }

        private object FakeLayer : MapLayer {
            override val layerId: String = "fake"
            override fun setPaintProperty(name: String, value: Any) = Unit
            override fun setLayoutProperty(name: String, value: Any) = Unit
            override fun remove() = Unit
        }
    }

    private fun pointFeature(lng: Double, lat: Double, id: String? = null) = Feature(
        id = id,
        geometry = Point.fromLngLat(LngLat(lng, lat)),
    )

    private fun featuresWith(renderer: FakeRenderer, scope: CoroutineScope) = Features(
        updateScope = scope,
        debounceMs = 50,
    ).also { it.init(renderer) }

    @Test
    fun `first feature creates its source synchronously`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)

        features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        runCurrent()

        assertEquals(1, renderer.createCount)
        assertEquals(1, renderer.sources["gm_markers"]?.features?.size)
    }

    @Test
    fun `subsequent updates are debounced and only the latest lands`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)
        features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")

        repeat(5) { index ->
            features.addGeoJsonFeature(pointFeature(3.0 + index, 4.0), "gm_markers")
        }
        runCurrent()
        assertEquals(0, renderer.appliedUpdates.size)

        advanceTimeBy(50)
        runCurrent()

        assertEquals(1, renderer.appliedUpdates.size)
        assertEquals(6, renderer.sources["gm_markers"]?.features?.size)
    }

    @Test
    fun `flushPendingUpdates applies immediately without waiting`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)
        features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        features.addGeoJsonFeature(pointFeature(3.0, 4.0), "gm_markers")
        runCurrent()

        features.flushPendingUpdates()

        assertEquals(1, renderer.appliedUpdates.size)
        assertEquals(2, renderer.sources["gm_markers"]?.features?.size)
    }

    @Test
    fun `shutdown flushes pending updates and detaches the renderer`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)
        features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        features.addGeoJsonFeature(pointFeature(3.0, 4.0), "gm_markers")
        runCurrent()

        features.shutdown()

        assertEquals(1, renderer.appliedUpdates.size)
        assertEquals(2, renderer.sources["gm_markers"]?.features?.size)

        // Further mutations no longer touch the map
        features.addGeoJsonFeature(pointFeature(5.0, 6.0), "gm_markers")
        advanceTimeBy(200)
        assertEquals(1, renderer.appliedUpdates.size)
    }

    @Test
    fun `removing a feature schedules a debounced update`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)
        val added = features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_markers")
        runCurrent()
        assertEquals(0, renderer.appliedUpdates.size)

        features.removeFeature("gm_markers", added.id)
        advanceTimeBy(50)
        runCurrent()

        assertEquals(1, renderer.appliedUpdates.size)
        assertEquals(0, renderer.sources["gm_markers"]?.features?.size)
    }

    @Test
    fun `layers are registered once per source at creation`() = runTest {
        val renderer = FakeRenderer()
        val features = featuresWith(renderer, backgroundScope)

        features.addGeoJsonFeature(pointFeature(1.0, 2.0), "gm_lines")
        features.flushPendingUpdates()

        assertEquals(listOf("gm_lines_line"), renderer.createdLayers)
        assertNotNull(renderer.getLayer("gm_lines_line"))
        assertNull(renderer.getLayer("gm_markers_symbol"))
        assertEquals(1, renderer.createCount)
    }
}
