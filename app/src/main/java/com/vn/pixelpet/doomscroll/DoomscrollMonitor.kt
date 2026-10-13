package com.vn.pixelpet.doomscroll

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.vn.pixelpet.Goal
import com.vn.pixelpet.PetRepository
import java.util.Calendar

/**
 * Optional doomscroll auto-detect (v2 behavior, PLAN.md §3).
 *
 * Uses Android's on-device UsageStats API to notice heavy short-video app
 * use (TikTok / Instagram / YouTube) and auto-logs once per day. Off by
 * default; the user enables it from the app, which routes here through
 * [hasPermission] / [requestPermission]. Nothing ever leaves the phone —
 * there is no network call anywhere in this file.
 *
 * DOCUMENTED APPROXIMATION: YouTube is counted wholesale — UsageStats
 * cannot distinguish Shorts from long-form watching, so long-form YouTube
 * time also counts toward the threshold. TikTok and Instagram are
 * short-video by nature, so the approximation only bites on YouTube. The
 * package list is deliberately unchanged: dropping YouTube would be a
 * product-behavior change, not a bug fix.
 *
 * [maybeAutoLog] returns the minutes of short-video use logged, or null when
 * nothing was logged (disabled, already logged today, below threshold, or
 * permission missing/revoked).
 */
object DoomscrollMonitor {

    /**
     * Short-video packages counted toward the daily threshold.
     * NOTE: YouTube is counted wholesale (see KDoc above) — UsageStats
     * cannot split Shorts from long-form, so long-form YouTube also counts.
     */
    private val SHORT_VIDEO_PKGS = setOf(
        "com.zhiliaoapp.musically", // TikTok (global)
        "com.ss.android.ugc.trending", // TikTok (alt)
        "com.instagram.android",
        "com.google.android.youtube"
    )

    /** Minutes of short-video use in a day before one auto-log fires. */
    private const val THRESHOLD_MINUTES = 60

    fun hasPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    fun requestPermission(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (t: Throwable) {
            // No usage-access settings screen on this device — stay silent.
        }
    }

    fun maybeAutoLog(context: Context, repo: PetRepository): Int? {
        if (!repo.autoDetectEnabled) return null
        if (!hasPermission(context)) return null
        val today = Goal.todayString()
        if (repo.lastAutoLogDate == today) return null // once per day
        val minutes = shortVideoMinutesToday(context) ?: return null
        if (minutes < THRESHOLD_MINUTES) return null
        repo.logDoomscroll()
        repo.lastAutoLogDate = today
        return minutes
    }

    /** Total foreground minutes on short-video apps since midnight, or null on error. */
    private fun shortVideoMinutesToday(context: Context): Int? {
        return try {
            val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = usage.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                cal.timeInMillis,
                System.currentTimeMillis()
            ) ?: return null
            var totalMs = 0L
            for (s in stats) {
                if (s.packageName in SHORT_VIDEO_PKGS) totalMs += s.totalTimeInForeground
            }
            (totalMs / 60000L).toInt()
        } catch (t: Throwable) {
            null // permission revoked or UsageStats unavailable — never crash
        }
    }
}
