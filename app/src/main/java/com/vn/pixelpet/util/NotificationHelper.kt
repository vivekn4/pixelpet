package com.vn.pixelpet.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vn.pixelpet.MainActivity

/**
 * Notification helper, framework APIs only (zero-dependency design).
 *
 * Two channels: the normal nudge channel (DEFAULT importance) and a
 * high-importance channel reserved for critical nudges (sick pet, or any
 * stat at 5 or below). Nudge copy lives in NudgeEngine; this file only
 * owns delivery. All nudges share one notification id (2001) so a fresh
 * nudge replaces the previous one instead of stacking, and opening the app
 * dismisses whatever is showing via [cancelAttention].
 */
object NotificationHelper {

    const val CHANNEL_ID = "pixelpet_attention"
    const val CHANNEL_CRITICAL_ID = "pixelpet_critical"
    private const val NOTIF_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "PixelPet nudges",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Gentle nudges when Pip needs attention." }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_CRITICAL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CRITICAL_ID,
                    "PixelPet urgent nudges",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Urgent nudges when Pip is in trouble." }
            )
        }
    }

    /** General nudge delivery (see NudgeEngine for the send policy). */
    fun notifyNudge(context: Context, title: String, body: String, critical: Boolean) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = android.app.Notification.Builder(
            context,
            if (critical) CHANNEL_CRITICAL_ID else CHANNEL_ID
        )
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (t: Throwable) {
            // notifications must never crash the app (e.g. permission revoked)
        }
    }

    /** Back-compat: the old attention-only call, now routed through notifyNudge. */
    fun notifyAttentionNeeded(context: Context, petName: String) {
        notifyNudge(
            context,
            "$petName needs attention!",
            "Pet $petName for a moment — attention is running low.",
            critical = false
        )
    }

    fun cancelAttention(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        } catch (t: Throwable) {}
    }
}
