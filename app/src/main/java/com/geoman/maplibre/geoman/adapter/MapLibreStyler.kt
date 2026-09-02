package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import com.geoman.maplibre.geoman.GeomanLogger
import com.geoman.maplibre.geoman.utils.runCatchingRethrowCancellation
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre implementation of image styling.
 */
class MapLibreStyler(private val map: MapLibreMap) : MapStyling {

    override suspend fun loadImage(id: String, image: Bitmap) {
        map.style?.addImage(id, image)
    }

    override fun removeImage(id: String) {
        runCatchingRethrowCancellation(
            onError = { GeomanLogger.d("MapLibreAdapter", "Failed to remove image $id: ${it.message}") },
        ) {
            map.style?.removeImage(id)
        }
    }
}
