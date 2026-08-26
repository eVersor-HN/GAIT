package dev.eversorhn.momentum.domain.live

/** Where you stand against the reference pace right now. */
enum class LiveZone { AHEAD, BEHIND, LEVEL }

/**
 * Pure state machine for mid-session callouts (the text channel of docs/live-audio.md):
 * the opponent speaks at kilometre marks and on lead changes, never on a timer, with a hard
 * cooldown and a per-session cap so it never turns into chatter. Lines come from the caller
 * (persona banks); this only decides *when* and in *which zone*.
 */
class LiveCommentary(
    private val cooldownSeconds: Int = 45,
    private val maxLines: Int = 12,
    /** Gap smaller than this (sec/km) counts as level — no lead-change spam around zero. */
    private val levelBandSecPerKm: Double = 4.0,
) {
    private var lastKm = 0
    /** Last AHEAD/BEHIND side seen (LEVEL never overwrites it). */
    private var lastSide: LiveZone? = null
    private var lastSpokenAt = -100_000
    private var spoken = 0

    sealed interface Trigger {
        data class KmMark(val km: Int, val zone: LiveZone, val gapSecPerKm: Double) : Trigger
        data class LeadChange(val zone: LiveZone, val gapSecPerKm: Double) : Trigger
    }

    fun zoneOf(gapSecPerKm: Double): LiveZone = when {
        gapSecPerKm > levelBandSecPerKm -> LiveZone.AHEAD
        gapSecPerKm < -levelBandSecPerKm -> LiveZone.BEHIND
        else -> LiveZone.LEVEL
    }

    /**
     * Feed every tracking tick. [gapSecPerKm] = reference pace − your pace (positive = you're
     * faster); null while no pace is known. Returns a trigger when the opponent should say
     * something, else null.
     */
    fun onTick(movingSeconds: Int, distanceMeters: Double, gapSecPerKm: Double?): Trigger? {
        if (spoken >= maxLines) return null
        val canSpeak = movingSeconds - lastSpokenAt >= cooldownSeconds
        val zone = gapSecPerKm?.let { zoneOf(it) }

        // Kilometre marks: the rhythm of the session. Always wanted, subject to cooldown.
        val km = (distanceMeters / 1000.0).toInt()
        if (km > lastKm) {
            lastKm = km
            if (canSpeak && zone != null) {
                speak(movingSeconds)
                if (zone != LiveZone.LEVEL) lastSide = zone
                return Trigger.KmMark(km, zone, gapSecPerKm!!)
            }
        }

        // Lead changes: the side flipped between AHEAD and BEHIND. LEVEL is a buffer zone.
        if (zone == LiveZone.AHEAD || zone == LiveZone.BEHIND) {
            when {
                lastSide == null -> if (movingSeconds >= 30) lastSide = zone // establish silently
                lastSide != zone && canSpeak -> {
                    lastSide = zone
                    speak(movingSeconds)
                    return Trigger.LeadChange(zone, gapSecPerKm!!)
                }
            }
        }
        return null
    }

    private fun speak(at: Int) { lastSpokenAt = at; spoken++ }
}
