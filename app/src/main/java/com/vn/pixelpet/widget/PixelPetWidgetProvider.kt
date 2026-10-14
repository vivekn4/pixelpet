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
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.LruCache
import android.widget.RemoteViews
import com.vn.pixelpet.MainActivity
import com.vn.pixelpet.PetRenderer
import com.vn.pixelpet.PetRepository
import com.vn.pixelpet.PetState
import com.vn.pixelpet.PremiumManager
import com.vn.pixelpet.R
import com.vn.pixelpet.Species

/**
 * Home-screen widget: glanceable Pip. The whole face is rendered as ONE bitmap
 * (RemoteViews can't host custom views) — pet from [PetRenderer], mini stat
 * bars and mood label drawn around it.
 *
 * Refresh triggers: [WidgetUpdateJob] every ~30 min, [ACTION_REFRESH_WIDGET]
 * broadcasts from the app after every state change, plus BOOT_COMPLETED
 * rescheduling. Updates run off the main thread via goAsync.
 */
class PixelPetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        Thread {
            try {
                for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                val pending = goAsync()
                Thread {
                    try {
                        refreshAllWidgets(context)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ACTION_QUICK_FEED -> {
                val pending = goAsync()
                Thread {
                    try {
                        // Snack: 15-min cooldown enforced by the repo.
                        PetRepository.get(context).snack()
                        refreshAllWidgets(context)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.vn.pixelpet.ACTION_REFRESH_WIDGET"
        const val ACTION_QUICK_FEED = "com.vn.pixelpet.ACTION_QUICK_FEED"
        private const val FACE_PX = 480

        // mood + stat buckets (tens) -> bitmap; caps variants while staying glanceable
        private val faceCache = object : LruCache<String, Bitmap>(12) {
            override fun sizeOf(key: String, value: Bitmap): Int = 1
        }

        fun refreshAll(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH_WIDGET).setPackage(context.packageName)
            )
        }

        fun refreshAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, PixelPetWidgetProvider::class.java)
            )
            for (id in ids) updateWidget(context, mgr, id)
        }

        /** First-launch widget pin prompt (API 26+). Once per install. */
        fun requestPinIfNeeded(activity: android.app.Activity) {
            val prefs = activity.getSharedPreferences("pixelpet", Context.MODE_PRIVATE)
            if (prefs.getBoolean("widget_prompted", false)) return
            prefs.edit().putBoolean("widget_prompted", true).apply()
            val mgr = activity.getSystemService(AppWidgetManager::class.java) ?: return
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(
                    ComponentName(activity, PixelPetWidgetProvider::class.java), null, null
                )
            }
        }

        private fun updateWidget(
            context: Context,
            mgr: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = PetRepository.get(context)
            val state = repo.snapshot()
            val now = SystemClock.elapsedRealtime()
            val mood = PetRenderer.moodFor(state, now, System.currentTimeMillis())
            val premium = PremiumManager.isPremium(repo.prefsForPremium())
            val aura = premium && state.species.category == Species.Category.MYSTICAL
            val key = "${state.speciesId}-$mood-${state.hunger / 10}-${state.happiness / 10}-" +
                "${state.energy / 10}-${state.attention / 10}-$aura-${state.stage}"
            var face = faceCache.get(key)
            if (face == null || face.isRecycled) {
                face = renderFace(context, state, mood, aura)
                faceCache.put(key, face)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_pixel_pet)
            views.setImageViewBitmap(R.id.widget_face, face)

            val openApp = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_face, pi)

            val feed = Intent(ACTION_QUICK_FEED).setPackage(context.packageName)
            val feedPi = PendingIntent.getBroadcast(
                context,
                10_000 + appWidgetId,
                feed,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_feed, feedPi)
            val cooling = now - state.lastSnackElapsed < 15 * 60 * 1000L
            views.setTextViewText(
                R.id.widget_feed,
                if (cooling) context.getString(R.string.widget_feed_cooldown)
                else context.getString(R.string.widget_feed)
            )

            try {
                mgr.updateAppWidget(appWidgetId, views)
            } catch (t: Throwable) {
                // stale widget id — ignore
            }
        }

        private fun renderFace(
            context: Context,
            state: PetState,
            mood: PetRenderer.Mood,
            aura: Boolean
        ): Bitmap {
            val s = FACE_PX.toFloat()
            val bmp = Bitmap.createBitmap(FACE_PX, FACE_PX, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            // card
            val card = Paint().apply {
                isAntiAlias = true; color = Color.parseColor("#171730")
            }
            c.drawRoundRect(RectF(8f, 8f, s - 8f, s - 8f), 36f, 36f, card)
            val edge = Paint().apply {
                isAntiAlias = true; color = Color.parseColor("#2A2A55")
                style = Paint.Style.STROKE; strokeWidth = 4f
            }
            c.drawRoundRect(RectF(8f, 8f, s - 8f, s - 8f), 36f, 36f, edge)

            // pet (scaled by growth stage: babies render smaller)
            val pet = PetRenderer.render(state.species, mood, 12, aura = aura)
            val petSize = 240f * state.stage.scale
            PetRenderer.drawScaled(
                c, pet,
                RectF((s - petSize) / 2f, 36f, (s + petSize) / 2f, 36f + petSize)
            )
            pet.recycle()

            // attention bubble
            if (state.attention < 25 && !state.sleeping) {
                PetRenderer.drawAttentionBubble(c, s / 2f, 300f, 9f, 1.2f)
            }

            // name + mood label
            val label = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#FFD54A")
                textSize = 28f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }
            val moodText = when (mood) {
                PetRenderer.Mood.HAPPY -> context.getString(R.string.mood_happy)
                PetRenderer.Mood.CONTENT -> context.getString(R.string.mood_content)
                PetRenderer.Mood.HUNGRY -> context.getString(R.string.mood_hungry)
                PetRenderer.Mood.SAD -> context.getString(R.string.mood_sad)
                PetRenderer.Mood.SULKING -> context.getString(R.string.mood_sulking)
                PetRenderer.Mood.SLEEPING -> context.getString(R.string.mood_sleeping)
                PetRenderer.Mood.SICK -> context.getString(R.string.mood_sick)
            }
            c.drawText("${state.species.displayName} · Lv ${state.level}: $moodText", s / 2f, 322f, label)

            // 4 mini stat bars
            drawMiniBar(c, 344f, state.hunger, "#FFD54A")
            drawMiniBar(c, 376f, state.happiness, "#9D7BFF")
            drawMiniBar(c, 408f, state.energy, "#5AFF8A")
            drawMiniBar(c, 440f, state.attention, "#FF9DC6")
            return bmp
        }

        private fun drawMiniBar(c: Canvas, y: Float, value: Int, colorHex: String) {
            val s = FACE_PX.toFloat()
            val left = 60f; val right = s - 60f; val h = 16f
            val bg = Paint().apply { isAntiAlias = true; color = Color.parseColor("#26264A") }
            val fg = Paint().apply { isAntiAlias = true; color = Color.parseColor(colorHex) }
            c.drawRoundRect(RectF(left, y, right, y + h), 8f, 8f, bg)
            val w = (right - left) * (value.coerceIn(0, 100) / 100f)
            if (w > 1f) c.drawRoundRect(RectF(left, y, left + w, y + h), 8f, 8f, fg)
        }
    }
}
