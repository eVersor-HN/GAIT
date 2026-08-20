package dev.eversorhn.gait.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * Posts Twin messages that break out of the app. Two callers, per the design docs:
 * - the same-day Predatory exception in docs/composure-system.md (a specific weak session),
 * - the periodic, sparse "idle taunt" in docs/notifications.md (no session needed at all).
 * Both go through one channel so the user has a single place to mute this if they want to.
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
                NotificationChannel(CHANNEL_ID, "Twin messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Messages from your Twin — same-day reactions and the occasional unprompted jab."
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
            .setAutoCancel(true)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context)
            .notify(notificationId++, notification)
    }
}
