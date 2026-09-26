package com.trafficgauge.app.routing

import com.trafficgauge.app.data.GeoUtils
import com.trafficgauge.app.data.NationwideSignalApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loads and caches the nationwide intersection list once, then answers "which signalized
 * intersection is closest to this GPS point" from memory (no network round-trip per lookup).
 *
 * The full list is nationwide and could be large; if it turns out too big to hold/paginate
 * in one shot, this should be changed to a bounding-box query instead — depends on what
 * filter parameters the real API supports (unknown until we have live access, see
 * NationwideSignalApiService's TODOs).
 */
class IntersectionRepository(
    private val api: NationwideSignalApiService,
    private val serviceKey: String,
) {
    private val mutex = Mutex()
    private var cached: List<IntersectionInfo>? = null

    private suspend fun ensureLoaded(): List<IntersectionInfo> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val loaded = runCatching { loadAllPages() }.getOrElse { emptyList() }
            cached = loaded
            loaded
        }
    }

    private suspend fun loadAllPages(): List<IntersectionInfo> {
        val result = mutableListOf<IntersectionInfo>()
        var page = 1
        while (true) {
            val response = api.getIntersections(serviceKey = serviceKey, pageNo = page)
            val items = response.response?.body?.items?.item.orEmpty()
            if (items.isEmpty()) break

            items.forEach { item ->
                val lat = item.latitude()
                val lon = item.longitude()
                val id = item.itstId
                if (lat != null && lon != null && id != null) {
                    result.add(
                        IntersectionInfo(
                            intersectionId = id,
                            name = item.itstNm,
                            location = LatLon(lat, lon),
                            orgCode = item.ctpvCd,
                            orgName = item.ctpvNm,
                            speedLimitKmh = item.spdLmt?.toIntOrNull(),
                        ),
                    )
                }
            }

            val totalCount = response.response?.body?.totalCount ?: 0
            if (result.size >= totalCount) break
            page++
        }
        return result
    }

    suspend fun findNearest(location: LatLon, maxDistanceMeters: Double): IntersectionInfo? {
        val intersections = ensureLoaded()
        return intersections
            .map { it to GeoUtils.distanceMeters(location.lat, location.lon, it.location.lat, it.location.lon) }
            .filter { (_, distance) -> distance <= maxDistanceMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }
}
