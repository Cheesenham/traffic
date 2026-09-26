package com.trafficgauge.app.gauge

import com.trafficgauge.app.routing.LatLon

/**
 * Supplies "how many seconds of green are left on the light ahead", for whichever source of
 * signal data is wired in. Returns null when no data is available for this location (caller
 * should fall back to not showing a gauge rather than a fabricated number).
 */
interface SignalTimingSource {
    suspend fun greenSecondsRemaining(driverLocation: LatLon, targetLocation: LatLon, nowMillis: Long): Double?
}

/**
 * PLACEHOLDER fallback for when no real signal feed is configured (no data.go.kr service key
 * set yet — see NationwideSignalTimingSource for the real implementation). Assumes a generic
 * signal cycle purely so the gauge has a plausible, moving number to render and test against;
 * this is not measured data and should never be trusted for an actual go/no-go decision.
 */
class AssumedCycleSignalTimingSource(
    private val cycleSeconds: Double = 120.0,
    private val greenShareOfCycle: Double = 0.5,
) : SignalTimingSource {

    // Deterministic per-intersection phase offset so different signals don't all appear
    // in sync — still an assumption, not measured data.
    private fun phaseOffsetSeconds(targetLocation: LatLon): Double {
        val id = "${targetLocation.lat},${targetLocation.lon}"
        return (id.hashCode().mod(cycleSeconds.toInt())).toDouble()
    }

    override suspend fun greenSecondsRemaining(driverLocation: LatLon, targetLocation: LatLon, nowMillis: Long): Double {
        val greenSeconds = cycleSeconds * greenShareOfCycle
        val elapsedInCycle = ((nowMillis / 1000.0) + phaseOffsetSeconds(targetLocation)) % cycleSeconds
        return if (elapsedInCycle < greenSeconds) {
            greenSeconds - elapsedInCycle
        } else {
            0.0 // currently red in this assumed model
        }
    }
}
