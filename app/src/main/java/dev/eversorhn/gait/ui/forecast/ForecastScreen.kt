package dev.eversorhn.gait.ui.forecast

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.R
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.CollapsiblePanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.FormDots
import dev.eversorhn.gait.ui.theme.MessageCard
import dev.eversorhn.gait.ui.theme.MessageTone
import dev.eversorhn.gait.ui.theme.Meter
import dev.eversorhn.gait.ui.theme.StageBar
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.PhaseTrack
import dev.eversorhn.gait.ui.theme.Quote
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.Sparkline
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextFaint

@Composable
fun ForecastScreen(
    onBoard: () -> Unit,
    onStartActivity: () -> Unit,
    onStartDuel: () -> Unit,
    onLogSession: () -> Unit,
    onMessages: () -> Unit,
    onRestDays: () -> Unit,
    onStats: () -> Unit,
) {
    val viewModel: ForecastViewModel = dev.eversorhn.gait.ui.gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            ForecastUiState.Loading, ForecastUiState.NoTwin -> {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            is ForecastUiState.Ready -> {
                val twinColor = if (s.isHorde) Alert else Cyan
                val them = if (s.isHorde) "the horde" else s.opponentName

                PhaseTrack(current = 1)
                ScreenTitle(
                    eyebrow = if (s.isHorde) "Contact projection" else "Pre-session forecast",
                    headline = if (s.isHorde) "Today's line" else "What ${s.opponentName} expects today",
                )

                // --- Model calibration: what the opponent can and cannot do yet ---
                CorpoPanel {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(if (s.isHorde) "Horde calibration" else "Model calibration", color = TextFaint)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (s.calibration.remaining == null) "${s.calibration.sessions} sessions"
                            else "${s.calibration.sessions} / ${dev.eversorhn.gait.ui.forecast.Calibration.FULL_AT}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                        )
                    }
                    StageBar(
                        progress = s.calibration.progress,
                        stages = listOf(
                            "Estimates" to dev.eversorhn.gait.ui.forecast.Calibration.FORECAST_AT / dev.eversorhn.gait.ui.forecast.Calibration.FULL_AT.toFloat(),
                            "Points" to dev.eversorhn.gait.ui.forecast.Calibration.STAKES_AT / dev.eversorhn.gait.ui.forecast.Calibration.FULL_AT.toFloat(),
                            "Full" to 1f,
                        ),
                        reached = listOf(s.calibration.forecasting, s.calibration.staking, s.calibration.remaining == null).count { it },
                    )
                    FootNote(s.calibration.label)
                }

                if (s.restStateLabel != null) {
                    Text(s.restStateLabel, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                }

                // --- The forecast itself. Before the first session the calibration bar says it all. ---
                if (!s.coldStart) CorpoPanel {
                    if (s.ruleNote != null) {
                        FootNote(s.ruleNote)
                    }
                    if (!s.coldStart) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.scoredOnDimensions) {
                                StatTile("Route", s.usualRouteShare?.let { "$it% usual" } ?: "—", accent = twinColor, sub = "new route = round")
                                StatTile("Steadiness", s.forecastConsistencyPercent?.let { "$it%" } ?: "—", accent = twinColor, sub = "beat it = round")
                                StatTile("Distance", s.forecastDistanceLabel, accent = twinColor)
                            } else {
                                StatTile(s.paceWord, s.forecastPaceLabel, accent = twinColor)
                                StatTile("Distance", s.forecastDistanceLabel, accent = twinColor)
                                if (s.forecastClimbLabel != null && (s.activityLabel == "Hiking" || s.activityLabel == "Cycling")) StatTile("Climb", s.forecastClimbLabel, accent = twinColor)
                                else StatTile("Finish", s.forecastFinishLabel, accent = twinColor)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        FootNote(
                            "Forecast confidence: ${s.confidencePercent}% · based on " +
                                pluralStringResource(R.plurals.sessions_count, s.basedOnSessions, s.basedOnSessions)
                        )
                    }
                }

                // --- The stake: the opponent commits points against you ---
                s.stake?.let { st ->
                    CorpoPanel(tone = if (st.called) PanelTone.WARN else PanelTone.TWIN) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SectionLabel(if (s.isHorde) "Containment commitment" else "Model commitment", color = if (st.called) Alert else twinColor)
                            Text(
                                (if (st.called) "${st.calledPoints} pts · called" else "${st.points} pts at stake").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (st.called) Alert else twinColor,
                            )
                        }
                        Text(st.claim, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        if (!st.called) {
                            Spacer(Modifier.height(2.dp))
                            CorpoButton(
                                text = "Counter-stake · make it ${st.calledPoints} pts",
                                onClick = viewModel::callStake,
                                kind = ButtonKind.RISK,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FootNote(if (s.isHorde) "Hold the line and the points are yours" else "Beat it and the points are yours")
                        } else {
                            FootNote("Exposure doubled both ways · ${st.calledPoints} pts ride on today's pace")
                        }
                    }
                }

                FootNote("${s.activityLabel} · ${s.generationLabel} ${s.generation} · ${s.opponentLabel} · basis: ${s.totalSessions} sessions")

                CorpoButton(
                    text = when {
                        s.coldStart -> "Start first session"
                        s.stake != null -> "Start route — take the points"
                        s.isHorde -> "Start route — outrun them"
                        else -> "Start route — refute it"
                    },
                    onClick = onStartActivity,
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- Phase 04: substitution eligible ---
                if (s.trialEligible) {
                    CorpoPanel(tone = PanelTone.WARN) {
                        SectionLabel("Substitution eligible", color = Alert)
                        Text(
                            "${s.opponentName} — ${s.metricLabel} ${s.metricPercent}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = Alert,
                        )
                        Meter(fraction = s.metricPercent / 100f, color = Alert, threshold = s.trialThresholdPercent / 100f)
                        Text(
                            if (s.isHorde) "One run faster than your own best breaks the wave."
                            else "Beat your own best over 1 km to reset ${s.opponentName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        CorpoButton("Start duel · 3 pts", onClick = onStartDuel, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
                        FootNote(
                            "${s.trialLabel} · min. 1 km · " + (s.trialDeadlineDays?.let { d ->
                                if (d == 0) "auto-review TODAY — uncontested, the model is ratified" else "auto-review in $d d — uncontested, the model is ratified"
                            } ?: "judged on average pace"),
                            color = if ((s.trialDeadlineDays ?: 9) <= 2) Alert else TextFaint,
                        )
                    }
                }

                // --- The ledger: how far apart you are ---
                CorpoPanel {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("Asset ledger")
                        FootNote("${s.ledger.roundsPlayed} rounds")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${s.ledger.userPoints}", style = MaterialTheme.typography.headlineLarge, color = Brass)
                        Text("—", style = MaterialTheme.typography.titleLarge, color = TextFaint)
                        Text("${s.ledger.twinPoints}", style = MaterialTheme.typography.headlineLarge, color = twinColor)
                        Spacer(Modifier.weight(1f))
                        FormDots(form = s.ledger.form().map { it == Side.USER }, twinColor = twinColor)
                    }
                    Text(
                        s.standing.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (s.ledger.leader) {
                            Side.USER -> Brass
                            Side.TWIN -> twinColor
                            null -> TextFaint
                        },
                    )
                }


                // --- Asset status: the number the whole loop is about (folded; summary in the header) ---
                CollapsiblePanel(
                    title = "Asset status",
                    summary = "${s.metricPercent}% ${s.metricLabel.lowercase()} · ${s.generationLabel.lowercase()} ${s.generation} · review at ${s.trialThresholdPercent}%",
                    initiallyExpanded = s.trialEligible,
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${s.metricPercent}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (s.trialEligible) Alert else Brass,
                        )
                        Text(
                            s.metricLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Meter(
                        fraction = s.metricPercent / 100f,
                        color = if (s.trialEligible) Alert else Brass,
                        threshold = s.trialThresholdPercent / 100f,
                    )
                    if (s.fidelityHistory.size >= 2) {
                        Spacer(Modifier.height(2.dp))
                        SectionLabel("${s.metricLabel} over sessions", color = TextFaint)
                        Sparkline(points = s.fidelityHistory, threshold = s.trialThresholdPercent / 100f)
                    }
                    FootNote("${s.generationLabel} ${s.generation} · next review at ${s.trialThresholdPercent}%")
                }


                // --- Secondary actions. Both are about a session; settings live in the header. ---
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CorpoButton("Log manually", onClick = onLogSession, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
                    CorpoButton("Availability", onClick = onRestDays, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** The demo's "MON · COWED" / "THU · PREDATORY" tags, relabelled for a Horde. */
fun composureTag(state: ComposureState, isHorde: Boolean): String = if (isHorde) {
    when (state) {
        ComposureState.COWED -> "fallen back"
        ComposureState.WATCHFUL -> "tracking"
        ComposureState.PREDATORY -> "swarming"
    }
} else {
    state.name.lowercase()
}
