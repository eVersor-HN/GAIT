package dev.eversorhn.gait.domain.horde

import dev.eversorhn.gait.domain.composure.ComposureState

/**
 * Selectable at setup instead of a Twin intensity toggle -- see docs/zombie-mode.md. Only
 * the Swarming (Predatory-equivalent) bank varies by intensity; Tracking/Fallen Back stay
 * shared, since those are baseline states rather than the "how brutal" dial.
 */
object HordeIntensity {
    const val CALM = "horde_calm"
    const val STANDARD = "horde_standard"
    const val RELENTLESS = "horde_relentless"

    val all = listOf(CALM, STANDARD, RELENTLESS)

    fun label(key: String): String = when (key) {
        CALM -> "Calm"
        RELENTLESS -> "Relentless"
        else -> "Standard"
    }
}

/**
 * Bracketed, non-verbal captions -- never comprehensible words, per the brief. This is also
 * the primary representation, not a stand-in for missing audio: captions work without sound
 * assets and are accessible by default. See docs/zombie-mode.md "Sound cue catalog".
 */
object HordeSoundCues {

    private val ambient = listOf(
        "[distant groan]",
        "[dragging footsteps, far off]",
        "[low murmur, many voices]",
        "[wet, ragged breathing, far off]",
    )

    private val fallenBack = listOf(
        "[groan fading into the distance]",
        "[shuffling footsteps retreat]",
        "[silence, for once]",
    )

    private val tracking = listOf(
        "[shuffling, several sets of footsteps]",
        "[wet, ragged breathing, steady]",
        "[dragging footsteps, keeping pace]",
    )

    private val swarmingCalm = listOf(
        "[breathing quickens, somewhere behind you]",
        "[footsteps, closer than before]",
    )

    private val swarmingStandard = listOf(
        "[snarling, just behind you]",
        "[fingers scraping pavement]",
        "[breathing, right at your ear]",
    )

    private val swarmingRelentless = listOf(
        "[a scream cuts through the group]",
        "[SCREECHING, close, closing]",
        "[ASSET CONNECTION UNSTABLE -- static]",
    )

    fun forecastCaption(basedOnSessions: Int): String =
        if (basedOnSessions <= 0) "[no signal yet]" else ambient.random()

    /** The horde "stakes" by closing in: a non-verbal claim on today's pace. */
    fun stakeCaption(paceLabel: String, points: Int): String =
        "[they've matched your $paceLabel. $points points closer if you don't break it]"

    fun callCaption(): String = listOf(
        "[the murmur rises -- they heard that]",
        "[a dozen heads turn your way]",
    ).random()

    fun liveAhead(gap: String): String = listOf(
        "[$gap of open road. the groaning fades]",
        "[footsteps falling back -- $gap]",
    ).random()

    fun liveBehind(gap: String): String = listOf(
        "[$gap closer. breathing at your shoulder]",
        "[they're $gap nearer than they should be]",
    ).random()

    fun liveLevel(km: Int): String = listOf(
        "[km $km. the same distance. always the same distance]",
        "[km $km. dragging footsteps, keeping pace]",
    ).random()

    /** A won Outrun Trial: the wave breaks and a new one forms -- the Horde's generational handoff. */
    fun handoffCaption(newWave: Int): String =
        listOf(
            "[the groaning thins out... then a new chorus, further back -- wave $newWave]",
            "[silence. then, far off, something starts walking again -- wave $newWave]",
        ).random()

    /** A lost Outrun Trial: they stay exactly where they were. */
    fun duelLostCaption(): String =
        listOf(
            "[breathing, steady, right where it was]",
            "[footsteps don't fall back. not even a little]",
        ).random()

    /** The horde's idle-taunt equivalent (docs/notifications.md) -- always ambient, never a spike. */
    fun idleCaption(): String = ambient.random()

    /**
     * Unlike a Twin (silent while Watchful), the horde always has *something* audible --
     * that constant ambient presence is the point of choosing this opponent.
     */
    fun captionFor(state: ComposureState, intensityKey: String): String = when (state) {
        ComposureState.COWED -> fallenBack.random()
        ComposureState.WATCHFUL -> tracking.random()
        ComposureState.PREDATORY -> swarmingFor(intensityKey).random()
    }

    private fun swarmingFor(intensityKey: String): List<String> = when (intensityKey) {
        HordeIntensity.CALM -> swarmingCalm
        HordeIntensity.RELENTLESS -> swarmingRelentless
        else -> swarmingStandard
    }
}
