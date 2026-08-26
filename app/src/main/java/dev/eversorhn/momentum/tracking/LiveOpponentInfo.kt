package dev.eversorhn.momentum.tracking

/**
 * What the live notification needs to know about the opponent, set by TrackViewModel when a
 * session starts (same process; no IPC). Null when no session is being compared.
 */
data class LiveOpponentInfo(
    val name: String,
    val referencePaceSecPerKm: Double?,
    val forecastFinishSeconds: Int?,
    val stake: Int,
    val isHorde: Boolean = false,
    /** What the model predicted you would cover — the distance the round is judged over. */
    val forecastDistanceMeters: Double? = null,
    val activityKey: String? = null,
    /** Your rank on the board when the session started, so the card can show movement. */
    val startRank: Int? = null,
) {
    companion object {
        @Volatile var current: LiveOpponentInfo? = null
    }
}
