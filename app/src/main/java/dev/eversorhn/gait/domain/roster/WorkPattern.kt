package dev.eversorhn.gait.domain.roster

/**
 * How an asset's week is shaped — the thing that decides when it can train at all.
 *
 * Roughly a sixth of employed people work something other than a day schedule (evenings,
 * nights, early shifts, rotating crews, split and irregular schedules), and a good deal more
 * work a day schedule that still isn't nine-to-five: flexitime, hybrid weeks, compressed
 * four-day weeks, part-time mornings or afternoons. On top of that sit the people not on a
 * payroll at all — students, parents at home, the retired, the between-jobs.
 *
 * Each pattern carries its own training windows: one for a working day, one for a free day.
 * Minutes are minutes of the local day.
 */
enum class WorkPattern(
    val label: String,
    /** Training window on a day this pattern is working. */
    val workDayWindow: IntRange,
    /** Training window on a day off. */
    val freeDayWindow: IntRange,
    /** Roughly how much of the roster sits on this pattern. Shares sum to 1. */
    val share: Double,
) {
    NINE_TO_FIVE("nine to five", 17 * 60..20 * 60, 9 * 60..12 * 60, 0.24),
    EARLY_RISER("nine to five · trains before it", 5 * 60 + 15..7 * 60 + 15, 7 * 60..9 * 60 + 30, 0.06),
    FLEXITIME("flexitime", 6 * 60 + 30..9 * 60, 9 * 60..12 * 60, 0.11),
    HYBRID_REMOTE("hybrid · works from home half the week", 11 * 60 + 30..14 * 60, 10 * 60..13 * 60, 0.10),
    COMPRESSED_WEEK("four ten-hour days", 19 * 60..20 * 60 + 45, 10 * 60..13 * 60, 0.04),
    PART_TIME_MORNINGS("part time · mornings", 13 * 60 + 30..16 * 60, 9 * 60 + 30..12 * 60, 0.05),
    PART_TIME_AFTERNOONS("part time · afternoons", 8 * 60 + 30..11 * 60, 9 * 60..12 * 60, 0.03),
    EARLY_SHIFT("early shift", 15 * 60..17 * 60 + 30, 9 * 60..12 * 60, 0.04),
    LATE_SHIFT("late shift", 9 * 60..11 * 60 + 30, 10 * 60..13 * 60, 0.05),
    NIGHT_SHIFT("night shift", 14 * 60 + 30..17 * 60, 10 * 60..13 * 60, 0.04),
    ROTATING_TWELVES("rotating twelve-hour crews", 20 * 60..21 * 60 + 30, 9 * 60 + 30..12 * 60, 0.03),
    SPLIT_SHIFT("split shift", 14 * 60 + 30..16 * 60 + 30, 10 * 60..13 * 60, 0.01),
    ON_CALL("on call · irregular", 6 * 60..21 * 60, 6 * 60..21 * 60, 0.03),
    SEASONAL("seasonal · blocks on, blocks off", 18 * 60..20 * 60, 9 * 60..13 * 60, 0.02),
    STUDENT("studying", 16 * 60..18 * 60 + 30, 10 * 60..13 * 60, 0.06),
    PARENT_AT_HOME("at home with children", 9 * 60 + 15..11 * 60, 9 * 60 + 15..11 * 60, 0.04),
    RETIRED("retired", 9 * 60..11 * 60, 9 * 60..11 * 60, 0.03),
    BETWEEN_JOBS("between jobs", 10 * 60..13 * 60, 10 * 60..13 * 60, 0.02),
    MAINTENANCE_WINDOW("maintenance window", 3 * 60..4 * 60, 3 * 60..4 * 60, 0.0);

    companion object {
        /** Everything a person can be assigned; synths get [MAINTENANCE_WINDOW] instead. */
        val human: List<WorkPattern> = entries.filter { it != MAINTENANCE_WINDOW }

        /** Picks a pattern from a uniform roll in [0,1) by share. */
        fun byRoll(roll: Double): WorkPattern {
            var acc = 0.0
            for (p in human) {
                acc += p.share
                if (roll < acc) return p
            }
            return NINE_TO_FIVE
        }
    }
}
