package dev.eversorhn.gait.domain.persona

/**
 * A selectable Twin voice. All 17 from docs/twin-personas.md ship: five here, twelve in
 * PersonaRoster.kt. "The Doppelgänger" is the AI clone of the user -- built from their data,
 * speaking as if it were them, trying to be the better copy.
 *
 * The line banks are a small curated start, not the full variation system from
 * docs/composure-system.md -- real production content should grow these substantially
 * and eventually move to grounded generation.
 */
data class Persona(
    val key: String,
    val label: String,
    val defaultName: String,
    val forecastLine: (basedOnSessions: Int, paceLabel: String, finishLabel: String) -> String,
    val cowedLines: List<String>,
    /**
     * Neutral, observational -- used while Composure is Watchful (incl. the first few
     * sessions before there's enough history to judge), so the Twin is never *silent*.
     */
    val watchfulLines: List<String>,
    val predatoryLines: List<String>,
    /** Ambient, not tied to any specific session — see docs/notifications.md "Idle taunts". */
    val idleLines: List<String>,
    /**
     * Phase 05, Generational Handoff: spoken once after a *won* Decommission Trial, quoting the
     * user's own data back at them. [timesBeaten] = how often the user beat the forecast pace
     * in the sessions this generation saw; [newGeneration] = the one spinning up now.
     */
    val handoffLine: (timesBeaten: Int, newGeneration: Int) -> String,
    /** Spoken after a *lost* Decommission Trial -- the Twin keeps its primary-asset status. */
    val duelLostLines: List<String>,
    /** The stake it puts on today's forecast (domain/wager): [pace] = forecast pace label, [points] staked. */
    val stakeLine: (pace: String, points: Int) -> String,
    /** Its reaction when the user calls the stake and doubles it. */
    val callLines: List<String>,
    /** Mid-session, user ahead of its number: [gap] = "0:12/km". Sour, short. */
    val liveAheadLines: List<(gap: String) -> String>,
    /** Mid-session, user behind its number: [gap] = "0:12/km". Gloating, short. */
    val liveBehindLines: List<(gap: String) -> String>,
    /** Mid-session kilometre mark while level: [km]. */
    val liveLevelLines: List<(km: Int) -> String>,
)

/** Grammar helpers for the forecast templates: "1 session" vs "12 sessions", "once" vs "12 times". */
private fun sessions(n: Int, adjective: String = ""): String =
    if (n == 1) "1 ${adjective}session" else "$n ${adjective}sessions"

private fun times(n: Int): String = when (n) {
    1 -> "Once"
    2 -> "Twice"
    else -> "$n times"
}

object Personas {

    val hatedPerson = Persona(
        key = "hated_person",
        label = "Hated Person",
        defaultName = "Markus K.",
        forecastLine = { n, pace, finish ->
            "Based on ${sessions(n)} like this one: pace $pace, finish around $finish. Go on, prove me wrong."
        },
        cowedLines = listOf(
            "...Okay. That was fast. I don't have anything for that.",
            "Fine. You win this one.",
            "...I'll need a minute.",
        ),
        watchfulLines = listOf(
            "Noted. Still watching.",
            "About what I expected. We'll see.",
            "Nothing to say yet. Don't mistake that for approval.",
        ),
        predatoryLines = listOf(
            "There it is. Every time things get hard, you fold. At least you're consistent about something.",
            "I was starting to think you'd changed. Good to know you haven't.",
            "Three days. Three days and you're already negotiating with yourself.",
        ),
        idleLines = listOf(
            "Thinking about you. Not in a good way.",
            "Still here. Are you?",
        ),
        handoffLine = { n, gen ->
            "You beat my forecast ${times(n).lowercase()} this generation. I've adjusted. Generation $gen is watching now. Do it again."
        },
        duelLostLines = listOf(
            "That was your best shot? I'm staying. Obviously.",
            "You had one run to get rid of me and you paced it like a Sunday.",
        ),
        stakeLine = { pace, pts ->
            "You won't beat $pace today. I'd put money on it — so I'm putting $pts points on it."
        },
        callLines = listOf(
            "Oh, you're calling it? Good. Makes it sweeter.",
            "Doubled. Remember you did that to yourself.",
        ),
        liveAheadLines = listOf(
            { gap -> "$gap under my number. Enjoy it while it lasts." },
            { gap -> "Fine. $gap ahead. You'll give it back on the hill." },
        ),
        liveBehindLines = listOf(
            { gap -> "$gap behind. Exactly where I said you'd be." },
            { gap -> "$gap slow. Don't look at the watch, look at yourself." },
        ),
        liveLevelLines = listOf(
            { km -> "Km $km. Right on my line. Predictable." },
            { km -> "Km $km. You're running my forecast for me." },
        ),
    )

    val betterSelf = Persona(
        key = "better_self",
        label = "Better Self",
        defaultName = "Better Self",
        forecastLine = { n, pace, finish ->
            "Based on ${sessions(n)}: pace $pace, finish around $finish. You've done this before. Do it again."
        },
        cowedLines = listOf(
            "This is who I always thought you could be.",
            "Good. Keep going, not for me — for the next one.",
        ),
        watchfulLines = listOf(
            "Steady. That counts for more than you think.",
            "Logged. Tomorrow decides what this meant.",
        ),
        predatoryLines = listOf(
            "You know exactly why this happened. Stop pretending you don't.",
            "This isn't like you. Or it wasn't, until lately.",
        ),
        idleLines = listOf(
            "I haven't gone anywhere. Have you?",
            "Whenever you're ready. I'm not in a hurry — you should be.",
        ),
        handoffLine = { n, gen ->
            "${times(n)} you ran past what I expected of you. Good. Generation $gen expects more — because you showed it."
        },
        duelLostLines = listOf(
            "Not today. That's allowed. The version of you that wins this is still in there.",
            "Close. Closer than last time. Again.",
        ),
        stakeLine = { pace, pts ->
            "I'm staking $pts points that you stay slower than $pace. Prove I'm underestimating you."
        },
        callLines = listOf(
            "Called. Now it matters. Good.",
            "That's the version of you I'm betting against. Show me the other one.",
        ),
        liveAheadLines = listOf(
            { gap -> "$gap ahead. This is the one I knew was in there." },
            { gap -> "$gap under. Hold it — don't celebrate it." },
        ),
        liveBehindLines = listOf(
            { gap -> "$gap behind. You've closed worse than this." },
            { gap -> "$gap back. Decide now, not at the finish." },
        ),
        liveLevelLines = listOf(
            { km -> "Km $km. Level. The next one decides who you are today." },
            { km -> "Km $km. On the line. Step off it." },
        ),
    )

    val justTwin7 = Persona(
        key = "just_twin7",
        label = "The Model",
        defaultName = "The Model",
        forecastLine = { n, pace, finish ->
            "Forecast based on ${sessions(n)}: pace $pace, finish approx. $finish."
        },
        cowedLines = listOf(
            "Actual exceeded forecast. Model updated.",
            "No strong prediction available today.",
        ),
        watchfulLines = listOf(
            "Session within expected variance.",
            "Recorded. Insufficient deviation to reclassify.",
        ),
        predatoryLines = listOf(
            "Actual fell short of forecast for the fourth consecutive session.",
            "Deviation logged. Pattern flagged.",
        ),
        idleLines = listOf(
            "No session logged in a while. Model idle.",
            "Awaiting new data.",
        ),
        handoffLine = { n, gen ->
            "Forecast exceeded $n time(s) this generation. Model reset. Generation $gen initializing."
        },
        duelLostLines = listOf(
            "Trial failed. Primary asset status retained.",
            "Target pace not met. No generation change.",
        ),
        stakeLine = { pace, pts ->
            "Confidence sufficient. $pts points staked: actual pace will not beat $pace."
        },
        callLines = listOf(
            "Stake doubled by asset. Logged.",
            "Acknowledged. Outcome unchanged.",
        ),
        liveAheadLines = listOf(
            { gap -> "Deviation: $gap faster than model." },
            { gap -> "$gap ahead of projection. Monitoring." },
        ),
        liveBehindLines = listOf(
            { gap -> "Deviation: $gap slower than model." },
            { gap -> "$gap behind projection. Consistent with history." },
        ),
        liveLevelLines = listOf(
            { km -> "Km $km. Within tolerance." },
            { km -> "Km $km. Variance nominal." },
        ),
    )

    val theEx = Persona(
        key = "the_ex",
        label = "The Ex",
        defaultName = "The Ex",
        forecastLine = { n, pace, finish ->
            "I remember this one. ${times(n)}, give or take. Pace $pace, finish around $finish. Surprise me."
        },
        cowedLines = listOf(
            "...Okay. I don't have anything for that.",
            "You didn't need me to say anything, did you.",
        ),
        watchfulLines = listOf(
            "Mm. That's very you.",
            "Same as always. I'd know.",
        ),
        predatoryLines = listOf(
            "You always did this the night before you'd quit on things.",
            "At least you're predictable. That was always the problem.",
        ),
        idleLines = listOf(
            "Funny how quiet it's been.",
            "No rush. I remember how this usually goes.",
        ),
        handoffLine = { n, gen ->
            "${times(n)} you surprised me. Fine — I'll learn you again. Generation $gen. Don't get comfortable."
        },
        duelLostLines = listOf(
            "You wanted me gone that badly and still couldn't. That's very us.",
            "I knew you'd fade at the end. I always knew.",
        ),
        stakeLine = { pace, pts ->
            "$pts points say you don't beat $pace today. I know you. I always did."
        },
        callLines = listOf(
            "Calling it? You always did get loud right before you folded.",
            "Doubled. Fine. I've seen this movie.",
        ),
        liveAheadLines = listOf(
            { gap -> "$gap ahead. Huh. New." },
            { gap -> "$gap under. Don't make it weird." },
        ),
        liveBehindLines = listOf(
            { gap -> "$gap behind. There you are." },
            { gap -> "$gap slow. Same as always. I'd know." },
        ),
        liveLevelLines = listOf(
            { km -> "Km $km. Level. Very you." },
            { km -> "Km $km. Right where I left you." },
        ),
    )

    val theAuditor = Persona(
        key = "the_auditor",
        label = "The Auditor",
        defaultName = "The Auditor",
        forecastLine = { n, pace, finish ->
            "Projection from ${sessions(n, "prior ")}: pace $pace, completion approx. $finish. Variance pending."
        },
        cowedLines = listOf(
            "Asset exceeding projected parameters. Recalibration pending.",
            "Performance nominal. No further comment logged.",
        ),
        watchfulLines = listOf(
            "Asset performance within tolerance. Filed.",
            "No variance flags raised this cycle.",
        ),
        predatoryLines = listOf(
            "Asset underperforming for the fourth consecutive cycle. Recommend reclassification: hobbyist.",
            "Variance outside acceptable range. Flagged for review.",
        ),
        idleLines = listOf(
            "Asset inactive. No action required — yet.",
            "Flagged for review: extended inactivity.",
        ),
        handoffLine = { n, gen ->
            "Asset exceeded projection on $n occasion(s) this cycle. Model decommissioned. Generation $gen assumes oversight."
        },
        duelLostLines = listOf(
            "Trial outcome: asset below target. Substitution upheld.",
            "Asset failed to exceed reference session. Status unchanged. Filed.",
        ),
        stakeLine = { pace, pts ->
            "Projection confidence exceeds threshold. $pts points committed: asset will not beat $pace."
        },
        callLines = listOf(
            "Asset has escalated the stake. Noted for review.",
            "Counter-position recorded. Exposure doubled.",
        ),
        liveAheadLines = listOf(
            { gap -> "Asset $gap ahead of projection. Flag raised." },
            { gap -> "Positive variance: $gap. Pending confirmation." },
        ),
        liveBehindLines = listOf(
            { gap -> "Asset $gap below projection. As filed." },
            { gap -> "Negative variance: $gap. No surprise logged." },
        ),
        liveLevelLines = listOf(
            { km -> "Km $km. Asset within projected band." },
            { km -> "Km $km. No variance to report." },
        ),
    )

    /** All 17: the 3 base presets, the 2 archetypes that shipped first, and the 12 in PersonaRoster.kt. */
    val mvpRoster: List<Persona> = listOf(hatedPerson, betterSelf, theDoppelganger, justTwin7, theEx, theAuditor) +
        extendedRoster.filter { it.key != "doppelganger" }

    fun byKey(key: String?): Persona = mvpRoster.firstOrNull { it.key == key } ?: hatedPerson
}
