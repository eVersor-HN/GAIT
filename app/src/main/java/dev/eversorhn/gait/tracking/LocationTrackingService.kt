package dev.eversorhn.gait.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.eversorhn.gait.notification.TwinNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs entirely as a foreground service so tracking survives the screen turning off or the
 * user switching apps -- see the manifest comment for why this avoids needing
 * ACCESS_BACKGROUND_LOCATION. See docs/scope-and-stack.md.
 */
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "dev.eversorhn.gait.tracking.START"
        const val ACTION_STOP = "dev.eversorhn.gait.tracking.STOP"
        private const val NOTIFICATION_ID = 42

        /** Fixes worse than this are dropped rather than folded into the distance total. */
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 25f

        /** Below this, live pace is hidden rather than showing a jitter-driven number. */
        private const val MIN_DISTANCE_FOR_PACE_METERS = 30.0
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var tickerJob: Job? = null
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var lastAcceptedLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onNewLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        val notification = NotificationCompat.Builder(this, TwinNotifier.trackingChannelId(this))
            .setContentTitle("GAIT is tracking")
            .setContentText("Recording your session in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        lastAcceptedLocation = null
        TrackingSessionState.reset()
        TrackingSessionState.update { it.copy(isTracking = true, startEpochMillis = System.currentTimeMillis()) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)

        tickerJob = serviceScope.launch {
            while (true) {
                delay(1_000L)
                val start = TrackingSessionState.snapshot.value.startEpochMillis ?: continue
                val elapsed = ((System.currentTimeMillis() - start) / 1000L).toInt()
                TrackingSessionState.update { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    private fun onNewLocation(location: Location) {
        if (location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return

        val previous = lastAcceptedLocation
        val addedMeters = previous?.distanceTo(location)?.toDouble() ?: 0.0
        lastAcceptedLocation = location

        TrackingSessionState.update { snapshot ->
            val newDistance = snapshot.distanceMeters + addedMeters
            // Below this, GPS jitter alone (stationary drift between "accepted" fixes) can
            // produce a distance so small the pace math blows up into a meaningless number.
            val pace = if (newDistance >= MIN_DISTANCE_FOR_PACE_METERS) {
                snapshot.elapsedSeconds / (newDistance / 1000.0)
            } else {
                null
            }
            snapshot.copy(
                distanceMeters = newDistance,
                currentPaceSecPerKm = pace,
                gpsFixCount = snapshot.gpsFixCount + 1,
            )
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tickerJob?.cancel()
        TrackingSessionState.update { it.copy(isTracking = false) }
        ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
