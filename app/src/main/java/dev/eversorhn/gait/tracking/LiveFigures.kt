package dev.eversorhn.gait.tracking

import dev.eversorhn.gait.domain.activity.Activities

/**
 * The live figures of a running session, computed once and read by everything that reports
 * them: the lock-screen card, the spoken readout, the live screen. One source so the card and
 * the voice can never disagree about where you stand.
 */
data class LiveFigures(
    val km: Int,
    val distanceMeters: Double,
    val movingSeconds: Int,
    val paceSecPerKm: Double?,
    val isHorde: Boolean,
    val opponentName: String,
    val stake: Int,
    val activityKey: String?,
    /** Seconds ahead (+) of the model at your current distance. */
    val gapSeconds: Int?,
    /** Horde: ground between you (+ = they are behind), and how that is moving per minute. */
    val separationMeters: Int?,
    val closingPerMinute: Int?,
    /** The pace that still takes the round over what is left. Null once past the forecast distance. */
    val holdPaceSecPerKm: Double?,
    val remainingMeters: Double?,
    val projectedFinishSeconds: Int?,
    val modelFinishSeconds: Int?,
) {
    fun pace(p: Double): String = Activities.formatPaceOrSpeed(p, activityKey)

    companion object {
        fun of(live: TrackingSnapshot, opp: LiveOpponentInfo?): LiveFigures {
            val ref = opp?.referencePaceSecPerKm
            val mine = live.currentPaceSecPerKm
            val target = opp?.forecastDistanceMeters
            val horde = opp?.isHorde == true

            val gap = if (ref != null && !horde) (ref * live.distanceMeters / 1000.0).toInt() - live.movingSeconds else null
            val separation = if (ref != null && horde) {
                (live.distanceMeters - live.movingSeconds / ref * 1000.0).toInt()
            } else null
            val closing = if (ref != null && horde && mine != null) {
                (((1000.0 / mine) - (1000.0 / ref)) * 60.0).toInt()
            } else null

            var hold: Double? = null
            var remaining: Double? = null
            if (ref != null && target != null && target > live.distanceMeters + 50) {
                remaining = target - live.distanceMeters
                val budget = ref * (target / 1000.0) - live.movingSeconds
                if (budget > 0) hold = budget / (remaining / 1000.0)
            }
            val projected = if (mine != null && target != null) {
                (live.movingSeconds + mine * ((target - live.distanceMeters).coerceAtLeast(0.0) / 1000.0)).toInt()
            } else null

            return LiveFigures(
                km = (live.distanceMeters / 1000.0).toInt(),
                distanceMeters = live.distanceMeters,
                movingSeconds = live.movingSeconds,
                paceSecPerKm = mine,
                isHorde = horde,
                opponentName = opp?.name ?: "the model",
                stake = opp?.stake ?: 1,
                activityKey = opp?.activityKey,
                gapSeconds = gap,
                separationMeters = separation,
                closingPerMinute = closing,
                holdPaceSecPerKm = hold,
                remainingMeters = remaining,
                projectedFinishSeconds = projected,
                modelFinishSeconds = opp?.forecastFinishSeconds,
            )
        }
    }
}
