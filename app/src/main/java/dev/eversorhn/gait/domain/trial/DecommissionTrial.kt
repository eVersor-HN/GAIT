package dev.eversorhn.gait.domain.trial

import dev.eversorhn.gait.data.db.entity.SessionEntity

/**
 * Phase 04/05 of the Asset Twin loop (README, demo/asset-twin-demo.html): once Fidelity
 * crosses [THRESHOLD] the Twin is "substitution eligible" and the only way back is a single
 * duel against its strongest session to date. Pure rules, no Android, so the numbers that
 * decide a win are unit-tested rather than buried in a ViewModel.
 *
 * The same math serves the Zombie Horde (relabelled Proximity/Wave in the UI).
 */
object DecommissionTrial {

    /** Fidelity at or above this makes the opponent eligible for a Trial. */
    const val THRESHOLD = 0.95f

    /** A won Trial resets Fidelity here -- the new generation starts sharper than 0.5, not from zero. */
    const val RESET_FIDELITY = 0.61f

    /** Shorter than this isn't a duel, it's a sprint -- the target pace must be held over real distance. */
    const val MIN_DUEL_DISTANCE_METERS = 1000.0

    fun isEligible(fidelity: Float): Boolean = fidelity >= THRESHOLD

    /** Percent of the way to the threshold, for the eligibility meter (0..100, clamped). */
    fun progressPercent(fidelity: Float): Int =
        ((fidelity / THRESHOLD) * 100).toInt().coerceIn(0, 100)

    /**
     * The Twin's "strongest session": the fastest pace the user ever actually held over a
     * real distance -- that's all the Twin is, a model of the user. Rest-day sessions count;
     * a good run is a good run regardless of the calendar.
     * Returns null when there's nothing to duel against yet.
     */
    fun targetPaceSecPerKm(history: List<SessionEntity>): Double? =
        history
            .filter { it.distanceMeters >= MIN_DUEL_DISTANCE_METERS && it.avgPaceSecPerKm > 0.0 }
            .minOfOrNull { it.avgPaceSecPerKm }

    enum class Verdict { WON, LOST, TOO_SHORT }

    fun judge(distanceMeters: Double, avgPaceSecPerKm: Double, targetPaceSecPerKm: Double): Verdict = when {
        distanceMeters < MIN_DUEL_DISTANCE_METERS -> Verdict.TOO_SHORT
        avgPaceSecPerKm < targetPaceSecPerKm -> Verdict.WON
        else -> Verdict.LOST
    }
}
