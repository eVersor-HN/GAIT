package dev.eversorhn.gait.ui.restdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.VACATION_DAYS_PER_YEAR
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class RestDaysUiState(
    val restDayMask: Int = 0,
    val vacationDaysRemaining: Int = VACATION_DAYS_PER_YEAR,
    val onVacationUntilLabel: String? = null,
    val loaded: Boolean = false,
)

class RestDaysViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RestDaysUiState())
    val uiState: StateFlow<RestDaysUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun currentYear() = Instant.now().atZone(ZoneId.systemDefault()).year

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val now = System.currentTimeMillis()
            _uiState.value = RestDaysUiState(
                restDayMask = profile.restDayMask,
                vacationDaysRemaining = RestDayPolicy.remainingVacationDays(profile, currentYear()),
                onVacationUntilLabel = if (RestDayPolicy.isOnVacation(profile, now)) {
                    formatDate(profile.vacationEndEpochMillis!!)
                } else null,
                loaded = true,
            )
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
