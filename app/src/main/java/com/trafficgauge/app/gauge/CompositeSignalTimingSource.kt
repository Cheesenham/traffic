package com.trafficgauge.app.gauge

import com.trafficgauge.app.routing.LatLon

/**
 * Tries [primary] first, falls back to [fallback] when it has no real data for this location.
 * Needed because the real nationwide feed (NationwideSignalTimingSource) currently only has
 * live coverage in 울산광역시 — everywhere else it returns null, and without a fallback the
 * gauge would simply go blank outside Ulsan.
 */
class CompositeSignalTimingSource(
    private val primary: SignalTimingSource,
    private val fallback: SignalTimingSource,
) : SignalTimingSource {
    override suspend fun greenSecondsRemaining(driverLocation: LatLon, targetLocation: LatLon, nowMillis: Long): Double? =
        primary.greenSecondsRemaining(driverLocation, targetLocation, nowMillis)
            ?: fallback.greenSecondsRemaining(driverLocation, targetLocation, nowMillis)
}
