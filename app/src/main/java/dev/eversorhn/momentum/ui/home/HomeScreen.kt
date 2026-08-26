package dev.eversorhn.momentum.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.momentum.ui.board.BoardScreen
import dev.eversorhn.momentum.ui.coach.CoachScreen
import dev.eversorhn.momentum.ui.forecast.ForecastScreen
import dev.eversorhn.momentum.ui.stats.StatsScreen
import dev.eversorhn.momentum.ui.theme.Brass
import dev.eversorhn.momentum.ui.theme.LineSoft
import dev.eversorhn.momentum.ui.theme.TextFaint
import dev.eversorhn.momentum.ui.theme.pressable
import kotlinx.coroutines.launch

/** The four pages of an open enrolment, in the order you need them. */
enum class HomePage(val label: String) { STANDING("Standing"), FORECAST("Forecast"), ANALYSIS("Analysis"), LOG("Log") }

/**
 * An open enrolment: Standing (the board, or the containment display for a horde) → Forecast →
 * Analysis → Log. Each page opens on what you came for; the session starts from the button at
 * the top of the first page.
 */
@Composable
fun HomeScreen(
    opponentName: String,
    isHorde: Boolean,
    onPageChanged: (HomePage) -> Unit,
    onStartActivity: () -> Unit,
    onStartDuel: () -> Unit,
    onLogSession: () -> Unit,
    onRestDays: () -> Unit,
    onProfiles: () -> Unit,
    onEnrolNew: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = 0, pageCount = { HomePage.entries.size })
    val scope = rememberCoroutineScope()
    fun go(page: HomePage) { scope.launch { pager.animateScrollToPage(page.ordinal) } }

    // Back inside an enrolment goes up to the list; the app only closes from there.
    androidx.activity.compose.BackHandler(enabled = true) { onProfiles() }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPageChanged(HomePage.entries[it]) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageIndicator(current = pager.currentPage, isHorde = isHorde, onSelect = { go(it) })
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (HomePage.entries[page]) {
                HomePage.STANDING -> BoardScreen(
                    onGo = onStartActivity,
                    onStartDuel = onStartDuel,
                    onEnrolNew = onEnrolNew,
                    onProfiles = onProfiles,
                )
                HomePage.FORECAST -> ForecastScreen(
                    onBoard = { go(HomePage.STANDING) },
                    onStartActivity = onStartActivity,
                    onStartDuel = onStartDuel,
                    onLogSession = onLogSession,
                    onMessages = { go(HomePage.LOG) },
                    onRestDays = onRestDays,
                    onStats = { go(HomePage.LOG) },
                )
                HomePage.ANALYSIS -> CoachScreen()
                HomePage.LOG -> StatsScreen(onDone = { go(HomePage.FORECAST) })
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, isHorde: Boolean, onSelect: (HomePage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomePage.entries.forEachIndexed { i, p ->
            val active = i == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.pressable(onClick = { onSelect(p) }),
            ) {
                Box(
                    Modifier.width(if (active) 18.dp else 6.dp).height(6.dp)
                        .background(if (active) Brass else LineSoft, RoundedCornerShape(3.dp))
                )
                Text(
                    (if (p == HomePage.STANDING && isHorde) "Contact" else p.label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Brass else TextFaint,
                )
            }
        }
    }
}
