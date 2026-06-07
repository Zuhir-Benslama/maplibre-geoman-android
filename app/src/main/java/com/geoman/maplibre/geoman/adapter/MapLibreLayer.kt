package com.geoman.maplibre.geoman.adapter

import com.geoman.maplibre.geoman.Geoman
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * MapLibre layer implementation for SDK 11.x
 */
class MapLibreLayer(
    private val geoman: Geoman,
    private val options: LayerOptions,
    private val map: MapLibreMap
) : MapLayer {

    override val layerId: String = options.id

    private var isAdded = false

    init {
        add()
    }

    private fun add() {
        val style = map.style ?: return

        val layer = when (options.type) {
            LayerType.FILL -> FillLayer(options.id, options.source)
            LayerType.LINE -> LineLayer(options.id, options.source)
            LayerType.CIRCLE -> CircleLayer(options.id, options.source)
            LayerType.SYMBOL -> SymbolLayer(options.id, options.source)
            else -> null
        }

        layer ?: return

        // Apply source layer if specified (SDK 11.x uses sourceLayer property)
        options.sourceLayer?.let { 
            when (layer) {
                is FillLayer -> layer.sourceLayer = it
                is LineLayer -> layer.sourceLayer = it
                is CircleLayer -> layer.sourceLayer = it
                is SymbolLayer -> layer.sourceLayer = it
            }
        }

        // Apply paint properties
        val paintProperties = mutableListOf<PropertyValue<*>>()
        options.paint.forEach { (name, value) ->
            valueToPropertyValue(name, value)?.let { paintProperties.add(it) }
        }

        if (paintProperties.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            when (layer) {
                is FillLayer -> layer.withProperties(*paintProperties.toTypedArray())
                is LineLayer -> layer.withProperties(*paintProperties.toTypedArray())
                is CircleLayer -> layer.withProperties(*paintProperties.toTypedArray())
                is SymbolLayer -> layer.withProperties(*paintProperties.toTypedArray())
            }
        }

        // Apply layout properties
        val layoutProperties = mutableListOf<PropertyValue<*>>()
        options.layout.forEach { (name, value) ->
            valueToPropertyValue(name, value)?.let { layoutProperties.add(it) }
        }

        if (layoutProperties.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            when (layer) {
                is FillLayer -> layer.withProperties(*layoutProperties.toTypedArray())
                is LineLayer -> layer.withProperties(*layoutProperties.toTypedArray())
                is CircleLayer -> layer.withProperties(*layoutProperties.toTypedArray())
                is SymbolLayer -> layer.withProperties(*layoutProperties.toTypedArray())
            }
        }

        // Apply filter if specified (SDK 11.x uses setFilter with Expression)
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
            } catch (e: Exception) {
                // Filter conversion failed
            }
        }

        // Apply zoom bounds
        options.minZoom?.let { layer.minZoom = it }
        options.maxZoom?.let { layer.maxZoom = it }

        try {
            style.addLayer(layer)
            isAdded = true
        } catch (e: Exception) {
            // Layer may already exist
        }
    }

    private fun valueToPropertyValue(name: String, value: Any): PropertyValue<*>? {
        return when (value) {
            is String -> PropertyValue(name, value)
            is Int -> PropertyValue(name, value.toFloat())
            is Float -> PropertyValue(name, value)
            is Double -> PropertyValue(name, value.toFloat())
            is Boolean -> PropertyValue(name, value.toString())
            is Array<*> -> PropertyValue(name, value.map { it?.toString() ?: "" }.toTypedArray())
            is List<*> -> PropertyValue(name, value.map { it?.toString() ?: "" }.toTypedArray())
            else -> PropertyValue(name, value.toString())
        }
    }

    private fun parseExpression(filter: Any): Expression? {
        // Simple filter parsing - in production, use proper Expression parsing
        return try {
            Expression.Converter.convert(filter.toString())
        } catch (e: Exception) {
            null
        }
    }

    override fun setPaintProperty(name: String, value: Any) {
        val style = map.style ?: return
        val layer = style.getLayer(layerId) ?: return
        
        val property = valueToPropertyValue(name, value) ?: return
        @Suppress("UNCHECKED_CAST")
        when (layer) {
            is FillLayer -> layer.withProperties(property)
            is LineLayer -> layer.withProperties(property)
            is CircleLayer -> layer.withProperties(property)
            is SymbolLayer -> layer.withProperties(property)
        }
    }

    override fun setLayoutProperty(name: String, value: Any) {
        setPaintProperty(name, value)
    }

    override fun remove() {
        try {
            map.style?.removeLayer(layerId)
        } catch (e: Exception) {
            // Layer may not exist
        }
        isAdded = false
    }
}
