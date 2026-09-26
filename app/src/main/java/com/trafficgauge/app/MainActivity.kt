package com.trafficgauge.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trafficgauge.app.gauge.GaugeResult
import com.trafficgauge.app.gauge.GaugeZone
import com.trafficgauge.app.gauge.SignalGaugeCalculator
import com.trafficgauge.app.gauge.SignalGaugeView
import com.trafficgauge.app.location.SpeedTracker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * MVP shell: shows live GPS speed and a signal-crossing gauge.
 *
 * The green-remaining-seconds input is a placeholder (MOCK_GREEN_REMAINING_SECONDS) until a
 * real signal timing source (ITS/C-ITS API) is wired in for the target region — see the
 * project plan for why that data source isn't finalized yet.
 */
class MainActivity : ComponentActivity() {

    private val calculator = SignalGaugeCalculator()

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates()
    }

    private var onSpeedUpdate: ((Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var speedKmh by remember { mutableStateOf(0.0) }
            onSpeedUpdate = { speedKmh = it }

            // Placeholder distance/green-time until routing + signal APIs feed real values.
            val distanceToSignalMeters = 250.0
            val greenRemainingSeconds = 36.0

            val gaugeResult = remember(speedKmh) {
                calculator.calculate(distanceToSignalMeters, speedKmh, greenRemainingSeconds)
            }

            TrafficGaugeApp(speedKmh = speedKmh, distanceMeters = distanceToSignalMeters, gaugeResult = gaugeResult)
        }

        ensureLocationPermission()
    }

    private fun ensureLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startLocationUpdates()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        val tracker = SpeedTracker(this)
        lifecycleScope.launch {
            tracker.observeSpeed().collect { sample ->
                onSpeedUpdate?.invoke(sample.speedKmh)
            }
        }
    }
}

@Composable
private fun TrafficGaugeApp(speedKmh: Double, distanceMeters: Double, gaugeResult: GaugeResult) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GaugeOverlay(speedKmh = speedKmh, distanceMeters = distanceMeters, gaugeResult = gaugeResult)
        }
    }
}

@Composable
private fun GaugeOverlay(speedKmh: Double, distanceMeters: Double, gaugeResult: GaugeResult) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "${speedKmh.toInt()} km/h", style = MaterialTheme.typography.headlineMedium)
            Text(text = "신호까지 ${distanceMeters.toInt()}m", style = MaterialTheme.typography.bodyMedium)
            Text(text = "구간: ${gaugeResult.zone}", style = MaterialTheme.typography.bodyMedium)
        }

        SignalGaugeView(
            result = gaugeResult,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(72.dp)
                .height(160.dp),
        )
    }
}
