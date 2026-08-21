package dev.eversorhn.gait

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.eversorhn.gait.data.db.GaitDatabase
import dev.eversorhn.gait.data.repository.GaitRepository
import kotlinx.coroutines.launch
import dev.eversorhn.gait.notification.TwinNotifier
import dev.eversorhn.gait.work.IdleTauntWorker
import java.util.concurrent.TimeUnit

class GaitApplication : Application() {
    val database: GaitDatabase by lazy { GaitDatabase.getInstance(this) }
    val repository: GaitRepository by lazy { GaitRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        TwinNotifier.ensureChannel(this)
        scheduleIdleTaunts()
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
                val today = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(now.toEpochMilli(), offset)
                val enrolled = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(enrolledMillis, offset)
                val empty = dev.eversorhn.gait.domain.ledger.LedgerState(0, 0, emptyList())
                dev.eversorhn.gait.domain.roster.RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, empty, 50, empty)
            }
        }
    }

    private fun scheduleIdleTaunts() {
        val request = PeriodicWorkRequestBuilder<IdleTauntWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            IdleTauntWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
