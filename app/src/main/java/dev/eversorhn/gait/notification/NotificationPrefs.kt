package dev.eversorhn.gait.notification

import android.content.Context

/**
 * One switch: does the opponent get to reach outside the app? Set from the exit dialog
 * ("close and mute") and from Settings. Messages are still recorded either way — muting
 * silences the channel, not the opponent.
 */
object NotificationPrefs {
    private const val PREFS = "gait_notification_prefs"
    private const val KEY_MUTED = "muted"

    fun isMuted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MUTED, false)

    fun setMuted(context: Context, muted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MUTED, muted).apply()
    }
}
