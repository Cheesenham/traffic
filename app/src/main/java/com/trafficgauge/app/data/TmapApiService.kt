package com.trafficgauge.app.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Tmap Directions REST API (apis.openapi.sk.com) — no native SDK dependency required,
 * so the app doesn't depend on Tmap's Maven repository coordinates.
 *
 * Docs: SK Open API console, "보행자/자동차 경로안내" under the app's product list.
 * The response model here is intentionally minimal; extend RouteResponse as needed once
 * you see real payloads back from your app key.
 */
interface TmapApiService {

    @POST("tmap/routes?version=1")
    suspend fun findCarRoute(
        @Header("appKey") appKey: String,
        @Body request: RouteRequest,
    ): RouteResponse
}

data class RouteRequest(
    val startX: String,
    val startY: String,
    val endX: String,
    val endY: String,
    val reqCoordType: String = "WGS84GEO",
    val resCoordType: String = "WGS84GEO",
    val searchOption: String = "0",
)

data class RouteResponse(
    val features: List<RouteFeature> = emptyList(),
)

data class RouteFeature(
    val geometry: RouteGeometry,
    val properties: Map<String, Any?> = emptyMap(),
)

data class RouteGeometry(
    val type: String,
    val coordinates: List<List<Double>>,
)
