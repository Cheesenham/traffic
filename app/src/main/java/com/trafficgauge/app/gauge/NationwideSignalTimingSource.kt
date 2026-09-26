package com.trafficgauge.app.gauge

import com.trafficgauge.app.data.GeoUtils
import com.trafficgauge.app.data.NationwideSignalApiService
import com.trafficgauge.app.routing.ApproachDirection
import com.trafficgauge.app.routing.IntersectionRepository
import com.trafficgauge.app.routing.LatLon
import com.trafficgauge.app.routing.SidoCodes
import com.trafficgauge.app.routing.SignalMovement
import com.trafficgauge.app.routing.SignalSnapshot

/**
 * Real signal data from the nationwide (행정안전부/한국지역정보개발원, KLID) "교차로 신호제어기
 * 실시간 정보" open dataset (data.go.kr B551982/rti).
 *
 * IMPORTANT — real-time coverage is uneven and still rolling out by region (see
 * NationwideSignalApiService's doc comment for what's actually been observed: Ulsan changing
 * live second-by-second, Seoul answering with a single suspiciously-static row). This class
 * returns null whenever it has nothing to say for the given location, and
 * NavigationViewModel wraps it in CompositeSignalTimingSource with
 * AssumedCycleSignalTimingSource as the fallback so the gauge still shows something wherever
 * live coverage isn't there yet.
 *
 * Approach direction is inferred from the bearing driverLocation -> targetLocation (matches
 * how the dataset assigns direction codes: "북(10)쪽 진입 차량" = the vehicle approaching
 * FROM the north). We read the STRAIGHT movement's signal for that direction as an MVP
 * simplification — picking the actual maneuver (e.g. via Tmap's guide-point turnType) would
 * be more correct for a turning movement.
 */
class NationwideSignalTimingSource(
    private val intersectionRepository: IntersectionRepository,
    private val api: NationwideSignalApiService,
    private val serviceKey: String,
    private val matchRadiusMeters: Double = 30.0,
    private val minFetchIntervalMillis: Long = 3000L,
) : SignalTimingSource {

    /** stdgCd values that answered K3(NODATA) at least once — not worth re-polling every tick. */
    private val knownNoDataStdgCd = mutableSetOf<String>()

    private var lastFetchIntersectionId: String? = null
    private var lastFetchAtMillis: Long = 0
    private var lastSnapshot: SignalSnapshot? = null

    override suspend fun greenSecondsRemaining(
        driverLocation: LatLon,
        targetLocation: LatLon,
        nowMillis: Long,
    ): Double? {
        val stdgCd = SidoCodes.stdgCdFor(targetLocation.lat, targetLocation.lon)
        if (stdgCd in knownNoDataStdgCd) return null

        val intersection = intersectionRepository.findNearest(stdgCd, targetLocation, matchRadiusMeters) ?: return null

        val bearing = GeoUtils.bearingDegrees(
            driverLocation.lat, driverLocation.lon,
            targetLocation.lat, targetLocation.lon,
        )
        val direction = ApproachDirection.fromBearing(bearing)

        val snapshot = if (
            intersection.intersectionId != lastFetchIntersectionId ||
            nowMillis - lastFetchAtMillis > minFetchIntervalMillis
        ) {
            fetchSnapshot(stdgCd, intersection.intersectionId, direction, SignalMovement.STRAIGHT)?.also {
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
        stdgCd: String,
        intersectionId: String,
        direction: ApproachDirection,
        movement: SignalMovement,
    ): SignalSnapshot? = runCatching {
        val response = api.getSignalTiming(serviceKey = serviceKey, stdgCd = stdgCd)
        if (response.header?.resultCode == NationwideSignalApiService.RESULT_NODATA) {
            knownNoDataStdgCd.add(stdgCd)
            return null
        }

        val item = response.body?.items?.item.orEmpty().firstOrNull { it["crsrdId"] == intersectionId } ?: return null

        // Real field naming confirmed against a working client: "{dir}{kind}sgSttsNm" /
        // "{dir}{kind}sgRmndCs" — note lowercase "sg" and "Stts"/"Rmnd", not the
        // "Sg"+"Stat"/"Rmdr" this project's earlier Seoul-only attempt assumed.
        val fieldKey = "${direction.fieldPrefix}${movement.fieldInfix}sg"
        val stateName = item["${fieldKey}SttsNm"]
        val remainingRaw = item["${fieldKey}RmndCs"]?.trim()?.toDoubleOrNull()
        // "36001" is the documented sentinel for "unknown" — never a real reading.
        val remainingMillis = remainingRaw?.takeUnless { it == 36001.0 }

        // Unit confirmed empirically against LIVE Ulsan data (not from any doc): polling the
        // same field ~4s apart while its state stayed constant showed it drop by ~3000 each
        // time, and values on freshly-started phases (2000, 109000, 158000, ...) only make
        // physical sense as milliseconds — 1/10s or 1/100s would imply multi-hour phases.
        SignalSnapshot(stateName = stateName, remainingSeconds = remainingMillis?.div(1000.0))
    }.getOrNull()
}
