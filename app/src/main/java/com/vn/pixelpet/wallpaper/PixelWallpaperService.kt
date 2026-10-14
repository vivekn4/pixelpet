package com.vn.pixelpet.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.vn.pixelpet.AuraSkin
import com.vn.pixelpet.PetRenderer
import com.vn.pixelpet.PetRepository
import com.vn.pixelpet.PetState
import com.vn.pixelpet.VariantRoster
import com.vn.pixelpet.WalkEngine
import com.vn.pixelpet.util.ScreenReceiver
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lock-screen live wallpaper (v3 Phase 4, PLAN_V3.md §10).
 *
 * The pet ROAMS the wallpaper canvas on the existing [WalkEngine], bounded
 * to the canvas with a margin. The draw loop is ~30fps and is gated exactly
 * like v2: it runs only while the wallpaper is visible AND the screen is on.
 * Roam pauses after 30s with no interaction (the pet keeps its last pose;
 * the loop stops until the next touch or visibility change).
 *
 * Tap = pet, double-tap = play (the v2 wallpaper behavior, kept). Sleep/wake
 * bookkeeping goes through [ScreenReceiver], the same dynamic receiver the
 * app uses — ACTION_SCREEN_OFF / SCREEN_ON are not delivered to
 * manifest-registered receivers.
 *
 * Battery notes: the pet bitmap is re-rendered only when its render key
 * (variant, mood, walk phase, blink, aura skin, squash bucket) changes —
 * otherwise the cached bitmap is reused. Pet state is refreshed at most
 * twice a second and cached in [cachedState]; drawOnce() never calls
 * snapshot(), so the steady draw path does no per-frame storage writes
 * and no allocation beyond the one cached bitmap. AOD still cannot be drawn on by
 * third-party apps (v2 limitation, unchanged): when the screen is off the
 * loop is stopped and Pip sleeps.
 */
class PixelWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PetEngine()

    inner class PetEngine : Engine() {

        private val repo: PetRepository by lazy { PetRepository.get(applicationContext) }
        private val walk = WalkEngine()
        private val handler = Handler(Looper.getMainLooper())
        private val screenReceiver = ScreenReceiver()
        private val bgPaint = Paint().apply { isAntiAlias = false }
        private val starPaint = Paint().apply { isAntiAlias = false; color = Color.parseColor("#FFFFFF") }
        private val shadowPaint = Paint().apply { isAntiAlias = false; color = Color.argb(70, 0, 0, 0) }

        private var visible = false
        private var running = false
        private var lastFrameMs: Long = 0L
        private var lastStateMs: Long = 0L
        private var lastInteractionMs: Long = 0L
        private var lastBlinkToggleMs: Long = 0L
        private var blinking: Boolean = false
        private var canvasW: Int = 0
        private var canvasH: Int = 0
        private var petPx: Float = 0f
        private var stars: List<Pair<Float, Float>> = emptyList()

        // Cached pet bitmap + the components of the key it was rendered for.
        // Compared field-by-field: building the key String every frame was
        // one needless allocation per frame on the draw path.
        private var petBitmap: android.graphics.Bitmap? = null
        private var lastVariantId: String = ""
        private var lastMood: PetRenderer.Mood = PetRenderer.Mood.CONTENT
        private var lastWalkPhase: Int = -1
        private var lastBlinking: Boolean = false
        private var lastAuraSkin: AuraSkin? = null
        private var lastSquashBucket: Int = Int.MIN_VALUE
        private var forceRender: Boolean = true
        private var mood: PetRenderer.Mood = PetRenderer.Mood.CONTENT
        private var auraSkin: AuraSkin? = null
        // Pet state refreshed at most twice a second by drawFrame(); drawOnce()
        // reads this instead of calling repo.snapshot() per frame (snapshot()
        // persists, which would be ~30 storage writes/sec on the draw loop).
        private var cachedState: PetState? = null
        // Reused draw rects / colors: no per-frame RectF or parseColor work.
        private val dstRect = RectF()
        private val shadowRect = RectF()
        private val bgColor = Color.parseColor("#0B0B18")
        private val bandColor = Color.parseColor("#141428")
        private var variant: VariantRoster.Variant =
            VariantRoster.variantForSpeciesId("cat")

        private val gestures = GestureDetector(
            applicationContext,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    repo.pet()
                    walk.hop()
                    poke()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    repo.play() // null when tired — wallpaper stays quiet
                    walk.hop()
                    poke()
                    return true
                }
            }
        )

        /** Screen on/off: restart/stop the loop. Repo sleep/wake is ScreenReceiver. */
        private val screenToggle = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        lastInteractionMs = SystemClock.uptimeMillis()
                        startLoop()
                    }
                    Intent.ACTION_SCREEN_OFF -> stopLoop()
                }
            }
        }

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                drawFrame()
                if (running) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            lastInteractionMs = SystemClock.uptimeMillis()
            ScreenReceiver.register(applicationContext, screenReceiver)
            ScreenReceiver.register(applicationContext, screenToggle)
        }

        override fun onDestroy() {
            stopLoop()
            try { applicationContext.unregisterReceiver(screenReceiver) } catch (t: Throwable) {}
            try { applicationContext.unregisterReceiver(screenToggle) } catch (t: Throwable) {}
            petBitmap?.recycle()
            petBitmap = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastInteractionMs = SystemClock.uptimeMillis()
                startLoop()
            } else {
                stopLoop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            canvasW = width
            canvasH = height
            // Pet occupies ~42% of the narrow dimension; roam bounds follow.
            petPx = (minOf(width, height) * 0.42f).coerceAtLeast(120f)
            walk.setBounds(width.toFloat(), height.toFloat(), petPx, petPx, 16f)
            if (walk.x == 0f && walk.y == 0f) {
                walk.reset(width / 2f - petPx / 2f, height * 0.55f)
            }
            // Static starfield, seeded per size so it is stable across frames.
            val rng = Random(width * 31L + height)
            stars = List(70) { rng.nextFloat() to rng.nextFloat() }
            petBitmap?.recycle()
            petBitmap = null
            forceRender = true
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopLoop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) poke()
            gestures.onTouchEvent(event)
        }

        /** Any interaction resets the 30s idle timer and wakes the loop. */
        private fun poke() {
            lastInteractionMs = SystemClock.uptimeMillis()
            startLoop()
        }

        private fun startLoop() {
            if (running) return
            if (!visible || !ScreenReceiver.screenOn) return
            running = true
            lastFrameMs = SystemClock.uptimeMillis()
            handler.post(frameRunnable)
        }

        private fun stopLoop() {
            running = false
            handler.removeCallbacks(frameRunnable)
        }

        private fun drawFrame() {
            val now = SystemClock.uptimeMillis()
            // 30s idle in wallpaper: pause the roam and stop the loop.
            if (now - lastInteractionMs > IDLE_PAUSE_MS) {
                drawOnce(now, advance = false)
                stopLoop()
                return
            }
            // Advance the roam at real frame delta (WalkEngine clamps to 0.1s).
            val dt = ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
            lastFrameMs = now
            // Blink on a slow timer (no per-frame Random).
            if (now - lastBlinkToggleMs > 2400) {
                lastBlinkToggleMs = now
                blinking = !blinking
                if (blinking) lastBlinkToggleMs = now - 2270 // blink lasts ~130ms
            }
            walk.update(dt)
            // Refresh stats/mood at most twice a second — snapshot() ticks decay.
            if (now - lastStateMs > 500) {
                lastStateMs = now
                val state = repo.snapshot()
                cachedState = state
                variant = VariantRoster.variantForSpeciesId(state.speciesId)
                mood = PetRenderer.moodFor(state, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                auraSkin = AuraSkin.effective(repo.prefsForPremium())
                // Sleeping pets do not roam.
                walk.setOverlay(
                    if (mood == PetRenderer.Mood.SLEEPING) WalkEngine.Overlay.SLEEP
                    else WalkEngine.Overlay.NONE
                )
            }
            drawOnce(now, advance = true)
        }

        /** Render (only when the render key changed) and blit one frame. */
        private fun drawOnce(now: Long, advance: Boolean) {
            if (!advance && petBitmap == null) return // nothing to show yet
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                drawBackground(canvas)
                // Squash / crouch / hop shape the destination rect.
                val squash = 1f + walk.squashStretch * 0.12f
                val crouchK = 1f - walk.crouch * 0.12f
                val sy = squash * crouchK
                val liftPx = walk.jumpLift * petPx * 0.22f
                val dx = walk.x
                val dy = walk.y - liftPx + petPx * (1f - sy) / 2f
                dstRect.set(dx, dy, dx + petPx, dy + petPx * sy)
                val dst = dstRect
                val squashBucket = (walk.squashStretch * 4).toInt()
                if (forceRender || petBitmap == null ||
                    variant.id != lastVariantId || mood != lastMood ||
                    walk.walkPhase != lastWalkPhase || blinking != lastBlinking ||
                    auraSkin != lastAuraSkin || squashBucket != lastSquashBucket
                ) {
                    petBitmap?.recycle()
                    val cellPx = (petPx / 24f).toInt().coerceIn(6, 24)
                    petBitmap = PetRenderer.renderVariant(
                        variant, mood, cellPx,
                        blink = blinking,
                        aura = auraSkin != null,
                        auraStyle = auraSkin ?: AuraSkin.GOLD_ORBIT,
                        wavePhase = 0f, // fixed: the walk bob comes from WalkEngine
                        walkPhase = walk.walkPhase,
                        legs = true
                    )
                    lastVariantId = variant.id
                    lastMood = mood
                    lastWalkPhase = walk.walkPhase
                    lastBlinking = blinking
                    lastAuraSkin = auraSkin
                    lastSquashBucket = squashBucket
                    forceRender = false
                }
                val bmp = petBitmap ?: return
                // Soft ground shadow under the pet.
                val cx = dx + petPx / 2f
                val shadowW = petPx * 0.36f * (1f - walk.jumpLift * 0.35f)
                shadowRect.set(cx - shadowW, dy + petPx * 0.96f, cx + shadowW, dy + petPx * 1.04f)
                canvas.drawOval(shadowRect, shadowPaint)
                if (walk.facingRight) {
                    PetRenderer.drawScaled(canvas, bmp, dst)
                } else {
                    canvas.save()
                    canvas.scale(-1f, 1f, cx, dy + petPx / 2f)
                    PetRenderer.drawScaled(canvas, bmp, dst)
                    canvas.restore()
                }
                // Attention bubble when attention is low and the pet is awake.
                // Uses the cached 500ms state — never snapshot() per frame.
                val st = cachedState
                if (st != null && st.attention < 25 && !st.sleeping) {
                    PetRenderer.drawAttentionBubble(
                        canvas, cx, dy - petPx * 0.28f,
                        (petPx / 24f) * 2.2f, now / 250f
                    )
                }
            } catch (t: Throwable) {
                // A wallpaper must never crash the lock screen.
            } finally {
                try {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas)
                } catch (t: Throwable) {}
            }
        }

        private fun drawBackground(canvas: Canvas) {
            canvas.drawColor(bgColor)
            // Night-sky gradient band (two flat fills — no shader allocation).
            bgPaint.color = bandColor
            canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH * 0.45f, bgPaint)
            starPaint.alpha = 140
            for ((sx, sy) in stars) {
                val tw = 0.6f + 0.4f * sin((SystemClock.uptimeMillis() / 700f) + sx * 20f)
                canvas.drawRect(
                    sx * canvasW, sy * canvasH * 0.7f,
                    sx * canvasW + 2f * tw, sy * canvasH * 0.7f + 2f * tw,
                    starPaint
                )
            }
            starPaint.alpha = 255
        }

        companion object {
            /** ~30fps frame interval. */
            private const val FRAME_MS = 33L
            /** Roam pauses after this long with no interaction. */
            private const val IDLE_PAUSE_MS = 30_000L
        }
    }
}
