package dev.eversorhn.gait.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.ui.forecast.ForecastScreen
import dev.eversorhn.gait.ui.logsession.LogSessionScreen
import dev.eversorhn.gait.ui.restdays.RestDaysScreen
import dev.eversorhn.gait.ui.setup.HordeSetupScreen
import dev.eversorhn.gait.ui.setup.NamingScreen
import dev.eversorhn.gait.ui.setup.OpponentTypeScreen
import dev.eversorhn.gait.ui.stats.StatsScreen
import dev.eversorhn.gait.ui.theme.CorpoBackground
import dev.eversorhn.gait.ui.theme.CorpoStatusBar
import dev.eversorhn.gait.ui.track.TrackScreen

private object Routes {
    const val LOADING = "loading"
    const val OPPONENT_TYPE = "opponent_type"
    const val NAMING = "naming"
    const val HORDE_SETUP = "horde_setup"
    const val FORECAST = "forecast"
    const val TRACK = "track"
    const val LOG_SESSION = "log_session"
    const val REST_DAYS = "rest_days"
    const val STATS = "stats"
}

private val routeLabels = mapOf(
    Routes.LOADING to "GAIT",
    Routes.OPPONENT_TYPE to "SETUP",
    Routes.NAMING to "SETUP",
    Routes.HORDE_SETUP to "SETUP",
    Routes.FORECAST to "FORECAST",
    Routes.TRACK to "TRACK",
    Routes.LOG_SESSION to "LOG SESSION",
    Routes.REST_DAYS to "REST & VACATION",
    Routes.STATS to "STATISTICS",
)

@Composable
fun GaitNavGraph() {
    val navController: NavHostController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val label = routeLabels[currentRoute?.destination?.route] ?: "GAIT"

    CorpoBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CorpoStatusBar(label = label)

            NavHost(navController = navController, startDestination = Routes.LOADING) {
                composable(Routes.LOADING) {
                    val app = LocalContext.current.applicationContext as GaitApplication
                    LaunchedEffect(Unit) {
                        val hasTwin = app.repository.getTwinProfile() != null
                        val destination = if (hasTwin) Routes.FORECAST else Routes.OPPONENT_TYPE
                        navController.navigate(destination) {
                            popUpTo(Routes.LOADING) { inclusive = true }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("GAIT")
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
                        onLogSession = { navController.navigate(Routes.LOG_SESSION) },
                        onRestDays = { navController.navigate(Routes.REST_DAYS) },
                        onStats = { navController.navigate(Routes.STATS) },
                    )
                }

                composable(Routes.TRACK) {
                    TrackScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.LOG_SESSION) {
                    LogSessionScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.REST_DAYS) {
                    RestDaysScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.STATS) {
                    StatsScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}
