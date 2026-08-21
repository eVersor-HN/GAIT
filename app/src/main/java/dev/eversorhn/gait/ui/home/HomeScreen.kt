package dev.eversorhn.gait.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.board.BoardScreen
import dev.eversorhn.gait.ui.forecast.ForecastScreen
import dev.eversorhn.gait.ui.messages.MessagesScreen
import dev.eversorhn.gait.ui.stats.StatsScreen
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ExitGuard
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.TextFaint
import kotlinx.coroutines.launch

/** The four swipeable pages of the main screen, in order. */
enum class HomePage(val label: String) { BOARD("Board"), FORECAST("Forecast"), CHANNEL("Channel"), STATS("Stats") }

/**
 * The main screen: Board (or Containment Map) → Forecast → Direct Channel → Statistics as
 * swipeable pages, with a page indicator under the HUD. Opens on the board, as before. Pushes
 * (Track, Log, Rest & Vacation, Settings) stay separate routes on top. One ExitGuard for the
 * whole thing, so the system back gesture always asks before the app closes.
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
    onSettings: () -> Unit,
    onEnrolNew: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = 0, pageCount = { HomePage.entries.size })
    val scope = rememberCoroutineScope()
    fun go(page: HomePage) { scope.launch { pager.animateScrollToPage(page.ordinal) } }

    ExitGuard(opponentName = if (isHorde) "The horde" else opponentName.ifBlank { "The model" })

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPageChanged(HomePage.entries[it]) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageIndicator(current = pager.currentPage, isHorde = isHorde, onSelect = { go(it) })
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (HomePage.entries[page]) {
                HomePage.BOARD -> BoardScreen(onContinue = { go(HomePage.FORECAST) }, onEnrolNew = onEnrolNew)
                HomePage.FORECAST -> ForecastScreen(
                    onBoard = { go(HomePage.BOARD) },
                    onStartActivity = onStartActivity,
                    onStartDuel = onStartDuel,
                    onLogSession = onLogSession,
                    onMessages = { go(HomePage.CHANNEL) },
                    onRestDays = onRestDays,
                    onStats = { go(HomePage.STATS) },
                    onSettings = onSettings,
                )
                HomePage.CHANNEL -> MessagesScreen(onDone = { go(HomePage.FORECAST) })
                HomePage.STATS -> StatsScreen(onDone = { go(HomePage.FORECAST) })
            }
        }
    }
}

/** Four small labelled dots; tap to jump, swipe to move. */
@Composable
private fun PageIndicator(current: Int, isHorde: Boolean, onSelect: (HomePage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomePage.entries.forEachIndexed { i, p ->
            val active = i == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(p) },
            ) {
                Box(
                    Modifier
                        .width(if (active) 18.dp else 6.dp)
                        .height(6.dp)
                        .background(if (active) Brass else LineSoft, RoundedCornerShape(3.dp))
                )
                Text(
                    (if (p == HomePage.BOARD && isHorde) "Map" else p.label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Brass else TextFaint,
                )
            }
        }
    }
}
