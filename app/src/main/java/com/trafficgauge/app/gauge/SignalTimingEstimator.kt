package com.trafficgauge.app.gauge

/**
 * PLACEHOLDER for real signal timing data.
 *
 * No nationwide, free, real-time "seconds remaining on this specific traffic light" API is
 * available in Korea today (see project plan: C-ITS SPaT/MAP is limited to a handful of
 * pilot-zone intersections; ITS national portal doesn't expose per-signal countdowns).
 * This class assumes a generic signal cycle so the gauge has a plausible, moving number to
 * render and test against, and should be replaced by a real SPaT feed wherever one becomes
 * available for the user's target intersections — see SignalTimingSource for the interface
 * a real implementation should satisfy.
 */
interface SignalTimingSource {
    /** Estimated seconds remaining in the current green phase for the given intersection. */
    fun greenSecondsRemaining(guidePointId: String, nowMillis: Long): Double
}

class AssumedCycleSignalTimingSource(
    private val cycleSeconds: Double = 120.0,
    private val greenShareOfCycle: Double = 0.5,
) : SignalTimingSource {

    // Deterministic per-intersection phase offset so different signals don't all appear
    // in sync — still an assumption, not measured data.
    private fun phaseOffsetSeconds(guidePointId: String): Double =
        (guidePointId.hashCode().mod(cycleSeconds.toInt())).toDouble()

    override fun greenSecondsRemaining(guidePointId: String, nowMillis: Long): Double {
        val greenSeconds = cycleSeconds * greenShareOfCycle
        val elapsedInCycle = ((nowMillis / 1000.0) + phaseOffsetSeconds(guidePointId)) % cycleSeconds
        return if (elapsedInCycle < greenSeconds) {
            greenSeconds - elapsedInCycle
        } else {
            0.0 // currently red in this assumed model
        }
    }
}
