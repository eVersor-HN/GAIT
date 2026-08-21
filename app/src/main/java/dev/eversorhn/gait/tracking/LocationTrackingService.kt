package dev.eversorhn.gait.tracking

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
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
 * user switching apps. See docs/scope-and-stack.md.
 *
 * Two foreground-service types, picked per mode -- this matters on Android 14+, which
 * refuses to start a `location`-typed FGS unless a location runtime permission is held:
 * - OUTDOOR -> `location`. Requires ACCESS_FINE/COARSE_LOCATION at start; the UI gates on it.
 * - INDOOR  -> `health`. No runtime permission needed; semantically a workout timer.
 * Using `location` for indoor (as v0.3.0 did) crashed a fresh install with a SecurityException
 * the moment someone picked Indoor first.
 *
 * Timing uses SystemClock.elapsedRealtime() (monotonic) rather than wall-clock, so a time-zone
 * or clock change mid-run can't corrupt the session. Pace is computed over *moving* time only:
 * the wait for the first GPS fix and any auto-paused stretches don't count.
 *
 * START_STICKY + [ActiveSessionStore]: if the system kills and later restarts the service,
 * it resumes from the last persisted snapshot instead of losing the run.
 */
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START_OUTDOOR = "dev.eversorhn.gait.tracking.START_OUTDOOR"
        const val ACTION_START_INDOOR = "dev.eversorhn.gait.tracking.START_INDOOR"
        const val ACTION_STOP = "dev.eversorhn.gait.tracking.STOP"
        private const val NOTIFICATION_ID = 42

        /** Fixes worse than this are dropped rather than folded into the distance total. */
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 25f

        /** Below this, live pace is hidden rather than showing a jitter-driven number. */
        private const val MIN_DISTANCE_FOR_PACE_METERS = 30.0

        /** An interval slower than this (m/s) counts as standing still -> auto-pause. ~1.8 km/h. */
        private const val AUTO_PAUSE_SPEED_MPS = 0.5

        /** How often the in-progress session is persisted for crash recovery. */
        private const val PERSIST_EVERY_SECONDS = 10
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var tickerJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var store: ActiveSessionStore
    private var lastAcceptedLocation: Location? = null
    private var lastAcceptedRealtime: Long = 0L
    /** (cumulative distance m, cumulative moving s) after each moving fix — the rolling-pace window. */
    private val paceSamples = ArrayDeque<Pair<Double, Int>>()
    private val rollingWindowMeters = 200.0
    // Route trace / climb / splits (domain/route/RouteMetrics)
    private val routePoints = ArrayList<dev.eversorhn.gait.domain.route.RouteMetrics.Point>()
    private var lastAltitude: Double? = null
    private var elevationGain = 0.0
    private var lastKmSplit = 0
    private var lastKmMoving = 0
    private val splits = ArrayList<Int>()
    private var locationUpdatesActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onNewLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        store = ActiveSessionStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OUTDOOR -> startFresh(TrackingMode.OUTDOOR)
            ACTION_START_INDOOR -> startFresh(TrackingMode.INDOOR)
            ACTION_STOP -> stopTracking()
            // null intent == the system restarted us after killing the process (START_STICKY).
            null -> {
                val saved = store.read()
                if (saved != null) resumeFrom(saved) else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startFresh(mode: TrackingMode) {
        if (!enterForeground(mode)) return

        lastAcceptedLocation = null
        lastAcceptedRealtime = 0L
        paceSamples.clear()
        routePoints.clear(); lastAltitude = null; elevationGain = 0.0; lastKmSplit = 0; lastKmMoving = 0; splits.clear()
        val nowReal = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()

        TrackingSessionState.reset()
        TrackingSessionState.update {
            it.copy(
                isTracking = true,
                mode = mode,
                startElapsedRealtimeMillis = nowReal,
                startEpochMillis = nowEpoch,
            )
        }
        persist()
        startLocationUpdatesIfOutdoor(mode)
        startTicker()
    }

    private fun resumeFrom(saved: ActiveSessionStore.Record) {
        if (!enterForeground(saved.mode)) return

        // The device may have rebooted since (elapsedRealtime resets) -- if so, fall back to
        // wall-clock deltas from the saved start, which is the best we have.
        val nowReal = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val rebooted = nowReal < saved.startElapsedRealtimeMillis
        val startReal = if (rebooted) nowReal - (nowEpoch - saved.startEpochMillis) else saved.startElapsedRealtimeMillis

        lastAcceptedLocation = null
        lastAcceptedRealtime = 0L
        TrackingSessionState.reset()
        TrackingSessionState.update {
            it.copy(
                isTracking = true,
                mode = saved.mode,
                startElapsedRealtimeMillis = startReal,
                startEpochMillis = saved.startEpochMillis,
                distanceMeters = saved.distanceMeters,
                movingSeconds = saved.movingSeconds,
                elapsedSeconds = ((nowReal - startReal) / 1000L).toInt(),
            )
        }
        startLocationUpdatesIfOutdoor(saved.mode)
        startTicker()
    }

    /** Returns false (and leaves an error in the snapshot) if the FGS couldn't be started. */
    private fun enterForeground(mode: TrackingMode): Boolean {
        val (title, text) = when (mode) {
            TrackingMode.OUTDOOR -> "GAIT is tracking" to "Recording your session in the background."
            TrackingMode.INDOOR -> "GAIT is timing" to "Timing your indoor session."
        }
        val notification = NotificationCompat.Builder(this, TwinNotifier.trackingChannelId(this))
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(TwinNotifier.openAppIntent(this))
            .setOngoing(true)
            .build()

        val type = when (mode) {
            TrackingMode.OUTDOOR -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            TrackingMode.INDOOR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                // Below API 34 the type isn't enforced; passing none keeps older OSes happy.
                0
            }
        }

        if (mode == TrackingMode.OUTDOOR && !hasLocationPermission()) {
            failToStart("Location permission is missing — grant it and try again.")
            return false
        }

        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: SecurityException) {
            failToStart("Couldn't start tracking: ${e.message ?: "permission denied"}")
            false
        } catch (e: IllegalStateException) {
            failToStart("Couldn't start tracking right now: ${e.message ?: "blocked by the system"}")
            false
        }
    }

    private fun failToStart(message: String) {
        TrackingSessionState.update { it.copy(isTracking = false, error = message) }
        store.clear()
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdatesIfOutdoor(mode: TrackingMode) {
        if (mode != TrackingMode.OUTDOOR || locationUpdatesActive) return
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
            locationUpdatesActive = true
        } catch (e: SecurityException) {
            TrackingSessionState.update { it.copy(error = "Location access was revoked mid-session.") }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var secondsSincePersist = 0
            while (true) {
                delay(1_000L)
                val start = TrackingSessionState.snapshot.value.startElapsedRealtimeMillis ?: continue
                val elapsed = ((SystemClock.elapsedRealtime() - start) / 1000L).toInt()
                TrackingSessionState.update { s ->
                    // Indoor has no GPS: the whole session is "moving" time by definition.
                    val moving = if (s.mode == TrackingMode.INDOOR) elapsed else s.movingSeconds
                    s.copy(elapsedSeconds = elapsed, movingSeconds = moving)
                }
                if (++secondsSincePersist >= PERSIST_EVERY_SECONDS) {
                    persist()
                    secondsSincePersist = 0
                }
            }
        }
    }

    private fun onNewLocation(location: Location) {
        if (location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return

        val nowReal = SystemClock.elapsedRealtime()
        val previous = lastAcceptedLocation
        val addedMeters = previous?.distanceTo(location)?.toDouble() ?: 0.0
        val intervalSeconds = if (previous != null && lastAcceptedRealtime > 0L) (nowReal - lastAcceptedRealtime) / 1000.0 else 0.0
        lastAcceptedLocation = location
        lastAcceptedRealtime = nowReal

        // Speed over this interval decides whether it counts as moving. The very first fix
        // has no interval and contributes neither distance nor moving time.
        val intervalSpeed = if (intervalSeconds > 0.0) addedMeters / intervalSeconds else 0.0
        val moved = intervalSeconds > 0.0 && intervalSpeed >= AUTO_PAUSE_SPEED_MPS

        TrackingSessionState.update { s ->
            val newDistance = if (moved) s.distanceMeters + addedMeters else s.distanceMeters
            val newMoving = if (moved) s.movingSeconds + intervalSeconds.toInt() else s.movingSeconds
            // Below this, GPS jitter alone (stationary drift between "accepted" fixes) can
            // produce a distance so small the pace math blows up into a meaningless number.
            val avgPace = if (newDistance >= MIN_DISTANCE_FOR_PACE_METERS && newMoving > 0) {
                newMoving / (newDistance / 1000.0)
            } else {
                null
            }
            // Rolling pace: over the oldest sample that is still >= rollingWindowMeters back.
            // Falls back to the session average until there's that much ground behind you.
            if (moved) {
                paceSamples.addLast(newDistance to newMoving)
                while (paceSamples.size > 2 && newDistance - paceSamples[1].first >= rollingWindowMeters) paceSamples.removeFirst()
            }
            val oldest = paceSamples.firstOrNull()
            val rolling = if (oldest != null && newDistance - oldest.first >= rollingWindowMeters && newMoving - oldest.second > 0) {
                (newMoving - oldest.second) / ((newDistance - oldest.first) / 1000.0)
            } else avgPace
            // Route trace: keep a point every ~25 m of movement. Climb: altitude steps >= 3 m.
            if (moved || previous == null) {
                val p = dev.eversorhn.gait.domain.route.RouteMetrics.Point(location.latitude, location.longitude)
                if (dev.eversorhn.gait.domain.route.RouteMetrics.shouldKeep(routePoints.lastOrNull(), p)) routePoints += p
                if (location.hasAltitude()) {
                    val alt = location.altitude
                    val la = lastAltitude
                    if (la == null) lastAltitude = alt
                    else if (kotlin.math.abs(alt - la) >= dev.eversorhn.gait.domain.route.RouteMetrics.CLIMB_STEP_METERS) { if (alt > la) elevationGain += alt - la; lastAltitude = alt }
                }
            }
            val km = (newDistance / 1000.0).toInt()
            if (km > lastKmSplit) {
                for (k in (lastKmSplit + 1)..km) { splits += newMoving - lastKmMoving; lastKmMoving = newMoving }
                lastKmSplit = km
            }
            s.copy(
                distanceMeters = newDistance,
                movingSeconds = newMoving,
                currentPaceSecPerKm = rolling,
                avgPaceSecPerKm = avgPace,
                routePolyline = if (routePoints.size % 4 == 0 || s.routePolyline.isEmpty()) dev.eversorhn.gait.domain.route.RouteMetrics.encode(routePoints) else s.routePolyline,
                elevationGainMeters = elevationGain,
                splitSeconds = splits.toList(),
                gpsFixCount = s.gpsFixCount + 1,
                autoPaused = previous != null && !moved,
            )
        }
    }

    private fun persist() {
        val s = TrackingSessionState.snapshot.value
        val mode = s.mode ?: return
        val startReal = s.startElapsedRealtimeMillis ?: return
        val startEpoch = s.startEpochMillis ?: return
        store.write(
            ActiveSessionStore.Record(
                mode = mode,
                startEpochMillis = startEpoch,
                startElapsedRealtimeMillis = startReal,
                distanceMeters = s.distanceMeters,
                movingSeconds = s.movingSeconds,
                elapsedSeconds = s.elapsedSeconds,
                lastSavedEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun stopTracking() {
        if (locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesActive = false
        }
        tickerJob?.cancel()
        TrackingSessionState.update { it.copy(isTracking = false) }
        store.clear()
        ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
