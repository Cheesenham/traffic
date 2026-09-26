package com.trafficgauge.app.gauge

/**
 * Zone the crossing gauge should show, matching the red/orange/green thirds in the reference UI.
 */
enum class GaugeZone { GREEN, ORANGE, RED }

data class GaugeResult(
    val zone: GaugeZone,
    /** Seconds until the signal turns red, as shown in the countdown badge. */
    val secondsRemaining: Int,
    /** 0f..1f fill ratio used to size the colored bar inside its zone. */
    val fillRatio: Float,
)

/**
 * Decides whether the driver can clear the upcoming intersection before the light turns red.
 *
 * bufferSeconds absorbs GPS/signal-timer jitter so the gauge doesn't flip GREEN/RED on noise
 * right at the boundary.
 */
class SignalGaugeCalculator(private val bufferSeconds: Double = 2.0) {

    fun calculate(
        distanceToSignalMeters: Double,
        currentSpeedKmh: Double,
        greenRemainingSeconds: Double,
    ): GaugeResult {
        val speedMs = currentSpeedKmh / 3.6
        val secondsRemaining = greenRemainingSeconds.toInt().coerceAtLeast(0)

        if (speedMs <= 0.1) {
            // Stopped or crawling: can't project an arrival time, treat as at-risk.
            return GaugeResult(GaugeZone.ORANGE, secondsRemaining, fillRatio = 0.5f)
        }

        val etaSeconds = distanceToSignalMeters / speedMs
        val margin = greenRemainingSeconds - etaSeconds

        return when {
            margin >= bufferSeconds -> GaugeResult(
                zone = GaugeZone.GREEN,
                secondsRemaining = secondsRemaining,
                fillRatio = (margin / (greenRemainingSeconds.coerceAtLeast(1.0))).toFloat().coerceIn(0f, 1f),
            )
            margin >= -bufferSeconds -> GaugeResult(
                zone = GaugeZone.ORANGE,
                secondsRemaining = secondsRemaining,
                fillRatio = 0.5f,
            )
            else -> GaugeResult(
                zone = GaugeZone.RED,
                secondsRemaining = secondsRemaining,
                fillRatio = (-margin / etaSeconds.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            )
        }
    }
}
