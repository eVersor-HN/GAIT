package dev.eversorhn.momentum

import android.app.Application
import dev.eversorhn.momentum.data.db.MomentumDatabase
import dev.eversorhn.momentum.data.repository.MomentumRepository
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.eversorhn.momentum.notification.TwinNotifier
import dev.eversorhn.momentum.work.DivisionReportWorker
import dev.eversorhn.momentum.work.OpponentSessionWorker
import java.util.concurrent.TimeUnit

class MomentumApplication : Application() {
    val database: MomentumDatabase by lazy { MomentumDatabase.getInstance(this) }
    val repository: MomentumRepository by lazy { MomentumRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        TwinNotifier.ensureChannel(this)
        scheduleDivisionReport()
        scheduleOpponentSessions()
        preloadRoster()
    }

    /**
     * The roster simulation is a one-off per day (~1,300 slots × ~520 days) and the board is
     * the first screen — so start it the moment the process exists, off the main thread. By the
     * time the splash has navigated, the cache is usually warm.
     */
    private fun preloadRoster() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            runCatching {
                val enrolledMillis = repository.earliestEnrolmentEpochMillis() ?: return@launch
                val now = java.time.Instant.now()
                val zoned = now.atZone(java.time.ZoneId.systemDefault())
                val offset = zoned.offset.totalSeconds * 1000L
                val today = dev.eversorhn.momentum.domain.roster.RosterEngine.epochDay(now.toEpochMilli(), offset)
                val enrolled = dev.eversorhn.momentum.domain.roster.RosterEngine.epochDay(enrolledMillis, offset)
                val empty = dev.eversorhn.momentum.domain.ledger.LedgerState(0, 0, emptyList())
                dev.eversorhn.momentum.domain.roster.RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, empty, 50, empty)
            }
        }
    }


    /**
     * The daily close: what moved while you weren't training. Once every 24 h with a flex
     * window, so it neither wakes the device nor lands at the same minute every day.
     */
    private fun scheduleDivisionReport() {
        val request = PeriodicWorkRequestBuilder<DivisionReportWorker>(1, TimeUnit.DAYS, 4, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DivisionReportWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * The opponent's own sessions, shared live. A quarter-hour beat is enough to notice one
     * starting; while it runs the worker re-enqueues itself every few minutes.
     */
    private fun scheduleOpponentSessions() {
        val request = PeriodicWorkRequestBuilder<OpponentSessionWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OpponentSessionWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
