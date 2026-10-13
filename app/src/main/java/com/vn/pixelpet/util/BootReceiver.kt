package com.vn.pixelpet.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vn.pixelpet.widget.WidgetUpdateJob

/** Re-arm the periodic widget refresh after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WidgetUpdateJob.schedule(context)
        }
    }
}
