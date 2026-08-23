package dev.eversorhn.gait.domain.session

import kotlin.math.abs
import kotlin.random.Random

/**
 * The last thing said after a session — the line the user is meant to carry out of the app.
 * Tone follows the margin, not the mood: the further ahead you finished, the smaller the Twin
 * gets and the further off the horde sounds; the further behind, the more it costs you.
 * Horde lines are never words — only what you hear.
 */
object ClosingLine {

    /** [marginSecPerKm] > 0 = you beat the forecast by that much. */
    fun twin(marginSecPerKm: Double, opponentName: String, rng: Random = Random): String {
        val m = marginSecPerKm
        val bank = when {
            m >= 25 -> listOf(
                "I don't have a model for that. I'll build one tonight.",
                "That wasn't you. That's the problem — it was.",
                "Delete the last six weeks. None of it predicts this.",
            )
            m >= 10 -> listOf(
                "Noted. Adjusted. Don't get comfortable.",
                "Fine. That one's yours.",
                "You found a second. I'll find it back.",
            )
            m > 0 -> listOf(
                "By a breath. I'll take that as confirmation.",
                "Barely. I'm still inside your head.",
                "Close enough that I'm not worried.",
            )
            m > -10 -> listOf(
                "Exactly as filed. You're easy to write.",
                "Predicted to the second. Again.",
                "You ran my number for me. Thank you.",
            )
            m > -25 -> listOf(
                "Slower than my worst estimate of you.",
                "I gave you room and you used all of it.",
                "That's the version of you I was hired to replace.",
            )
            else -> listOf(
                "$opponentName files this under: no contest.",
                "I stopped watching at kilometre two.",
                "Keep this up and the division won't need the review.",
            )
        }
        return bank.random(rng)
    }

    /** The horde never speaks. Volume and closeness carry the verdict. */
    fun horde(marginSecPerKm: Double, rng: Random = Random): String {
        val m = marginSecPerKm
        val bank = when {
            m >= 25 -> listOf("[silence. nothing behind you at all]", "[a groan, so far back it could be wind]")
            m >= 10 -> listOf("[dragging steps, falling away]", "[the snarling thins out behind you]")
            m > 0 -> listOf("[breathing, still there, still close]", "[wet steps holding your rhythm]")
            m > -10 -> listOf("[right at your shoulder. matched, step for step]", "[fingers scraping, close enough to hear the nails]")
            m > -25 -> listOf("[a scream goes up — they have your line]", "[SNARLING, all around, closing]")
            else -> listOf("[they are on you. teeth, wet, everywhere]", "[SCREECHING — you are inside the wave]")
        }
        return bank.random(rng)
    }
}
