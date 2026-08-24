package dev.eversorhn.gait.domain.opponent

import dev.eversorhn.gait.data.db.entity.SessionEntity

/**
 * The session the opponent is doing today: when it starts, how far it goes, how fast, and how
 * much of it is already behind it at any moment.
 *
 * It trains on the weekdays you train on, at roughly the hour you train at, over the distance
 * and pace it last predicted for you — so what it does is always something you could have done
 * yourself. Deterministic from (profile, day): no state, no scheduling drift, the same answer
 * from the notification worker and the screen.
 */
object OpponentSession {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MILLIS_PER_MINUTE = 60_000L

    data class Plan(
        /** Local wall-clock minute the session starts. */
        val startMinuteOfDay: Int,
        val durationSeconds: Int,
        val distanceMeters: Double,
        val paceSecPerKm: Double,
    ) {
        val endMinuteOfDay: Int get() = startMinuteOfDay + durationSeconds / 60
    }

    /** How far through the session it is, and what that means in metres and seconds. */
    data class Live(
        val plan: Plan,
        val elapsedSeconds: Int,
        val remainingSeconds: Int,
        val coveredMeters: Double,
    ) {
        val fraction: Float get() = (elapsedSeconds.toFloat() / plan.durationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /**
     * The weekdays you actually train on — any ISO day carrying at least a fifth of your
     * sessions. The opponent keeps your schedule; [OpponentActivity] reads the same set, so the
     * daily report and the live notification can never disagree about whether it trained.
     */
    fun trainingWeekdays(sessionsNewestFirst: List<SessionEntity>): Set<Int> {
        val byWeekday = sessionsNewestFirst.groupingBy { it.dayOfWeek }.eachCount()
        val busiest = byWeekday.values.maxOrNull() ?: return emptySet()
        return byWeekday.filter { it.value * 5 >= busiest }.keys
    }

    /** The hour you usually start at, from your own history. 18:00 until there is one. */
    private fun habitualMinute(sessionsNewestFirst: List<SessionEntity>, zoneOffsetMillis: Long): Int {
        val minutes = sessionsNewestFirst.take(20).map {
            (((it.startTimeEpochMillis + zoneOffsetMillis) % MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()
        }
        if (minutes.isEmpty()) return 18 * 60
        return minutes.sorted()[minutes.size / 2]
    }

    /**
     * What the opponent is doing on [epochDay], or null if it is not training that day.
     *
     * @param seed something stable per enrolment (the profile id) so two enrolments don't train
     *   in lockstep
     */
    fun planFor(
        seed: Long,
        epochDay: Long,
        sessionsNewestFirst: List<SessionEntity>,
        forecastPaceSecPerKm: Double?,
        forecastDistanceMeters: Double?,
        zoneOffsetMillis: Long,
        plannedDaysOff: Set<Long> = emptySet(),
        weeklyRestDays: Set<Int> = emptySet(),
    ): Plan? {
        if (forecastPaceSecPerKm == null || forecastDistanceMeters == null) return null
        if (forecastDistanceMeters < 200.0 || forecastPaceSecPerKm < 60.0) return null

        val isoDay = (((epochDay + 3) % 7 + 7) % 7 + 1).toInt()
        if (epochDay in plannedDaysOff || isoDay in weeklyRestDays) return null
        val trains = trainingWeekdays(sessionsNewestFirst)
        if (trains.isNotEmpty() && isoDay !in trains) return null

        // ±50 minutes around your habitual hour, fixed for the day.
        val jitter = ((hash(seed, epochDay) % 101L).toInt()) - 50
        val start = (habitualMinute(sessionsNewestFirst, zoneOffsetMillis) + jitter).coerceIn(4 * 60, 22 * 60)
        val duration = (forecastPaceSecPerKm * forecastDistanceMeters / 1000.0).toInt().coerceAtLeast(60)
        return Plan(start, duration, forecastDistanceMeters, forecastPaceSecPerKm)
    }

    /** Where the session stands at [nowEpochMillis]; null when it has not started or is long over. */
    fun liveAt(plan: Plan, epochDay: Long, nowEpochMillis: Long, zoneOffsetMillis: Long): Live? {
        val startMillis = (epochDay * MILLIS_PER_DAY) - zoneOffsetMillis + plan.startMinuteOfDay * MILLIS_PER_MINUTE
        val elapsed = ((nowEpochMillis - startMillis) / 1000L).toInt()
        if (elapsed < 0 || elapsed > plan.durationSeconds) return null
        return Live(
            plan = plan,
            elapsedSeconds = elapsed,
            remainingSeconds = plan.durationSeconds - elapsed,
            coveredMeters = plan.distanceMeters * (elapsed.toDouble() / plan.durationSeconds),
        )
    }

    /** Wall-clock millis the session ends, for a countdown the system can tick itself. */
    fun endMillis(plan: Plan, epochDay: Long, zoneOffsetMillis: Long): Long =
        (epochDay * MILLIS_PER_DAY) - zoneOffsetMillis + plan.startMinuteOfDay * MILLIS_PER_MINUTE + plan.durationSeconds * 1000L

    private fun hash(a: Long, b: Long): Long {
        var x = (a * -0x61c8864680b583ebL) xor (b * -0x40a7b892e31b1a47L)
        x = (x xor (x ushr 31)) * -0x7fb5d329728ea185L
        x = (x xor (x ushr 27)) * -0x6b8c0d2b5f4c9e3fL
        return (x xor (x ushr 33)) and Long.MAX_VALUE
    }
}
