package dev.eversorhn.gait

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.eversorhn.gait.data.db.GaitDatabase
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.notification.TwinNotifier
import dev.eversorhn.gait.work.IdleTauntWorker
import java.util.concurrent.TimeUnit

class GaitApplication : Application() {
    val database: GaitDatabase by lazy { GaitDatabase.getInstance(this) }
    val repository: GaitRepository by lazy { GaitRepository(database) }

    override fun onCreate() {
        super.onCreate()
        TwinNotifier.ensureChannel(this)
        scheduleIdleTaunts()
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
