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

/**
 * Posts opponent messages that break out of the app. Callers, per the design docs:
 * - the same-day Predatory/Swarming exception in docs/composure-system.md,
 * - the gap-predatory and idle-taunt pings in docs/notifications.md.
 * All go through one channel so the user has a single place to mute this if they want to.
 * Every notification opens the app on tap.
 */
object TwinNotifier {

    private const val CHANNEL_ID = "twin_messages"
    private const val TRACKING_CHANNEL_ID = "tracking_status"
    private var notificationId = 1000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Opponent messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Messages from your Twin or Horde — same-day reactions and the occasional unprompted jab."
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
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun postTwinMessage(context: Context, twinName: String, body: String) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(twinName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId++, notification)
    }
}
