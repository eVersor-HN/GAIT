package dev.eversorhn.gait.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.repository.GaitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.ALL,
    val totalSessions: Int = 0,
    val totalDistanceLabel: String = "0.0 km",
    val avgPaceLabel: String = "—",
    val fidelityPercent: Int = 0,
    val fidelityTrend: List<Float> = emptyList(),
    val rows: List<SessionRow> = emptyList(),
    val loaded: Boolean = false,
)

class StatsViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var allSessions: List<SessionEntity> = emptyList()

    init {
        viewModelScope.launch {
            allSessions = repository.getSessions() // newest-first, per SessionDao
            val profile = repository.getTwinProfile()
            applyPeriod(StatsPeriod.ALL, profile?.fidelity ?: 0f)
        }
    }

    fun selectPeriod(period: StatsPeriod) {
        viewModelScope.launch {
            val profile = repository.getTwinProfile()
            applyPeriod(period, profile?.fidelity ?: 0f)
        }
    }

    private fun applyPeriod(period: StatsPeriod, currentFidelity: Float) {
        val cutoff = period.days?.let { System.currentTimeMillis() - it * 86_400_000L }
        val filtered = if (cutoff == null) allSessions else allSessions.filter { it.startTimeEpochMillis >= cutoff }

        val totalDistanceKm = filtered.sumOf { it.distanceMeters } / 1000.0
        val totalDurationSec = filtered.sumOf { it.durationSeconds }
        val avgPace = if (totalDistanceKm > 0) totalDurationSec / totalDistanceKm else null

        // Oldest-first for a left-to-right trend line, using each session's own delta from
        // its forecast (0 when there wasn't one yet) as a cheap fidelity-shape proxy.
        val trend = filtered.reversed().mapNotNull { s ->
            s.forecastPaceSecPerKm?.let { fp -> (1.0 - abs(fp - s.avgPaceSecPerKm) / fp).toFloat().coerceIn(0f, 1f) }
        }

        _uiState.value = StatsUiState(
            period = period,
            totalSessions = filtered.size,
            totalDistanceLabel = "%.1f km".format(totalDistanceKm),
            avgPaceLabel = avgPace?.let { formatMinSec(it) + "/km" } ?: "—",
            fidelityPercent = (currentFidelity * 100).toInt(),
            fidelityTrend = trend,
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
        )
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private fun formatMinSec(totalSeconds: Double): String {
        val whole = totalSeconds.toInt()
        return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
    }
}
