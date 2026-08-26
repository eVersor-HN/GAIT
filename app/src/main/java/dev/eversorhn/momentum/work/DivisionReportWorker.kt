package dev.eversorhn.momentum.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eversorhn.momentum.MomentumApplication
import dev.eversorhn.momentum.data.db.entity.isHorde
import dev.eversorhn.momentum.domain.forecast.ForecastEngine
import dev.eversorhn.momentum.domain.ledger.Ledger
import dev.eversorhn.momentum.domain.opponent.OpponentActivity
import dev.eversorhn.momentum.domain.roster.RosterEngine
import dev.eversorhn.momentum.notification.NotificationPrefs
import dev.eversorhn.momentum.notification.TwinNotifier
import java.time.Instant
import java.time.ZoneId

/**
 * The daily close. The division keeps running while you don't: the roster re-ranks, your model
 * holds or gains, the horde closes or falls back. Once a day this reports what moved, in
 * figures — no voice, no message, just the standing you would otherwise have to open the app
 * to see.
 *
 * One line per enrolment, at most three, on the Division channel (low importance).
 */
class DivisionReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MomentumApplication ?: return Result.success()
        if (NotificationPrefs.isMuted(applicationContext)) return Result.success()
        val repository = app.repository

        val now = Instant.now()
        val zoned = now.atZone(ZoneId.systemDefault())
        val offset = zoned.offset.totalSeconds * 1000L
        val today = RosterEngine.epochDay(now.toEpochMilli(), offset)

        val profiles = runCatching { repository.listProfiles() }.getOrNull().orEmpty().take(3)
        for (profile in profiles) {
            val sessions = runCatching { repository.getSessions(profile.id) }.getOrNull().orEmpty()
            if (sessions.isEmpty()) continue
            val ledger = Ledger.from(sessions)
            val enrolled = RosterEngine.epochDay(profile.createdAtEpochMillis, offset)
            val fidelityPercent = (profile.fidelity * 100).toInt()
            val daysSince = sessions.firstOrNull()?.let { (now.toEpochMilli() - it.startTimeEpochMillis) / 86_400_000L }

            // What it did while you were out. The model keeps its own schedule.
            val forecast = ForecastEngine().forecast(sessions, zoned.dayOfWeek.value, now.toEpochMilli())
            val activity = OpponentActivity.since(
                sessionsNewestFirst = sessions,
                forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                forecastDistanceMeters = forecast?.forecastDistanceMeters,
                nowEpochMillis = now.toEpochMilli(),
                plannedDaysOff = runCatching { repository.getPlannedDaysOff().toSet() }.getOrNull().orEmpty(),
                weeklyRestDays = (1..7).filter { (profile.restDayMask shr (it - 1)) and 1 == 1 }.toSet(),
            )
            val activityLine = if (!activity.active) null else {
                val km = "%.1f km".format(activity.distanceMeters / 1000.0)
                val pace = activity.paceSecPerKm?.let {
                    dev.eversorhn.momentum.domain.activity.Activities.formatPaceOrSpeed(it, profile.activityType)
                }
                if (profile.isHorde) "Horde covered $km while you were out"
                else "${profile.twinName} trained ${activity.sessions}× · $km${pace?.let { " at $it" } ?: ""}"
            }

            val body: String = if (profile.isHorde) {
                // Proximity is the horde's whole story: how much ground is left, and which way it moved.
                val snapshot = runCatching {
                    RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelityPercent, ledger)
                }.getOrNull()
                val separation = ((100 - fidelityPercent).coerceAtLeast(1)) * 6
                buildString {
                    activityLine?.let { append(it); append(" · ") }
                    append("Horde at $separation m · proximity $fidelityPercent%")
                    snapshot?.let { append(" · ${it.decommissioned30d} released in 30 d") }
                    daysSince?.let { if (it >= 2) append(" · $it days since your last session") }
                }
            } else {
                val snapshot = runCatching {
                    RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelityPercent, ledger)
                }.getOrNull() ?: continue
                val you = snapshot.user
                val model = snapshot.twin
                buildString {
                    activityLine?.let { append(it); append(" · ") }
                    append("You #${you.rank}")
                    append(if (you.delta == 0) " (±0)" else if (you.delta > 0) " (+${you.delta})" else " (${you.delta})")
                    model?.let {
                        append(" · ${profile.twinName} #${it.rank}")
                        append(if (it.delta == 0) " (±0)" else if (it.delta > 0) " (+${it.delta})" else " (${it.delta})")
                        val places = it.rank - you.rank
                        append(
                            when {
                                places > 0 -> " · $places ${if (places == 1) "place" else "places"} behind you"
                                places < 0 -> " · ${-places} ${if (places == -1) "place" else "places"} ahead of you"
                                else -> " · level with you"
                            }
                        )
                    }
                    append(" · ledger ${ledger.userPoints}–${ledger.twinPoints}")
                    if (snapshot.nextCullInDays <= 7) append(" · cull in ${snapshot.nextCullInDays} d, line #${snapshot.cullLine}")
                    daysSince?.let { if (it >= 2) append(" · $it days since your last session") }
                }
            }

            val title = profile.profileName.ifBlank { if (profile.isHorde) "Containment" else profile.twinName }
            // One stable id per enrolment: today's close replaces yesterday's rather than stacking.
            TwinNotifier.postDivisionNotice(applicationContext, title, body, TwinNotifier.Kind.NOTICE, stableId = 9000 + profile.id.toInt())
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "momentum-division-report"
    }
}
