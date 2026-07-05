package com.geoman.maplibre.geoman.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.geoman.maplibre.geoman.R
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre popup implementation
 */
class MapLibrePopup(
    map: MapLibreMap,
    private val context: Context,
    private var options: PopupOptions,
    private var lngLat: LngLat? = null,
) : Popup(map) {

    private val mapLibreMap: MapLibreMap = map
    private var popupWindow: PopupWindow? = null
    private var contentView: View? = null
    private var isAdded = false

    override fun getLngLat(): LngLat? = lngLat

    override fun setLngLat(lngLat: LngLat): Popup {
        this.lngLat = lngLat

        // Update popup position if already shown
        if (isAdded && popupWindow?.isShowing == true) {
            updatePosition()
        }

        return this
    }

    override fun getContent(): String = options.content

    override fun setContent(content: String): Popup {
        options = options.copy(content = content)

        // Update content view if popup is showing
        if (isAdded && popupWindow?.isShowing == true) {
            updateContent()
        }

        return this
    }

    override fun addToMap(): Popup {
        if (isAdded) return this

        contentView = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)

        // Set content
        updateContent()

        // Create popup window
        popupWindow = PopupWindow(
            contentView,
            options.maxWidth.toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true, // Focusable
        ).apply {
            isOutsideTouchable = options.closeOnClick
            animationStyle = android.R.style.Animation_Dialog
        }

        // Set up close button if enabled
        if (options.closeButton) {
            contentView?.findViewById<View>(R.id.popup_close_button)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    close()
                }
            }
        } else {
            contentView?.findViewById<View>(R.id.popup_close_button)?.visibility = View.GONE
        }

        isAdded = true

        // Show popup if we have a location
        lngLat?.let { showAtLocation(it) }

        return this
    }

    private fun showAtLocation(lngLat: LngLat) {
        val screenPoint = mapLibreMap.projection.toScreenLocation(
            LatLng(lngLat.latitude, lngLat.longitude),
        )

        contentView?.let { content ->
            popupWindow?.showAsDropDown(
                content,
                screenPoint.x.toInt(),
                screenPoint.y.toInt() - content.measuredHeight - 20,
            )
        }
    }

    private fun updatePosition() {
        lngLat?.let {
            popupWindow?.dismiss()
            showAtLocation(it)
        }
    }

    private fun updateContent() {
        contentView?.findViewById<TextView>(R.id.popup_content)?.text = options.content
    }

    override fun remove() {
        close()
        isAdded = false
    }

    override fun isOpen(): Boolean = popupWindow?.isShowing == true

    override fun close(): Popup {
        popupWindow?.dismiss()
        return this
    }
}
