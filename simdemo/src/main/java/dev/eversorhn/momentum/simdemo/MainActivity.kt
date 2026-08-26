package dev.eversorhn.momentum.simdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.eversorhn.momentum.simdemo.ui.theme.CorpoBackground
import dev.eversorhn.momentum.simdemo.ui.theme.CorpoStatusBar
import dev.eversorhn.momentum.simdemo.ui.theme.MomentumDemoTheme

/**
 * The entire app is this one screen -- a standalone, shareable teaser for MOMENTUM that never
 * touches the real app's data (it can't; separate applicationId, separate sandbox). See
 * docs/simulation-mode.md for why this is a second APK instead of a mode inside the real app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveFullscreen()
        setContent {
            MomentumDemoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CorpoBackground {
                        Column(modifier = Modifier.fillMaxSize()) {
                            CorpoStatusBar(label = "MOMENTUM DEMO")
                            SimulationScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
