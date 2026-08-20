package dev.eversorhn.gait.tracking

import android.content.Context

/**
 * Tiny persisted record of an in-progress session, so a process death mid-run (low memory,
 * system kill, battery optimizer) doesn't silently throw the whole session away.
 *
 * The service writes it on start and every ~10 s; clears it on a clean stop. On the next
 * app launch, if a record is present but nothing is tracking, the UI offers to save or
 * discard what was captured. If the system restarts the service itself (START_STICKY),
 * the service resumes from it directly instead. See docs/scope-and-stack.md.
 */
class ActiveSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Record(
        val mode: TrackingMode,
        val startEpochMillis: Long,
        val startElapsedRealtimeMillis: Long,
        val distanceMeters: Double,
        val movingSeconds: Int,
        val elapsedSeconds: Int,
        val lastSavedEpochMillis: Long,
    )

    fun write(r: Record) {
        prefs.edit()
            .putString(K_MODE, r.mode.name)
            .putLong(K_START_EPOCH, r.startEpochMillis)
            .putLong(K_START_REALTIME, r.startElapsedRealtimeMillis)
            .putLong(K_DISTANCE_BITS, r.distanceMeters.toBits())
            .putInt(K_MOVING, r.movingSeconds)
            .putInt(K_ELAPSED, r.elapsedSeconds)
            .putLong(K_SAVED, r.lastSavedEpochMillis)
            .putBoolean(K_ACTIVE, true)
            .apply()
    }

    fun read(): Record? {
        if (!prefs.getBoolean(K_ACTIVE, false)) return null
        val mode = runCatching { TrackingMode.valueOf(prefs.getString(K_MODE, null) ?: return null) }.getOrNull() ?: return null
        return Record(
            mode = mode,
            startEpochMillis = prefs.getLong(K_START_EPOCH, 0L),
            startElapsedRealtimeMillis = prefs.getLong(K_START_REALTIME, 0L),
            distanceMeters = Double.fromBits(prefs.getLong(K_DISTANCE_BITS, 0L)),
            movingSeconds = prefs.getInt(K_MOVING, 0),
            elapsedSeconds = prefs.getInt(K_ELAPSED, 0),
            lastSavedEpochMillis = prefs.getLong(K_SAVED, 0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "gait_active_session"
        const val K_ACTIVE = "active"
        const val K_MODE = "mode"
        const val K_START_EPOCH = "start_epoch"
        const val K_START_REALTIME = "start_realtime"
        const val K_DISTANCE_BITS = "distance_bits"
        const val K_MOVING = "moving_s"
        const val K_ELAPSED = "elapsed_s"
        const val K_SAVED = "saved_epoch"
    }
}
