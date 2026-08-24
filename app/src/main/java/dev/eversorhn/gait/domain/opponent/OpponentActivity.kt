package dev.eversorhn.gait.domain.opponent

import dev.eversorhn.gait.data.db.entity.SessionEntity

/**
 * What the opponent did while you were not training.
 *
 * The model does not wait for you. On every day you were absent that you would normally have
 * trained on — your own weekday pattern, read out of your history — it completes its own
 * session at the pace it last predicted for you. The horde covers the same ground and closes on
 * you by it.
 *
 * Pure and deterministic: the same history and the same day always give the same report, so it
 * can be recomputed anywhere (the daily notice, the standing page) without being stored.
 */
object OpponentActivity {

    data class Report(
        /** Days since your last session. 0 while you trained today. */
        val daysAbsent: Int,
        /** Sessions the opponent completed in that time. */
        val sessions: Int,
        val distanceMeters: Double,
        val paceSecPerKm: Double?,
        /** Ground it took out of your lead — metres for a horde, its own distance otherwise. */
        val groundMeters: Int,
    ) {
        val active: Boolean get() = sessions > 0
    }

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * @param sessionsNewestFirst your history
     * @param plannedDaysOff epoch days you declared off — the opponent honours them too
     * @param weeklyRestDays ISO day numbers you declared as weekly rest
     */
    fun since(
        sessionsNewestFirst: List<SessionEntity>,
        forecastPaceSecPerKm: Double?,
        forecastDistanceMeters: Double?,
        nowEpochMillis: Long,
        plannedDaysOff: Set<Long> = emptySet(),
        weeklyRestDays: Set<Int> = emptySet(),
    ): Report {
        val last = sessionsNewestFirst.firstOrNull() ?: return Report(0, 0, 0.0, null, 0)
        val daysAbsent = ((nowEpochMillis - last.startTimeEpochMillis) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
        if (daysAbsent < 1 || forecastPaceSecPerKm == null || forecastDistanceMeters == null) {
            return Report(daysAbsent, 0, 0.0, forecastPaceSecPerKm, 0)
        }

        // The same weekday rule the live session card uses, so the two can never disagree.
        val trainingDays = OpponentSession.trainingWeekdays(sessionsNewestFirst)

        var count = 0
        val lastEpochDay = last.startTimeEpochMillis / MILLIS_PER_DAY
        val todayEpochDay = nowEpochMillis / MILLIS_PER_DAY
        var day = lastEpochDay + 1
        while (day <= todayEpochDay) {
            // epoch day 0 was a Thursday (ISO 4); this maps a day to its ISO weekday.
            val isoDay = (((day + 3) % 7 + 7) % 7 + 1).toInt()
            val excused = day in plannedDaysOff || isoDay in weeklyRestDays
            if (!excused && (trainingDays.isEmpty() || isoDay in trainingDays)) count++
            day++
        }

        val distance = forecastDistanceMeters * count
        return Report(
            daysAbsent = daysAbsent,
            sessions = count,
            distanceMeters = distance,
            paceSecPerKm = forecastPaceSecPerKm,
            groundMeters = distance.toInt(),
        )
    }
}
