package com.trafficgauge.app.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class SpeedSample(
    val speedKmh: Double,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Streams GPS-derived speed at ~1Hz, smoothed with a simple moving average to reduce
 * fix-to-fix jitter before it feeds the gauge calculator.
 */
class SpeedTracker(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val smoothingWindow = ArrayDeque<Double>()
    private val windowSize = 3

    @SuppressLint("MissingPermission")
    fun observeSpeed(): Flow<SpeedSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speedKmh = (location.speed * 3.6).toDouble()

                smoothingWindow.addLast(speedKmh)
                if (smoothingWindow.size > windowSize) smoothingWindow.removeFirst()
                val smoothed = smoothingWindow.average()

                trySend(SpeedSample(smoothed, location.latitude, location.longitude))
            }
        }

        fusedClient.requestLocationUpdates(request, callback, null)
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
