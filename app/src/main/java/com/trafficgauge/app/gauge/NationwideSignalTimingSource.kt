package com.trafficgauge.app.gauge

import com.trafficgauge.app.data.GeoUtils
import com.trafficgauge.app.data.NationwideSignalApiService
import com.trafficgauge.app.routing.ApproachDirection
import com.trafficgauge.app.routing.IntersectionRepository
import com.trafficgauge.app.routing.LatLon
import com.trafficgauge.app.routing.SignalMovement
import com.trafficgauge.app.routing.SignalSnapshot

/**
 * Real signal data from the nationwide (행정안전부/한국지역정보개발원) "교차로 신호제어기
 * 신호잔여시간 정보" open dataset — see NationwideSignalApiService for the caveats on exact
 * field names/paths that still need verifying against a live response.
 *
 * Approach direction is inferred from the bearing driverLocation -> targetLocation (matches
 * how the dataset assigns direction codes: "북(10)쪽 진입 차량" = the vehicle approaching
 * FROM the north). We read the STRAIGHT movement's signal for that direction as an MVP
 * simplification — a real implementation should use the route's actual turn type (from
 * Tmap's guide point "turnType") to pick LEFT_TURN/etc. instead when the maneuver isn't
 * straight through.
 */
class NationwideSignalTimingSource(
    private val intersectionRepository: IntersectionRepository,
    private val api: NationwideSignalApiService,
    private val serviceKey: String,
    private val matchRadiusMeters: Double = 30.0,
    private val minFetchIntervalMillis: Long = 3000L,
) : SignalTimingSource {

    private var lastFetchIntersectionId: String? = null
    private var lastFetchAtMillis: Long = 0
    private var lastSnapshot: SignalSnapshot? = null

    override suspend fun greenSecondsRemaining(
        driverLocation: LatLon,
        targetLocation: LatLon,
        nowMillis: Long,
    ): Double? {
        val intersection = intersectionRepository.findNearest(targetLocation, matchRadiusMeters) ?: return null

        val bearing = GeoUtils.bearingDegrees(
            driverLocation.lat, driverLocation.lon,
            targetLocation.lat, targetLocation.lon,
        )
        val direction = ApproachDirection.fromBearing(bearing)

        val snapshot = if (
            intersection.intersectionId != lastFetchIntersectionId ||
            nowMillis - lastFetchAtMillis > minFetchIntervalMillis
        ) {
            fetchSnapshot(intersection.intersectionId, direction, SignalMovement.STRAIGHT)?.also {
                lastSnapshot = it
                lastFetchAtMillis = nowMillis
                lastFetchIntersectionId = intersection.intersectionId
            } ?: lastSnapshot
        } else {
            lastSnapshot
        } ?: return null

        if (!snapshot.isGreen) return 0.0

        val secondsSinceFetch = (nowMillis - lastFetchAtMillis) / 1000.0
        val remaining = (snapshot.remainingSeconds ?: 0.0) - secondsSinceFetch
        return remaining.coerceAtLeast(0.0)
    }

    private suspend fun fetchSnapshot(
        intersectionId: String,
        direction: ApproachDirection,
        movement: SignalMovement,
    ): SignalSnapshot? = runCatching {
        val response = api.getSignalTiming(serviceKey = serviceKey, intersectionId = intersectionId)
        val item = response.response?.body?.items?.item?.firstOrNull() ?: return null

        val fieldKey = "${direction.fieldPrefix}${movement.fieldInfix}Sg"
        val stateName = item["${fieldKey}StatNm"] as? String
        // Field name suggests centiseconds ("Cs"), but the Seoul dataset's own description
        // text says "1/10초" (deciseconds) — using deciseconds here; verify against a real
        // response and switch the divisor to 100.0 if it turns out to actually be centiseconds.
        val remainingRaw = (item["${fieldKey}RmdrCs"] as? Number)?.toDouble()
        SignalSnapshot(stateName = stateName, remainingSeconds = remainingRaw?.div(10.0))
    }.getOrNull()
}
