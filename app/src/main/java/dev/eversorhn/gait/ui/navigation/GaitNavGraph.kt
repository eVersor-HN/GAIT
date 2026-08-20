package dev.eversorhn.gait.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.ui.forecast.ForecastScreen
import dev.eversorhn.gait.ui.logsession.LogSessionScreen
import dev.eversorhn.gait.ui.setup.NamingScreen

private object Routes {
    const val LOADING = "loading"
    const val NAMING = "naming"
    const val FORECAST = "forecast"
    const val LOG_SESSION = "log_session"
}

@Composable
fun GaitNavGraph() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LOADING) {
        composable(Routes.LOADING) {
            val app = LocalContext.current.applicationContext as GaitApplication
            LaunchedEffect(Unit) {
                val hasTwin = app.repository.getTwinProfile() != null
                val destination = if (hasTwin) Routes.FORECAST else Routes.NAMING
                navController.navigate(destination) {
                    popUpTo(Routes.LOADING) { inclusive = true }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("GAIT")
            }
        }

        composable(Routes.NAMING) {
            NamingScreen(onConfirmed = {
                navController.navigate(Routes.FORECAST) {
                    popUpTo(Routes.NAMING) { inclusive = true }
                }
            })
        }

        composable(Routes.FORECAST) {
            ForecastScreen(onLogSession = { navController.navigate(Routes.LOG_SESSION) })
        }

        composable(Routes.LOG_SESSION) {
            LogSessionScreen(onDone = { navController.popBackStack() })
        }
    }
}
