package com.vn.pixelpet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.vn.pixelpet.AuraSkin
import com.vn.pixelpet.MainActivity
import com.vn.pixelpet.PetRenderer
import com.vn.pixelpet.PetRepository
import com.vn.pixelpet.R
import com.vn.pixelpet.VariantRoster
import kotlin.math.sin

/**
 * Home-screen widget (v3 Phase 4, PLAN_V3.md §10, bug #31).
 *
 * RemoteViews still cannot host a live view, so the widget stays a SNAPSHOT
 * bitmap — but the snapshot now renders the pet's CURRENT walk frame
 * (walk phase + stride bob derived from elapsedRealtime, sleeping pets
 * stand still) instead of a static pose. It re-renders on every stat tick
 * (via [refreshAll], called from the app after any care action) and on tap
 * (quick-feed).
 *
 * The face is one bitmap: pet + 4 mini stat bars + name label, drawn by
 * [renderFace]. The feed button sends [ACTION_QUICK_FEED]; tapping the face
 * opens the app.
 */
class PixelPetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_QUICK_FEED -> {
                val repo = PetRepository.get(context)
                if (repo.snack() == null) {
                    Toast.makeText(context, R.string.widget_feed_cooldown, Toast.LENGTH_SHORT).show()
                }
                refreshAll(context)
                return
            }
            ACTION_REFRESH_WIDGET -> {
                refreshAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_QUICK_FEED = "com.vn.pixelpet.ACTION_QUICK_FEED"
        const val ACTION_REFRESH_WIDGET = "com.vn.pixelpet.ACTION_REFRESH_WIDGET"
        private const val PREFS_WIDGET = "pixelpet_widget"
        private const val KEY_PIN_OFFERED = "pin_offered"

        // ---- hardening: hoisted paints / colors (no per-refresh allocation) ----
        // Widget updates run on the main thread, so sharing these is safe.
        private val barPaint = Paint().apply { isAntiAlias = false }
        private val barBgPaint = Paint().apply { isAntiAlias = false; color = Color.parseColor("#2A2A55") }
        private val textPaint = Paint().apply {
            isAntiAlias = false
            color = Color.parseColor("#EDEDF7")
            textAlign = Paint.Align.CENTER
        }
        private val faceBgColor = Color.parseColor("#0B0B18")
        private val statColors = intArrayOf(
            Color.parseColor("#FFD54A"),
            Color.parseColor("#FF9DC6"),
            Color.parseColor("#6AC8FF"),
            Color.parseColor("#5AFF8A")
        )

        /**
         * Face bitmap size, scaled by screen density so the widget stays crisp
         * on high-dpi screens (the old fixed 288px blurred on xxhdpi+).
         * Capped at 864px: an 864x864 ARGB bitmap is ~3MB transient, fine
         * for a 30-min job / stat tick, and big enough for any widget size.
         */
        private fun facePx(context: Context): Int =
            (288f * context.resources.displayMetrics.density).toInt().coerceIn(288, 864)

        /** Re-render every widget instance (called after any stat change). */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, PixelPetWidgetProvider::class.java)
            )
            val provider = PixelPetWidgetProvider()
            for (id in ids) provider.updateOne(context, mgr, id)
        }

        /** First-launch pin prompt (v2 behavior). Offered once, never nagged. */
        fun requestPinIfNeeded(activity: android.app.Activity) {
            try {
                val prefs = activity.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_PIN_OFFERED, false)) return
                prefs.edit().putBoolean(KEY_PIN_OFFERED, true).apply()
                val mgr = AppWidgetManager.getInstance(activity)
                if (!mgr.isRequestPinAppWidgetSupported) return
                val success = PendingIntent.getBroadcast(
                    activity, 0,
                    Intent(activity, PixelPetWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH_WIDGET
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                mgr.requestPinAppWidget(
                    ComponentName(activity, PixelPetWidgetProvider::class.java),
                    null, success
                )
            } catch (t: Throwable) {
                // Pin prompt is a nicety — never break launch for it.
            }
        }

        /**
         * Current walk frame, derived from the clock so every snapshot shows
         * the pet mid-stride: phase alternates 1/2 every [STRIDE_MS], with a
         * small horizontal bob. Sleeping pets (or a 0 phase) stand still.
         */
        private fun currentWalkFrame(sleeping: Boolean): Pair<Int, Float> {
            if (sleeping) return 0 to 0f
            val now = SystemClock.elapsedRealtime()
            val phase = if ((now / STRIDE_MS) % 2L == 0L) 1 else 2
            val bob = sin(now / STRIDE_MS.toFloat() * Math.PI.toFloat()).toFloat() * 6f
            return phase to bob
        }

        private const val STRIDE_MS = 350L
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_pixel_pet)
            views.setImageViewBitmap(R.id.widget_face, renderFace(context))
            views.setOnClickPendingIntent(
                R.id.widget_face,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_feed,
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, PixelPetWidgetProvider::class.java).apply {
                        action = ACTION_QUICK_FEED
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            mgr.updateAppWidget(id, views)
        } catch (t: Throwable) {
            // A widget must never crash the launcher.
        }
    }

    /**
     * Render the widget face: pet (current walk frame) + 4 mini stat bars +
     * name label, into one bitmap. Honest snapshot — not a live view.
     */
    private fun renderFace(context: Context): Bitmap {
        val repo = PetRepository.get(context)
        val state = repo.snapshot()
        val variant = VariantRoster.variantForSpeciesId(state.speciesId)
        val mood = PetRenderer.moodFor(
            state, SystemClock.elapsedRealtime(), System.currentTimeMillis()
        )
        val auraSkin = AuraSkin.effective(repo.prefsForPremium())
        val (walkPhase, bob) = currentWalkFrame(state.sleeping)
        val pet = PetRenderer.renderVariant(
            variant, mood, cellPx = 9,
            blink = false,
            aura = auraSkin != null,
            auraStyle = auraSkin ?: AuraSkin.GOLD_ORBIT,
            wavePhase = 0f,
            walkPhase = walkPhase,
            legs = true
        )
        val facePx = facePx(context)
        val k = facePx / 288f // scale bars / text with the face so proportions hold
        val face = Bitmap.createBitmap(facePx, facePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(face)
        c.drawColor(faceBgColor)
        val petSize = facePx * 0.62f
        val px = (facePx - petSize) / 2f + bob
        val py = facePx * 0.06f
        PetRenderer.drawScaled(
            c, pet,
            android.graphics.RectF(px, py, px + petSize, py + petSize)
        )
        pet.recycle()
        // 4 mini stat bars: hunger / happiness / energy / attention.
        val stats = listOf(
            state.hunger,
            state.happiness,
            state.energy,
            state.attention
        )
        val barW = facePx * 0.72f
        val barH = 8f * k
        var by = facePx * 0.74f
        for ((index, value) in stats.withIndex()) {
            c.drawRect((facePx - barW) / 2f, by, (facePx + barW) / 2f, by + barH, barBgPaint)
            barPaint.color = statColors[index]
            c.drawRect(
                (facePx - barW) / 2f, by,
                (facePx - barW) / 2f + barW * (value.coerceIn(0, 100) / 100f), by + barH,
                barPaint
            )
            by += barH + 5f * k
        }
        // Name label.
        textPaint.textSize = 22f * k
        c.drawText(variant.displayName, facePx / 2f, facePx - 10f * k, textPaint)
        return face
    }
}
