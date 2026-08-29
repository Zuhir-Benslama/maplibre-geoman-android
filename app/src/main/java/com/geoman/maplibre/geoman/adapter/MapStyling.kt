package com.geoman.maplibre.geoman.adapter

import android.graphics.Bitmap
import com.geoman.maplibre.geoman.types.CursorType

/**
 * Image styling and cursor contract for map adapters.
 */
interface MapStyling {
    /**
     * Load an image for use in markers/icons
     */
    suspend fun loadImage(id: String, image: Bitmap)

    /**
     * Remove a loaded image
     */
    fun removeImage(id: String)

    /**
     * Set the map cursor. A no-op on Android where the cursor is handled by
     * the system.
     */
    fun setCursor(cursor: CursorType) {}
}
