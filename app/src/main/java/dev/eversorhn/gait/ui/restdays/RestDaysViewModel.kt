package dev.eversorhn.gait.ui.restdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.VACATION_DAYS_PER_YEAR
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.domain.wager.WagerPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One cell of the calendar grid. */
data class CalendarDay(
    val epochDay: Long,
    val dayOfMonth: Int,
    val isoDayOfWeek: Int,
    val isToday: Boolean,
    val isPast: Boolean,
    val weeklyRest: Boolean,
    val planned: Boolean,
    val onVacation: Boolean,
)

data class RestDaysUiState(
    val restDayMask: Int = 0,
    val vacationDaysRemaining: Int = VACATION_DAYS_PER_YEAR,
    val onVacationUntilLabel: String? = null,
    val loaded: Boolean = false,
    // --- calendar ---
    val monthLabel: String = "",
    val monthOffset: Int = 0,
    /** Leading blanks so the 1st lands under its weekday (Monday-first). */
    val leadingBlanks: Int = 0,
    val days: List<CalendarDay> = emptyList(),
    val plannedCount: Int = 0,
    val plannedUpcoming: Int = 0,
)

/**
 * Weekly rest pattern + vacation bank (as before) plus the calendar: tap any day from today
 * on to mark it off in advance; the app remembers and treats it as a rest day when it comes.
 */
class RestDaysViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RestDaysUiState())
    val uiState: StateFlow<RestDaysUiState> = _uiState.asStateFlow()

    private var monthOffset = 0

    init { refresh() }

    private fun currentYear() = Instant.now().atZone(ZoneId.systemDefault()).year

    fun showMonth(offset: Int) { monthOffset = offset.coerceIn(0, 11); refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayEpoch = today.toEpochDay()
            val planned = repository.getPlannedDaysOff().toSet()
            val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
            val first = month.atDay(1)
            val vacationEndDay = profile.vacationEndEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            val days = (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                val e = date.toEpochDay()
                CalendarDay(
                    epochDay = e,
                    dayOfMonth = d,
                    isoDayOfWeek = date.dayOfWeek.value,
                    isToday = e == todayEpoch,
                    isPast = e < todayEpoch,
                    weeklyRest = RestDayPolicy.isRestDay(profile, date.dayOfWeek.value),
                    planned = e in planned,
                    onVacation = vacationEndDay != null && e in todayEpoch..vacationEndDay,
                )
            }
            _uiState.value = RestDaysUiState(
                restDayMask = profile.restDayMask,
                vacationDaysRemaining = RestDayPolicy.remainingVacationDays(profile, currentYear()),
                onVacationUntilLabel = if (RestDayPolicy.isOnVacation(profile, now)) formatDate(profile.vacationEndEpochMillis!!) else null,
                loaded = true,
                monthLabel = "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                monthOffset = monthOffset,
                leadingBlanks = first.dayOfWeek.value - 1,
                days = days,
                plannedCount = planned.size,
                plannedUpcoming = planned.count { it >= todayEpoch },
            )
        }
    }

    fun togglePlanned(epochDay: Long) {
        viewModelScope.launch {
            val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            if (epochDay < todayEpoch) return@launch
            val currently = repository.isPlannedDayOff(epochDay)
            repository.setPlannedDayOff(epochDay, !currently)
            refresh()
        }
    }

    fun toggleDay(dayOfWeek: Int) {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            repository.updateTwinProfile(RestDayPolicy.toggleRestDay(profile, dayOfWeek))
            refresh()
        }
    }

    fun startVacation(days: Int) {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val updated = RestDayPolicy.startVacation(profile, days, System.currentTimeMillis(), currentYear())
            repository.updateTwinProfile(updated)
            refresh()
        }
    }

    fun endVacationEarly() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            repository.updateTwinProfile(RestDayPolicy.endVacationEarly(profile))
            refresh()
        }
    }

    private fun formatDate(epochMillis: Long): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return "${zdt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${zdt.dayOfMonth}"
    }
}
