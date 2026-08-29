package com.geoman.maplibre.geoman.adapter

import android.graphics.PointF
import com.geoman.maplibre.geoman.types.geojson.LatLngBounds
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.ScreenPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre implementation of viewport and camera operations.
 */
class MapLibreViewport(private val map: MapLibreMap) : MapViewport {

    private companion object {
        const val DEFAULT_CAMERA_PADDING = 100
    }

    override fun getBounds(): LatLngBounds {
        val projection = map.projection
        val visibleRegion = projection.visibleRegion

        val farRight = visibleRegion.farRight
        val nearLeft = visibleRegion.nearLeft

        val northeast = LngLat(
            farRight?.longitude ?: 0.0,
            farRight?.latitude ?: 0.0,
        )
        val southwest = LngLat(
            nearLeft?.longitude ?: 0.0,
            nearLeft?.latitude ?: 0.0,
        )

        return LatLngBounds(northeast = northeast, southwest = southwest)
    }

    override fun fitBounds(bounds: LatLngBounds, options: FitBoundsOptions?) {
        val latLngBounds = org.maplibre.android.geometry.LatLngBounds.from(
            bounds.northeast.latitude,
            bounds.northeast.longitude,
            bounds.southwest.latitude,
            bounds.southwest.longitude,
        )

        val cameraUpdate = if (options != null) {
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                latLngBounds,
                options.padding.toInt(),
            )
        } else {
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(latLngBounds, DEFAULT_CAMERA_PADDING)
        }

        map.animateCamera(cameraUpdate)
    }

    override fun project(position: LngLat): ScreenPoint {
        val latLng = LatLng(position.latitude, position.longitude)
        val point = map.projection.toScreenLocation(latLng)
        return ScreenPoint(point.x, point.y)
    }

    override fun unproject(point: ScreenPoint): LngLat {
        val screenPoint = PointF(point.x, point.y)
        val latLng = map.projection.fromScreenLocation(screenPoint)
        return LngLat(latLng.longitude, latLng.latitude)
    }

    override fun coordBoundsToScreenBounds(bounds: LatLngBounds): Pair<ScreenPoint, ScreenPoint> {
        val sw = project(bounds.southwest)
        val ne = project(bounds.northeast)
        return sw to ne
    }
}
