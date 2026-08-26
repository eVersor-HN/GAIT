package dev.eversorhn.momentum.wear

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem

/**
 * The one message the phone sends the watch. Kept as a flat DataMap on a single path so the
 * newest one always replaces the last: the watch never has to reconcile a queue.
 *
 * The same keys are written on the phone side (dev.eversorhn.momentum.wear.WearPublisher).
 */
object WearSync {
    const val PATH = "/momentum/standing"

    const val KEY_LIVE = "live"
    const val KEY_OPPONENT = "opponent"
    const val KEY_GAP = "gap"
    const val KEY_GAP_AHEAD = "gap_ahead"
    const val KEY_DISTANCE = "distance"
    const val KEY_PACE = "pace"
    const val KEY_HEART = "heart"
    const val KEY_RANK = "rank"
    const val KEY_STANDING = "standing"
    const val KEY_SENT_AT = "sent_at"

    /** Anything older than this is shown as out of reach rather than as fact. */
    private const val STALE_AFTER_MILLIS = 10 * 60 * 1000L

    fun decode(item: DataMapItem): WearState = decode(item.dataMap)

    fun decode(map: DataMap): WearState {
        val sentAt = map.getLong(KEY_SENT_AT, 0L)
        return WearState(
            live = map.getBoolean(KEY_LIVE, false),
            opponent = map.getString(KEY_OPPONENT, ""),
            gapLabel = map.getString(KEY_GAP, ""),
            gapAhead = map.getBoolean(KEY_GAP_AHEAD, true),
            distanceLabel = map.getString(KEY_DISTANCE, ""),
            paceLabel = map.getString(KEY_PACE, ""),
            heartRate = map.getInt(KEY_HEART, 0).takeIf { it > 0 },
            rank = map.getString(KEY_RANK, ""),
            standing = map.getString(KEY_STANDING, ""),
            stale = sentAt > 0 && System.currentTimeMillis() - sentAt > STALE_AFTER_MILLIS,
        )
    }
}
