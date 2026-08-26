package dev.eversorhn.momentum.audio

import android.content.Context
import dev.eversorhn.momentum.tracking.LiveFigures

/**
 * The spoken side of a session. It lives in the tracking service, not in a screen, so it keeps
 * talking with the display off and the app swiped away — which is the whole point of it.
 *
 * It is not a character and never comments: it reads the figures you would otherwise have to
 * unlock the phone for. Cadence: an opening orientation, every kilometre, every lead change,
 * a status line every two minutes, an immediate warning when the horde comes inside 50 m, and
 * a closing summary when the session ends.
 */
class SessionVoice(private val context: Context) {

    private var commentator: Commentator? = null
    private var lastSpokenAt = -1000
    private var spokenLines = 0
    private var lastKm = 0
    private var lastStatusAt = 0
    private var lastAhead: Boolean? = null
    private var warnedClose = false
    private var opened = false

    private companion object {
        const val COOLDOWN_SECONDS = 40
        const val MAX_LINES = 24
        const val STATUS_EVERY_SECONDS = 120
        const val HORDE_WARN_METERS = 50
    }

    fun reset() {
        lastSpokenAt = -1000; spokenLines = 0; lastKm = 0; lastStatusAt = 0
        lastAhead = null; warnedClose = false; opened = false
        commentator?.stop()
    }

    /** Called on every ticker beat while a session is recording. */
    fun onTick(f: LiveFigures) {
        if (!VoicePrefs.isEnabled(context)) return
        val t = f.movingSeconds

        if (!opened) {
            opened = true
            say(openingLine(f), t, force = true)
            return
        }

        // The horde inside striking distance jumps the queue — that is the one urgent fact.
        if (f.isHorde) {
            val sep = f.separationMeters
            if (sep != null && sep < HORDE_WARN_METERS && !warnedClose) {
                warnedClose = true
                say("Horde at $sep metres.", t, force = true)
                return
            }
            if (sep != null && sep > HORDE_WARN_METERS * 2) warnedClose = false
        }

        if (f.km > lastKm && f.km >= 1) {
            lastKm = f.km
            say(kilometreLine(f), t)
            return
        }

        val ahead = if (f.isHorde) f.separationMeters?.let { it > 0 } else f.gapSeconds?.let { it > 0 }
        if (ahead != null && lastAhead != null && ahead != lastAhead && t > 60) {
            lastAhead = ahead
            say(leadChangeLine(f, ahead), t)
            return
        }
        if (ahead != null && lastAhead == null) lastAhead = ahead

        if (t - lastStatusAt >= STATUS_EVERY_SECONDS && t > 90) {
            statusLine(f)?.let { say(it, t) }
            lastStatusAt = t
        }
    }

    /** Spoken once when the session stops, so the result lands without unlocking anything. */
    fun onFinish(f: LiveFigures) {
        if (!VoicePrefs.isEnabled(context)) return
        val parts = ArrayList<String>()
        parts += "Session ended. %.2f kilometres in %s.".format(f.distanceMeters / 1000.0, spokenDuration(f.movingSeconds))
        f.paceSecPerKm?.let { parts += "Average ${f.pace(it)}." }
        if (f.isHorde) {
            f.separationMeters?.let { parts += if (it >= 0) "Horde finished $it metres behind." else "The horde passed you." }
        } else {
            f.gapSeconds?.let { parts += if (it >= 0) "${spokenSeconds(it)} ahead of the forecast." else "${spokenSeconds(-it)} behind the forecast." }
        }
        say(parts.joinToString(" "), f.movingSeconds, force = true)
    }

    fun stop() = commentator?.stop()
    fun shutdown() { commentator?.shutdown(); commentator = null }

    // --- What it says. Figures, in the order they matter. ---

    private fun openingLine(f: LiveFigures): String = buildString {
        append("Recording.")
        if (f.stake > 1) append(" ${f.stake} points riding.")
        if (f.isHorde) {
            f.separationMeters?.let { append(" Horde at $it metres.") }
        } else {
            f.holdPaceSecPerKm?.let { append(" Hold ${f.pace(it)} to take the round.") }
        }
    }

    private fun kilometreLine(f: LiveFigures): String = buildString {
        append("Kilometre ${f.km}.")
        if (f.isHorde) {
            f.separationMeters?.let { append(if (it >= 0) " Horde at $it metres." else " Horde ahead by ${-it} metres.") }
            f.closingPerMinute?.let { if (it < -5) append(" Closing ${-it} a minute.") else if (it > 5) append(" Gaining $it a minute.") }
        } else {
            f.gapSeconds?.let {
                append(if (kotlin.math.abs(it) < 3) " Level." else if (it > 0) " ${spokenSeconds(it)} up." else " ${spokenSeconds(-it)} down.")
            }
            f.holdPaceSecPerKm?.let { append(" Hold ${f.pace(it)}.") }
        }
    }

    private fun leadChangeLine(f: LiveFigures, nowAhead: Boolean): String = when {
        f.isHorde && nowAhead -> "You are pulling clear."
        f.isHorde -> "The horde is past you."
        nowAhead -> "You are ahead. " + (f.holdPaceSecPerKm?.let { "Hold ${f.pace(it)}." } ?: "")
        else -> "You are behind. " + (f.holdPaceSecPerKm?.let { "${f.pace(it)} takes it back." } ?: "")
    }.trim()

    private fun statusLine(f: LiveFigures): String? {
        if (f.isHorde) {
            val sep = f.separationMeters ?: return null
            val rate = f.closingPerMinute ?: 0
            return when {
                rate < -15 -> "Horde at $sep metres, closing ${-rate} a minute."
                rate > 15 -> "Horde at $sep metres, falling back $rate a minute."
                else -> "Horde at $sep metres."
            }
        }
        val hold = f.holdPaceSecPerKm
        val remaining = f.remainingMeters
        if (hold != null && remaining != null) {
            return "%.1f kilometres left. Hold %s.".format(remaining / 1000.0, f.pace(hold))
        }
        val projected = f.projectedFinishSeconds
        val model = f.modelFinishSeconds
        if (projected != null && model != null) {
            return "Projected finish ${spokenDuration(projected)} against ${spokenDuration(model)}."
        }
        return f.gapSeconds?.let { if (it >= 0) "${spokenSeconds(it)} up." else "${spokenSeconds(-it)} down." }
    }

    private fun say(text: String, atSeconds: Int, force: Boolean = false) {
        if (!force && (atSeconds - lastSpokenAt < COOLDOWN_SECONDS || spokenLines >= MAX_LINES)) return
        val horde = lastVoiceHorde
        val c = commentator ?: Commentator(
            context,
            if (horde) VoiceFx.Voice.HORDE else VoiceFx.Voice.DIVISION,
        ).also { commentator = it }
        c.say(text)
        lastSpokenAt = atSeconds
        spokenLines++
    }

    /** Set once per session so the engine is built with the right voice. */
    var lastVoiceHorde: Boolean = false

    private fun spokenSeconds(s: Int): String = if (s < 60) "$s seconds" else "${s / 60} minutes ${s % 60} seconds"
    private fun spokenDuration(s: Int): String =
        if (s < 3600) "${s / 60} ${if (s / 60 == 1) "minute" else "minutes"} ${s % 60}"
        else "${s / 3600} hours ${(s % 3600) / 60} minutes"
}
