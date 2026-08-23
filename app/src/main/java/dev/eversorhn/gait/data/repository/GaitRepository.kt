package dev.eversorhn.gait.data.repository

import android.content.Context
import dev.eversorhn.gait.data.db.GaitDatabase
import dev.eversorhn.gait.data.db.entity.ImportedAssetEntity
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.PlannedDayOffEntity
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinMessageEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

const val ACTIVITY_RUNNING = "RUNNING"

/**
 * One repository, scoped to the **active profile**. A profile is a named opponent for one
 * activity — its own sessions, ledger, messages, rest days, settings. Everything below reads
 * and writes for [activeProfileId] unless told otherwise; the profile list screen is the only
 * place that works across profiles.
 */
class GaitRepository(private val db: GaitDatabase, private val appContext: Context? = null) {

    private val prefs get() = appContext?.getSharedPreferences("gait_repository", Context.MODE_PRIVATE)

    /** The profile every default-argument call refers to. 0 = none chosen yet. */
    var activeProfileId: Long = prefs?.getLong("active_profile_id", 0L) ?: 0L
        set(value) {
            field = value
            prefs?.edit()?.putLong("active_profile_id", value)?.apply()
        }

    /** The active profile's activity, cached for the many call sites that just need the label. */
    var activeActivityType: String = prefs?.getString("active_activity", ACTIVITY_RUNNING) ?: ACTIVITY_RUNNING
        set(value) {
            field = value
            prefs?.edit()?.putString("active_activity", value)?.apply()
        }

    // ---------------------------------------------------------------- profiles

    suspend fun listProfiles(): List<TwinProfileEntity> = db.twinProfileDao().getAll()

    fun observeProfiles(): Flow<List<TwinProfileEntity>> = db.twinProfileDao().observeAll()

    suspend fun selectProfile(id: Long) {
        activeProfileId = id
        db.twinProfileDao().getById(id)?.let { activeActivityType = it.activityType }
    }

    /** Creates a profile and makes it active. Returns its id. */
    suspend fun createProfile(
        profileName: String,
        activityType: String,
        opponentType: String,
        personaKey: String?,
        hordeIntensity: String?,
        opponentName: String,
    ): Long {
        val id = db.twinProfileDao().insert(
            TwinProfileEntity(
                activityType = activityType,
                profileName = profileName,
                opponentType = opponentType,
                personaKey = personaKey,
                hordeIntensity = hordeIntensity,
                twinName = opponentName,
                fidelity = 0.5f,
                generation = 1,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        )
        activeProfileId = id
        activeActivityType = activityType
        return id
    }

    /** Deletes a profile and everything that belongs to it. */
    suspend fun deleteProfile(profile: TwinProfileEntity) {
        db.sessionDao().deleteForProfile(profile.id)
        db.twinMessageDao().deleteForProfile(profile.id)
        db.plannedDayOffDao().deleteForProfile(profile.id)
        db.twinProfileDao().delete(profile)
        // The demo record described rows that no longer exist.
        appContext?.let { dev.eversorhn.gait.domain.demo.DemoRecord.clear(it, profile.id) }
        if (activeProfileId == profile.id) activeProfileId = 0L
    }

    /** When the asset first enrolled — the oldest profile. Tenure and the roster's founding day hang off this. */
    suspend fun earliestEnrolmentEpochMillis(): Long? = db.twinProfileDao().earliestCreatedAt()

    fun observeTwinProfile(profileId: Long = activeProfileId): Flow<TwinProfileEntity?> =
        if (profileId == 0L) flowOf(null) else db.twinProfileDao().observeById(profileId)

    suspend fun getTwinProfile(profileId: Long = activeProfileId): TwinProfileEntity? =
        if (profileId == 0L) null else db.twinProfileDao().getById(profileId)

    suspend fun updateTwinProfile(profile: TwinProfileEntity) {
        db.twinProfileDao().update(profile)
    }

    // ---------------------------------------------------------------- sessions

    suspend fun getSessions(profileId: Long = activeProfileId): List<SessionEntity> =
        db.sessionDao().getSessions(profileId)

    suspend fun getRecentSessions(limit: Int, profileId: Long = activeProfileId): List<SessionEntity> =
        db.sessionDao().getRecentSessions(profileId, limit)

    fun observeSessions(profileId: Long = activeProfileId): Flow<List<SessionEntity>> =
        db.sessionDao().observeSessions(profileId)

    /** Stamps the active profile onto the row, so callers never have to remember. */
    /** Returns the new row id, so callers that may need to take the session back can keep it. */
    suspend fun logSession(session: SessionEntity): Long =
        db.sessionDao().insert(session.copy(profileId = activeProfileId, activityType = activeActivityType))

    suspend fun deleteSession(id: Long) {
        db.sessionDao().deleteById(id)
    }

    // ---------------------------------------------------------------- the opponent's inbox

    suspend fun recordMessage(kind: String, line: String, composureState: String? = null, epochMillis: Long = System.currentTimeMillis()) {
        db.twinMessageDao().insert(
            TwinMessageEntity(profileId = activeProfileId, epochMillis = epochMillis, kind = kind, line = line, composureState = composureState)
        )
    }

    suspend fun getMessages(): List<TwinMessageEntity> = db.twinMessageDao().getAll(activeProfileId)

    fun observeRecentMessages(limit: Int = 5): Flow<List<TwinMessageEntity>> =
        db.twinMessageDao().observeRecent(activeProfileId, limit)

    // ---------------------------------------------------------------- rest & vacation calendar

    suspend fun getPlannedDaysOff(): List<Long> = db.plannedDayOffDao().getAll(activeProfileId).map { it.epochDay }

    suspend fun isPlannedDayOff(epochDay: Long): Boolean = db.plannedDayOffDao().count(activeProfileId, epochDay) > 0

    suspend fun setPlannedDayOff(epochDay: Long, off: Boolean) {
        if (off) db.plannedDayOffDao().upsert(PlannedDayOffEntity(activeProfileId, epochDay, System.currentTimeMillis()))
        else db.plannedDayOffDao().delete(activeProfileId, epochDay)
    }

    // ---------------------------------------------------------------- asset transfer

    suspend fun getImportedAssets(): List<ImportedAssetEntity> = db.importedAssetDao().getAll()

    suspend fun importAsset(id: String, name: String, payload: String, importedEpochDay: Long) {
        db.importedAssetDao().upsert(ImportedAssetEntity(id, name, payload, importedEpochDay, System.currentTimeMillis()))
    }

    suspend fun deleteImportedAsset(id: String) = db.importedAssetDao().delete(id)

    // ---------------------------------------------------------------- reset

    /** Full reset: every profile and everything attached to it. Used by Settings → Erase. */
    suspend fun wipeAll() {
        db.sessionDao().deleteAll()
        db.twinMessageDao().deleteAll()
        db.plannedDayOffDao().deleteAll()
        db.importedAssetDao().deleteAll()
        val profileIds = db.twinProfileDao().getAll().map { it.id }
        db.twinProfileDao().deleteAll()
        appContext?.let { ctx -> profileIds.forEach { dev.eversorhn.gait.domain.demo.DemoRecord.clear(ctx, it) } }
        activeProfileId = 0L
    }
}
