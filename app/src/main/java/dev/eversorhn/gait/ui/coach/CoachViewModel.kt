package dev.eversorhn.gait.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.activity.Activities
import dev.eversorhn.gait.domain.coach.Coach
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.domain.wager.WagerPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class CoachUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val headline: String? = null,
    val headlineUrgent: Boolean = false,
    val items: List<Coach.Item> = emptyList(),
)

class CoachViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val sessions = repository.getSessions()
            val now = Instant.now()
            val zoned = now.atZone(ZoneId.systemDefault())
            val offset = zoned.offset.totalSeconds * 1000L
            val todayIso = zoned.dayOfWeek.value
            val ledger = Ledger.from(sessions)
            val forecast = ForecastEngine().forecast(sessions, todayIso, now.toEpochMilli())
            val isHorde = profile.isHorde
            val activityKey = repository.activeActivityType
            val fidelity = (profile.fidelity * 100).toInt()

            // Board position (twin only) — the coach needs the cull line to be concrete.
            var rank: Int? = null; var cullLine: Int? = null; var cullDays: Int? = null
            if (!isHorde) {
                runCatching {
                    val today = RosterEngine.epochDay(now.toEpochMilli(), offset)
                    val enrolled = RosterEngine.epochDay(repository.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis, offset)
                    val snap = withContext(Dispatchers.Default) {
                        RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelity, ledger)
                    }
                    rank = snap.user.rank; cullLine = snap.cullLine; cullDays = snap.nextCullInDays
                }
            }
            val todayEpochDay = WagerPolicy.epochDay(now.toEpochMilli(), offset)
            val stake = if (profile.wagerStake > 0 && profile.wagerEpochDay == todayEpochDay)
                WagerPolicy.roundStake(true, profile.wagerCalled, profile.wagerStake) else 1
            val deadline = profile.trialDeadlineEpochDay.takeIf { it >= 0 && DecommissionTrial.isEligible(profile.fidelity) }
                ?.let { (it - todayEpochDay).toInt().coerceAtLeast(0) }

            val items = Coach.advise(
                sessionsNewestFirst = sessions,
                ledger = ledger,
                isHorde = isHorde,
                opponentName = profile.twinName,
                fidelityPercent = fidelity,
                forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                forecastDistanceMeters = forecast?.forecastDistanceMeters,
                userRank = rank,
                cullLine = cullLine,
                nextCullInDays = cullDays,
                trialDeadlineDays = deadline,
                stakePoints = stake,
                formatPace = { Activities.formatPaceOrSpeed(it, activityKey) },
                nowEpochMillis = now.toEpochMilli(),
                todayIso = todayIso,
            )

            val them = if (isHorde) "the horde" else profile.twinName
            val headline = when {
                isHorde -> "Proximity $fidelity% · ${if (fidelity >= 80) "they are reading you well" else "they are guessing"}. ${ledger.roundsPlayed} runs on file."
                ledger.roundsPlayed == 0 -> "No rounds yet. The first forecasted session starts the ledger."
                ledger.leader == Side.USER -> "You lead $them ${ledger.userPoints}–${ledger.twinPoints}${rank?.let { " · board #$it" } ?: ""}."
                ledger.leader == Side.TWIN -> "$them leads ${ledger.twinPoints}–${ledger.userPoints}${rank?.let { " · board #$it" } ?: ""}."
                else -> "Level with $them at ${ledger.userPoints}${rank?.let { " · board #$it" } ?: ""}."
            }

            _uiState.value = CoachUiState(
                loaded = true,
                isHorde = isHorde,
                headline = headline,
                headlineUrgent = ledger.leader == Side.TWIN || (rank != null && cullLine != null && rank!! > cullLine!!),
                items = items,
            )
        }
    }
}
