package com.trafficgauge.app.gauge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vertical three-zone gauge (red top / orange middle / green bottom) with the countdown
 * badge above it, matching the reference navigation screenshot.
 */
@Composable
fun SignalGaugeView(result: GaugeResult, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val zoneHeight = size.height / 3f

            drawRect(color = Color(0xFFD32F2F), topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, zoneHeight))
            drawRect(color = Color(0xFFFF9800), topLeft = androidx.compose.ui.geometry.Offset(0f, zoneHeight), size = androidx.compose.ui.geometry.Size(size.width, zoneHeight))
            drawRect(color = Color(0xFF43A047), topLeft = androidx.compose.ui.geometry.Offset(0f, zoneHeight * 2f), size = androidx.compose.ui.geometry.Size(size.width, zoneHeight))

            // Marker line showing where the current ETA/green-time margin lands within its zone.
            val zoneTop = when (result.zone) {
                GaugeZone.RED -> 0f
                GaugeZone.ORANGE -> zoneHeight
                GaugeZone.GREEN -> zoneHeight * 2f
            }
            val markerY = zoneTop + zoneHeight * (1f - result.fillRatio)
            drawLine(
                color = Color.Black,
                start = androidx.compose.ui.geometry.Offset(0f, markerY),
                end = androidx.compose.ui.geometry.Offset(size.width, markerY),
                strokeWidth = 6f,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        ) {
            Text(
                text = "${result.secondsRemaining}초",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
