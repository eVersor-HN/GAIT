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

    /** Minimum forecast confidence (%) and history depth before the opponent puts anything up. */
    const val MIN_CONFIDENCE = 55
    const val MIN_SESSIONS = 3

    fun shouldStake(confidencePercent: Int, basedOnSessions: Int, isRestPeriod: Boolean): Boolean =
        !isRestPeriod && confidencePercent >= MIN_CONFIDENCE && basedOnSessions >= MIN_SESSIONS

    /** Points a round is worth given the profile's open wager state for that day. */
    fun roundStake(hasOpenStake: Boolean, called: Boolean): Int = when {
        !hasOpenStake -> 1
        called -> CALLED_STAKE
        else -> STAKE
    }

    fun epochDay(epochMillis: Long, zoneOffsetMillis: Long): Long =
        Math.floorDiv(epochMillis + zoneOffsetMillis, 86_400_000L)
}
