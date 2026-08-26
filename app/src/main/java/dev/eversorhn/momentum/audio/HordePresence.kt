package dev.eversorhn.momentum.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * The horde, heard rather than described.
 *
 * A low growl generated on the spot — no sample, no words — placed behind you and to one side,
 * louder and closer together the less ground is left. Direction comes from the two things ears
 * actually use: a level difference between them and a fraction of a millisecond of delay. The
 * sound is deliberately dark; what little brightness a source has is what makes it read as being
 * in front, so taking it away puts the horde at your back.
 */
class HordePresence(private val context: Context) {

    private val sampleRate = 22_050
    private var track: AudioTrack? = null
    private var lastPlayedAt = 0L
    private var phase = 0.0

    fun reset() {
        lastPlayedAt = 0L
    }

    /**
     * @param separationMeters ground between you; negative once they are past you
     * @param nowElapsedMillis the session clock, so the cadence is independent of wall time
     */
    fun onTick(separationMeters: Int?, nowElapsedMillis: Long) {
        if (!SoundPrefs.isEnabled(context)) return
        val sep = separationMeters ?: return
        val interval = intervalMillis(sep)
        if (nowElapsedMillis - lastPlayedAt < interval) return
        lastPlayedAt = nowElapsedMillis
        runCatching { play(sep, nowElapsedMillis) }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    private fun intervalMillis(sep: Int): Long = when {
        sep <= 0 -> 5_000L
        sep < 50 -> 8_000L
        sep < 150 -> 16_000L
        sep < 400 -> 34_000L
        else -> 70_000L
    }

    /** 0 = they are on you, 1 = as far as it matters. */
    private fun closeness(sep: Int): Double = (1.0 - (sep.coerceAtLeast(0) / 600.0)).coerceIn(0.05, 1.0)

    private fun play(sep: Int, nowElapsedMillis: Long) {
        val close = closeness(sep)
        val durationMs = 900 + (close * 500).toInt()
        val n = sampleRate * durationMs / 1000

        // Where they are, in the horizontal plane behind you: a slow drift so it never sits still.
        val angle = sin(nowElapsedMillis / 37_000.0) // −1 = hard left, +1 = hard right
        val leftGain = (1.0 - angle * 0.45).coerceIn(0.2, 1.0)
        val rightGain = (1.0 + angle * 0.45).coerceIn(0.2, 1.0)
        // Up to ~0.6 ms between the ears — the delay the head itself would cause.
        val itdSamples = (abs(angle) * 0.0006 * sampleRate).toInt()

        val mono = DoubleArray(n)
        var noise = 0.0
        var seed = 0x9E3779B97F4A7C15uL.toLong() xor nowElapsedMillis
        for (i in 0 until n) {
            // A growl is a sub tone plus filtered noise, both wobbling.
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val white = ((seed ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
            noise = noise * 0.93 + white * 0.07          // heavily low-passed: no air, no direction "in front"
            phase += 2 * PI * (44.0 + 7.0 * sin(i / (sampleRate * 0.9))) / sampleRate
            val sub = sin(phase)
            val t = i.toDouble() / n
            // Slow swell in, faster fall away.
            val env = exp(-3.2 * t) * (1.0 - exp(-14.0 * t))
            mono[i] = (sub * 0.55 + noise * 2.6) * env
        }

        val out = ShortArray(n * 2)
        val level = (0.12 + close * 0.55)
        for (i in 0 until n) {
            val l = mono[(i - if (angle > 0) itdSamples else 0).coerceAtLeast(0)]
            val r = mono[(i - if (angle < 0) itdSamples else 0).coerceAtLeast(0)]
            out[i * 2] = ((l * leftGain * level).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
            out[i * 2 + 1] = ((r * rightGain * level).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }

        release()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(out.size * 2)
            .build()
        track = t
        t.write(out, 0, out.size)
        t.play()
    }

    /** Unused for now; kept so the class can duck music later without changing its callers. */
    private fun audioManager(): AudioManager? = context.getSystemService(AudioManager::class.java)
}

/**
 * Ambient sound during a session, separate from the spoken readout: some want the numbers read
 * out and no atmosphere, some want the opposite.
 */
object SoundPrefs {
    private const val PREFS = "momentum_sound"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
