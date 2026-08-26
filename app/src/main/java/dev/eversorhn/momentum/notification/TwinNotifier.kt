package dev.eversorhn.momentum.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.eversorhn.momentum.MainActivity
import dev.eversorhn.momentum.R

/**
 * Everything that reaches the notification shade. Nothing here is anyone speaking — every
 * notification is a figure you would otherwise have to unlock the phone to read. Two channels,
 * so the user can tune each in Android's own settings:
 *  - **Division** (low importance): the daily close — where you and your opponent stand, how
 *    the ground between you moved, cull and review deadlines. Quiet by design.
 *  - **Tracking** (low): the live session card, readable on the lock screen.
 * Every notification opens the app on tap. Muting (NotificationPrefs) silences the division
 * channel at the source; the tracking card is mandatory for a foreground service and stays.
 */
object TwinNotifier {

    private const val CHANNEL_DIVISION = "division_notices"
    private const val CHANNEL_OPPONENT_SESSION = "opponent_session"
    private const val TRACKING_CHANNEL_ID = "tracking_status"
    private const val GROUP_DIVISION = "momentum.division"
    private var notificationId = 1000

    enum class Kind(val subtext: String) {
        REACTION("Same-day reaction"), STAKE("Stake on today's forecast"), IDLE("Unprompted"), GAP("You went quiet"),
        COMMENDATION("Commendation"), NOTICE("Division notice"),
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_OPPONENT_SESSION) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_OPPONENT_SESSION, "Opponent sessions", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The live card while your opponent is training. Silent; it only sits in the shade."
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_DIVISION) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DIVISION, "Division notices", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The Asset Performance Division: commendations, review and cull notices. Quiet."
                }
            )
        }
        if (manager.getNotificationChannel(TRACKING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(TRACKING_CHANNEL_ID, "Tracking status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The ongoing notification shown while MOMENTUM is recording a session."
                }
            )
        }
    }

    /** The foreground service's persistent notification lives on its own low-importance channel. */
    fun trackingChannelId(context: Context): String {
        ensureChannel(context)
        return TRACKING_CHANNEL_ID
    }

    /** Tap target for every notification: bring the app to the front (or launch it). */
    fun openAppIntent(context: Context): PendingIntent {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun allowed(context: Context): Boolean {
        if (NotificationPrefs.isMuted(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }


    /**
     * The opponent's session as it happens — the way a training partner's live share reads:
     * who, how far, how fast, how much is done, and a countdown the system ticks itself so the
     * card stays live without the app doing anything.
     *
     * [stableId] keeps one card per enrolment, replaced on every update rather than stacked.
     */
    fun postOpponentSession(
        context: Context,
        stableId: Int,
        who: String,
        title: String,
        body: String,
        progressPercent: Int,
        endsAtEpochMillis: Long,
    ) {
        ensureChannel(context)
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_OPPONENT_SESSION)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(who)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            // The system counts this down once a second on its own — live, at no cost.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAtEpochMillis)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .build()
        NotificationManagerCompat.from(context).notify(stableId, n)
    }

    /** What it ended up doing. Replaces the live card and can be swiped away. */
    fun postOpponentSessionDone(context: Context, stableId: Int, who: String, title: String, body: String) {
        ensureChannel(context)
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_OPPONENT_SESSION)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(who)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(stableId, n)
    }

    fun cancelOpponentSession(context: Context, stableId: Int) {
        NotificationManagerCompat.from(context).cancel(stableId)
    }

    /** The company speaking: commendations, cull/review notices. Low importance, grouped. */
    fun postDivisionNotice(context: Context, title: String, body: String, kind: Kind = Kind.NOTICE, stableId: Int? = null) {
        ensureChannel(context)
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_DIVISION)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("Asset Performance Division · ${kind.subtext}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .setGroup(GROUP_DIVISION)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(stableId ?: notificationId++, n)
        postSummary(context, CHANNEL_DIVISION, GROUP_DIVISION, 2, "Asset Performance Division")
    }

    private fun postSummary(context: Context, channel: String, group: String, id: Int, title: String) {
        val summary = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(group)
            .setGroupSummary(true)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, summary)
    }
}
