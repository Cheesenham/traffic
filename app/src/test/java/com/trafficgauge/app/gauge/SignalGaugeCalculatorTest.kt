package com.trafficgauge.app.gauge

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalGaugeCalculatorTest {

    private val calculator = SignalGaugeCalculator(bufferSeconds = 2.0)

    @Test
    fun `plenty of green time yields GREEN`() {
        // 250m at 60km/h ≈ 15s to arrive, well inside a 36s green window.
        val result = calculator.calculate(distanceToSignalMeters = 250.0, currentSpeedKmh = 60.0, greenRemainingSeconds = 36.0)
        assertEquals(GaugeZone.GREEN, result.zone)
    }

    @Test
    fun `light will turn red before arrival yields RED`() {
        // 250m at 20km/h ≈ 45s to arrive, past a 10s green window.
        val result = calculator.calculate(distanceToSignalMeters = 250.0, currentSpeedKmh = 20.0, greenRemainingSeconds = 10.0)
        assertEquals(GaugeZone.RED, result.zone)
    }

    @Test
    fun `arrival right at the edge yields ORANGE`() {
        // 100m at 36km/h (10 m/s) = 10s to arrive, matching a 10s green window exactly.
        val result = calculator.calculate(distanceToSignalMeters = 100.0, currentSpeedKmh = 36.0, greenRemainingSeconds = 10.0)
        assertEquals(GaugeZone.ORANGE, result.zone)
    }

    @Test
    fun `stopped vehicle is treated as at-risk`() {
        val result = calculator.calculate(distanceToSignalMeters = 50.0, currentSpeedKmh = 0.0, greenRemainingSeconds = 20.0)
        assertEquals(GaugeZone.ORANGE, result.zone)
    }
}
