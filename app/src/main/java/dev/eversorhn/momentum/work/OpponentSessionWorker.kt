package dev.eversorhn.momentum.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.eversorhn.momentum.MomentumApplication
import dev.eversorhn.momentum.data.db.entity.isHorde
import dev.eversorhn.momentum.domain.activity.Activities
import dev.eversorhn.momentum.domain.forecast.ForecastEngine
import dev.eversorhn.momentum.domain.opponent.OpponentSession
import dev.eversorhn.momentum.notification.NotificationPrefs
import dev.eversorhn.momentum.notification.TwinNotifier
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The opponent's session, shared while it happens — the way a training partner's live card
 * reads: how far in it is, how far it has to go, and a countdown to the finish that the system
 * ticks itself.
 *
 * Runs on a quarter-hour beat, and while a session is actually live it re-enqueues itself every
 * few minutes so the distance keeps moving. Nothing runs when nobody is training.
 */
class OpponentSessionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MomentumApplication ?: return Result.success()
        val repository = app.repository
        val now = Instant.now()
        val zoned = now.atZone(ZoneId.systemDefault())
        val offset = zoned.offset.totalSeconds * 1000L
        val epochDay = Math.floorDiv(now.toEpochMilli() + offset, 86_400_000L)

        var anyLive = false
        val profiles = runCatching { repository.listProfiles() }.getOrNull().orEmpty().take(3)
        for (profile in profiles) {
            val id = NOTIFICATION_ID_BASE + profile.id.toInt()
            if (NotificationPrefs.isMuted(applicationContext)) {
                TwinNotifier.cancelOpponentSession(applicationContext, id)
                continue
            }
            val sessions = runCatching { repository.getSessions(profile.id) }.getOrNull().orEmpty()
            val forecast = ForecastEngine().forecast(sessions, zoned.dayOfWeek.value, now.toEpochMilli())
            val plan = OpponentSession.planFor(
                seed = profile.id,
                epochDay = epochDay,
                sessionsNewestFirst = sessions,
                forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                forecastDistanceMeters = forecast?.forecastDistanceMeters,
                zoneOffsetMillis = offset,
                plannedDaysOff = runCatching { repository.getPlannedDaysOff().toSet() }.getOrNull().orEmpty(),
                weeklyRestDays = (1..7).filter { (profile.restDayMask shr (it - 1)) and 1 == 1 }.toSet(),
            )
            if (plan == null) {
                TwinNotifier.cancelOpponentSession(applicationContext, id)
                continue
            }

            val who = if (profile.isHorde) "The horde" else profile.twinName
            val activity = Activities.byKey(profile.activityType)
            val live = OpponentSession.liveAt(plan, epochDay, now.toEpochMilli(), offset)
            val endMillis = OpponentSession.endMillis(plan, epochDay, offset)

            when {
                live != null -> {
                    anyLive = true
                    val covered = "%.2f".format(live.coveredMeters / 1000.0)
                    val total = "%.2f".format(plan.distanceMeters / 1000.0)
                    val paceLabel = Activities.formatPaceOrSpeed(plan.paceSecPerKm, profile.activityType)
                    TwinNotifier.postOpponentSession(
                        context = applicationContext,
                        stableId = id,
                        who = who,
                        title = "$who is training · ${activity.label.lowercase()}",
                        body = "$covered / $total km · $paceLabel · ${(live.fraction * 100).toInt()}% done",
                        progressPercent = (live.fraction * 100).toInt(),
                        endsAtEpochMillis = endMillis,
                    )
                }
                // Just finished: the closing figures, then it can be swiped away.
                now.toEpochMilli() in endMillis..(endMillis + DONE_WINDOW_MILLIS) -> {
                    val total = "%.2f".format(plan.distanceMeters / 1000.0)
                    val paceLabel = Activities.formatPaceOrSpeed(plan.paceSecPerKm, profile.activityType)
                    val mins = plan.durationSeconds / 60
                    TwinNotifier.postOpponentSessionDone(
                        context = applicationContext,
                        stableId = id,
                        who = who,
                        title = "$who finished",
                        body = "$total km in ${mins}:${"%02d".format(plan.durationSeconds % 60)} · $paceLabel",
                    )
                }
                else -> TwinNotifier.cancelOpponentSession(applicationContext, id)
            }
        }

        // While something is live, come back sooner than the quarter-hour beat.
        if (anyLive) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                FOLLOW_UP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<OpponentSessionWorker>()
                    .setInitialDelay(FOLLOW_UP_MINUTES, TimeUnit.MINUTES)
                    .build(),
            )
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "momentum-opponent-session"
        const val FOLLOW_UP_WORK_NAME = "momentum-opponent-session-follow-up"
        private const val FOLLOW_UP_MINUTES = 3L
        private const val DONE_WINDOW_MILLIS = 25 * 60 * 1000L
        private const val NOTIFICATION_ID_BASE = 9500
    }
}
