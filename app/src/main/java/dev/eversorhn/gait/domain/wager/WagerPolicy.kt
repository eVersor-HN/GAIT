package dev.eversorhn.gait.domain.wager

/**
 * The opponent puts points on its own forecast. It only stakes when it's actually confident
 * — a stake is a provocation, and a provocation from a guess is just noise. The user can
 * *call* the stake, which doubles it: the "I can't let that sit" button.
 *
 * One stake per local day, made when the Forecast is first viewed; consumed by the next
 * scored session that day; a day with a stake but no session is simply a day the opponent
 * was right to doubt you (no points move — absence is punished elsewhere, by Composure).
 */
object WagerPolicy {

    const val STAKE = 2
    const val CALLED_STAKE = 4

    /**
     * Arcade rule: the model raises what it puts up as you pull ahead — 2 normally, 3 once you
     * lead by 4+, 4 at 8+. Calling always doubles. Never more than that; pressure, not punishment.
     */
    fun stakeFor(userLead: Int): Int = when {
        userLead >= 8 -> 4
        userLead >= 4 -> 3
        else -> STAKE
    }
    fun calledStakeFor(baseStake: Int): Int = baseStake * 2

    /** Minimum forecast confidence (%) and history depth before the opponent puts anything up. */
    const val MIN_CONFIDENCE = 55
    const val MIN_SESSIONS = 3

    fun shouldStake(confidencePercent: Int, basedOnSessions: Int, isRestPeriod: Boolean): Boolean =
        !isRestPeriod && confidencePercent >= MIN_CONFIDENCE && basedOnSessions >= MIN_SESSIONS

    /** Points a round is worth given the profile's open wager state for that day. */
    fun roundStake(hasOpenStake: Boolean, called: Boolean, openStake: Int = STAKE): Int = when {
        !hasOpenStake -> 1
        called -> calledStakeFor(openStake)
        else -> openStake
    }

    fun epochDay(epochMillis: Long, zoneOffsetMillis: Long): Long =
        Math.floorDiv(epochMillis + zoneOffsetMillis, 86_400_000L)
}
