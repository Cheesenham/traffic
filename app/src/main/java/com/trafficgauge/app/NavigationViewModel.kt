package com.trafficgauge.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trafficgauge.app.data.GeoUtils
import com.trafficgauge.app.gauge.AssumedCycleSignalTimingSource
import com.trafficgauge.app.gauge.GaugeResult
import com.trafficgauge.app.gauge.SignalGaugeCalculator
import com.trafficgauge.app.gauge.SignalTimingSource
import com.trafficgauge.app.routing.GuidePoint
import com.trafficgauge.app.routing.LatLon
import com.trafficgauge.app.routing.PoiResult
import com.trafficgauge.app.routing.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavigationUiState(
    val speedKmh: Double = 0.0,
    val currentLocation: LatLon? = null,
    val destinationQuery: String = "",
    val searchResults: List<PoiResult> = emptyList(),
    val isSearching: Boolean = false,
    val routePolyline: List<LatLon> = emptyList(),
    val guidePoints: List<GuidePoint> = emptyList(),
    val nextGuidePoint: GuidePoint? = null,
    val distanceToNextGuidePointMeters: Double? = null,
    val gaugeResult: GaugeResult? = null,
    val errorMessage: String? = null,
)

/** How close a guide point must be before it's treated as "the upcoming intersection". */
private const val GUIDE_POINT_ARRIVAL_RADIUS_METERS = 15.0

class NavigationViewModel(
    private val routeRepository: RouteRepository,
    private val signalTimingSource: SignalTimingSource = AssumedCycleSignalTimingSource(),
    private val gaugeCalculator: SignalGaugeCalculator = SignalGaugeCalculator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState

    fun onSpeedSample(speedKmh: Double, lat: Double, lon: Double) {
        _uiState.update { it.copy(speedKmh = speedKmh, currentLocation = LatLon(lat, lon)) }
        recomputeGauge()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(destinationQuery = query) }
    }

    fun search() {
        val query = _uiState.value.destinationQuery
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            val results = routeRepository.searchDestination(query)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    errorMessage = if (results.isEmpty()) "검색 결과가 없습니다" else null,
                )
            }
        }
    }

    fun selectDestination(poi: PoiResult) {
        val origin = _uiState.value.currentLocation ?: run {
            _uiState.update { it.copy(errorMessage = "현재 위치를 아직 확인할 수 없습니다") }
            return
        }

        viewModelScope.launch {
            val route = routeRepository.fetchRoute(origin, poi.location)
            if (route == null) {
                _uiState.update { it.copy(errorMessage = "경로를 찾을 수 없습니다") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    routePolyline = route.polyline,
                    guidePoints = route.guidePoints,
                    searchResults = emptyList(),
                    destinationQuery = poi.name,
                )
            }
            recomputeGauge()
        }
    }

    private fun recomputeGauge() {
        val state = _uiState.value
        val current = state.currentLocation ?: return
        val nextGuidePoint = state.guidePoints.firstOrNull { guidePoint ->
            GeoUtils.distanceMeters(
                current.lat, current.lon,
                guidePoint.location.lat, guidePoint.location.lon,
            ) > GUIDE_POINT_ARRIVAL_RADIUS_METERS
        } ?: state.guidePoints.firstOrNull()

        if (nextGuidePoint == null) {
            _uiState.update { it.copy(nextGuidePoint = null, distanceToNextGuidePointMeters = null, gaugeResult = null) }
            return
        }

        val distance = GeoUtils.distanceMeters(
            current.lat, current.lon,
            nextGuidePoint.location.lat, nextGuidePoint.location.lon,
        )
        val guidePointId = "${nextGuidePoint.location.lat},${nextGuidePoint.location.lon}"
        val greenRemaining = signalTimingSource.greenSecondsRemaining(guidePointId, System.currentTimeMillis())

        val gaugeResult = gaugeCalculator.calculate(
            distanceToSignalMeters = distance,
            currentSpeedKmh = state.speedKmh,
            greenRemainingSeconds = greenRemaining,
        )

        _uiState.update {
            it.copy(
                nextGuidePoint = nextGuidePoint,
                distanceToNextGuidePointMeters = distance,
                gaugeResult = gaugeResult,
            )
        }
    }

    class Factory(private val appKey: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NavigationViewModel(RouteRepository(appKey)) as T
        }
    }
}
