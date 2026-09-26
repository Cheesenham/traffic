package com.trafficgauge.app.routing

import com.trafficgauge.app.data.GeoUtils
import com.trafficgauge.app.data.NationwideSignalApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loads and caches the intersection list for a given 시/도 (stdgCd), then answers "which
 * signalized intersection is closest to this GPS point" from memory. The API's own only
 * filter is stdgCd (see SidoCodes), so caching is naturally per-province.
 */
class IntersectionRepository(
    private val api: NationwideSignalApiService,
    private val serviceKey: String,
) {
    private val mutex = Mutex()
    private val cacheByStdgCd = mutableMapOf<String, List<IntersectionInfo>>()

    private suspend fun ensureLoaded(stdgCd: String): List<IntersectionInfo> {
        cacheByStdgCd[stdgCd]?.let { return it }
        return mutex.withLock {
            cacheByStdgCd[stdgCd]?.let { return it }
            val loaded = runCatching { loadAllPages(stdgCd) }.getOrElse { emptyList() }
            cacheByStdgCd[stdgCd] = loaded
            loaded
        }
    }

    private suspend fun loadAllPages(stdgCd: String): List<IntersectionInfo> {
        val result = mutableListOf<IntersectionInfo>()
        var page = 1
        while (true) {
            val response = api.getIntersections(serviceKey = serviceKey, stdgCd = stdgCd, pageNo = page)
            if (response.header?.resultCode == NationwideSignalApiService.RESULT_NODATA) break

            val items = response.body?.items?.item.orEmpty()
            if (items.isEmpty()) break

            items.forEach { item ->
                val lat = item["mapCtptIntLat"]?.trim()?.toDoubleOrNull()
                val lon = item["mapCtptIntLot"]?.trim()?.toDoubleOrNull()
                val id = item["crsrdId"]?.trim()
                if (lat != null && lon != null && !id.isNullOrEmpty()) {
                    result.add(
                        IntersectionInfo(
                            intersectionId = id,
                            name = item["crsrdNm"],
                            location = LatLon(lat, lon),
                            orgCode = item["stdgCd"],
                            orgName = item["lclgvNm"],
                            speedLimitKmh = item["lmtSpd"]?.trim()?.toIntOrNull(),
                        ),
                    )
                }
            }

            val totalCount = response.body?.totalCount ?: 0
            if (result.size >= totalCount || items.size < 1000) break
            page++
        }
        return result
    }

    suspend fun findNearest(stdgCd: String, location: LatLon, maxDistanceMeters: Double): IntersectionInfo? {
        val intersections = ensureLoaded(stdgCd)
        return intersections
            .map { it to GeoUtils.distanceMeters(location.lat, location.lon, it.location.lat, it.location.lon) }
            .filter { (_, distance) -> distance <= maxDistanceMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }
}
