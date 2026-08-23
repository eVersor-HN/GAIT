package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object MessageKind {
    /** Unprompted ambient jab (docs/notifications.md "Idle taunts"). */
    const val IDLE = "idle"
    /** Gap-predatory: you went quiet beyond your own rhythm. */
    const val GAP = "gap"
    /** The opponent staked points on today's forecast. */
    const val STAKE = "stake"
    /** The user called the stake; the opponent's reaction. */
    const val CALL = "call"
    /** A note from the Asset Performance Division — the company, not the opponent. */
    const val COMMENDATION = "commendation"
}

/**
 * Everything the opponent says *outside* a session Debrief (those lines live on the
 * session row). Idle taunts used to be fire-and-forget notifications; now they're also on
 * record, so the Direct Channel reads like a real inbox rather than a list of debriefs.
 */
@Entity(tableName = "twin_messages")
data class TwinMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0,
    val epochMillis: Long,
    /** One of [MessageKind]. */
    val kind: String,
    val line: String,
    /** ComposureState.name the line was spoken in, or null for neutral/ambient. */
    val composureState: String? = null,
)
