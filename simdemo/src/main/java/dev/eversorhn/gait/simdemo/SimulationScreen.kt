package dev.eversorhn.gait.simdemo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.simdemo.util.withFullMotion
import kotlin.random.Random

// Fixed demo content -- this app has no real Twin/Horde data to read, by design (a separate
// app can't see the main GAIT app's database, and shouldn't try to). See
// docs/simulation-mode.md.
private const val OPPONENT_NAME = "Markus K."
private val COWED_LINES = listOf(
    "...Okay. That was fast. I don't have anything for that.",
    "Fine. You win this one.",
    "...I'll need a minute.",
)

private const val TOTAL_SECONDS = 1500 // a simulated 25:00
private const val YOUR_PACE_SEC_PER_KM = 300.0 // 5:00/km
private const val OPPONENT_PACE_SEC_PER_KM = 315.0 // 5:15/km

@Composable
fun SimulationScreen() {
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    var resultLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(finished) {
        if (!finished) {
            progress.snapTo(0f)
            withFullMotion {
                progress.animateTo(1f, animationSpec = tween(durationMillis = 16_000, easing = LinearEasing))
            }
            resultLine = COWED_LINES.random(Random)
            finished = true
        }
    }

    val elapsedSeconds = (progress.value * TOTAL_SECONDS).toInt()
    val yourKm = elapsedSeconds / YOUR_PACE_SEC_PER_KM
    val opponentKm = elapsedSeconds / OPPONENT_PACE_SEC_PER_KM
    val gapMeters = ((yourKm - opponentKm) * 1000).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "DEMO — NOT A REAL SESSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        if (!finished) {
            Text("DEMO SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.headlineLarge)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${"%.2f".format(yourKm)} km · you",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${"%.2f".format(opponentKm)} km · $OPPONENT_NAME",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    if (gapMeters >= 0) "+$gapMeters m ahead" else "$gapMeters m behind",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text("DEMO COMPLETE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("This is what GAIT feels like", style = MaterialTheme.typography.headlineLarge)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Finished 5.00 km · 25:00 · pace 5:00/km", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Beat $OPPONENT_NAME by $gapMeters m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (resultLine != null) {
                Text("“$resultLine”", style = MaterialTheme.typography.bodyLarge)
            }

            Text(
                "The real app runs this against your own history, with GPS or indoor tracking, " +
                    "a name and voice you pick, and a lot more at stake. This demo is just the shape of it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    finished = false
                    resultLine = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("RUN AGAIN")
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
