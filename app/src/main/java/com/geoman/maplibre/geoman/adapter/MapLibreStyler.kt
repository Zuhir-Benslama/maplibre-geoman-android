package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import com.geoman.maplibre.geoman.GeomanLogger
import kotlinx.coroutines.CancellationException
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre implementation of image styling.
 */
class MapLibreStyler(private val map: MapLibreMap) : MapStyling {

    override suspend fun loadImage(id: String, image: Bitmap) {
        map.style?.addImage(id, image)
    }

    override fun removeImage(id: String) {
        try {
            map.style?.removeImage(id)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (e is CancellationException) throw e
            GeomanLogger.d("MapLibreAdapter", "Failed to remove image $id: ${e.message}")
        }
    }
}
