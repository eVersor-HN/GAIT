package dev.eversorhn.gait.data.repository

import dev.eversorhn.gait.data.db.GaitDatabase
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import kotlinx.coroutines.flow.Flow

const val ACTIVITY_RUNNING = "RUNNING"

class GaitRepository(private val db: GaitDatabase) {

    fun observeTwinProfile(activityType: String = ACTIVITY_RUNNING): Flow<TwinProfileEntity?> =
        db.twinProfileDao().observeProfile(activityType)

    suspend fun getTwinProfile(activityType: String = ACTIVITY_RUNNING): TwinProfileEntity? =
        db.twinProfileDao().getProfile(activityType)

    suspend fun createTwinProfile(
        personaKey: String,
        twinName: String,
        opponentType: String = OpponentType.TWIN,
        activityType: String = ACTIVITY_RUNNING,
    ) {
        db.twinProfileDao().insert(
            TwinProfileEntity(
                activityType = activityType,
                personaKey = personaKey,
                twinName = twinName,
                fidelity = 0.5f,
                generation = 1,
                createdAtEpochMillis = System.currentTimeMillis(),
                opponentType = opponentType,
            )
        )
    }

    suspend fun updateTwinProfile(profile: TwinProfileEntity) {
        db.twinProfileDao().update(profile)
    }

    suspend fun getSessions(activityType: String = ACTIVITY_RUNNING): List<SessionEntity> =
        db.sessionDao().getSessions(activityType)

    suspend fun getRecentSessions(limit: Int, activityType: String = ACTIVITY_RUNNING): List<SessionEntity> =
        db.sessionDao().getRecentSessions(activityType, limit)

    fun observeSessions(activityType: String = ACTIVITY_RUNNING): Flow<List<SessionEntity>> =
        db.sessionDao().observeSessions(activityType)

    suspend fun logSession(session: SessionEntity) {
        db.sessionDao().insert(session)
    }
}
