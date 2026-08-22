package dev.eversorhn.gait.tracking

/**
 * What the live notification needs to know about the opponent, set by TrackViewModel when a
 * session starts (same process; no IPC). Null when no session is being compared.
 */
data class LiveOpponentInfo(
    val name: String,
    val referencePaceSecPerKm: Double?,
    val forecastFinishSeconds: Int?,
    val stake: Int,
) {
    companion object {
        @Volatile var current: LiveOpponentInfo? = null
    }
}
