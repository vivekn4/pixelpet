package com.vn.pixelpet.wallpaper

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.vn.pixelpet.Haptics
import com.vn.pixelpet.PetRenderer
import com.vn.pixelpet.PetRepository
import com.vn.pixelpet.PremiumManager
import com.vn.pixelpet.SfxEngine
import com.vn.pixelpet.Species
import com.vn.pixelpet.util.ScreenReceiver
import kotlin.math.sin
import kotlin.random.Random

/**
 * The lock-screen pet. A live wallpaper rendering Pip — on Samsung devices the
 * user can set it as the *lock-screen* wallpaper specifically, so the pet is
 * visible on every wake. (Android removed lock-screen widgets in 5.0; this is
 * the real mechanism — documented in PLAN.md §1.1.)
 *
 * - Tap the pet → petting (attention + hearts + purr).
 * - Double-tap → play (jump + giggle).
 * - Draw loop runs ~30fps only while visible AND screen on (battery).
 * - SCREEN_OFF/ON tracked via a dynamically-registered receiver (manifest
 *   registration does not work for these broadcasts).
 */
class PixelWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PetEngine()

    private data class Ripple(var x: Float, var y: Float, var r: Float, var life: Long)
    private data class Star(val x: Float, val y: Float, val s: Float, val tw: Float)

    inner class PetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var xOffset = 0f
        private var lastTapUp = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downT = 0L
        private val ripples = mutableListOf<Ripple>()
        private var stars: List<Star> = emptyList()
        private val screenReceiver = ScreenReceiver()

        private val loop = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible && ScreenReceiver.screenOn) handler.postDelayed(this, 33) // ~30fps
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            // Receiver updates a static flag the engine polls (service may outlive it).
            this@PixelWallpaperService.registerReceiver(screenReceiver, f)
        }

        override fun onDestroy() {
            try { this@PixelWallpaperService.unregisterReceiver(screenReceiver) } catch (t: Throwable) {}
            handler.removeCallbacks(loop)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(loop)
            if (visible && ScreenReceiver.screenOn) handler.post(loop)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(loop)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            this.xOffset = xOffset
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; downT = SystemClock.uptimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dt = SystemClock.uptimeMillis() - downT
                    val moved = kotlin.math.hypot(event.x - downX, event.y - downY)
                    if (dt < 350 && moved < 60 && inPet(event.x, event.y)) {
                        val now = SystemClock.uptimeMillis()
                        val repo = PetRepository.get(this@PixelWallpaperService)
                        if (now - lastTapUp < 350 &&
                            kotlin.math.hypot(event.x - lastTapX, event.y - lastTapY) < 90
                        ) {
                            // double-tap → play
                            val s = repo.play()
                            if (s != null) {
                                SfxEngine.play("play_giggle")
                                Haptics.bounce(this@PixelWallpaperService)
                                PixelPetWidgetProviderHelper.refresh(this@PixelWallpaperService)
                            }
                            lastTapUp = 0L
                        } else {
                            // tap → pet
                            repo.pet()
                            SfxEngine.play("pet_purr")
                            Haptics.tick(this@PixelWallpaperService)
                            ripples.add(Ripple(event.x, event.y, 10f, 400L))
                            PixelPetWidgetProviderHelper.refresh(this@PixelWallpaperService)
                            lastTapUp = now; lastTapX = event.x; lastTapY = event.y
                        }
                    }
                }
            }
            super.onTouchEvent(event)
        }

        private var petRect = RectF()

        private fun inPet(x: Float, y: Float): Boolean =
            petRect.contains(x, y)

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val repo = PetRepository.get(this@PixelWallpaperService)
            val state = repo.snapshot()
            val now = SystemClock.uptimeMillis()
            val nowWall = System.currentTimeMillis()
            val mood = PetRenderer.moodFor(state, now, nowWall)
            val premium = PremiumManager.isPremium(repo.prefsForPremium())
            val aura = premium && state.species.category == Species.Category.MYSTICAL

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()

                // background: deep gradient + parallax starfield
                val bg = Paint().apply { color = Color.parseColor("#0B0B18") }
                canvas.drawRect(0f, 0f, w, h, bg)
                if (stars.isEmpty() || stars.size < 60) {
                    stars = List(70) {
                        Star(Random.nextFloat() * w, Random.nextFloat() * h,
                            Random.nextFloat() * 3 + 1, Random.nextFloat() * 6)
                    }
                }
                val starPaint = Paint().apply { isAntiAlias = false }
                val t = now / 1000f
                for (s in stars) {
                    val tw = (0.4f + 0.6f * kotlin.math.abs(sin(t * s.tw + s.x))).toInt()
                    starPaint.color = Color.argb(60 + tw * 40, 185, 185, 232)
                    val sx = ((s.x - xOffset * 60) % w + w) % w // parallax
                    canvas.drawRect(sx, s.y, sx + s.s, s.y + s.s, starPaint)
                }

                // pet
                val cellPx = 16
                val pet = PetRenderer.render(
                    state.species, mood, cellPx,
                    blink = (now / 3000) % 5 < 0.13f,
                    aura = aura, wavePhase = t * 2.2f
                )
                val targetW = w * 0.52f * state.stage.scale
                val sc = targetW / pet.width
                val dw = pet.width * sc; val dh = pet.height * sc
                val dx = (w - dw) / 2f
                val dy = (h - dh) / 2f + sin(t * 2.4f) * 10f
                petRect.set(dx - 30, dy - 30, dx + dw + 30, dy + dh + 30)
                PetRenderer.drawScaled(canvas, pet, RectF(dx, dy, dx + dw, dy + dh))
                pet.recycle()

                // attention bubble
                if (state.attention < 25 && !state.sleeping) {
                    PetRenderer.drawAttentionBubble(canvas, dx + dw / 2f, dy - 70f, sc * 2.4f, t * 4f)
                }

                // touch ripples
                val rp = Paint().apply {
                    isAntiAlias = true; color = Color.parseColor("#FF9DC6")
                    style = Paint.Style.STROKE; strokeWidth = 5f
                }
                val it = ripples.iterator()
                while (it.hasNext()) {
                    val r = it.next()
                    r.life -= 33; r.r += 9
                    if (r.life <= 0) { it.remove(); continue }
                    rp.alpha = (r.life / 400f * 200).toInt().coerceIn(0, 200)
                    canvas.drawCircle(r.x, r.y, r.r, rp)
                }
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (t: Throwable) {}
                }
            }
        }
    }

    /** Tiny indirection so the wallpaper module doesn't import the widget package. */
    private object PixelPetWidgetProviderHelper {
        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent("com.vn.pixelpet.ACTION_REFRESH_WIDGET")
                    .setPackage(context.packageName)
            )
        }
    }
}
