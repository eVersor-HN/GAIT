package dev.eversorhn.gait.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.MainActivity
import dev.eversorhn.gait.R
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.roster.RosterEngine
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The division on the home screen: your rank, the gap to your opponent, and the days to the
 * next cull. It is the pressure the app is about, visible without opening it.
 *
 * Refreshed on the system's own widget beat and by a half-hourly worker, so the rank keeps up
 * with the board as the roster moves through the day.
 */
class StandingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRefreshWorker.schedule(context)
        WidgetRenderer.renderAll(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.UNIQUE_WORK_NAME)
    }
}

/** Builds the views. Shared by the provider and the worker so both draw exactly the same card. */
object WidgetRenderer {

    suspend fun render(context: Context) = renderInternal(context)

    fun renderAll(context: Context) {
        // Called on the main thread by the provider: show what can be read without the database,
        // then let the worker fill in the numbers.
        WidgetRefreshWorker.runOnce(context)
    }

    internal suspend fun renderInternal(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StandingWidget::class.java))
        if (ids.isEmpty()) return

        val app = context.applicationContext as? GaitApplication ?: return
        val repository = app.repository
        val profile = runCatching { repository.getTwinProfile() }.getOrNull()
            ?: runCatching { repository.listProfiles().firstOrNull() }.getOrNull()

        val views = RemoteViews(context.packageName, R.layout.widget_standing)
        views.setOnClickPendingIntent(
            R.id.widget_rank,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        if (profile == null) {
            views.setTextViewText(R.id.widget_rank, "—")
            views.setTextViewText(R.id.widget_of, "no enrolment")
            views.setTextViewText(R.id.widget_ledger, "Enrol to be assigned an opponent")
            views.setTextViewText(R.id.widget_cull, "")
            ids.forEach { manager.updateAppWidget(it, views) }
            return
        }

        val sessions = runCatching { repository.getSessions(profile.id) }.getOrNull().orEmpty()
        val ledger = Ledger.from(sessions)
        val now = Instant.now()
        val zoned = now.atZone(ZoneId.systemDefault())
        val offset = zoned.offset.totalSeconds * 1000L
        val today = RosterEngine.epochDay(now.toEpochMilli(), offset)
        val enrolled = RosterEngine.epochDay(profile.createdAtEpochMillis, offset)
        val fidelity = (profile.fidelity * 100).toInt()
        val snap = runCatching {
            RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelity, ledger)
        }.getOrNull()

        val who = if (profile.isHorde) "the horde" else profile.twinName
        views.setTextViewText(R.id.widget_rank, snap?.let { "#${it.user.rank}" } ?: "—")
        views.setTextViewText(R.id.widget_of, snap?.let { "of ${"%,d".format(it.enrolled)}" } ?: "")
        views.setTextViewText(
            R.id.widget_ledger,
            if (ledger.roundsPlayed == 0) "No rounds yet · $who is waiting"
            else Ledger.standingLabel(ledger, profile.twinName, profile.isHorde),
        )
        views.setTextViewText(
            R.id.widget_cull,
            snap?.let { s ->
                val below = s.user.rank - s.cullLine
                buildString {
                    append(if (s.nextCullInDays == 0) "Cull today" else "Cull in ${s.nextCullInDays} d")
                    append(" · line #${s.cullLine}")
                    if (below > 0) append(" · $below below it")
                }
            } ?: "",
        )
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Keeps the card current without the app running. */
class WidgetRefreshWorker(context: Context, params: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { WidgetRenderer.renderInternal(applicationContext) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "gait-widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }
    }
}
