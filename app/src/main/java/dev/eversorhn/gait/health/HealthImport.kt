package dev.eversorhn.gait.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.repository.GaitRepository
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Health Connect import (docs/scope-and-stack.md's integration point, remembered backlog):
 * reads exercise sessions of the active activity's kind from the last 30 days and inserts the
 * ones GAIT doesn't have yet, tagged [SessionSource.HEALTH]. Read-only; GAIT writes nothing
 * back. No client is created unless the SDK reports Health Connect available on this device.
 */
object HealthImport {

    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean = availability(context) == HealthConnectClient.SDK_AVAILABLE

    fun client(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean = runCatching {
        client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** Exercise types that map onto a GAIT activity key. */
    private fun exerciseTypesFor(activityKey: String): Set<Int> = when (activityKey) {
        "RUNNING" -> setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        "WALKING", "HIKING" -> setOf(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING)
        "CYCLING", "E_BIKE", "HAND_CYCLE" -> setOf(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY)
        "WHEELCHAIR" -> setOf(ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR)
        else -> emptySet()
    }

    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    /**
     * Imports the last 30 days for the active activity. Dedupe: a session whose start is within
     * 5 minutes of an existing one is skipped. Distance comes from DistanceRecords overlapping
     * the session window; sessions without any distance are skipped (no pace to judge).
     */
    suspend fun importRecent(context: Context, repository: GaitRepository): Result {
        return try {
            val c = client(context)
            val now = Instant.now()
            val range = TimeRangeFilter.between(now.minus(30, ChronoUnit.DAYS), now)
            val wanted = exerciseTypesFor(repository.activeActivityType)
            val sessions = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                .filter { wanted.isEmpty() || it.exerciseType in wanted }
            val distances = c.readRecords(ReadRecordsRequest(DistanceRecord::class, range)).records
            val existing = repository.getSessions().map { it.startTimeEpochMillis }
            var imported = 0; var skipped = 0
            for (s in sessions) {
                val startMs = s.startTime.toEpochMilli()
                if (existing.any { kotlin.math.abs(it - startMs) < 5 * 60_000L }) { skipped++; continue }
                val durationSec = ((s.endTime.toEpochMilli() - startMs) / 1000L).toInt()
                if (durationSec < 60) { skipped++; continue }
                val meters = distances
                    .filter { it.startTime < s.endTime && it.endTime > s.startTime }
                    .sumOf { it.distance.inMeters }
                if (meters < 100.0) { skipped++; continue }
                val pace = durationSec / (meters / 1000.0)
                repository.logSession(
                    SessionEntity(
                        activityType = repository.activeActivityType,
                        startTimeEpochMillis = startMs,
                        dayOfWeek = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).dayOfWeek.value,
                        durationSeconds = durationSec,
                        distanceMeters = meters,
                        avgPaceSecPerKm = pace,
                        forecastPaceSecPerKm = null,   // history import: baseline material, not a scored round
                        forecastFinishSeconds = null,
                        dataSource = SessionSource.HEALTH,
                    )
                )
                imported++
            }
            Result(imported, skipped)
        } catch (e: Exception) {
            Result(0, 0, e.message ?: e.javaClass.simpleName)
        }
    }
}
