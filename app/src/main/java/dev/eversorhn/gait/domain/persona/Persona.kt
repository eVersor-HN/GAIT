package dev.eversorhn.gait.domain.persona

/**
 * A selectable Twin voice. v1 ships 5 of the 17 designed in docs/twin-personas.md;
 * the rest move to v1.1 per docs/scope-and-stack.md.
 *
 * cowedLines / predatoryLines are a small curated starting bank, not the full
 * variation system from docs/composure-system.md — real production content
 * should grow these substantially and eventually move to grounded generation.
 */
data class Persona(
    val key: String,
    val label: String,
    val defaultName: String,
    val forecastLine: (basedOnSessions: Int, paceLabel: String, finishLabel: String) -> String,
    val cowedLines: List<String>,
    val predatoryLines: List<String>,
    /** Ambient, not tied to any specific session — see docs/notifications.md "Idle taunts". */
    val idleLines: List<String>,
)

object Personas {

    val hatedPerson = Persona(
        key = "hated_person",
        label = "Hated Person",
        defaultName = "Markus K.",
        forecastLine = { n, pace, finish ->
            "Based on $n sessions like this one: pace $pace, finish around $finish. Go on, prove me wrong."
        },
        cowedLines = listOf(
            "...Okay. That was fast. I don't have anything for that.",
            "Fine. You win this one.",
            "...I'll need a minute.",
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
    )

    val betterSelf = Persona(
        key = "better_self",
        label = "Better Self",
        defaultName = "Better Self",
        forecastLine = { n, pace, finish ->
            "Based on $n sessions: pace $pace, finish around $finish. You've done this before. Do it again."
        },
        cowedLines = listOf(
            "This is who I always thought you could be.",
            "Good. Keep going, not for me — for the next one.",
        ),
        predatoryLines = listOf(
            "You know exactly why this happened. Stop pretending you don't.",
            "This isn't like you. Or it wasn't, until lately.",
        ),
        idleLines = listOf(
            "I haven't gone anywhere. Have you?",
            "Whenever you're ready. I'm not in a hurry — you should be.",
        ),
    )

    val justTwin7 = Persona(
        key = "just_twin7",
        label = "Just Twin-7",
        defaultName = "Twin-7",
        forecastLine = { n, pace, finish ->
            "Forecast based on $n sessions: pace $pace, finish approx. $finish."
        },
        cowedLines = listOf(
            "Actual exceeded forecast. Model updated.",
            "No strong prediction available today.",
        ),
        predatoryLines = listOf(
            "Actual fell short of forecast for the fourth consecutive session.",
            "Deviation logged. Pattern flagged.",
        ),
        idleLines = listOf(
            "No session logged in a while. Model idle.",
            "Awaiting new data.",
        ),
    )

    val theEx = Persona(
        key = "the_ex",
        label = "The Ex",
        defaultName = "The Ex",
        forecastLine = { n, pace, finish ->
            "I remember this one. $n times, give or take. Pace $pace, finish around $finish. Surprise me."
        },
        cowedLines = listOf(
            "...Okay. I don't have anything for that.",
            "You didn't need me to say anything, did you.",
        ),
        predatoryLines = listOf(
            "You always did this the night before you'd quit on things.",
            "At least you're predictable. That was always the problem.",
        ),
        idleLines = listOf(
            "Funny how quiet it's been.",
            "No rush. I remember how this usually goes.",
        ),
    )

    val theAuditor = Persona(
        key = "the_auditor",
        label = "The Auditor",
        defaultName = "The Auditor",
        forecastLine = { n, pace, finish ->
            "Projection from $n prior sessions: pace $pace, completion approx. $finish. Variance pending."
        },
        cowedLines = listOf(
            "Asset exceeding projected parameters. Recalibration pending.",
            "Performance nominal. No further comment logged.",
        ),
        predatoryLines = listOf(
            "Asset underperforming for the fourth consecutive cycle. Recommend reclassification: hobbyist.",
            "Variance outside acceptable range. Flagged for review.",
        ),
        idleLines = listOf(
            "Asset inactive. No action required — yet.",
            "Flagged for review: extended inactivity.",
        ),
    )

    val mvpRoster: List<Persona> = listOf(hatedPerson, betterSelf, justTwin7, theEx, theAuditor)

    fun byKey(key: String): Persona = mvpRoster.first { it.key == key }
}
