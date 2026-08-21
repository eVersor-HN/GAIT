package dev.eversorhn.gait.data.repository

import android.content.Context
import dev.eversorhn.gait.data.db.GaitDatabase
import dev.eversorhn.gait.data.db.entity.ImportedAssetEntity
import dev.eversorhn.gait.data.db.entity.PlannedDayOffEntity
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinMessageEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import kotlinx.coroutines.flow.Flow

const val ACTIVITY_RUNNING = "RUNNING"

class GaitRepository(private val db: GaitDatabase, private val appContext: Context? = null) {

    private val prefs get() = appContext?.getSharedPreferences("gait_repository", Context.MODE_PRIVATE)

    /**
     * The activity whose profile/sessions every default-argument call below refers to. Chosen
     * at setup (ActivityScreen), switchable in Settings; persisted so a relaunch keeps it.
     */
    var activeActivityType: String = prefs?.getString("active_activity", ACTIVITY_RUNNING) ?: ACTIVITY_RUNNING
        set(value) {
            field = value
            prefs?.edit()?.putString("active_activity", value)?.apply()
        }

    /** When the asset first enrolled — the oldest profile across activities. Tenure and the roster's founding day hang off this. */
    suspend fun earliestEnrolmentEpochMillis(): Long? = db.twinProfileDao().earliestCreatedAt()

    /** Activities that already have an opponent profile. */
    suspend fun activitiesWithProfile(): List<String> = db.twinProfileDao().getAllActivityTypes()

    // --- Asset transfer: assets imported from other divisions ---

    suspend fun getImportedAssets(): List<ImportedAssetEntity> = db.importedAssetDao().getAll()

    suspend fun importAsset(id: String, name: String, payload: String, importedEpochDay: Long) {
        db.importedAssetDao().upsert(ImportedAssetEntity(id, name, payload, importedEpochDay, System.currentTimeMillis()))
    }

    suspend fun deleteImportedAsset(id: String) = db.importedAssetDao().delete(id)

    // --- Rest & Vacation calendar: days marked off in advance (local epoch-day) ---

    suspend fun getPlannedDaysOff(): List<Long> = db.plannedDayOffDao().getAll().map { it.epochDay }

    fun observePlannedDaysOff(): Flow<List<PlannedDayOffEntity>> = db.plannedDayOffDao().observeAll()

    suspend fun isPlannedDayOff(epochDay: Long): Boolean = db.plannedDayOffDao().count(epochDay) > 0

    suspend fun setPlannedDayOff(epochDay: Long, off: Boolean) {
        if (off) db.plannedDayOffDao().upsert(PlannedDayOffEntity(epochDay, System.currentTimeMillis()))
        else db.plannedDayOffDao().delete(epochDay)
    }

    fun observeTwinProfile(activityType: String = activeActivityType): Flow<TwinProfileEntity?> =
        db.twinProfileDao().observeProfile(activityType)

    suspend fun getTwinProfile(activityType: String = activeActivityType): TwinProfileEntity? =
        db.twinProfileDao().getProfile(activityType)

    suspend fun createTwinProfile(
        twinName: String,
        personaKey: String,
        activityType: String = activeActivityType,
    ) {
        db.twinProfileDao().insert(
            TwinProfileEntity(
                activityType = activityType,
                opponentType = OpponentType.TWIN,
                personaKey = personaKey,
                hordeIntensity = null,
                twinName = twinName,
                fidelity = 0.5f,
                generation = 1,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun createHordeProfile(
        hordeIntensity: String,
        activityType: String = activeActivityType,
    ) {
        db.twinProfileDao().insert(
            TwinProfileEntity(
                activityType = activityType,
                opponentType = OpponentType.HORDE,
                personaKey = null,
                hordeIntensity = hordeIntensity,
                twinName = "The Horde",
                fidelity = 0.5f,
                generation = 1,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateTwinProfile(profile: TwinProfileEntity) {
        db.twinProfileDao().update(profile)
    }

    suspend fun getSessions(activityType: String = activeActivityType): List<SessionEntity> =
        db.sessionDao().getSessions(activityType)

    suspend fun getRecentSessions(limit: Int, activityType: String = activeActivityType): List<SessionEntity> =
        db.sessionDao().getRecentSessions(activityType, limit)

    fun observeSessions(activityType: String = activeActivityType): Flow<List<SessionEntity>> =
        db.sessionDao().observeSessions(activityType)

    suspend fun logSession(session: SessionEntity) {
        db.sessionDao().insert(session)
    }

    suspend fun deleteSession(id: Long) {
        db.sessionDao().deleteById(id)
    }

    // --- The opponent's inbox (everything said outside a Debrief) ---

    suspend fun recordMessage(kind: String, line: String, composureState: String? = null, epochMillis: Long = System.currentTimeMillis()) {
        db.twinMessageDao().insert(TwinMessageEntity(epochMillis = epochMillis, kind = kind, line = line, composureState = composureState))
    }

    suspend fun getMessages(): List<TwinMessageEntity> = db.twinMessageDao().getAll()

    fun observeRecentMessages(limit: Int = 5): Flow<List<TwinMessageEntity>> = db.twinMessageDao().observeRecent(limit)

    /** Full reset: every session, message, and the opponent profile. Used by Settings > Reset. */
    suspend fun wipeAll() {
        db.sessionDao().deleteAll()
        db.twinMessageDao().deleteAll()
        db.plannedDayOffDao().deleteAll()
        db.importedAssetDao().deleteAll()
        db.twinProfileDao().deleteAll()
    }
}
