package dev.eversorhn.gait.ui.debrief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.directive.Directive
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.ui.theme.FormDots
import dev.eversorhn.gait.domain.session.DebriefResult
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.ui.forecast.composureTag
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CompareGrid
import dev.eversorhn.gait.ui.theme.CompareRow
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Good
import dev.eversorhn.gait.ui.theme.MessageCard
import dev.eversorhn.gait.ui.theme.MessageTone
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.PhaseTrack
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.Sparkline
import dev.eversorhn.gait.ui.theme.TextFaint
import dev.eversorhn.gait.ui.theme.formatSignedPoints

/**
 * Shared by both entry points into a finished session: manual logging and GPS tracking.
 * Phase 03 (Fidelity Debrief) and, after a Trial, Phase 05 (Generational Handoff) — laid out
 * like the concept demo: compare grid, big Fidelity number with delta and sparkline, the
 * opponent's message as a card.
 */
@Composable
fun DebriefContent(result: DebriefResult, onDone: () -> Unit) {
    val isHorde = result.opponentType == OpponentType.HORDE
    val duel = result.duel
    val won = duel?.verdict == DecommissionTrial.Verdict.WON
    val lost = duel?.verdict == DecommissionTrial.Verdict.LOST

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PhaseTrack(current = if (won) 5 else if (duel != null) 4 else 3)
        ScreenTitle(
            eyebrow = when {
                won -> "Generational handoff"
                duel != null -> if (isHorde) "Outrun Trial" else "Decommission Trial"
                else -> "Debrief"
            },
            headline = when {
                won -> if (isHorde) "The wave breaks" else "${result.opponentName} is decommissioned"
                lost -> if (isHorde) "They kept up" else "${result.opponentName} stays"
                else -> "Forecast vs. Actual"
            },
            headlineColor = when {
                won -> Good
                lost -> Alert
                else -> MaterialTheme.colorScheme.onBackground
            },
        )

        // --- The ruling: who took the round, and the ledger after it ---
        result.roundWinner?.let { winner ->
            val toUser = winner == Side.USER
            val twinColor = if (isHorde) Alert else Cyan
            CorpoPanel(tone = if (toUser) PanelTone.GOOD else PanelTone.WARN) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("Ruling", color = if (toUser) Good else Alert)
                    FootNote(
                        when {
                            duel != null -> "duel · ${result.stake} pts"
                            result.stakeCalled -> "called stake · ${result.stake} pts"
                            result.stakeWasOpen -> "staked · ${result.stake} pts"
                            else -> "${result.stake} pt"
                        },
                    )
                }
                Text(
                    Directive.ruling(toUser, result.stake, result.opponentName, isHorde),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (toUser) Good else Alert,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        FootNote("You")
                        Text("${result.ledger.userPoints}", style = MaterialTheme.typography.headlineLarge, color = Brass)
                    }
                    Text("—", style = MaterialTheme.typography.headlineLarge, color = TextFaint)
                    Column {
                        FootNote(if (isHorde) "horde" else result.opponentName)
                        Text("${result.ledger.twinPoints}", style = MaterialTheme.typography.headlineLarge, color = twinColor)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        FootNote("Form")
                        FormDots(form = result.ledger.form().map { it == Side.USER }, twinColor = twinColor)
                    }
                }
                FootNote(
                    Directive.standing(result.ledger, result.opponentName, isHorde) +
                        " · was ${result.ledgerBefore.userPoints}—${result.ledgerBefore.twinPoints}",
                    color = if (toUser) Brass else twinColor,
                )
            }
        }

        result.commendation?.let { note ->
            CorpoPanel(tone = PanelTone.GOOD) {
                SectionLabel("Asset Performance Division · commendation", color = Good)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // --- Duel verdict, when there was one ---
        duel?.let { d ->
            CorpoPanel(tone = if (won) PanelTone.GOOD else PanelTone.WARN) {
                SectionLabel(
                    when (d.verdict) {
                        DecommissionTrial.Verdict.WON -> "Duel: won"
                        DecommissionTrial.Verdict.LOST -> "Duel: lost"
                        DecommissionTrial.Verdict.TOO_SHORT -> "Duel: void"
                    },
                    color = if (won) Good else Alert,
                )
                CompareGrid(
                    rows = listOf(CompareRow(result.paceWord, d.targetPaceLabel, result.actualPaceLabel, actualGood = if (d.verdict == DecommissionTrial.Verdict.TOO_SHORT) null else won)),
                    forecastHeader = "Target",
                    actualHeader = "You",
                )
                if (d.verdict == DecommissionTrial.Verdict.TOO_SHORT) {
                    Text(
                        "Under ${DecommissionTrial.MIN_DUEL_DISTANCE_METERS.toInt()} m — a sprint isn't a duel. Logged as a normal session.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // --- Forecast vs actual ---
        if (result.hadForecast) {
            CorpoPanel {
                CompareGrid(
                    rows = buildList {
                        if (result.scoredOnDimensions) {
                            add(CompareRow("Route", "usual", result.routeNoveltyPercent?.let { if (it >= 40) "new · $it%" else "usual · $it% new" } ?: "no trace", actualGood = result.routeNoveltyPercent?.let { it >= 40 }))
                            add(CompareRow("Steadiness", result.forecastConsistencyPercent?.let { "$it%" } ?: "—", result.consistencyPercent?.let { "$it%" } ?: "—",
                                actualGood = if (result.consistencyPercent != null && result.forecastConsistencyPercent != null) result.consistencyPercent >= result.forecastConsistencyPercent + 2 else null))
                            add(CompareRow(result.paceWord, result.forecastPaceLabel, result.actualPaceLabel))
                        } else {
                            add(CompareRow(result.paceWord, result.forecastPaceLabel, result.actualPaceLabel, actualGood = result.beatForecast))
                            result.consistencyPercent?.let { add(CompareRow("Steadiness", result.forecastConsistencyPercent?.let { f -> "$f%" } ?: "—", "$it%")) }
                        }
                        add(CompareRow("Distance", result.forecastDistanceLabel, result.actualDistanceLabel))
                        if (result.elevationGainLabel != null) add(CompareRow("Climb", result.forecastElevationLabel ?: "—", result.elevationGainLabel))
                        add(CompareRow("Finish", result.forecastFinishLabel, result.actualFinishLabel))
                        if (!result.scoredOnDimensions && result.routeNoveltyPercent != null) add(CompareRow("Route", "usual", if (result.routeNoveltyPercent >= 40) "new · ${result.routeNoveltyPercent}%" else "usual"))
                    }
                )
                if (result.scoredOnDimensions) FootNote("Motor-assisted: judged on route novelty (≥ 40 % new) or steadiness above the model's expectation — not on speed")
                if (result.dataSource == SessionSource.MANUAL) {
                    Spacer(Modifier.height(2.dp))
                    FootNote("Self-reported · not GPS-verified")
                }
            }
        } else {
            CorpoPanel {
                Text(
                    "No forecast existed yet — this session is the baseline.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(result.actualPaceLabel, style = MaterialTheme.typography.titleLarge, color = Brass)
                    Text(result.actualDistanceLabel, style = MaterialTheme.typography.titleLarge)
                    Text(result.actualFinishLabel, style = MaterialTheme.typography.titleLarge)
                }
                if (result.dataSource == SessionSource.MANUAL) FootNote("Self-reported · not GPS-verified")
            }
        }

        // --- First sessions: what happens next (the new user's "so what now?") ---
        if (!result.hadForecast) {
            CorpoPanel {
                SectionLabel("What happens next")
                Text("From your next session ${if (isHorde) "the horde" else result.opponentName} forecasts you — and every forecasted session is a round on the ledger.", style = MaterialTheme.typography.bodyMedium)
                Text("From three sessions it starts staking points on its forecast. You can counter.", style = MaterialTheme.typography.bodyMedium)
                Text("You're a new hire: protected from the quarterly cull for 60 days. Everyone starts at the bottom of the board.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // --- Fidelity balance ---
        val delta = result.newFidelityPercent - result.previousFidelityPercent
        CorpoPanel {
            SectionLabel("Overall ${result.metricLabel}")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${result.newFidelityPercent}%", style = MaterialTheme.typography.headlineLarge, color = if (won) Good else Brass)
                Text(
                    when {
                        won -> "reset from ${result.previousFidelityPercent}%"
                        result.restNote != null -> "frozen"
                        !result.hadForecast -> "baseline"
                        else -> "${formatSignedPoints(delta)} this session"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        won || delta < 0 -> Good
                        delta > 0 -> Alert
                        else -> TextFaint
                    },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (result.fidelityHistory.size >= 2) {
                Sparkline(points = result.fidelityHistory, threshold = result.trialThresholdPercent / 100f)
            }
            FootNote(
                "${result.generationLabel} ${result.generation} · " +
                    if (won) "initialising" else "next review at ${result.trialThresholdPercent}%"
            )
            Text("Composure: ${composureTag(result.composureState, isHorde)}".uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
        }

        result.restNote?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cyan)
        }

        // --- What it said ---
        if (result.twinLine != null) {
            val tone = when {
                won -> MessageTone.TWIN
                else -> when (result.composureState) {
                    ComposureState.PREDATORY -> MessageTone.PREDATORY
                    ComposureState.COWED -> MessageTone.COWED
                    ComposureState.WATCHFUL -> MessageTone.WATCHFUL
                }
            }
            MessageCard(
                from = if (isHorde) "The Horde" else "${result.opponentName} (Twin-${if (won) result.generation - 1 else result.generation})",
                tag = if (won) "direct channel" else composureTag(result.composureState, isHorde),
                body = result.twinLine,
                tone = tone,
                footer = if (won) {
                    {
                        Text("DUEL: WON", style = MaterialTheme.typography.labelSmall, color = Good)
                        Text("${result.metricLabel} reset → ${result.newFidelityPercent}%".uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
                        Text("${result.generationLabel} ${result.generation} is initialising…".uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    }
                } else null,
            )
        }

        CorpoButton("Back to forecast", onClick = onDone, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}
