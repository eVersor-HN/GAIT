package dev.eversorhn.momentum.health

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Length
import dev.eversorhn.momentum.data.db.entity.SessionEntity
import dev.eversorhn.momentum.data.db.entity.SessionSource
import java.time.Instant
import java.time.ZoneId

/**
 * Writing a finished session back to Health Connect, so what you record in MOMENTUM counts
 * everywhere else on the phone — the ring closes, the step app and the watch see the same run.
 *
 * Opt-in and separate from the import permission: a device that only reads keeps only reading.
 * Sessions that came *from* Health Connect are never written back, or the same run would
 * multiply every time it is imported.
 */
object HealthExport {

    val PERMISSIONS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )

    suspend fun hasPermissions(context: Context): Boolean = runCatching {
        HealthImport.client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** The Health Connect exercise type for a MOMENTUM activity, or null when there is no sensible one. */
    private fun exerciseTypeFor(activityKey: String, indoor: Boolean): Int? = when (activityKey) {
        "RUNNING", "GUIDE_RUN" -> if (indoor) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL else ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        "WALKING", "NORDIC_WALKING" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        "HIKING" -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        "CYCLING", "E_BIKE", "HAND_CYCLE", "BIKE_ERG" ->
            if (indoor || activityKey == "BIKE_ERG") ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
            else ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        "WHEELCHAIR", "RACING_CHAIR" -> ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR
        "ROWING", "ADAPTIVE_ROWING" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
        "ROWING_ERG" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        "SWIMMING" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
        "KAYAK", "PARA_CANOE" -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING
        "XC_SKI", "SIT_SKI" -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
        "INLINE" -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
        "ELLIPTICAL" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        "STAIR" -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        else -> null
    }

    /**
     * Writes one finished session. Returns true when it landed; false when Health Connect is
     * absent, the permission is not granted, the session came from there in the first place, or
     * the activity has no matching type.
     */
    suspend fun write(context: Context, session: SessionEntity, indoor: Boolean): Boolean {
        if (session.dataSource == SessionSource.HEALTH) return false
        if (!HealthImport.isAvailable(context)) return false
        if (!ExportPrefs.isEnabled(context)) return false
        if (!hasPermissions(context)) return false
        val type = exerciseTypeFor(session.activityType, indoor) ?: return false

        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(session.startTimeEpochMillis)
            val end = start.plusSeconds(session.durationSeconds.toLong())
            val startOffset = zone.rules.getOffset(start)
            val endOffset = zone.rules.getOffset(end)

            HealthImport.client(context).insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        exerciseType = type,
                        title = "MOMENTUM session",
                    ),
                    DistanceRecord(
                        startTime = start,
                        startZoneOffset = startOffset,
                        endTime = end,
                        endZoneOffset = endOffset,
                        distance = Length.meters(session.distanceMeters),
                    ),
                )
            )
            true
        }.getOrDefault(false)
    }
}

/** Writing back is off until it is switched on: it puts data somewhere the user did not ask for. */
object ExportPrefs {
    private const val PREFS = "momentum_health_export"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
