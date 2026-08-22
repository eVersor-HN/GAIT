package dev.eversorhn.gait.notification

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
import dev.eversorhn.gait.MainActivity
import dev.eversorhn.gait.R

/**
 * Everything that reaches the notification shade. Three channels, so the user can tune each
 * in Android's own settings:
 *  - **Opponent** (default importance): same-day Predatory lines, stakes, the occasional
 *    unprompted jab. Title = who's talking, text = the line, subtext = what kind of message.
 *  - **Division** (low importance): commendations and cull/review notices — the company, not
 *    the rival. Quiet by design.
 *  - **Tracking** (low): the ongoing recording notification.
 * Every notification opens the app on tap. Muting (NotificationPrefs) silences the opponent
 * and division channels at the source; the tracking notification is mandatory for a
 * foreground service and stays.
 */
object TwinNotifier {

    private const val CHANNEL_OPPONENT = "twin_messages"
    private const val CHANNEL_DIVISION = "division_notices"
    private const val TRACKING_CHANNEL_ID = "tracking_status"
    private const val GROUP_OPPONENT = "gait.opponent"
    private const val GROUP_DIVISION = "gait.division"
    private var notificationId = 1000

    enum class Kind(val subtext: String) {
        REACTION("Same-day reaction"), STAKE("Stake on today's forecast"), IDLE("Unprompted"), GAP("You went quiet"),
        COMMENDATION("Commendation"), NOTICE("Division notice"),
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_OPPONENT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_OPPONENT, "Opponent messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your Twin or the Horde: same-day reactions, stakes on today's forecast, the occasional unprompted jab."
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
                    description = "The ongoing notification shown while GAIT is recording a session."
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

    /** The opponent speaking. [kind] becomes the subtext so a glance says what this is. */
    fun postTwinMessage(context: Context, twinName: String, body: String, kind: Kind = Kind.REACTION) {
        ensureChannel(context)
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_OPPONENT)
            .setContentTitle(twinName)
            .setContentText(body)
            .setSubText(kind.subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setGroup(GROUP_OPPONENT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId++, n)
        postSummary(context, CHANNEL_OPPONENT, GROUP_OPPONENT, 1, twinName)
    }

    /** The company speaking: commendations, cull/review notices. Low importance, grouped. */
    fun postDivisionNotice(context: Context, title: String, body: String, kind: Kind = Kind.NOTICE) {
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
        NotificationManagerCompat.from(context).notify(notificationId++, n)
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
