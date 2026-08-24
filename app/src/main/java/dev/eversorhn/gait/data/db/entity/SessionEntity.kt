package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object SessionSource {
    /** GPS-verified — the device measured the distance itself. */
    const val GPS = "gps"
    /** Self-reported — typed in, e.g. off a treadmill or ergometer console. Not verifiable. */
    const val MANUAL = "manual"
    /** Imported from Health Connect (another app's recording). Treated as verified-ish, tagged. */
    const val HEALTH = "health"
}

/**
 * A completed (or manually logged) session. This is the training corpus the
 * k-nearest-analog Forecast engine queries — see docs/telemetry-and-forecasting.md.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The profile this session belongs to (twin_profiles.id). */
    val profileId: Long = 0,
    val activityType: String,
    val startTimeEpochMillis: Long,
    val dayOfWeek: Int, // 1 (Monday) .. 7 (Sunday), ISO-8601
    val durationSeconds: Int,
    val distanceMeters: Double,
    val avgPaceSecPerKm: Double,
    val forecastPaceSecPerKm: Double?,
    val forecastFinishSeconds: Int?,
    val isRestDay: Boolean = false,
    /** [SessionSource.GPS] or [SessionSource.MANUAL] — see docs/activities-and-dimensions.md. */
    val dataSource: String = SessionSource.GPS,
    /** What the opponent said at this session's Debrief, so the Direct Channel can show a log. Null if silent. */
    val twinLine: String? = null,
    /** ComposureState.name at the Debrief (COWED / WATCHFUL / PREDATORY). Null if not evaluated (rest day). */
    val composureState: String? = null,
    /** True if this session was run as a Decommission Trial -- see domain/trial. */
    val isDuel: Boolean = false,
    /** Duel verdict: true = won (generation advanced), false = lost, null = not a duel / too short. */
    val duelWon: Boolean? = null,
    /**
     * Ledger points this round was worth (domain/ledger): 1 by default, 2 when the opponent
     * staked on the forecast, 4 when the user called that stake, 3 for a duel.
     */
    val stake: Int = 1,
    // --- v0.14.0: the dimensions beyond pace (domain/route/RouteMetrics) ---
    /** Downsampled GPS trace, "lat,lon;…" (~25 m steps). Null for indoor/manual sessions. */
    val route: String? = null,
    /** Positive altitude gain in metres, from GPS altitude. Null if unknown. */
    val elevationGainMeters: Double? = null,
    /** Steadiness 0..1 (1 − CV of km splits). Null if < 2 splits. */
    val consistency: Double? = null,
    /** 0..1 vs. every earlier route; null for the first route / no route. */
    val routeNovelty: Double? = null,
    /** The model's expected steadiness for this session (EWMA of your prior ones). */
    val forecastConsistency: Double? = null,

    // --- v0.23.0: what the session cost you, when a monitor was connected ---
    /** Mean heart rate over the session, from a paired chest strap or watch. */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
)
