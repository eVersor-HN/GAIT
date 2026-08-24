package dev.eversorhn.gait.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.ui.briefing.BriefingScreen
import dev.eversorhn.gait.ui.enrol.EnrolScreen
import dev.eversorhn.gait.ui.home.HomePage
import dev.eversorhn.gait.ui.home.HomeScreen
import dev.eversorhn.gait.ui.logsession.LogSessionScreen
import dev.eversorhn.gait.ui.profiles.ProfilesScreen
import dev.eversorhn.gait.ui.restdays.RestDaysScreen
import dev.eversorhn.gait.ui.settings.SettingsScreen
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.CorpoBackground
import dev.eversorhn.gait.ui.theme.CorpoStatusBar
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.LedgerStrip
import dev.eversorhn.gait.ui.theme.TickerItem
import dev.eversorhn.gait.ui.theme.TickerStrip
import dev.eversorhn.gait.ui.track.TrackScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private object Routes {
    const val PROFILES = "profiles"
    const val ENROL = "enrol"
    const val HOME = "home"
    const val TRACK = "track"
    const val TRACK_PATTERN = "track?duel={duel}"
    const val LOG_SESSION = "log_session"
    const val REST_DAYS = "rest_days"
    const val SETTINGS = "settings"
    const val BRIEFING = "briefing"
}

private val routeLabels = mapOf(
    Routes.PROFILES to "ENROLMENTS",
    Routes.ENROL to "ENROLMENT",
    Routes.LOG_SESSION to "LOG SESSION",
    Routes.REST_DAYS to "AVAILABILITY",
    Routes.SETTINGS to "SETTINGS",
    Routes.BRIEFING to "BRIEFING",
)

@Composable
fun GaitNavGraph(startSession: Boolean = false) {
    val navController: NavHostController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    var homePage by remember { mutableStateOf(HomePage.STANDING) }
    val app = LocalContext.current.applicationContext as GaitApplication

    // Nothing else runs until this has been read once. Declining closes the app.
    val gateContext = LocalContext.current
    var disclaimerAccepted by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(
            dev.eversorhn.gait.ui.onboarding.DisclaimerPrefs.isAccepted(gateContext)
        )
    }
    if (!disclaimerAccepted) {
        CorpoBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                CorpoStatusBar(label = "NOTICE")
                dev.eversorhn.gait.ui.onboarding.DisclaimerGate(onAccepted = { disclaimerAccepted = true })
            }
        }
        return
    }

    // Opened from the quick-settings tile: go to the enrolment you were last in and start there.
    androidx.compose.runtime.LaunchedEffect(startSession) {
        if (startSession && app.repository.activeProfileId != 0L) {
            navController.navigate(Routes.HOME)
            navController.navigate(Routes.TRACK)
        }
    }

    val ledgerFlow = remember(route) {
        combine(app.repository.observeTwinProfile(), app.repository.observeSessions()) { profile, sessions ->
            profile?.let { Triple(it, Ledger.from(sessions), Ledger.standingLabel(Ledger.from(sessions), it.twinName, it.isHorde)) }
        }
    }
    val ledgerInfo by ledgerFlow.collectAsState(initial = null)
    val onHome = route == Routes.HOME
    val isHorde = ledgerInfo?.first?.isHorde == true

    val label = when {
        route == Routes.TRACK_PATTERN && currentEntry?.arguments?.getBoolean("duel") == true -> "TRIAL"
        route == Routes.TRACK_PATTERN -> "SESSION"
        onHome -> when (homePage) {
            HomePage.STANDING -> if (isHorde) "CONTACT" else "STANDINGS"
            HomePage.FORECAST -> if (isHorde) "TARGET" else "FORECAST"
            HomePage.ANALYSIS -> "ANALYSIS"
            HomePage.LOG -> "LOG"
        }
        else -> routeLabels[route] ?: "GAIT"
    }

    CorpoBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CorpoStatusBar(
                label = label,
                // Settings belong to the enrolment you are inside — and to nowhere else.
                onSettings = if (onHome) ({ navController.navigate(Routes.SETTINGS) }) else null,
            )
            ledgerInfo?.let { (profile, ledger, standing) ->
                // The ticker is the division's board feed — a Twin enrolment only. A horde has no
                // board and no news; its page carries its own signals instead.
                if (onHome && !profile.isHorde) {
                    val ticker by produceState<List<TickerItem>>(initialValue = emptyList(), key1 = ledger.roundsPlayed, key2 = profile.fidelity) {
                        while (true) {
                            value = withContext(Dispatchers.Default) {
                                val now = Instant.now()
                                val zoned = now.atZone(ZoneId.systemDefault())
                                val offset = zoned.offset.totalSeconds * 1000L
                                val today = RosterEngine.epochDay(now.toEpochMilli(), offset)
                                val enrolled = RosterEngine.epochDay(app.repository.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis, offset)
                                val snap = RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, (profile.fidelity * 100).toInt(), ledger)
                                val news = ArrayList<TickerItem>()
                                news += TickerItem("You #${snap.user.rank}", snap.user.delta, isUser = true)
                                snap.twin?.let { news += TickerItem("${profile.twinName} #${it.rank}", it.delta) }
                                news += TickerItem(if (snap.nextCullInDays == 0) "Cull today · bottom ${RosterEngine.CULL_COUNT}" else "Cull in ${snap.nextCullInDays} d · bottom ${RosterEngine.CULL_COUNT}", 0, isNews = true)
                                news += TickerItem("${snap.underReview} under review", 0, isNews = true)
                                if (snap.decommissioned30d > 0) news += TickerItem("${snap.decommissioned30d} released · 30 d", 0, isNews = true)
                                snap.decommissioned.firstOrNull()?.let { news += TickerItem("Released: ${it.asset.name}", 0, isNews = true) }
                                if (profile.wagerStake > 0) news += TickerItem("${profile.twinName} has ${profile.wagerStake} pts on today", 0, isNews = true)
                                news += TickerItem("${"%,d".format(snap.enrolled)} enrolled", 0, isNews = true)
                                val movers = snap.movers.map { TickerItem("${it.asset.name} #${it.rank}", it.delta) }
                                val out = ArrayList<TickerItem>()
                                for (i in 0 until maxOf(news.size, movers.size)) {
                                    movers.getOrNull(i)?.let { out += it }
                                    news.getOrNull(i)?.let { out += it }
                                }
                                out
                            }
                            delay(60_000L)
                        }
                    }
                    TickerStrip(items = ticker)
                }
                if (onHome) {
                    LedgerStrip(
                        userPoints = ledger.userPoints,
                        twinPoints = ledger.twinPoints,
                        userShare = ledger.userShare,
                        opponentLabel = if (profile.isHorde) "HORDE" else profile.twinName,
                        standing = standing,
                        twinColor = if (profile.isHorde) Alert else Cyan,
                    )
                }
            }

            NavHost(navController = navController, startDestination = Routes.PROFILES) {
                composable(Routes.PROFILES) {
                    ProfilesScreen(
                        onOpen = { navController.navigate(Routes.HOME) },
                        onNew = { navController.navigate(Routes.ENROL) },
                        onBriefing = { navController.navigate(Routes.BRIEFING) },
                    )
                }

                composable(Routes.ENROL) {
                    EnrolScreen(
                        onCreated = { navController.navigate(Routes.HOME) { popUpTo(Routes.PROFILES) { inclusive = false } } },
                        onCancel = { navController.popBackStack() },
                    )
                }

                composable(Routes.HOME) {
                    val p = ledgerInfo?.first
                    HomeScreen(
                        opponentName = p?.twinName ?: "",
                        isHorde = p?.isHorde == true,
                        onPageChanged = { homePage = it },
                        onStartActivity = { navController.navigate(Routes.TRACK) },
                        onStartDuel = { navController.navigate("${Routes.TRACK}?duel=true") },
                        onLogSession = { navController.navigate(Routes.LOG_SESSION) },
                        onRestDays = { navController.navigate(Routes.REST_DAYS) },
                        onProfiles = { navController.navigate(Routes.PROFILES) { popUpTo(Routes.PROFILES) { inclusive = true } } },
                        onEnrolNew = { navController.navigate(Routes.ENROL) { popUpTo(Routes.PROFILES) { inclusive = false } } },
                    )
                }

                composable(
                    Routes.TRACK_PATTERN,
                    arguments = listOf(navArgument("duel") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    TrackScreen(
                        duel = entry.arguments?.getBoolean("duel") == true,
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(Routes.LOG_SESSION) { LogSessionScreen(onDone = { navController.popBackStack() }) }
                composable(Routes.REST_DAYS) { RestDaysScreen(onDone = { navController.popBackStack() }) }
                composable(Routes.BRIEFING) { BriefingScreen(onDone = { navController.popBackStack() }) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onDone = { navController.popBackStack() },
                        onWiped = { navController.navigate(Routes.PROFILES) { popUpTo(0) { inclusive = true } } },
                    )
                }
            }
        }
    }
}
