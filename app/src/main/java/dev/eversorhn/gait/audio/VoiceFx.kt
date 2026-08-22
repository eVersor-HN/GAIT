package dev.eversorhn.gait.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The processing chain from docs/voice-design.md, reduced to what's cheap on a phone and
 * effective on a TTS voice: high-pass 80 Hz → gentle cut ~270 Hz → presence lift ~3.2 kHz →
 * air lift ~10 kHz → micro-doubling (~11 ms, low gain, slightly detuned by a 1-sample wobble)
 * → soft saturation/limiter. Input/output: 16-bit mono PCM. Pure Kotlin, no allocations in
 * the loop beyond the output buffer.
 */
object VoiceFx {

    /** One biquad section, Direct Form 1. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private fun highPass(fs: Double, f0: Double, q: Double): Biquad {
        val w = 2 * PI * f0 / fs; val alpha = sin(w) / (2 * q); val c = cos(w)
        val a0 = 1 + alpha
        return Biquad(((1 + c) / 2) / a0, (-(1 + c)) / a0, ((1 + c) / 2) / a0, (-2 * c) / a0, (1 - alpha) / a0)
    }

    private fun peaking(fs: Double, f0: Double, q: Double, gainDb: Double): Biquad {
        val a = Math.pow(10.0, gainDb / 40); val w = 2 * PI * f0 / fs
        val alpha = sin(w) / (2 * q); val c = cos(w)
        val a0 = 1 + alpha / a
        return Biquad((1 + alpha * a) / a0, (-2 * c) / a0, (1 - alpha * a) / a0, (-2 * c) / a0, (1 - alpha / a) / a0)
    }

    private fun highShelf(fs: Double, f0: Double, gainDb: Double): Biquad {
        val a = Math.pow(10.0, gainDb / 40); val w = 2 * PI * f0 / fs
        val c = cos(w); val s = sin(w); val alpha = s / 2 * sqrt(2.0)
        val ap1 = a + 1; val am1 = a - 1; val sq = 2 * sqrt(a) * alpha
        val a0 = ap1 - am1 * c + sq
        return Biquad(
            (a * (ap1 + am1 * c + sq)) / a0,
            (-2 * a * (am1 + ap1 * c)) / a0,
            (a * (ap1 + am1 * c - sq)) / a0,
            (2 * (am1 - ap1 * c)) / a0,
            (ap1 - am1 * c - sq) / a0,
        )
    }

    /** Process 16-bit mono PCM in place-ish; returns a new array of the same length. */
    fun process(pcm: ShortArray, sampleRate: Int): ShortArray {
        val fs = sampleRate.toDouble()
        val chain = listOf(
            highPass(fs, 80.0, 0.707),
            peaking(fs, 270.0, 1.0, -2.5),
            peaking(fs, 3200.0, 1.0, 3.0),
            highShelf(fs, 9500.0, 3.5),
        )
        val delaySamples = (0.011 * fs).toInt().coerceAtLeast(1) // ~11 ms micro-double
        val out = ShortArray(pcm.size)
        val wet = 0.16
        var wobblePhase = 0.0
        val wobbleStep = 2 * PI * 0.7 / fs // 0.7 Hz drift → subtle detune of the double
        for (i in pcm.indices) {
            var x = pcm[i] / 32768.0
            for (f in chain) x = f.process(x)
            // Micro-doubling: read the *filtered-input* delayed copy from the source (cheap, pre-filter),
            // wobbled by ±1 sample for a slightly synthetic shimmer.
            wobblePhase += wobbleStep
            val wob = (sin(wobblePhase) * 1.5).toInt()
            val j = i - delaySamples + wob
            if (j >= 0) x += (pcm[j] / 32768.0) * wet
            // Soft saturation + limiter: tanh knee, then clamp.
            x = tanh(x * 1.25) * 0.92
            out[i] = (x * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Parse a RIFF/WAVE file (as TextToSpeech.synthesizeToFile writes): returns (sampleRate, mono 16-bit samples), or null. */
    fun readWav(bytes: ByteArray): Pair<Int, ShortArray>? {
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var pos = 12
        var sampleRate = 0; var channels = 1; var bits = 16
        var data: ShortArray? = null
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = le32(bytes, pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    if (bits != 16) return null
                    val n = (len / 2 / channels)
                    val s = ShortArray(n)
                    for (i in 0 until n) {
                        val o = body + i * 2 * channels
                        if (o + 1 >= bytes.size) break
                        s[i] = ((bytes[o].toInt() and 0xff) or (bytes[o + 1].toInt() shl 8)).toShort()
                    }
                    data = s
                }
            }
            pos = body + len + (len and 1)
        }
        val d = data ?: return null
        if (sampleRate <= 0) return null
        return sampleRate to d
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
}
