package com.trafficgauge.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.trafficgauge.app.gauge.SignalGaugeView
import com.trafficgauge.app.location.SpeedTracker
import com.trafficgauge.app.ui.DestinationSearchBar
import com.trafficgauge.app.ui.MapScreen
import com.trafficgauge.app.ui.configureOsmdroid
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: NavigationViewModel by viewModels {
        NavigationViewModel.Factory(BuildConfig.TMAP_APP_KEY)
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid(applicationContext)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavigationScreen(
                        state = uiState,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                        onResultSelected = viewModel::selectDestination,
                    )
                }
            }
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
                viewModel.onSpeedSample(sample.speedKmh, sample.latitude, sample.longitude)
            }
        }
    }
}

@Composable
private fun NavigationScreen(
    state: NavigationUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultSelected: (com.trafficgauge.app.routing.PoiResult) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            currentLocation = state.currentLocation,
            routePolyline = state.routePolyline,
            nextGuidePoint = state.nextGuidePoint,
            modifier = Modifier.fillMaxSize(),
        )

        DestinationSearchBar(
            query = state.destinationQuery,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            searchResults = state.searchResults,
            onResultSelected = onResultSelected,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )

        StatusPanel(state = state, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))

        state.gaugeResult?.let { gauge ->
            SignalGaugeView(
                result = gauge,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .width(64.dp)
                    .height(140.dp),
            )
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 88.dp),
                tonalElevation = 4.dp,
            ) {
                Text(text = message, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun StatusPanel(state: NavigationUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "${state.speedKmh.toInt()} km/h", style = MaterialTheme.typography.headlineMedium)
        state.distanceToNextGuidePointMeters?.let { distance ->
            Text(text = "다음 교차로까지 ${distance.toInt()}m", style = MaterialTheme.typography.bodyMedium)
        }
        state.gaugeResult?.let { gauge ->
            Text(text = "구간: ${gauge.zone}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
