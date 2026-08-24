package dev.eversorhn.gait.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/**
 * GAIT on the wrist: the two numbers you would otherwise pull a phone out of a pocket for —
 * where you stand against your opponent right now, and where you stand on the board.
 *
 * The watch computes nothing. The phone owns the simulation and pushes a small snapshot over
 * the data layer; this draws it. That keeps the watch cheap on battery and means the two halves
 * can never disagree.
 */
class WearActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearScreen() }
    }
}

/** Everything the phone sends, in one place. */
data class WearState(
    val live: Boolean = false,
    val opponent: String = "",
    val gapLabel: String = "",
    val gapAhead: Boolean = true,
    val distanceLabel: String = "",
    val paceLabel: String = "",
    val heartRate: Int? = null,
    val rank: String = "",
    val standing: String = "",
    val stale: Boolean = true,
)

private val Ink = Color(0xFF07080A)
private val Brass = Color(0xFFD8A62A)
private val Alert = Color(0xFFC8412F)
private val TextDim = Color(0xFF8A8F98)

@Composable
private fun WearScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(WearState()) }

    DisposableEffect(Unit) {
        val client = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.dataItem.uri.path == WearSync.PATH) {
                    state = WearSync.decode(DataMapItem.fromDataItem(event.dataItem))
                }
            }
        }
        client.addListener(listener)
        // Whatever the phone last sent, so the face is not empty before the next update.
        client.dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item ->
                if (item.uri.path == WearSync.PATH) state = WearSync.decode(DataMapItem.fromDataItem(item))
            }
            buffer.release()
        }
        onDispose { client.removeListener(listener) }
    }

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Ink).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (state.opponent.isBlank()) {
                    Text("GAIT", color = Brass, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Open the phone to enrol", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                    return@Column
                }

                if (state.live) {
                    Text(
                        state.gapLabel,
                        color = if (state.gapAhead) Brass else Alert,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(if (state.gapAhead) "UP ON ${state.opponent.uppercase()}" else "DOWN ON ${state.opponent.uppercase()}",
                        color = TextDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text("${state.distanceLabel} · ${state.paceLabel}", color = Color.White, fontSize = 13.sp)
                    state.heartRate?.let { Text("$it bpm", color = TextDim, fontSize = 12.sp) }
                } else {
                    Text(state.rank, color = Brass, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("ON THE BOARD", color = TextDim, fontSize = 10.sp)
                    Text(state.standing, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (state.stale) Text("phone out of reach", color = TextDim, fontSize = 9.sp)
            }
        }
    }
}
