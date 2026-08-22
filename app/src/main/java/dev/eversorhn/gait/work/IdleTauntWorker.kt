package dev.eversorhn.gait.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.domain.composure.ComposureEngine
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.notification.TwinNotifier
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Runs once a day. Two jobs, in priority order (see docs/notifications.md and
 * docs/composure-system.md):
 *
 * 1. **Gap-predatory check.** If the user has gone quiet for well longer than *their own*
 *    historical rhythm (z-score via [ComposureEngine.isGapPredatory]), the opponent goes
 *    for the throat -- a Predatory line, not an idle jab. This is the "3 days and you're
 *    already negotiating with yourself" behavior from the concept, fired from absence.
 * 2. **Idle taunt.** Otherwise, the sparse randomized ambient jab, 2-4 days apart.
 *
 * Both are suppressed entirely during vacation. Declared weekly rest days are *not* a
 * reason to stay silent (the horde / twin doesn't know or care that it's Sunday), but they
 * are excluded from the gap calculation so a Sun-only rest pattern never reads as a lapse.
 */
class IdleTauntWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "idle_taunt_worker"
        private const val PREFS_NAME = "gait_taunt_prefs"
        private const val KEY_NEXT_DUE = "next_taunt_due_epoch_millis"
        private const val KEY_LAST_GAP_TAUNT_DAY = "last_gap_taunt_epoch_day"
        private val minGapMillis = TimeUnit.DAYS.toMillis(2)
        private val maxGapMillis = TimeUnit.DAYS.toMillis(4)
        private const val MILLIS_PER_DAY = 86_400_000.0
    }

    private val composureEngine = ComposureEngine()

    override suspend fun doWork(): Result {
        val app = applicationContext as GaitApplication
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val profile = app.repository.getTwinProfile() ?: return Result.success()
        if (RestDayPolicy.isOnVacation(profile, now)) return Result.success()
        if (app.repository.isPlannedDayOff(java.time.LocalDate.now().toEpochDay())) return Result.success()

        // --- 0. Division notices: cull in 7 / 1 days, and the cull day itself (once each). ---
        runCatching {
            val zoned = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
            val offset = zoned.offset.totalSeconds * 1000L
            val today = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(now, offset)
            val enrolled = dev.eversorhn.gait.domain.roster.RosterEngine.epochDay(app.repository.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis, offset)
            val founding = enrolled - dev.eversorhn.gait.domain.roster.RosterEngine.PREHISTORY_DAYS
            val since = ((today - founding) % dev.eversorhn.gait.domain.roster.RosterEngine.CULL_EVERY_DAYS).toInt()
            val untilCull = if (since == 0) 0 else dev.eversorhn.gait.domain.roster.RosterEngine.CULL_EVERY_DAYS - since
            val key = "cull_notice_day"
            if (untilCull in listOf(7, 1, 0) && prefs.getLong(key, -1L) != today) {
                val body = when (untilCull) {
                    7 -> "Quarterly cull in 7 days. The bottom ${dev.eversorhn.gait.domain.roster.RosterEngine.CULL_COUNT} assets by Retention Index will be released. Check your position on the board."
                    1 -> "Quarterly cull tomorrow. Positions close at midnight."
                    else -> "Quarterly cull today. The board has been re-ranked; see the notice if you were affected."
                }
                TwinNotifier.postDivisionNotice(applicationContext, "Asset Performance Division", body, TwinNotifier.Kind.NOTICE)
                prefs.edit().putLong(key, today).apply()
            }
        }

        // --- 1. Gap-predatory: has the user gone quiet beyond their own rhythm? ---
        val sessions = app.repository.getSessions() // newest first
        if (sessions.size >= 4) {
            val timestamps = sessions.map { it.startTimeEpochMillis }
            val gaps = timestamps.zipWithNext { newer, older -> (newer - older) / MILLIS_PER_DAY }
            val sinceLast = (now - timestamps.first()) / MILLIS_PER_DAY
            val todayEpochDay = now / MILLIS_PER_DAY.toLong()
            val alreadyFiredToday = prefs.getLong(KEY_LAST_GAP_TAUNT_DAY, -1L) == todayEpochDay

            if (!alreadyFiredToday && composureEngine.isGapPredatory(sinceLast, gaps)) {
                val body = if (profile.isHorde) {
                    HordeSoundCues.captionFor(ComposureState.PREDATORY, profile.hordeIntensity ?: "")
                } else {
                    Personas.byKey(profile.personaKey).predatoryLines.random(Random)
                }
                TwinNotifier.postTwinMessage(applicationContext, profile.twinName, body, TwinNotifier.Kind.GAP)
                app.repository.recordMessage(MessageKind.GAP, body, ComposureState.PREDATORY.name, now)
                prefs.edit().putLong(KEY_LAST_GAP_TAUNT_DAY, todayEpochDay).apply()
                // A predatory ping counts as contact: push the next idle taunt out too.
                prefs.edit().putLong(KEY_NEXT_DUE, now + randomGapMillis()).apply()
                return Result.success()
            }
        }

        // --- 2. Idle taunt, on its randomized schedule ---
        val nextDue = prefs.getLong(KEY_NEXT_DUE, -1L)
        if (nextDue < 0L) {
            // First run ever: schedule the first taunt out, don't fire immediately on install.
            prefs.edit().putLong(KEY_NEXT_DUE, now + randomGapMillis()).apply()
            return Result.success()
        }
        if (now < nextDue) return Result.success()

        val body = if (profile.isHorde) {
            HordeSoundCues.idleCaption()
        } else {
            Personas.byKey(profile.personaKey).idleLines.random(Random)
        }
        TwinNotifier.postTwinMessage(applicationContext, profile.twinName, body, TwinNotifier.Kind.IDLE)
        app.repository.recordMessage(MessageKind.IDLE, body, null, now)
        prefs.edit().putLong(KEY_NEXT_DUE, now + randomGapMillis()).apply()
        return Result.success()
    }

    private fun randomGapMillis(): Long =
        minGapMillis + Random.nextLong(maxGapMillis - minGapMillis)
}
