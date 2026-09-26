package com.trafficgauge.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Tmap Directions + POI Search REST APIs (apis.openapi.sk.com) — plain HTTP, no native
 * SDK dependency required.
 *
 * NOTE: the response field names below (searchPoiInfo/pois/poi, noorLat/noorLon, and the
 * route feature properties) are written from documented Tmap conventions but are NOT yet
 * verified against a live response in this environment (no network access to Tmap's docs
 * or endpoints here). Parsing is defensive (nullable fields, try/catch at the call site) so
 * a mismatch fails soft instead of crashing — first real test run should confirm field names
 * against your actual appKey's responses and this file should be adjusted if they differ.
 */
interface TmapApiService {

    @POST("tmap/routes?version=1")
    suspend fun findCarRoute(
        @Header("appKey") appKey: String,
        @Body request: RouteRequest,
    ): RouteResponse

    @GET("tmap/pois")
    suspend fun searchPoi(
        @Header("appKey") appKey: String,
        @Query("version") version: String = "1",
        @Query("format") format: String = "json",
        @Query("searchKeyword") searchKeyword: String,
        @Query("resCoordType") resCoordType: String = "WGS84GEO",
        @Query("reqCoordType") reqCoordType: String = "WGS84GEO",
        @Query("count") count: Int = 10,
    ): PoiSearchResponse
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
    val properties: RouteProperties? = null,
)

data class RouteGeometry(
    val type: String,
    val coordinates: List<Any> = emptyList(),
)

data class RouteProperties(
    val pointType: String? = null,
    val turnType: Int? = null,
    val distance: Double? = null,
    val time: Double? = null,
    val description: String? = null,
)

data class PoiSearchResponse(
    val searchPoiInfo: SearchPoiInfo? = null,
)

data class SearchPoiInfo(
    val pois: PoiList? = null,
)

data class PoiList(
    val poi: List<Poi> = emptyList(),
)

data class Poi(
    val name: String? = null,
    val noorLat: String? = null,
    val noorLon: String? = null,
    val newAddressList: NewAddressList? = null,
) {
    fun latitude(): Double? = noorLat?.toDoubleOrNull()
    fun longitude(): Double? = noorLon?.toDoubleOrNull()
}

data class NewAddressList(
    val newAddress: List<NewAddress> = emptyList(),
)

data class NewAddress(
    val fullAddressRoad: String? = null,
)
