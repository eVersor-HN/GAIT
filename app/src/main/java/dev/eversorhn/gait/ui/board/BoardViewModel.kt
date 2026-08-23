package dev.eversorhn.gait.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.domain.roster.RosterSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** The user's own career numbers for the board: how long they've lasted, and how well. */
data class Career(
    val tenureDays: Long,
    val cullsSurvived: Int,
    val reviewsSurvived: Int,
    val bestStreak: Int,
    val roundsPlayed: Int,
)

/** The end of the line: the user was in the bottom CULL_COUNT at a quarterly cull. */
data class Termination(val day: Long, val rank: Int, val headcount: Int, val cullLine: Int, val daysAgo: Long)

data class BoardUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val opponentName: String = "",
    val proximityPercent: Int = 50,
    val snapshot: RosterSnapshot? = null,
    val career: Career? = null,
    val termination: Termination? = null,
    /** Slot of the asset whose dossier is open, if any. */
    val dossier: RosterEngine.Dossier? = null,
    val dossierStanding: dev.eversorhn.gait.domain.roster.Standing? = null,
    // --- what the Standing page needs beyond the roster ---
    val userPoints: Int = 0,
    val opponentPoints: Int = 0,
    val roundsPlayed: Int = 0,
    val form: List<Boolean> = emptyList(),
    val standingLine: String = "",
    val trialEligible: Boolean = false,
    val trialDeadlineDays: Int? = null,
    /** The last few things the opponent said (Twin) or the last sounds (Horde). */
    val transmissions: List<String> = emptyList(),
    val separationMeters: Int = 0,
    val releasedTotal: Int = 0,
    val daysSinceLast: Long? = null,
)

/**
 * Builds the division's roster snapshot for the Asset Board (Twin) / the containment map
 * (Horde). The simulation is pure and cached in RosterEngine; this supplies the user's side
 * (ledger today vs. yesterday, Fidelity), the local clock — and keeps the board live: it
 * re-evaluates every minute while open, because assets' results land at their own training
 * minute, so a row moves when *that person* finishes, not on a timer of its own.
 */
class BoardViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
            // Minute tick: recompute the intraday view (cheap; the day simulation is cached).
            while (isActive) {
                delay(60_000L)
                refresh()
            }
        }
    }

    fun openDossier(slot: Int) {
        val snap = _uiState.value.snapshot ?: return
        val d = RosterEngine.dossier(slot, snap.day) ?: return
        _uiState.value = _uiState.value.copy(dossier = d, dossierStanding = snap.standings.firstOrNull { it.asset.slot == slot })
    }

    fun closeDossier() {
        _uiState.value = _uiState.value.copy(dossier = null, dossierStanding = null)
    }

    /** "Enrol a new asset": the terminated profile and its history are wiped; the app returns to setup. */
    fun enrolNewAsset(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.wipeAll()
            onDone()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val sessions = repository.getSessions()
            val now = Instant.now()
            val zoned = now.atZone(ZoneId.systemDefault())
            val offset = zoned.offset.totalSeconds * 1000L
            val today = RosterEngine.epochDay(now.toEpochMilli(), offset)
            val enrolled = RosterEngine.epochDay(repository.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis, offset)
            val startOfToday = (today * 86_400_000L) - offset
            val ledger = Ledger.from(sessions)
            val ledgerYesterday = Ledger.from(sessions.filter { it.startTimeEpochMillis < startOfToday })
            val fidelity = (profile.fidelity * 100).toInt()

            val imported = repository.getImportedAssets().mapNotNull { row ->
                dev.eversorhn.gait.domain.transfer.AssetTransfer.decode(row.payload)?.let { t ->
                    dev.eversorhn.gait.domain.roster.ImportedSpec(
                        id = t.id, name = t.name, kind = t.kind, archetype = t.archetype, talent = t.talent,
                        consistency = t.consistency, grit = t.grit, trend = t.trend, trainingMinute = t.trainingMinute,
                        restMask = t.restMask, startIndex = t.indexAtExport.toDouble().coerceIn(0.0, RosterEngine.CEILING),
                        importedDay = row.importedEpochDay,
                    )
                }
            }
            val snapshot = withContext(Dispatchers.Default) {
                RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelity, ledgerYesterday, includeTwin = !profile.isHorde, imported = imported)
            }

            // --- Quarterly culls since enrolment: was the user ever in the bottom 400? ---
            var termination: Termination? = null
            var cullsSurvived = 0
            for (cullDay in RosterEngine.cullDaysSince(enrolled, today)) {
                val endOfCullDay = ((cullDay + 1) * 86_400_000L) - offset
                val ledgerThen = Ledger.from(sessions.filter { it.startTimeEpochMillis < endOfCullDay })
                val verdict = RosterEngine.cullVerdict(cullDay, RosterEngine.userIndex(ledgerThen, fidelity)) ?: continue
                if (verdict.culled) {
                    termination = Termination(cullDay, verdict.rank, verdict.headcount, verdict.cullLine, today - cullDay)
                    break
                }
                cullsSurvived++
            }

            val bestStreak = run {
                var best = 0; var cur = 0; var side: Side? = null
                for (r in ledger.rounds.asReversed()) {
                    if (r.winner == Side.USER) { cur = if (side == Side.USER) cur + 1 else 1; side = Side.USER; if (cur > best) best = cur }
                    else { cur = 0; side = Side.TWIN }
                }
                best
            }
            val career = Career(
                tenureDays = today - enrolled,
                cullsSurvived = cullsSurvived,
                reviewsSurvived = ((today - enrolled) / RosterEngine.REVIEW_EVERY_DAYS).toInt(),
                bestStreak = bestStreak,
                roundsPlayed = ledger.roundsPlayed,
            )

            // A big climb since yesterday's close earns a division note — once per day.
            if (ledgerYesterday.roundsPlayed > 0) snapshot.user.prevRank?.let { prev ->
                dev.eversorhn.gait.domain.directive.Commendation.forClimb(prev - snapshot.user.rank, snapshot.user.rank)?.let { note ->
                    val already = repository.getMessages().any { it.kind == dev.eversorhn.gait.data.db.entity.MessageKind.COMMENDATION && it.epochMillis >= startOfToday && it.line.startsWith(note.code) }
                    if (!already) repository.recordMessage(dev.eversorhn.gait.data.db.entity.MessageKind.COMMENDATION, "${note.code} · ${note.body}", null, now.toEpochMilli())
                }
            }

            val messages = repository.getMessages()
            val transmissions = if (profile.isHorde) {
                // A horde does not speak: the log is what you heard, in brackets, newest first.
                (sessions.mapNotNull { it.twinLine } + messages.map { it.line }).filter { it.startsWith("[") }.take(4)
                    .ifEmpty { listOf(dev.eversorhn.gait.domain.horde.HordeSoundCues.idleCaption()) }
            } else {
                (sessions.mapNotNull { s -> s.twinLine } + messages.map { it.line }).take(3)
            }
            val separation = ((100 - fidelity).coerceAtLeast(1) * 6)
            _uiState.value = _uiState.value.copy(
                loaded = true,
                userPoints = ledger.userPoints,
                opponentPoints = ledger.twinPoints,
                roundsPlayed = ledger.roundsPlayed,
                form = ledger.form().map { it == Side.USER },
                standingLine = dev.eversorhn.gait.domain.directive.Directive.standing(ledger, profile.twinName, profile.isHorde),
                trialEligible = dev.eversorhn.gait.domain.trial.DecommissionTrial.isEligible(profile.fidelity),
                trialDeadlineDays = profile.trialDeadlineEpochDay.takeIf { it >= 0 }?.let { (it - today).toInt().coerceAtLeast(0) },
                transmissions = transmissions,
                separationMeters = separation,
                releasedTotal = snapshot.decommissioned.size,
                daysSinceLast = sessions.firstOrNull()?.let { (System.currentTimeMillis() - it.startTimeEpochMillis) / 86_400_000L },
                isHorde = profile.isHorde,
                opponentName = profile.twinName,
                proximityPercent = fidelity,
                snapshot = snapshot,
                career = career,
                termination = termination,
            )
        }
    }
}
