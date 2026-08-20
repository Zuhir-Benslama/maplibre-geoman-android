package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanLogger
import org.json.JSONArray
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer

/**
 * MapLibre layer implementation for SDK 11.x
 */
class MapLibreLayer(private val geoman: Geoman, private val options: LayerOptions, private val map: MapLibreMap) :
    MapLayer {

    override val layerId: String = options.id

    private var isAdded = false

    init {
        add()
    }

    private fun add() {
        if (isAdded) return

        val style = map.style ?: return

        val layer = createLayer() ?: return

        applySourceLayer(layer)
        applyProperties(layer, options.paint)
        applyProperties(layer, options.layout)
        applyFilter(layer)
        applyZoomBounds(layer)

        try {
            style.addLayer(layer)
            isAdded = true
        } catch (e: IllegalStateException) {
            GeomanLogger.w("MapLibreLayer", "Failed to add layer $layerId", e)
        }
    }

    private fun createLayer(): org.maplibre.android.style.layers.Layer? = when (options.type) {
        LayerType.FILL -> FillLayer(options.id, options.source)
        LayerType.LINE -> LineLayer(options.id, options.source)
        LayerType.CIRCLE -> CircleLayer(options.id, options.source)
        LayerType.SYMBOL -> SymbolLayer(options.id, options.source)
        else -> null
    }

    private fun applySourceLayer(layer: org.maplibre.android.style.layers.Layer) {
        options.sourceLayer?.let {
            when (layer) {
                is FillLayer -> layer.sourceLayer = it
                is LineLayer -> layer.sourceLayer = it
                is CircleLayer -> layer.sourceLayer = it
                is SymbolLayer -> layer.sourceLayer = it
            }
        }
    }

    private fun applyProperties(layer: org.maplibre.android.style.layers.Layer, properties: Map<String, Any>) {
        val propertyValues = mutableListOf<PropertyValue<*>>()
        properties.forEach { (name, value) ->
            valueToPropertyValue(name, value)?.let { propertyValues.add(it) }
        }
        if (propertyValues.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            when (layer) {
                is FillLayer -> layer.withProperties(*propertyValues.toTypedArray())
                is LineLayer -> layer.withProperties(*propertyValues.toTypedArray())
                is CircleLayer -> layer.withProperties(*propertyValues.toTypedArray())
                is SymbolLayer -> layer.withProperties(*propertyValues.toTypedArray())
            }
        }
    }

    private fun applyFilter(layer: org.maplibre.android.style.layers.Layer) {
        options.filter?.let { filter ->
            try {
                val expression = parseExpression(filter)
                expression?.let { expr ->
                    when (layer) {
                        is FillLayer -> layer.setFilter(expr)
                        is LineLayer -> layer.setFilter(expr)
                        is CircleLayer -> layer.setFilter(expr)
                        is SymbolLayer -> layer.setFilter(expr)
                    }
                }
            } catch (e: IllegalArgumentException) {
                GeomanLogger.w("MapLibreLayer", "Filter conversion failed for layer $layerId", e)
            }
        }
    }

    private fun applyZoomBounds(layer: org.maplibre.android.style.layers.Layer) {
        options.minZoom?.let { layer.minZoom = it }
        options.maxZoom?.let { layer.maxZoom = it }
    }

    private fun valueToPropertyValue(name: String, value: Any): PropertyValue<*>? = when (value) {
        is String -> PropertyValue(name, value)
        is Boolean -> PropertyValue(name, value)
        is Int -> PropertyValue(name, value.toFloat())
        is Float -> PropertyValue(name, value)
        is Double -> PropertyValue(name, value)
        is Array<*> -> PropertyValue(name, value.map { it?.toString() ?: "" }.toTypedArray())
        is List<*> -> PropertyValue(name, value.map { it?.toString() ?: "" }.toTypedArray())
        else -> PropertyValue(name, value.toString())
    }

    private fun parseExpression(filter: List<Any>): Expression? = try {
        Expression.Converter.convert(JSONArray(filter).toString())
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        GeomanLogger.w("MapLibreLayer", "Failed to parse filter expression for layer $layerId", e)
        null
    }

    override fun setPaintProperty(name: String, value: Any) {
        val style = map.style ?: return
        val layer = style.getLayer(layerId) ?: return

        val property = valueToPropertyValue(name, value) ?: return
        @Suppress("UNCHECKED_CAST")
        when (layer) {
            is FillLayer -> layer.setProperties(property)
            is LineLayer -> layer.setProperties(property)
            is CircleLayer -> layer.setProperties(property)
            is SymbolLayer -> layer.setProperties(property)
        }
    }

    override fun setLayoutProperty(name: String, value: Any) {
        val style = map.style ?: return
        val layer = style.getLayer(layerId) ?: return

        val property = valueToPropertyValue(name, value) ?: return
        @Suppress("UNCHECKED_CAST")
        when (layer) {
            is FillLayer -> layer.setProperties(property)
            is LineLayer -> layer.setProperties(property)
            is CircleLayer -> layer.setProperties(property)
            is SymbolLayer -> layer.setProperties(property)
        }
    }

    override fun remove() {
        try {
            map.style?.removeLayer(layerId)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            GeomanLogger.d("MapLibreLayer", "Layer $layerId may not exist during removal: ${e.message}")
        }
        isAdded = false
    }
}
