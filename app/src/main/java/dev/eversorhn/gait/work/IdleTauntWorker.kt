package dev.eversorhn.gait.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.notification.TwinNotifier
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The "random jabs every few days" ask from the chat, done properly instead of a fixed
 * daily timer: this worker runs once a day (WorkManager's periodic floor is 15 minutes,
 * so daily is already coarse), but only actually posts when a randomized due-time — reset
 * to 2–4 days out after every taunt — has passed. See docs/notifications.md.
 */
class IdleTauntWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "idle_taunt_worker"
        private const val PREFS_NAME = "gait_taunt_prefs"
        private const val KEY_NEXT_DUE = "next_taunt_due_epoch_millis"
        private val minGapMillis = TimeUnit.DAYS.toMillis(2)
        private val maxGapMillis = TimeUnit.DAYS.toMillis(4)
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as GaitApplication
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val nextDue = prefs.getLong(KEY_NEXT_DUE, -1L)

        if (nextDue < 0L) {
            // First run ever: schedule the first taunt out, don't fire immediately on install.
            prefs.edit().putLong(KEY_NEXT_DUE, now + randomGapMillis()).apply()
            return Result.success()
        }

        if (now < nextDue) return Result.success()

        val profile = app.repository.getTwinProfile()
        if (profile != null) {
            val body = if (profile.opponentType == OpponentType.HORDE) {
                HordeSoundCues.idleCaption()
            } else {
                Personas.byKey(profile.personaKey).idleLines.random(Random)
            }
            TwinNotifier.postTwinMessage(
                context = applicationContext,
                twinName = profile.twinName,
                body = body,
            )
        }

        prefs.edit().putLong(KEY_NEXT_DUE, now + randomGapMillis()).apply()
        return Result.success()
    }

    private fun randomGapMillis(): Long =
        minGapMillis + Random.nextLong(maxGapMillis - minGapMillis)
}
