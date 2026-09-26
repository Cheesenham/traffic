package com.trafficgauge.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.trafficgauge.app.routing.GuidePoint
import com.trafficgauge.app.routing.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

fun configureOsmdroid(context: Context) {
    // Use app-private storage for the tile cache so we don't need WRITE_EXTERNAL_STORAGE.
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE),
    )
    Configuration.getInstance().osmdroidBasePath = context.filesDir
    Configuration.getInstance().osmdroidTileCache = context.cacheDir
    Configuration.getInstance().userAgentValue = context.packageName
}

@Composable
fun MapScreen(
    currentLocation: LatLon?,
    routePolyline: List<LatLon>,
    nextGuidePoint: GuidePoint?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            currentLocation?.let { loc ->
                mapView.controller.setCenter(GeoPoint(loc.lat, loc.lon))
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = GeoPoint(loc.lat, loc.lon)
                        title = "현재 위치"
                    },
                )
            }

            if (routePolyline.isNotEmpty()) {
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        setPoints(routePolyline.map { GeoPoint(it.lat, it.lon) })
                    },
                )
            }

            nextGuidePoint?.let { guide ->
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = GeoPoint(guide.location.lat, guide.location.lon)
                        title = guide.description ?: "다음 교차로"
                    },
                )
            }

            mapView.invalidate()
        },
    )
}
