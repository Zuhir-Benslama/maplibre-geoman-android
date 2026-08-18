package com.geoman.maplibre.geoman.adapter

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.geoman.maplibre.geoman.R
import com.geoman.maplibre.geoman.types.geojson.LngLat
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
    private val mapView: ViewGroup,
) : Popup(map) {

    private val mapLibreMap: MapLibreMap = map
    private var popupWindow: PopupWindow? = null
    private var contentView: View? = null
    private var isAdded = false
    private var cameraMoveListener: MapLibreMap.OnCameraMoveListener? = null

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

        contentView = LayoutInflater.from(context).inflate(R.layout.popup_layout, mapView, false)

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

        // Keep the popup anchored to its location while the camera moves
        cameraMoveListener = MapLibreMap.OnCameraMoveListener {
            updatePosition()
        }
        mapLibreMap.addOnCameraMoveListener(cameraMoveListener!!)

        isAdded = true

        // Show popup if we have a location
        lngLat?.let { showAtLocation(it) }

        return this
    }

    private fun showAtLocation(lngLat: LngLat) {
        val screenPoint = mapLibreMap.projection.toScreenLocation(
            LatLng(lngLat.latitude, lngLat.longitude),
        )
        val content = contentView ?: return

        content.measure(
            View.MeasureSpec.makeMeasureSpec(options.maxWidth.toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val x = screenPoint.x.toInt()
        val y = when (options.anchor) {
            MarkerAnchor.BOTTOM, MarkerAnchor.BOTTOM_LEFT, MarkerAnchor.BOTTOM_RIGHT ->
                (screenPoint.y - content.measuredHeight - ANCHOR_GAP_PX).toInt()

            MarkerAnchor.TOP, MarkerAnchor.TOP_LEFT, MarkerAnchor.TOP_RIGHT ->
                (screenPoint.y + ANCHOR_GAP_PX).toInt()

            else -> screenPoint.y.toInt()
        }

        popupWindow?.showAtLocation(mapView, Gravity.TOP or Gravity.START, x, y)
    }

    private fun updatePosition() {
        if (popupWindow?.isShowing != true) return
        lngLat?.let { showAtLocation(it) }
    }

    private fun updateContent() {
        contentView?.findViewById<TextView>(R.id.popup_content)?.text = options.content
    }

    override fun remove() {
        cameraMoveListener?.let { mapLibreMap.removeOnCameraMoveListener(it) }
        cameraMoveListener = null
        close()
        isAdded = false
    }

    override fun isOpen(): Boolean = popupWindow?.isShowing == true

    override fun close(): Popup {
        popupWindow?.dismiss()
        return this
    }

    private companion object {
        const val ANCHOR_GAP_PX = 10
    }
}
