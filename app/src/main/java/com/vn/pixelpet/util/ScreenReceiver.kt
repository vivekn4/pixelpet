package com.vn.pixelpet.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vn.pixelpet.PetRepository

/**
 * Tracks screen on/off for the sleep/wake cycle.
 *
 * MUST be registered dynamically (e.g. from MainActivity and
 * PixelWallpaperService) — ACTION_SCREEN_OFF / ACTION_SCREEN_ON are not
 * delivered to manifest-registered receivers. Sleep/wake tracking therefore
 * works while the wallpaper engine or the app process is alive.
 */
class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = PetRepository.get(context)
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                screenOn = false
                repo.enterSleep()
            }
            Intent.ACTION_SCREEN_ON -> {
                screenOn = true
                // Wake now so the recap is ready for the next UI surface.
                val recap = repo.wake()
                if (recap.isNotEmpty()) repo.lastRecap = recap
            }
        }
    }

    companion object {
        /** Polled by the wallpaper engine's draw loop. */
        @Volatile var screenOn: Boolean = true
    }
}
