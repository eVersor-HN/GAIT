package dev.eversorhn.gait.ui.track

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.live.LiveCommentary
import dev.eversorhn.gait.domain.live.LiveZone
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

/** One completed kilometre: your moving seconds for it against the model's expected seconds. */
data class Split(val km: Int, val yourSeconds: Int, val modelSeconds: Int)

/**
 * What the session looks like if you hold this: the numbers that make the live screen worth
 * watching. Everything derives from the snapshot + the opponent's forecast; nothing is stored.
 */
data class LiveProjection(
    /** Seconds you are ahead (+) / behind (−) of the model *at your current distance*. */
    val gapSeconds: Int,
    /** Finish time if you hold the rolling pace over the forecast distance. */
    val projectedFinishSeconds: Int?,
    /** Whether the round would go to you on the session average right now. */
    val roundToUser: Boolean?,
    /** Board rank if the round landed now, and the change vs. today's rank. */
    val projectedRank: Int?,
    val rankDelta: Int?,
    /** Horde: metres of separation at your current pace vs. theirs (+ = you're ahead). */
    val separationMeters: Int?,
    /** Horde: metres per minute they close (+) or fall back (−). */
    val closingPerMinute: Int?,
    /** The model's forecast confidence, decayed by how far off its number you are. */
    val modelConfidencePercent: Int?,
    val splits: List<Split>,
)

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
    val forecastConfidencePercent: Int = 0,
    val enrolledEpochDay: Long = 0L,
    val todayRank: Int? = null,
)

data class TrackUiState(
    val mode: TrackMode? = null,
    /** True when this session was opened as a Decommission/Outrun Trial from the Forecast screen. */
    val duel: Boolean = false,
    val opponent: LiveOpponent? = null,
    /** Newest last. The opponent talking mid-session (text channel of docs/live-audio.md). */
    val callouts: List<LiveCallout> = emptyList(),
    val projection: LiveProjection? = null,
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
    /** The spoken commentator (docs/voice-design.md). Created lazily so a TTS engine is only bound once a session runs. */
    private var commentator: dev.eversorhn.gait.audio.Commentator? = null
    private var lastSpokenAt = -1000
    private var lastStatusAt = -1000
    private var lastAheadSpoken: Boolean? = null
    private var spokenLines = 0
    private var lastKmMarked = 0
    private var lastKmMovingSeconds = 0
    private var splits: List<Split> = emptyList()
    private var lastProjectionAt = -100

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
                    lastKmMarked = 0; lastKmMovingSeconds = 0; splits = emptyList(); lastProjectionAt = -100
                    lastSpokenAt = -1000; lastStatusAt = 0; lastAheadSpoken = null; spokenLines = 0; lastSpokenKm = 0
                    _uiState.value = _uiState.value.copy(callouts = emptyList(), projection = null)
                    _uiState.value.opponent?.let { opp ->
                        if (snap.mode == TrackingMode.OUTDOOR) speak(dev.eversorhn.gait.domain.live.CommentaryScript.startLine(scriptInput(snap, opp, null)), snap.movingSeconds, force = true)
                    }
                }
                if (!snap.isTracking && wasTracking) commentator?.stop()
                wasTracking = snap.isTracking
                if (!snap.isTracking || snap.mode != TrackingMode.OUTDOOR) return@collect
                val opp = _uiState.value.opponent ?: return@collect
                val reference = if (_uiState.value.duel) opp.duelTargetPaceSecPerKm ?: opp.forecastPaceSecPerKm else opp.forecastPaceSecPerKm

                // --- Splits: one row per completed kilometre ---
                val km = (snap.distanceMeters / 1000.0).toInt()
                if (km > lastKmMarked && reference != null) {
                    for (k in (lastKmMarked + 1)..km) {
                        splits = splits + Split(k, snap.movingSeconds - lastKmMovingSeconds, reference.toInt())
                        lastKmMovingSeconds = snap.movingSeconds
                    }
                    lastKmMarked = km
                }
                // --- Projection (every 5 s of moving time; the rank part sorts 1,300 rows) ---
                if (snap.movingSeconds - lastProjectionAt >= 5 && reference != null) {
                    lastProjectionAt = snap.movingSeconds
                    val proj = project(snap, opp, reference)
                    _uiState.value = _uiState.value.copy(projection = proj)
                    // --- Spoken commentary: km marks, lead changes, and a status line every ~2 min ---
                    val input = scriptInput(snap, opp, proj)
                    val aheadNow: Boolean? = if (opp.isHorde) proj.separationMeters?.let { it > 0 } else proj.gapSeconds.let { it > 0 }
                    when {
                        km > (lastSpokenKm) && km >= 1 -> { lastSpokenKm = km; speak(dev.eversorhn.gait.domain.live.CommentaryScript.kmLine(input), snap.movingSeconds) }
                        aheadNow != null && lastAheadSpoken != null && aheadNow != lastAheadSpoken && snap.movingSeconds > 60 ->
                            speak(dev.eversorhn.gait.domain.live.CommentaryScript.leadChangeLine(input, aheadNow), snap.movingSeconds)
                        snap.movingSeconds - lastStatusAt >= 120 && snap.movingSeconds > 90 -> {
                            dev.eversorhn.gait.domain.live.CommentaryScript.statusLine(input)?.let { speak(it, snap.movingSeconds) }
                            lastStatusAt = snap.movingSeconds
                        }
                    }
                    if (aheadNow != null && (lastAheadSpoken == null || snap.movingSeconds > 60)) lastAheadSpoken = aheadNow
                }

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

    private var lastSpokenKm = 0

    private fun scriptInput(snap: dev.eversorhn.gait.tracking.TrackingSnapshot, opp: LiveOpponent, p: LiveProjection?): dev.eversorhn.gait.domain.live.CommentaryScript.Input {
        val gapMetres = p?.gapSeconds?.let { g -> snap.currentPaceSecPerKm?.let { pace -> (g * 1000.0 / pace).toInt() } }
        return dev.eversorhn.gait.domain.live.CommentaryScript.Input(
            opponentName = opp.name, isHorde = opp.isHorde, km = (snap.distanceMeters / 1000.0).toInt(),
            gapSeconds = p?.gapSeconds, gapMetres = gapMetres, separationMetres = p?.separationMeters, closingPerMinute = p?.closingPerMinute,
            roundToUser = p?.roundToUser, stake = opp.stake, modelConfidence = p?.modelConfidencePercent,
            projectedFinishSeconds = p?.projectedFinishSeconds, modelFinishSeconds = opp.forecastFinishSeconds,
        )
    }

    /** Cooldown 40 s, 20 lines per session, respects the Voice setting. [force] for the opening line. */
    private fun speak(text: String, atSeconds: Int, force: Boolean = false) {
        if (!dev.eversorhn.gait.audio.VoicePrefs.isEnabled(appContext)) return
        if (!force && (atSeconds - lastSpokenAt < 40 || spokenLines >= 20)) return
        val horde = _uiState.value.opponent?.isHorde == true
        val c = commentator ?: dev.eversorhn.gait.audio.Commentator(
            appContext,
            if (horde) dev.eversorhn.gait.audio.VoiceFx.Voice.HORDE else dev.eversorhn.gait.audio.VoiceFx.Voice.DIVISION,
        ).also { commentator = it }
        // A horde caption is written for the eye ("[snarling, close]"); spoken, it is the words
        // inside the brackets, deep and slow. The division's lines are spoken as written.
        c.say(if (horde) text.trim().removePrefix("[").removeSuffix("]") else text)
        lastSpokenAt = atSeconds; spokenLines++
    }

    override fun onCleared() {
        commentator?.shutdown()
        super.onCleared()
    }

    private fun project(snap: dev.eversorhn.gait.tracking.TrackingSnapshot, opp: LiveOpponent, reference: Double): LiveProjection {
        val km = snap.distanceMeters / 1000.0
        val modelSecondsHere = (reference * km).toInt()
        val gapSeconds = modelSecondsHere - snap.movingSeconds
        val rolling = snap.currentPaceSecPerKm
        val projectedFinish = if (rolling != null && opp.forecastDistanceMeters != null && opp.forecastDistanceMeters > 0)
            (snap.movingSeconds + rolling * ((opp.forecastDistanceMeters - snap.distanceMeters).coerceAtLeast(0.0) / 1000.0)).toInt() else null
        val avg = snap.avgPaceSecPerKm
        val roundToUser = avg?.let { it < reference }
        // Model confidence decays with how far off its number your session average is.
        val confidence = if (avg != null && opp.forecastConfidencePercent > 0) {
            val off = kotlin.math.abs(avg - reference) / reference
            (opp.forecastConfidencePercent * (1.0 - (off * 2.5).coerceIn(0.0, 0.9))).toInt()
        } else null
        // Horde: separation in metres at your average pace vs theirs over the moving time so far.
        val separation = if (opp.isHorde && snap.movingSeconds > 30) {
            val hordeMeters = snap.movingSeconds / reference * 1000.0
            (snap.distanceMeters - hordeMeters).toInt()
        } else null
        val closing = if (opp.isHorde && rolling != null) {
            // metres per minute by which they close at your *current* pace: their speed − yours.
            ((1000.0 / reference - 1000.0 / rolling) * 60).toInt()
        } else null
        // Board projection: the ledger with this round added, through the cached daily roster.
        var projectedRank: Int? = null
        var rankDelta: Int? = null
        if (roundToUser != null && !opp.isHorde) {
            runCatching {
                val sessions = cachedSessions ?: return@runCatching
                val ledgerNow = dev.eversorhn.gait.domain.ledger.Ledger.from(sessions)
                val lead = ledgerNow.lead + (if (roundToUser) opp.stake else -opp.stake)
                val streak = ledgerNow.streak?.let { (side, n) -> if (side == dev.eversorhn.gait.domain.ledger.Side.USER) n else -n } ?: 0
                val projectedLedger = dev.eversorhn.gait.domain.ledger.LedgerState(
                    userPoints = (ledgerNow.userPoints + if (roundToUser) opp.stake else 0),
                    twinPoints = (ledgerNow.twinPoints + if (!roundToUser) opp.stake else 0),
                    rounds = ledgerNow.rounds,
                )
                val now = Instant.now(); val zoned = now.atZone(ZoneId.systemDefault())
                val offset = zoned.offset.totalSeconds * 1000L
                val today = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(now.toEpochMilli(), offset)
                val snapRoster = dev.eversorhn.gait.domain.roster.RosterEngine.snapshot(
                    opp.enrolledEpochDay, today, zoned.hour * 60 + zoned.minute, projectedLedger, (opp.fidelity * 100).toInt(), ledgerNow,
                )
                projectedRank = snapRoster.user.rank
                rankDelta = opp.todayRank?.let { it - snapRoster.user.rank }
            }
        }
        return LiveProjection(gapSeconds, projectedFinish, roundToUser, projectedRank, rankDelta, separation, closing, confidence, splits)
    }

    private var cachedSessions: List<dev.eversorhn.gait.data.db.entity.SessionEntity>? = null

    /** One line for the live feed. Numbers, not voices: the gap, the mark, the direction. */
    private fun lineFor(opp: LiveOpponent, trigger: LiveCommentary.Trigger): String {
        fun gapLabel(g: Double): String {
            val t = kotlin.math.abs(g).toInt()
            return "${t / 60}:${(t % 60).toString().padStart(2, '0')}/km"
        }
        val (zone, gap, km) = when (trigger) {
            is LiveCommentary.Trigger.KmMark -> Triple(trigger.zone, trigger.gapSecPerKm, trigger.km)
            is LiveCommentary.Trigger.LeadChange -> Triple(trigger.zone, trigger.gapSecPerKm, null)
        }
        val mark = if (km != null) "Km $km" else "Lead change"
        return when (zone) {
            LiveZone.AHEAD -> "$mark · ${gapLabel(gap)} up"
            LiveZone.BEHIND -> "$mark · ${gapLabel(gap)} down"
            LiveZone.LEVEL -> "$mark · level"
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
            cachedSessions = sessions
            val offset = zoned.offset.totalSeconds * 1000L
            val enrolledDay = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(repository.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis, offset)
            val todayRank = if (!profile.isHorde) runCatching {
                dev.eversorhn.gait.domain.roster.RosterEngine.snapshot(
                    enrolledDay, dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(now.toEpochMilli(), offset), zoned.hour * 60 + zoned.minute,
                    dev.eversorhn.gait.domain.ledger.Ledger.from(sessions), (profile.fidelity * 100).toInt(), dev.eversorhn.gait.domain.ledger.Ledger.from(sessions),
                ).user.rank
            }.getOrNull() else null
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
                    forecastConfidencePercent = forecast?.confidencePercent ?: 0,
                    enrolledEpochDay = enrolledDay,
                    todayRank = todayRank,
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
        _uiState.value.opponent?.let { o ->
            val ref = if (_uiState.value.duel) o.duelTargetPaceSecPerKm ?: o.forecastPaceSecPerKm else o.forecastPaceSecPerKm
            dev.eversorhn.gait.tracking.LiveOpponentInfo.current = dev.eversorhn.gait.tracking.LiveOpponentInfo(
                name = if (o.isHorde) "Horde" else o.name, referencePaceSecPerKm = ref, forecastFinishSeconds = o.forecastFinishSeconds, stake = o.stake,
            )
        }
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
            val result = finalizer.finalize(
                distanceMeters, movingSeconds, dataSource = SessionSource.GPS, duel = _uiState.value.duel,
                route = snapshot.routePolyline.takeIf { it.isNotBlank() },
                elevationGainMeters = snapshot.elevationGainMeters.takeIf { snapshot.gpsFixCount > 0 },
                splitSeconds = snapshot.splitSeconds,
            )
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
