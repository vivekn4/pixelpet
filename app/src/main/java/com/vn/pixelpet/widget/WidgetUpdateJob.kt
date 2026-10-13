package com.vn.pixelpet.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Periodic widget refresh (v3 Phase 4). The widget is a snapshot bitmap, so
 * something has to re-render it when the app is not in the foreground: this
 * job fires every 30 minutes and calls [PixelPetWidgetProvider.refreshAll].
 * Re-armed after reboot by BootReceiver. Best-effort: the job is not a
 * substitute for the stat-tick refreshes the app sends while it runs.
 */
class WidgetUpdateJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            PixelPetWidgetProvider.refreshAll(applicationContext)
        } catch (t: Throwable) {
            // Widget refresh must never crash the job.
        }
        return false // work is done synchronously
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 1001
        private const val INTERVAL_MS = 30L * 60L * 1000L // 30 minutes

        /** (Re)schedule the periodic widget refresh. Idempotent. */
        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val job = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(context, WidgetUpdateJob::class.java)
                )
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(job)
            } catch (t: Throwable) {
                // Scheduling is best-effort; the app still refreshes on stat ticks.
            }
        }
    }
}
