package dev.eversorhn.gait.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.eversorhn.gait.tracking.LiveFigures

/**
 * What the session feels like through the trouser pocket.
 *
 * No screen, no headphones, no words: a pulse that tightens as the horde closes, a double knock
 * when the lead changes, one tick at every kilometre. It is the same information the card and
 * the voice carry, in the one channel that reaches you while you are running and looking at the
 * road.
 */
class SessionHaptics(private val context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    private var lastPulseAt = 0L
    private var lastKm = 0
    private var lastAhead: Boolean? = null

    fun reset() {
        lastPulseAt = 0L; lastKm = 0; lastAhead = null
    }

    /** Called on the service's ticker beat while a session is recording. */
    fun onTick(f: LiveFigures, nowElapsedMillis: Long) {
        if (!HapticPrefs.isEnabled(context)) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        // A kilometre is one short tick, whatever the mode.
        if (f.km > lastKm && f.km >= 1) {
            lastKm = f.km
            oneShot(TICK_MS, TICK_AMPLITUDE)
            return
        }

        // Lead changes are a double knock — you feel which way it went by what follows.
        val ahead = if (f.isHorde) f.separationMeters?.let { it > 0 } else f.gapSeconds?.let { it > 0 }
        if (ahead != null && lastAhead != null && ahead != lastAhead) {
            lastAhead = ahead
            pattern(if (ahead) GAINED else LOST)
            return
        }
        if (ahead != null && lastAhead == null) lastAhead = ahead

        // The horde has a heartbeat: the closer it is, the less time between beats.
        if (f.isHorde) {
            val sep = f.separationMeters ?: return
            val interval = pulseIntervalMillis(sep)
            if (nowElapsedMillis - lastPulseAt >= interval) {
                lastPulseAt = nowElapsedMillis
                oneShot(PULSE_MS, pulseAmplitude(sep))
            }
        }
    }

    /** Far away it is a slow knock; inside fifty metres it is almost continuous. */
    private fun pulseIntervalMillis(separationMeters: Int): Long = when {
        separationMeters <= 0 -> 2_000L
        separationMeters < 50 -> 4_000L
        separationMeters < 150 -> 9_000L
        separationMeters < 400 -> 20_000L
        else -> 45_000L
    }

    private fun pulseAmplitude(separationMeters: Int): Int = when {
        separationMeters < 50 -> 255
        separationMeters < 150 -> 190
        separationMeters < 400 -> 130
        else -> 90
    }

    private fun oneShot(millis: Long, amplitude: Int) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(millis, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis)
            }
        }
    }

    private fun pattern(timings: LongArray) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }
    }

    private companion object {
        const val TICK_MS = 40L
        const val TICK_AMPLITUDE = 160
        const val PULSE_MS = 55L
        /** Two quick knocks: you took it. */
        val GAINED = longArrayOf(0, 45, 90, 45)
        /** One long, one short: it took it back. */
        val LOST = longArrayOf(0, 160, 90, 45)
    }
}

/** Whether the pocket channel is on. Separate from the voice: some want one and not the other. */
object HapticPrefs {
    private const val PREFS = "gait_haptics"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
