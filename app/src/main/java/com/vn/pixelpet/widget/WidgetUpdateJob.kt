package com.vn.pixelpet.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.vn.pixelpet.util.NudgeEngine

/**
 * Periodic widget refresh (~30 min, persisted across reboots).
 * Timing is intentionally inexact — Doze may delay it, which is fine for a
 * pet game: every app open and widget tap also recomputes state.
 *
 * The same tick also runs the nudge engine: the background job is the only
 * reliable place to check "has the pet been neglected while the app is
 * closed", which is exactly when a nudge is worth sending.
 */
class WidgetUpdateJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                PixelPetWidgetProvider.refreshAllWidgets(this@WidgetUpdateJob)
                NudgeEngine.maybeNudge(this@WidgetUpdateJob)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true // work continues on our thread
    }

    override fun onStopJob(params: JobParameters): Boolean = true // reschedule

    companion object {
        private const val JOB_ID = 1001
        private const val INTERVAL_MS = 30 * 60 * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(context, WidgetUpdateJob::class.java)
            val info = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build()
            scheduler.schedule(info)
        }
    }
}
