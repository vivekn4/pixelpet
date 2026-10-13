package com.vn.pixelpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * The star of the show: canvas-drawn Pip with idle bounce + blink, and
 * one-shot VFX routines (confetti, hearts, sulk shake, sleep Zs).
 *
 * The animation loop runs only while attached AND visible; all animators are
 * view-scoped so nothing leaks the Activity.
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var mood: PetRenderer.Mood = PetRenderer.Mood.CONTENT
    private var blinkUntil: Long = 0L
    private var nextBlink: Long = SystemClock.uptimeMillis() + 2500
    private var shakeUntil: Long = 0L
    private var sulkPulseUntil: Long = 0L
    private var jumpUntil: Long = 0L
    private var eatUntil: Long = 0L
    private var spinUntil: Long = 0L
    private var evolveUntil: Long = 0L
    private var flashUntil: Long = 0L
    // v3 Phase 3: power-effect overlays (all additive; each expires on its own)
    private var trailUntil: Long = 0L
    private var moonUntil: Long = 0L
    private var inkUntil: Long = 0L
    private var shieldRingUntil: Long = 0L
    private var ringUntil: Long = 0L
    private var ringDurationMs: Long = 600L
    private var ringColor: Int = Color.parseColor("#6AC8FF")
    private var sneakUntil: Long = 0L
    private var napUntil: Long = 0L
    private var diveUntil: Long = 0L
    private var flyUntil: Long = 0L
    private var bubbleUntil: Long = 0L
    private var lastBubbleSpawn: Long = 0L
    private val ringPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 5f }
    /** Set by the activity when attention < 25. */
    var showAttentionBubble: Boolean = false
    /** Current species for rendering; set by the activity. */
    var species: Species = Species.CAT
    /** Premium aura (v3: all variants, not just Mystical). */
    var aura: Boolean = false
    /** Premium aura skin (v3 Phase 4); only drawn when [aura] is true. */
    var auraStyle: AuraSkin = AuraSkin.GOLD_ORBIT
    private var wavePhase: Float = 0f

    private data class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Long, var maxLife: Long, var color: Int, var size: Float, var kind: Int
    )

    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply { isAntiAlias = false }
    private val textPaint = Paint().apply {
        isAntiAlias = false; color = Color.parseColor("#B9B9E8"); textSize = 42f
    }
    private val vignettePaint = Paint().apply { isAntiAlias = false }

    private var ticker: Runnable? = null
    private var lastFrame: Long = 0L

    private fun startTicker() {
        if (ticker != null) return
        lastFrame = SystemClock.uptimeMillis()
        val r = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                updateParticles((now - lastFrame).coerceAtMost(100))
                lastFrame = now
                // blink scheduler
                if (now >= nextBlink) {
                    blinkUntil = now + 130
                    nextBlink = now + 2600 + Random.nextLong(1800)
                }
                // v3 Phase 3: axolotl bubble parade — spawn while active
                if (now < bubbleUntil && now - lastBubbleSpawn > 200) {
                    lastBubbleSpawn = now
                    particles.add(
                        Particle(
                            width / 2f + Random.nextFloat() * 160 - 80, height * 0.75f,
                            Random.nextFloat() * 30 - 15, -Random.nextFloat() * 60 - 40,
                            2200, 2200, Color.parseColor("#B8E8FF"),
                            Random.nextFloat() * 8 + 8, KIND_BUBBLE
                        )
                    )
                }
                invalidate()
                ticker = this
                postOnAnimation(this)
            }
        }
        ticker = r
        postOnAnimation(r)
    }

    private fun stopTicker() {
        ticker?.let { removeCallbacks(it) }
        ticker = null
    }

    fun setMood(m: PetRenderer.Mood) {
        mood = m
        contentDescription = when (m) {
            PetRenderer.Mood.HAPPY -> "Pip is thriving"
            PetRenderer.Mood.CONTENT -> "Pip is doing okay"
            PetRenderer.Mood.HUNGRY -> "Pip is hungry"
            PetRenderer.Mood.SAD -> "Pip is sad"
            PetRenderer.Mood.SULKING -> "Pip is sulking"
            PetRenderer.Mood.SLEEPING -> "Pip is sleeping"
            PetRenderer.Mood.SICK -> "Pip is sick"
        }
    }

    /** Goal complete: confetti burst + squash-and-stretch jump. */
    fun celebrate() {
        val now = SystemClock.uptimeMillis()
        jumpUntil = now + 600
        confettiBurst(26)
    }

    /** Shared confetti burst (colors = the v2 FX palette). */
    private fun confettiBurst(count: Int, colors: IntArray? = null) {
        val cx = width / 2f
        val cy = height / 2f
        val palette = colors ?: intArrayOf(
            Color.parseColor("#FFD54A"), Color.parseColor("#9D7BFF"),
            Color.parseColor("#5AFF8A"), Color.parseColor("#FF9DC6"),
            Color.parseColor("#6AC8FF")
        )
        repeat(count) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            val sp = Random.nextFloat() * 500 + 200
            particles.add(
                Particle(
                    cx, cy,
                    (Math.cos(a) * sp).toFloat(), (Math.sin(a) * sp).toFloat() - 300,
                    900, 900, palette.random(), Random.nextFloat() * 10 + 6, KIND_CONFETTI
                )
            )
        }
    }

    /**
     * v3 Phase 3: play one family power visual effect. All branches are
     * additive — no existing animation behavior changes. Premium only adds a
     * gold sparkle burst on top (look only, never the power itself).
     */
    fun powerFx(fx: PowerManager.FxKind, premium: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val cx = width / 2f
        val cy = height / 2f
        when (fx) {
            PowerManager.FxKind.HEARTS -> feedAnim()
            PowerManager.FxKind.CONFETTI -> confettiBurst(26)
            PowerManager.FxKind.GIFT -> confettiBurst(
                30,
                intArrayOf(
                    Color.parseColor("#FFD54A"), Color.parseColor("#FF9DC6"),
                    Color.parseColor("#FFFFFF")
                )
            )
            PowerManager.FxKind.CLOVER -> confettiBurst(
                26,
                intArrayOf(
                    Color.parseColor("#5AFF8A"), Color.parseColor("#2F8A3F"),
                    Color.parseColor("#FFD54A")
                )
            )
            PowerManager.FxKind.SPARKS -> repeat(16) {
                particles.add(
                    Particle(
                        cx + Random.nextFloat() * 160 - 80, cy,
                        Random.nextFloat() * 120 - 60, -Random.nextFloat() * 160 - 60,
                        900, 900, Color.parseColor("#FFD54A"),
                        Random.nextFloat() * 6 + 4, KIND_SPARK
                    )
                )
            }
            PowerManager.FxKind.SNOW -> repeat(40) {
                particles.add(
                    Particle(
                        Random.nextFloat() * width, -20f,
                        Random.nextFloat() * 40 - 20, Random.nextFloat() * 60 + 40,
                        2600, 2600, Color.parseColor("#FFFFFF"),
                        Random.nextFloat() * 6 + 4, KIND_SNOW
                    )
                )
            }
            PowerManager.FxKind.BUBBLES -> bubbleUntil = now + 20000
            PowerManager.FxKind.RAINBOW_TRAIL -> trailUntil = now + 30000
            PowerManager.FxKind.INK_DOODLE -> inkUntil = now + 8000
            PowerManager.FxKind.MUD_SPLAT -> repeat(14) {
                particles.add(
                    Particle(
                        cx + Random.nextFloat() * 160 - 80, cy + 60,
                        Random.nextFloat() * 200 - 100, -Random.nextFloat() * 160 - 40,
                        800, 800, Color.parseColor("#8A5A2A"),
                        Random.nextFloat() * 10 + 8, KIND_MUD
                    )
                )
            }
            PowerManager.FxKind.MOON_GLOW -> moonUntil = now + 6000
            PowerManager.FxKind.SNEAK_FADE -> sneakUntil = now + 4000
            PowerManager.FxKind.POUNCE -> {
                jumpUntil = now + 1100
                shakeUntil = now + 450
            }
            PowerManager.FxKind.STOMP -> {
                shakeUntil = now + 450
                ringUntil = now + 600
                ringDurationMs = 600
                ringColor = Color.parseColor("#C8A86A")
            }
            PowerManager.FxKind.RING_SHIELD -> shieldRingUntil = now + 10000
            PowerManager.FxKind.QUACK_RIPPLES -> {
                ringUntil = now + 900
                ringDurationMs = 900
                ringColor = Color.parseColor("#6AC8FF")
            }
            PowerManager.FxKind.FLY_CATCH -> flyUntil = now + 3000
            PowerManager.FxKind.COIL_NAP -> napUntil = now + 4000
            PowerManager.FxKind.SKY_DIVE -> diveUntil = now + 2500
            PowerManager.FxKind.ECHO_RINGS -> {
                ringUntil = now + 900
                ringDurationMs = 900
                ringColor = Color.parseColor("#5AFF8A")
            }
        }
        // Premium = extra gold sparkle look only.
        if (premium) {
            repeat(12) {
                particles.add(
                    Particle(
                        cx + Random.nextFloat() * 200 - 100, cy + Random.nextFloat() * 120 - 60,
                        Random.nextFloat() * 60 - 30, -Random.nextFloat() * 100 - 40,
                        1100, 1100, Color.parseColor("#FFD54A"),
                        Random.nextFloat() * 5 + 3, KIND_SPARK
                    )
                )
            }
        }
    }

    /** Feeding: hearts rise. */
    fun feedAnim() {
        val cx = width / 2f
        val cy = height / 2f
        repeat(6) {
            particles.add(
                Particle(
                    cx + Random.nextFloat() * 120 - 60, cy,
                    Random.nextFloat() * 40 - 20, -Random.nextFloat() * 220 - 120,
                    1100, 1100, Color.parseColor("#FF6A9E"),
                    Random.nextFloat() * 8 + 10, KIND_HEART
                )
            )
        }
    }

    /** Doomscroll logged: shake + red edge pulse. */
    fun sulkAnim() {
        val now = SystemClock.uptimeMillis()
        shakeUntil = now + 450
        sulkPulseUntil = now + 1200
    }

    /** Eating: chomps + crumbs. */
    fun eatAnim() {
        eatUntil = SystemClock.uptimeMillis() + 1000
        val cx = width / 2f
        val cy = height / 2f + 40
        repeat(8) {
            particles.add(
                Particle(
                    cx + Random.nextFloat() * 160 - 80, cy,
                    Random.nextFloat() * 200 - 100, -Random.nextFloat() * 160 - 40,
                    700, 700, Color.parseColor("#B07A3F"),
                    Random.nextFloat() * 8 + 5, KIND_CRUMB
                )
            )
        }
    }

    /** Playing: spin + streaks. */
    fun playAnim() {
        spinUntil = SystemClock.uptimeMillis() + 1100
        jumpUntil = SystemClock.uptimeMillis() + 1100
    }

    /** Evolution/unlock: particle storm + screen flash. */
    fun evolveAnim() {
        val now = SystemClock.uptimeMillis()
        evolveUntil = now + 2000
        flashUntil = now + 300
        val cx = width / 2f
        val cy = height / 2f
        val colors = intArrayOf(
            Color.parseColor("#FFD54A"), Color.parseColor("#9D7BFF"),
            Color.parseColor("#5AFF8A"), Color.parseColor("#FF9DC6"),
            Color.parseColor("#6AC8FF"), Color.parseColor("#FFFFFF")
        )
        repeat(60) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            val sp = Random.nextFloat() * 650 + 250
            particles.add(
                Particle(
                    cx, cy,
                    (Math.cos(a) * sp).toFloat(), (Math.sin(a) * sp).toFloat() - 350,
                    1400, 1400, colors.random(), Random.nextFloat() * 12 + 6, KIND_CONFETTI
                )
            )
        }
    }

    // ---------------- render loop ----------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTicker()
    }

    override fun onDetachedFromWindow() {
        stopTicker()
        particles.clear()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Keep the loop cheap: pause when hidden.
        if (visibility != VISIBLE) stopTicker()
        else if (isAttachedToWindow) startTicker()
    }

    private fun updateParticles(dtMs: Long) {
        val dt = dtMs / 1000f
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dtMs
            if (p.life <= 0) { it.remove(); continue }
            when (p.kind) {
                KIND_SPARK -> p.vy -= 60 * dt // sparks drift upward
                KIND_SNOW -> {
                    p.vy += 60 * dt // gentle snowfall
                    p.x += sin(p.life / 120f) * 14 * dt // sway
                }
                KIND_BUBBLE -> p.vy -= 80 * dt // bubbles rise
                else -> p.vy += 900 * dt // gravity for confetti/hearts/crumbs/mud
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f

        // red edge pulse on sulk trigger
        if (now < sulkPulseUntil) {
            val k = 1f - (sulkPulseUntil - now) / 1200f
            val alpha = ((1f - k) * 120).toInt().coerceIn(0, 120)
            vignettePaint.color = Color.argb(alpha, 255, 90, 90)
            val w = width.toFloat(); val h = height.toFloat(); val e = 26f
            c.drawRect(0f, 0f, w, e, vignettePaint)
            c.drawRect(0f, h - e, w, h, vignettePaint)
            c.drawRect(0f, 0f, e, h, vignettePaint)
            c.drawRect(w - e, 0f, w, h, vignettePaint)
        }

        // pet bitmap
        val blinking = now < blinkUntil
        wavePhase = t * 2.2f
        val cellPx = 14
        val pet = PetRenderer.render(species, mood, cellPx, blink = blinking, aura = aura, auraStyle = auraStyle, wavePhase = wavePhase)
        val petW = pet.width.toFloat()
        val petH = pet.height.toFloat()
        val scale = ((width.coerceAtMost(height) * 0.62f) / petW).coerceAtMost(3.2f)
        val dw = petW * scale
        val dh = petH * scale

        var dx = (width - dw) / 2f
        var dy = (height - dh) / 2f + sin(t * 5.2f) * 8f // idle bounce
        if (now < shakeUntil) {
            dx += sin(t * 55f) * 10f // sulk shake
        }
        var sy = 1f
        var rotation = 0f
        if (now < jumpUntil) { // squash & stretch
            val k = 1f - (jumpUntil - now) / 600f
            sy = 1f + sin(k * Math.PI).toFloat() * 0.18f
            dy -= sin(k * Math.PI).toFloat() * 46f
        }
        if (now < spinUntil) { // play spin
            val k = 1f - (spinUntil - now) / 1100f
            rotation = k * 360f
            dy -= sin(k * Math.PI).toFloat() * 60f
        }
        if (now < eatUntil) { // chomps
            val k = (eatUntil - now) / 1000f
            sy = 1f - Math.abs(sin(k * Math.PI * 6)).toFloat() * 0.15f
        }
        if (now < evolveUntil) { // evolve rise
            val k = 1f - (evolveUntil - now) / 2000f
            dy -= sin(k * Math.PI).toFloat() * 90f
        }
        if (now < diveUntil) { // v3 Phase 3: griffin sky-dive loop
            val k = 1f - (diveUntil - now) / 2500f
            rotation = k * 360f
            dy -= sin(k * Math.PI).toFloat() * 140f
        }
        val dst = RectF(dx, dy + dh * (1 - sy) / 2f, dx + dw, dy + dh - dh * (1 - sy) / 2f)
        if (rotation != 0f) {
            c.save()
            c.rotate(rotation, dx + dw / 2f, dy + dh / 2f)
            PetRenderer.drawScaled(c, pet, dst)
            c.restore()
        } else {
            PetRenderer.drawScaled(c, pet, dst)
        }
        pet.recycle()

        // ---- v3 Phase 3: power overlays (all additive) ----
        if (now < trailUntil) { // unicorn rainbow trail behind the pet
            val rainbow = intArrayOf(
                Color.parseColor("#FF5A5A"), Color.parseColor("#FFB84A"),
                Color.parseColor("#FFD54A"), Color.parseColor("#5AFF8A"),
                Color.parseColor("#6AC8FF"), Color.parseColor("#B9A7FF")
            )
            for (i in 0 until 8) {
                val k = i / 8f
                val px = dx - 10 - k * 90f
                val py = dy + dh * 0.7f + sin(t * 6f + k * 5f) * 14f
                particlePaint.color = rainbow[(i + (t * 6).toInt()) % rainbow.size]
                particlePaint.alpha = ((1f - k) * 220).toInt().coerceIn(0, 220)
                c.drawRect(px, py, px + 12, py + 12, particlePaint)
            }
            particlePaint.alpha = 255
        }
        if (now < moonUntil) { // wolf / owl moon glow
            val mx = width - 90f
            val my = 90f
            particlePaint.color = Color.parseColor("#FFF3D6")
            particlePaint.alpha = 160
            c.drawCircle(mx, my, 46f, particlePaint)
            particlePaint.alpha = 255
            c.drawCircle(mx, my, 30f, particlePaint)
        }
        if (now < inkUntil) { // kraken ink doodle (tiny squiggle, bottom-left)
            particlePaint.color = Color.parseColor("#1E1E2A")
            particlePaint.alpha = 255
            val ix = 30f
            val iy = height - 90f
            c.drawRect(ix, iy, ix + 44, iy + 8, particlePaint)
            c.drawRect(ix + 20, iy + 8, ix + 64, iy + 16, particlePaint)
            c.drawRect(ix + 8, iy + 16, ix + 40, iy + 24, particlePaint)
        }
        if (now < shieldRingUntil) { // turtle shell shield ring
            ringPaint.color = Color.parseColor("#FFD54A")
            ringPaint.alpha = 200
            val cxr = dx + dw / 2f
            val cyr = dy + dh / 2f
            val r = dw * 0.72f
            c.drawCircle(cxr, cyr, r, ringPaint)
        }
        if (now < ringUntil) { // expanding ripple / stomp / echo ring
            val k = 1f - (ringUntil - now).toFloat() / ringDurationMs
            ringPaint.color = ringColor
            ringPaint.alpha = ((1f - k) * 220).toInt().coerceIn(0, 220)
            val cxr = dx + dw / 2f
            val cyr = dy + dh * 0.8f
            c.drawCircle(cxr, cyr, 20f + k * 190f, ringPaint)
        }
        if (now < sneakUntil) { // fox sneak: translucent veil over the pet
            vignettePaint.color = Color.argb(110, 255, 255, 255)
            c.drawRect(dx, dy, dx + dw, dy + dh, vignettePaint)
        }
        if (now < flyUntil) { // frog fly: tiny dot orbiting the pet
            val a = t * 6f
            val fx = dx + dw / 2f + kotlin.math.cos(a) * dw * 0.7f
            val fy = dy - 30f + sin(a) * 22f
            particlePaint.color = Color.parseColor("#14141F")
            particlePaint.alpha = 255
            c.drawRect(fx, fy, fx + 5, fy + 5, particlePaint)
        }

        // attention bubble
        if (showAttentionBubble) {
            PetRenderer.drawAttentionBubble(c, dx + dw / 2f, dy - 90f, scale * 2.2f, t * 4f)
        }

        // evolve screen flash
        if (now < flashUntil) {
            val k = 1f - (flashUntil - now) / 300f
            val alpha = (sin(k * Math.PI) * 90).toInt().coerceIn(0, 90)
            vignettePaint.color = Color.argb(alpha, 255, 213, 74)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }

        // sleep Zs drift
        if (mood == PetRenderer.Mood.SLEEPING || now < napUntil) { // nap = snake coil nap
            val zk = (t * 0.7f) % 1f
            textPaint.alpha = ((1f - zk) * 255).toInt()
            c.drawText("Z", dx + dw * 0.75f, dy - zk * 90f, textPaint)
            textPaint.alpha = 255
        }

        // particles
        for (p in particles) {
            val a = (p.life.toFloat() / p.maxLife * 255).toInt().coerceIn(0, 255)
            particlePaint.color = p.color
            particlePaint.alpha = a
            when (p.kind) {
                KIND_CONFETTI -> c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size * 0.6f, particlePaint)
                KIND_CRUMB -> c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, particlePaint)
                KIND_SPARK -> c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, particlePaint)
                KIND_SNOW -> c.drawCircle(p.x, p.y, p.size * 0.5f, particlePaint)
                KIND_BUBBLE -> {
                    ringPaint.color = p.color
                    ringPaint.alpha = a
                    c.drawCircle(p.x, p.y, p.size * 0.5f, ringPaint)
                }
                KIND_MUD -> c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, particlePaint)
                KIND_HEART -> {
                    c.drawCircle(p.x, p.y, p.size * 0.5f, particlePaint)
                    c.drawCircle(p.x + p.size * 0.5f, p.y, p.size * 0.5f, particlePaint)
                    c.drawRect(p.x, p.y, p.x + p.size * 0.5f, p.y + p.size * 0.7f, particlePaint)
                }
            }
        }
        particlePaint.alpha = 255
    }

    companion object {
        private const val KIND_CONFETTI = 0
        private const val KIND_HEART = 1
        private const val KIND_CRUMB = 2
        // v3 Phase 3 power particles
        private const val KIND_SPARK = 3
        private const val KIND_SNOW = 4
        private const val KIND_BUBBLE = 5
        private const val KIND_MUD = 6
    }
}
