package com.trafficgauge.app.routing

import com.trafficgauge.app.data.NetworkModule
import com.trafficgauge.app.data.RouteRequest

data class LatLon(val lat: Double, val lon: Double)

/** A guide/turn point along the route — the closest proxy Tmap's directions API gives us
 * for "an intersection ahead"; it is not guaranteed to have a traffic signal. */
data class GuidePoint(
    val location: LatLon,
    val description: String?,
)

data class RouteResult(
    val polyline: List<LatLon>,
    val guidePoints: List<GuidePoint>,
)

data class PoiResult(
    val name: String,
    val location: LatLon,
    val address: String?,
)

class RouteRepository(private val appKey: String) {

    private val api = NetworkModule.tmapApi

    suspend fun searchDestination(keyword: String): List<PoiResult> {
        if (appKey.isBlank()) return emptyList()
        return runCatching {
            val response = api.searchPoi(appKey = appKey, searchKeyword = keyword)
            response.searchPoiInfo?.pois?.poi.orEmpty().mapNotNull { poi ->
                val lat = poi.latitude() ?: return@mapNotNull null
                val lon = poi.longitude() ?: return@mapNotNull null
                PoiResult(
                    name = poi.name ?: "이름 없음",
                    location = LatLon(lat, lon),
                    address = poi.newAddressList?.newAddress?.firstOrNull()?.fullAddressRoad,
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchRoute(start: LatLon, destination: LatLon): RouteResult? {
        if (appKey.isBlank()) return null
        return runCatching {
            val response = api.findCarRoute(
                appKey = appKey,
                request = RouteRequest(
                    startX = start.lon.toString(),
                    startY = start.lat.toString(),
                    endX = destination.lon.toString(),
                    endY = destination.lat.toString(),
                ),
            )

            val polyline = mutableListOf<LatLon>()
            val guidePoints = mutableListOf<GuidePoint>()

            for (feature in response.features) {
                when (feature.geometry.type) {
                    "LineString" -> {
                        @Suppress("UNCHECKED_CAST")
                        val coords = feature.geometry.coordinates as? List<List<Double>> ?: continue
                        coords.forEach { pair ->
                            if (pair.size >= 2) polyline.add(LatLon(lat = pair[1], lon = pair[0]))
                        }
                    }
                    "Point" -> {
                        @Suppress("UNCHECKED_CAST")
                        val coord = feature.geometry.coordinates as? List<Double> ?: continue
                        if (coord.size < 2) continue
                        val point = LatLon(lat = coord[1], lon = coord[0])
                        if (feature.properties?.pointType == "GP") {
                            guidePoints.add(GuidePoint(point, feature.properties.description))
                        }
                    }
                }
            }

            RouteResult(polyline, guidePoints)
        }.getOrNull()
    }
}
