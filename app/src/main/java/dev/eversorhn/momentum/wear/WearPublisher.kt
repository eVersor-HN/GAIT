package dev.eversorhn.momentum.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.eversorhn.momentum.tracking.LiveFigures

/**
 * The phone's half of the watch link. One data item on one path, replaced every time: the watch
 * only ever renders the newest, and nothing on the wrist has to compute anything.
 *
 * Sending is best-effort — no watch paired simply means the call goes nowhere.
 */
object WearPublisher {

    private const val PATH = "/momentum/standing"

    private const val KEY_LIVE = "live"
    private const val KEY_OPPONENT = "opponent"
    private const val KEY_GAP = "gap"
    private const val KEY_GAP_AHEAD = "gap_ahead"
    private const val KEY_DISTANCE = "distance"
    private const val KEY_PACE = "pace"
    private const val KEY_HEART = "heart"
    private const val KEY_RANK = "rank"
    private const val KEY_STANDING = "standing"
    private const val KEY_SENT_AT = "sent_at"

    private var lastSentAt = 0L

    /** While a session runs: the gap, the ground and what it is costing. */
    fun publishLive(context: Context, f: LiveFigures, heartRate: Int?, minIntervalMillis: Long = 5_000L) {
        val now = System.currentTimeMillis()
        if (now - lastSentAt < minIntervalMillis) return
        lastSentAt = now

        val ahead: Boolean
        val gap: String
        if (f.isHorde) {
            val sep = f.separationMeters ?: 0
            ahead = sep >= 0
            gap = "${kotlin.math.abs(sep)} m"
        } else {
            val g = f.gapSeconds ?: 0
            ahead = g >= 0
            val a = kotlin.math.abs(g)
            gap = "${a / 60}:${(a % 60).toString().padStart(2, '0')}"
        }
        put(context) {
            putBoolean(KEY_LIVE, true)
            putString(KEY_OPPONENT, f.opponentName)
            putString(KEY_GAP, gap)
            putBoolean(KEY_GAP_AHEAD, ahead)
            putString(KEY_DISTANCE, "%.2f km".format(f.distanceMeters / 1000.0))
            putString(KEY_PACE, f.paceSecPerKm?.let { f.pace(it) } ?: "—")
            putInt(KEY_HEART, heartRate ?: 0)
            putLong(KEY_SENT_AT, now)
        }
    }

    /** Between sessions: where you stand on the board. */
    fun publishStanding(context: Context, opponentName: String, rank: String, standing: String) {
        val now = System.currentTimeMillis()
        lastSentAt = now
        put(context) {
            putBoolean(KEY_LIVE, false)
            putString(KEY_OPPONENT, opponentName)
            putString(KEY_RANK, rank)
            putString(KEY_STANDING, standing)
            putLong(KEY_SENT_AT, now)
        }
    }

    private inline fun put(context: Context, fill: com.google.android.gms.wearable.DataMap.() -> Unit) {
        runCatching {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.fill()
                setUrgent()
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest())
        }
    }
}
