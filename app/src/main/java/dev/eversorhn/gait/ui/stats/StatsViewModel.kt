package dev.eversorhn.gait.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.eversorhn.gait.domain.directive.Directive
import dev.eversorhn.gait.domain.ledger.Ledger
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

enum class StatsPeriod(val label: String, val days: Int?) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    ALL("ALL", null),
}

data class SessionRow(
    val id: Long,
    val dateLabel: String,
    val distanceLabel: String,
    val paceLabel: String,
    val deltaLabel: String?,
    val deltaIsGood: Boolean,
    val isVerified: Boolean,
    val isRestDay: Boolean,
)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.ALL,
    val totalSessions: Int = 0,
    val totalDistanceLabel: String = "0.0 km",
    val avgPaceLabel: String = "—",
    val metricLabel: String = "Fidelity",
    val metricPercent: Int = 0,
    /**
     * Per-session forecast accuracy (1 - |forecast - actual| / forecast), oldest first.
     * Deliberately labeled as *accuracy*, not Fidelity: Fidelity is the EWMA of this, and the
     * two shouldn't be confused on screen.
     */
    val accuracyTrend: List<Float> = emptyList(),
    val rows: List<SessionRow> = emptyList(),
    val loaded: Boolean = false,
    // --- ledger ---
    val isHorde: Boolean = false,
    val opponentName: String = "",
    val userPoints: Int = 0,
    val twinPoints: Int = 0,
    val roundsPlayed: Int = 0,
    val standing: String = "",
    /** ISO weekday 1..7 → (user wins, twin wins), only weekdays with rounds. */
    val weekdayRecord: List<Triple<Int, Int, Int>> = emptyList(),
    val ownershipLine: String? = null,
)

class StatsViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var allSessions: List<SessionEntity> = emptyList()

    init {
        reload(StatsPeriod.ALL)
    }

    fun selectPeriod(period: StatsPeriod) = reload(period)

    fun refresh() = reload(_uiState.value.period)

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.deleteSession(id)
            reload(_uiState.value.period)
        }
    }

    private fun reload(period: StatsPeriod) {
        viewModelScope.launch {
            allSessions = repository.getSessions() // newest-first, per SessionDao
            val profile = repository.getTwinProfile()
            applyPeriod(
                period = period,
                currentFidelity = profile?.fidelity ?: 0f,
                metricLabel = if (profile?.isHorde == true) "Proximity" else "Fidelity",
                isHorde = profile?.isHorde == true,
                opponentName = profile?.twinName ?: "",
            )
        }
    }

    private fun applyPeriod(period: StatsPeriod, currentFidelity: Float, metricLabel: String, isHorde: Boolean, opponentName: String) {
        val cutoff = period.days?.let { System.currentTimeMillis() - it * 86_400_000L }
        val filtered = if (cutoff == null) allSessions else allSessions.filter { it.startTimeEpochMillis >= cutoff }

        val totalDistanceKm = filtered.sumOf { it.distanceMeters } / 1000.0
        val totalDurationSec = filtered.sumOf { it.durationSeconds }
        val avgPace = if (totalDistanceKm > 0) totalDurationSec / totalDistanceKm else null

        val trend = filtered.reversed().mapNotNull { s ->
            s.forecastPaceSecPerKm?.let { fp -> (1.0 - abs(fp - s.avgPaceSecPerKm) / fp).toFloat().coerceIn(0f, 1f) }
        }

        val ledger = Ledger.from(filtered)
        val them = if (isHorde) "the horde" else opponentName
        fun dayName(iso: Int) = DayOfWeek.of(iso).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val ownership = ledger.opponentStrongestWeekday()?.let { (d, v) -> "$them owns your ${dayName(d)}s, ${v.second}–${v.first}." }
            ?: ledger.userStrongestWeekday()?.let { (d, v) -> "${dayName(d)}s are yours, ${v.first}–${v.second}." }

        _uiState.value = StatsUiState(
            isHorde = isHorde,
            opponentName = opponentName,
            userPoints = ledger.userPoints,
            twinPoints = ledger.twinPoints,
            roundsPlayed = ledger.roundsPlayed,
            standing = Directive.standing(ledger, opponentName, isHorde),
            weekdayRecord = ledger.byWeekday().entries.sortedBy { it.key }.map { Triple(it.key, it.value.first, it.value.second) },
            ownershipLine = ownership,
            period = period,
            totalSessions = filtered.size,
            totalDistanceLabel = "%.1f km".format(totalDistanceKm),
            avgPaceLabel = avgPace?.let { formatMinSec(it) + "/km" } ?: "—",
            metricLabel = metricLabel,
            metricPercent = (currentFidelity * 100).toInt(),
            accuracyTrend = trend,
            rows = filtered.map { it.toRow() },
            loaded = true,
        )
    }

    private fun SessionEntity.toRow(): SessionRow {
        val delta = forecastPaceSecPerKm?.let { fp -> fp - avgPaceSecPerKm } // + means faster than forecast
        return SessionRow(
            id = id,
            dateLabel = formatDate(startTimeEpochMillis),
            distanceLabel = "%.2f km".format(distanceMeters / 1000.0),
            paceLabel = "${formatMinSec(avgPaceSecPerKm)}/km",
            deltaLabel = delta?.let { d ->
                val sign = if (d >= 0) "-" else "+"
                "$sign${formatMinSec(abs(d))} vs forecast"
            },
            deltaIsGood = (delta ?: 0.0) >= 0,
            isVerified = dataSource == SessionSource.GPS,
            isRestDay = isRestDay,
        )
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private fun formatMinSec(totalSeconds: Double): String {
        val whole = totalSeconds.toInt()
        return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
    }
}
