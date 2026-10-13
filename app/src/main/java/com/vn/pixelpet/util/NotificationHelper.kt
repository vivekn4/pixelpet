package com.vn.pixelpet.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Minimal notification helper, framework APIs only (zero-dependency design).
 * Used solely for the "needs attention" nudge — rate-limited to one per
 * 4 hours and auto-cancelled when attention recovers.
 */
object NotificationHelper {

    const val CHANNEL_ID = "pixelpet_attention"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PixelPet nudges",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Gentle nudges when Pip needs attention." }
        )
    }

    fun notifyAttentionNeeded(context: Context, petName: String) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notif = android.app.Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("$petName needs attention!")
            .setContentText("Pet Pip for a moment — attention is running low.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(2001, notif)
        } catch (t: Throwable) {
            // notifications must never crash the app (e.g. permission revoked)
        }
    }

    fun cancelAttention(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(2001)
        } catch (t: Throwable) {}
    }
}
