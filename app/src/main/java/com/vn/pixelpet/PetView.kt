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
 * one-shot VFX routines (confetti, hearts, sulk shake, sleep Z's).
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
    /** Set by the activity when attention < 25. */
    var showAttentionBubble: Boolean = false
    /** Current species for rendering; set by the activity. */
    var species: Species = Species.CAT
    /** Premium aura for mystical species. */
    var aura: Boolean = false
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
        val cx = width / 2f
        val cy = height / 2f
        val colors = intArrayOf(
            Color.parseColor("#FFD54A"), Color.parseColor("#9D7BFF"),
            Color.parseColor("#5AFF8A"), Color.parseColor("#FF9DC6"),
            Color.parseColor("#6AC8FF")
        )
        repeat(26) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            val sp = Random.nextFloat() * 500 + 200
            particles.add(
                Particle(
                    cx, cy,
                    (Math.cos(a) * sp).toFloat(), (Math.sin(a) * sp).toFloat() - 300,
                    900, 900, colors.random(), Random.nextFloat() * 10 + 6, KIND_CONFETTI
                )
            )
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
            p.vy += 900 * dt // gravity for confetti
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
        val pet = PetRenderer.render(species, mood, cellPx, blink = blinking, aura = aura, wavePhase = wavePhase)
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

        // sleep Z's drift
        if (mood == PetRenderer.Mood.SLEEPING) {
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
    }
}
