package dev.eversorhn.gait.ui.track

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.live.LiveCommentary
import dev.eversorhn.gait.domain.live.LiveZone
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.ui.forecast.formatPace
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.domain.session.DebriefResult
import dev.eversorhn.gait.domain.session.SessionFinalizer
import dev.eversorhn.gait.tracking.ActiveSessionStore
import dev.eversorhn.gait.tracking.LocationTrackingService
import dev.eversorhn.gait.tracking.TrackingMode
import dev.eversorhn.gait.tracking.TrackingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.time.Instant
import java.time.ZoneId

/** Kept as the UI-facing name; maps 1:1 onto the service's TrackingMode. */
enum class TrackMode { OUTDOOR, INDOOR }

private fun TrackMode.toTracking() = when (this) {
    TrackMode.OUTDOOR -> TrackingMode.OUTDOOR
    TrackMode.INDOOR -> TrackingMode.INDOOR
}

private fun TrackingMode.toUi() = when (this) {
    TrackingMode.OUTDOOR -> TrackMode.OUTDOOR
    TrackingMode.INDOOR -> TrackMode.INDOOR
}

/** An interrupted session found on launch (process died mid-run), offered for save/discard. */
data class RecoverableSession(
    val mode: TrackMode,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val movingSeconds: Int,
)

/**
 * Who you're running against, resolved once when the screen opens: the opponent's name and
 * today's forecast (pace/distance/finish) drive the live You-vs-Twin comparison, and a duel
 * target pace is set when this session is a Decommission Trial. See docs phase 02.
 */
/** One mid-session callout, for the Track screen's comms feed. */
data class LiveCallout(val atSeconds: Int, val text: String, val zone: LiveZone)

data class LiveOpponent(
    val name: String,
    val isHorde: Boolean,
    val personaKey: String?,
    val hordeIntensity: String?,
    val generation: Int,
    val fidelity: Float,
    /** Points riding on this round (1 / 2 staked / 4 called / 3 duel). */
    val stake: Int,
    val stakeCalled: Boolean,
    val forecastPaceSecPerKm: Double?,
    val forecastDistanceMeters: Double?,
    val forecastFinishSeconds: Int?,
    val duelTargetPaceSecPerKm: Double?,
)

data class TrackUiState(
    val mode: TrackMode? = null,
    /** True when this session was opened as a Decommission/Outrun Trial from the Forecast screen. */
    val duel: Boolean = false,
    val opponent: LiveOpponent? = null,
    /** Newest last. The opponent talking mid-session (text channel of docs/live-audio.md). */
    val callouts: List<LiveCallout> = emptyList(),
    val finishing: Boolean = false,
    /** Indoor only: timer stopped, waiting for the user to type the distance off the machine. */
    val awaitingIndoorDistance: Boolean = false,
    val indoorElapsedSeconds: Int = 0,
    val indoorDistanceKm: String = "",
    val result: DebriefResult? = null,
    /** Shown on the Ready screen after a stop that couldn't be saved (too short, etc.). */
    val stopMessage: String? = null,
    val recoverable: RecoverableSession? = null,
)

/** Below this, GPS jitter alone can look like "a session" and produce a nonsense pace. */
private const val MIN_DISTANCE_TO_SAVE_METERS = 20.0

class TrackViewModel(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val finalizer = SessionFinalizer(repository, appContext)
    private val store = ActiveSessionStore(appContext)

    val trackingSnapshot = TrackingSessionState.snapshot

    private val _uiState = MutableStateFlow(TrackUiState())
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    private var commentary = LiveCommentary()

    init {
        observeLiveSession()
        // If we're already tracking (user came back to this screen mid-run), pick the mode up
        // from the live snapshot so the screen renders the right layout immediately.
        trackingSnapshot.value.mode?.let { live ->
            if (trackingSnapshot.value.isTracking) {
                _uiState.value = _uiState.value.copy(mode = live.toUi())
            }
        }
        checkForInterruptedSession()
        loadOpponent()
    }

    /**
     * Every tracking tick runs through [LiveCommentary]; when it fires, a persona line for the
     * zone is appended to the feed. Reset when a new session starts.
     */
    private fun observeLiveSession() {
        viewModelScope.launch {
            var wasTracking = false
            trackingSnapshot.collect { snap ->
                if (snap.isTracking && !wasTracking) {
                    commentary = LiveCommentary()
                    _uiState.value = _uiState.value.copy(callouts = emptyList())
                }
                wasTracking = snap.isTracking
                if (!snap.isTracking || snap.mode != TrackingMode.OUTDOOR) return@collect
                val opp = _uiState.value.opponent ?: return@collect
                val reference = if (_uiState.value.duel) opp.duelTargetPaceSecPerKm ?: opp.forecastPaceSecPerKm else opp.forecastPaceSecPerKm
                val gap = if (reference != null && snap.currentPaceSecPerKm != null) reference - snap.currentPaceSecPerKm else null
                val trigger = commentary.onTick(snap.movingSeconds, snap.distanceMeters, gap) ?: return@collect
                val text = lineFor(opp, trigger)
                val zone = when (trigger) {
                    is LiveCommentary.Trigger.KmMark -> trigger.zone
                    is LiveCommentary.Trigger.LeadChange -> trigger.zone
                }
                _uiState.value = _uiState.value.copy(
                    callouts = (_uiState.value.callouts + LiveCallout(snap.movingSeconds, text, zone)).takeLast(12)
                )
            }
        }
    }

    private fun lineFor(opp: LiveOpponent, trigger: LiveCommentary.Trigger): String {
        fun gapLabel(g: Double): String {
            val t = kotlin.math.abs(g).toInt()
            return "${t / 60}:${(t % 60).toString().padStart(2, '0')}/km"
        }
        val (zone, gap, km) = when (trigger) {
            is LiveCommentary.Trigger.KmMark -> Triple(trigger.zone, trigger.gapSecPerKm, trigger.km)
            is LiveCommentary.Trigger.LeadChange -> Triple(trigger.zone, trigger.gapSecPerKm, null)
        }
        if (opp.isHorde) {
            return when (zone) {
                LiveZone.AHEAD -> HordeSoundCues.liveAhead(gapLabel(gap))
                LiveZone.BEHIND -> HordeSoundCues.liveBehind(gapLabel(gap))
                LiveZone.LEVEL -> HordeSoundCues.liveLevel(km ?: 0)
            }
        }
        val persona = Personas.byKey(opp.personaKey)
        val prefix = if (km != null) "Km $km. " else ""
        return when (zone) {
            LiveZone.AHEAD -> prefix + persona.liveAheadLines.random(Random)(gapLabel(gap))
            LiveZone.BEHIND -> prefix + persona.liveBehindLines.random(Random)(gapLabel(gap))
            LiveZone.LEVEL -> persona.liveLevelLines.random(Random)(km ?: 0)
        }
    }

    /** Called by the screen with its nav argument. Idempotent; re-resolves the duel target. */
    fun setDuel(duel: Boolean) {
        if (_uiState.value.duel == duel && _uiState.value.opponent != null) return
        _uiState.value = _uiState.value.copy(duel = duel)
        loadOpponent()
    }

    private fun loadOpponent() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val sessions = repository.getSessions()
            val now = Instant.now()
            val forecast = ForecastEngine().forecast(
                sessions, now.atZone(ZoneId.systemDefault()).dayOfWeek.value, now.toEpochMilli(),
            )
            val zoned = now.atZone(ZoneId.systemDefault())
            val today = dev.eversorhn.gait.domain.wager.WagerPolicy.epochDay(now.toEpochMilli(), zoned.offset.totalSeconds * 1000L)
            val stakeOpen = profile.wagerStake > 0 && profile.wagerEpochDay == today && forecast != null
            val stake = when {
                _uiState.value.duel -> dev.eversorhn.gait.domain.ledger.Ledger.DUEL_STAKE
                else -> dev.eversorhn.gait.domain.wager.WagerPolicy.roundStake(stakeOpen, profile.wagerCalled, profile.wagerStake)
            }
            _uiState.value = _uiState.value.copy(
                opponent = LiveOpponent(
                    name = profile.twinName,
                    isHorde = profile.isHorde,
                    personaKey = profile.personaKey,
                    hordeIntensity = profile.hordeIntensity,
                    generation = profile.generation,
                    fidelity = profile.fidelity,
                    stake = stake,
                    stakeCalled = stakeOpen && profile.wagerCalled,
                    forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                    forecastDistanceMeters = forecast?.forecastDistanceMeters,
                    forecastFinishSeconds = forecast?.forecastFinishSeconds,
                    duelTargetPaceSecPerKm = if (_uiState.value.duel) DecommissionTrial.targetPaceSecPerKm(sessions) else null,
                )
            )
        }
    }

    /**
     * A persisted record with nothing actually tracking means the process died mid-session
     * and the system didn't restart the service. Offer what was captured rather than losing it.
     */
    private fun checkForInterruptedSession() {
        if (trackingSnapshot.value.isTracking) return
        val saved = store.read() ?: return
        _uiState.value = _uiState.value.copy(
            recoverable = RecoverableSession(
                mode = saved.mode.toUi(),
                distanceMeters = saved.distanceMeters,
                durationSeconds = saved.elapsedSeconds,
                movingSeconds = saved.movingSeconds,
            )
        )
    }

    fun saveRecovered() {
        val r = _uiState.value.recoverable ?: return
        store.clear()
        when (r.mode) {
            TrackMode.OUTDOOR -> {
                if (r.distanceMeters < MIN_DISTANCE_TO_SAVE_METERS || r.movingSeconds <= 0) {
                    _uiState.value = _uiState.value.copy(
                        recoverable = null,
                        stopMessage = "Interrupted session was too short to save (${r.distanceMeters.toInt()} m).",
                    )
                    return
                }
                _uiState.value = _uiState.value.copy(recoverable = null, finishing = true)
                viewModelScope.launch {
                    val result = finalizer.finalize(r.distanceMeters, r.movingSeconds, dataSource = SessionSource.GPS, duel = _uiState.value.duel)
                    _uiState.value = _uiState.value.copy(finishing = false, result = result)
                }
            }
            TrackMode.INDOOR -> {
                // No GPS distance to recover -- hand off to the normal indoor distance prompt.
                _uiState.value = _uiState.value.copy(
                    recoverable = null,
                    mode = TrackMode.INDOOR,
                    awaitingIndoorDistance = true,
                    indoorElapsedSeconds = r.durationSeconds,
                )
            }
        }
    }

    fun discardRecovered() {
        store.clear()
        _uiState.value = _uiState.value.copy(recoverable = null)
    }

    fun chooseMode(mode: TrackMode) {
        _uiState.value = _uiState.value.copy(mode = mode, stopMessage = null)
    }

    fun start() {
        val mode = _uiState.value.mode ?: return
        _uiState.value = _uiState.value.copy(stopMessage = null)
        TrackingSessionState.update { it.copy(error = null) }
        val action = when (mode.toTracking()) {
            TrackingMode.INDOOR -> LocationTrackingService.ACTION_START_INDOOR
            TrackingMode.OUTDOOR -> LocationTrackingService.ACTION_START_OUTDOOR
        }
        appContext.startForegroundService(Intent(appContext, LocationTrackingService::class.java).setAction(action))
    }

    fun stop() {
        val snapshot = trackingSnapshot.value
        val elapsedSeconds = snapshot.elapsedSeconds
        val movingSeconds = snapshot.movingSeconds

        appContext.startService(
            Intent(appContext, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)
        )

        if (_uiState.value.mode == TrackMode.INDOOR) {
            if (elapsedSeconds <= 0) {
                _uiState.value = _uiState.value.copy(stopMessage = "Nothing to save — the timer never started.")
                return
            }
            // No GPS distance to fall back on -- ask for what the machine showed.
            _uiState.value = _uiState.value.copy(awaitingIndoorDistance = true, indoorElapsedSeconds = elapsedSeconds)
            return
        }

        val distanceMeters = snapshot.distanceMeters
        when {
            snapshot.gpsFixCount == 0 -> {
                _uiState.value = _uiState.value.copy(
                    stopMessage = "Not saved — no GPS fix came in. Try again outside, or log it manually.",
                )
                return
            }
            distanceMeters < MIN_DISTANCE_TO_SAVE_METERS -> {
                _uiState.value = _uiState.value.copy(
                    stopMessage = "Not saved — only ${distanceMeters.toInt()} m recorded, under the ${MIN_DISTANCE_TO_SAVE_METERS.toInt()} m minimum.",
                )
                return
            }
            movingSeconds <= 0 -> {
                _uiState.value = _uiState.value.copy(stopMessage = "Not saved — no moving time was recorded.")
                return
            }
        }

        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            // Pace and the session's duration are based on moving time, not wall time.
            val result = finalizer.finalize(distanceMeters, movingSeconds, dataSource = SessionSource.GPS, duel = _uiState.value.duel)
            _uiState.value = _uiState.value.copy(finishing = false, result = result)
        }
    }

    fun updateIndoorDistance(value: String) {
        _uiState.value = _uiState.value.copy(indoorDistanceKm = value)
    }

    fun submitIndoorDistance() {
        val distanceKm = _uiState.value.indoorDistanceKm.toDoubleOrNull() ?: return
        if (distanceKm <= 0.0) return

        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            val result = finalizer.finalize(
                distanceMeters = distanceKm * 1000.0,
                durationSeconds = _uiState.value.indoorElapsedSeconds,
                dataSource = SessionSource.MANUAL,
                duel = _uiState.value.duel,
            )
            _uiState.value = _uiState.value.copy(finishing = false, awaitingIndoorDistance = false, result = result)
        }
    }

    fun discardIndoor() {
        _uiState.value = _uiState.value.copy(awaitingIndoorDistance = false, indoorDistanceKm = "", indoorElapsedSeconds = 0)
    }

    fun reset() {
        TrackingSessionState.reset()
        _uiState.value = TrackUiState(duel = _uiState.value.duel, opponent = _uiState.value.opponent)
    }
}
