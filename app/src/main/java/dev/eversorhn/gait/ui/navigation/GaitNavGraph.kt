package dev.eversorhn.gait.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.ui.forecast.ForecastScreen
import dev.eversorhn.gait.ui.logsession.LogSessionScreen
import dev.eversorhn.gait.ui.messages.MessagesScreen
import dev.eversorhn.gait.ui.restdays.RestDaysScreen
import dev.eversorhn.gait.ui.settings.SettingsScreen
import dev.eversorhn.gait.ui.setup.HordeSetupScreen
import dev.eversorhn.gait.ui.setup.NamingScreen
import dev.eversorhn.gait.ui.setup.OpponentTypeScreen
import dev.eversorhn.gait.ui.stats.StatsScreen
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.CorpoBackground
import dev.eversorhn.gait.ui.theme.CorpoStatusBar
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.track.TrackScreen

private object Routes {
    const val LOADING = "loading"
    const val OPPONENT_TYPE = "opponent_type"
    const val NAMING = "naming"
    const val HORDE_SETUP = "horde_setup"
    const val FORECAST = "forecast"
    const val TRACK = "track"
    const val TRACK_PATTERN = "track?duel={duel}"
    const val LOG_SESSION = "log_session"
    const val MESSAGES = "messages"
    const val REST_DAYS = "rest_days"
    const val STATS = "stats"
    const val SETTINGS = "settings"
}

private val routeLabels = mapOf(
    Routes.LOADING to "GAIT",
    Routes.OPPONENT_TYPE to "SETUP",
    Routes.NAMING to "SETUP",
    Routes.HORDE_SETUP to "SETUP",
    Routes.FORECAST to "PRE-SESSION",
    Routes.TRACK_PATTERN to "TRACK",
    Routes.LOG_SESSION to "LOG SESSION",
    Routes.MESSAGES to "DIRECT CHANNEL",
    Routes.REST_DAYS to "REST & VACATION",
    Routes.STATS to "STATISTICS",
    Routes.SETTINGS to "SETTINGS",
)

@Composable
fun GaitNavGraph() {
    val navController: NavHostController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    val label = when {
        route == Routes.TRACK_PATTERN && currentEntry?.arguments?.getBoolean("duel") == true -> "ASSET REVIEW"
        else -> routeLabels[route] ?: "GAIT"
    }

    CorpoBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CorpoStatusBar(label = label)

            NavHost(navController = navController, startDestination = Routes.LOADING) {
                composable(Routes.LOADING) {
                    val app = LocalContext.current.applicationContext as GaitApplication
                    LaunchedEffect(Unit) {
                        val hasOpponent = app.repository.getTwinProfile() != null
                        val destination = if (hasOpponent) Routes.FORECAST else Routes.OPPONENT_TYPE
                        navController.navigate(destination) {
                            popUpTo(Routes.LOADING) { inclusive = true }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "GAIT",
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp, letterSpacing = 0.04.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            FootNote("Asset Twin Systems · R&D internal", color = Brass)
                        }
                    }
                }

                composable(Routes.OPPONENT_TYPE) {
                    OpponentTypeScreen(
                        onTwin = { navController.navigate(Routes.NAMING) },
                        onHorde = { navController.navigate(Routes.HORDE_SETUP) },
                    )
                }

                composable(Routes.NAMING) {
                    NamingScreen(onConfirmed = {
                        navController.navigate(Routes.FORECAST) {
                            popUpTo(Routes.OPPONENT_TYPE) { inclusive = true }
                        }
                    })
                }

                composable(Routes.HORDE_SETUP) {
                    HordeSetupScreen(onConfirmed = {
                        navController.navigate(Routes.FORECAST) {
                            popUpTo(Routes.OPPONENT_TYPE) { inclusive = true }
                        }
                    })
                }

                composable(Routes.FORECAST) {
                    ForecastScreen(
                        onStartActivity = { navController.navigate(Routes.TRACK) },
                        onStartDuel = { navController.navigate("${Routes.TRACK}?duel=true") },
                        onLogSession = { navController.navigate(Routes.LOG_SESSION) },
                        onMessages = { navController.navigate(Routes.MESSAGES) },
                        onRestDays = { navController.navigate(Routes.REST_DAYS) },
                        onStats = { navController.navigate(Routes.STATS) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
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

                composable(Routes.LOG_SESSION) {
                    LogSessionScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.MESSAGES) {
                    MessagesScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.REST_DAYS) {
                    RestDaysScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.STATS) {
                    StatsScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onDone = { navController.popBackStack() },
                        onWiped = {
                            // Everything's gone: drop the whole back stack and restart at setup.
                            navController.navigate(Routes.OPPONENT_TYPE) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}
