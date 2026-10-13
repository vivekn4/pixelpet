package com.vn.pixelpet

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Roam state machine for the v3 pet (PLAN_V3.md §3).
 *
 * IDLE → LOOK → WALK → PAUSE → IDLE … with EAT / PLAY / SLEEP / SULK / POWER
 * overlays that freeze roaming while they are active. Movement is delta-time
 * based (pixels/second × dt), so the pet covers the same distance at 30fps
 * and 60fps. The walk cycle (2-frame leg alternation) is driven by distance
 * travelled, not by frame count, so stride stays consistent at any frame rate.
 *
 * Pure Kotlin, no Android imports: the math is unit-testable on the JVM.
 * No allocation in [update] — all state lives in fields.
 */
class WalkEngine(
    private val random: Random = Random.Default
) {

    enum class State { IDLE, LOOK, WALK, PAUSE }
    enum class Overlay { NONE, EAT, PLAY, SLEEP, SULK, POWER }

    companion object {
        /** Base roam speed, pixels/second, before personality multiplier. */
        const val BASE_SPEED_PX_S = 90f
        /** Distance inside which the pet eases out as it approaches its target. */
        const val SLOW_RADIUS_PX = 90f
        /** Turn-around animation: flip + hop, milliseconds. */
        const val TURN_MS = 120L
        /** Distance per walk-cycle step; phase toggles once per stride. */
        const val STEP_STRIDE_PX = 26f
        /** Arrival threshold, pixels. */
        const val ARRIVE_DIST_PX = 4f
        /** New roam targets must be at least this fraction of stage width away. */
        const val MIN_TARGET_DIST_FRAC = 0.25f
        /** Crouch (anticipation) before a hop, milliseconds. */
        const val CROUCH_MS = 150L
        /** Hop air time, milliseconds. */
        const val HOP_MS = 350L
        /** Squash & stretch decay time after a step landing, seconds. */
        const val SQUASH_DECAY_S = 0.12f
        /** Roam pause durations, seconds (plan §3: 2–6s). */
        const val PAUSE_MIN_S = 2f
        const val PAUSE_MAX_S = 6f
        /** Idle / look durations, seconds. */
        const val IDLE_MIN_S = 0.4f
        const val IDLE_MAX_S = 0.9f
        const val LOOK_MIN_S = 0.6f
        const val LOOK_MAX_S = 1.2f
        /** Default margin inside the stage bounds, pixels. */
        const val DEFAULT_MARGIN_PX = 8f
    }

    /** Current position, stage coordinates, pixels. Top-left origin. */
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    /** Current roam state. */
    var state: State = State.IDLE
        private set

    /** Active overlay; anything but NONE freezes roaming. */
    var overlay: Overlay = Overlay.NONE
        private set

    /** Walk-cycle phase for the renderer: 0 = standing, 1/2 = alternating strides. */
    var walkPhase: Int = 0
        private set

    /** True while the pet faces right; false = facing left. */
    var facingRight: Boolean = true
        private set

    /** Squash & stretch pulse after a step landing: 1 = just landed, 0 = settled. */
    var squashStretch: Float = 0f
        private set

    /** Crouch anticipation before a hop: 1 = fully crouched, 0 = none. */
    var crouch: Float = 0f
        private set

    /** Hop lift: 0 = grounded, 1 = apex. */
    var jumpLift: Float = 0f
        private set

    /** Speed multiplier from Personality (walkSpeedMultiplier). 1 = default. */
    var speedMultiplier: Float = 1f

    // ---- bounds ----
    private var stageW: Float = 0f
    private var stageH: Float = 0f
    private var petW: Float = 0f
    private var petH: Float = 0f
    private var margin: Float = DEFAULT_MARGIN_PX

    // ---- internal timers (seconds unless noted) ----
    private var targetX: Float = 0f
    private var targetY: Float = 0f
    private var stateTimeLeftS: Float = 0.5f
    private var stepAccPx: Float = 0f
    private var turnTimerMs: Long = 0L
    private var pendingFacingRight: Boolean = true
    private var crouchTimerMs: Long = 0L
    private var jumpTimerMs: Long = 0L

    /** Stage bounds for roam targets and clamping. Call when the view is laid out. */
    fun setBounds(stageW: Float, stageH: Float, petW: Float, petH: Float, margin: Float = DEFAULT_MARGIN_PX) {
        this.stageW = stageW
        this.stageH = stageH
        this.petW = petW
        this.petH = petH
        this.margin = margin
        x = clampX(x)
        y = clampY(y)
        targetX = clampX(targetX)
        targetY = clampY(targetY)
    }

    /** Place the pet and reset the state machine (tests + first layout). */
    fun reset(startX: Float, startY: Float) {
        x = clampX(startX)
        y = clampY(startY)
        targetX = x
        targetY = y
        state = State.IDLE
        stateTimeLeftS = 0.5f
        walkPhase = 0
        stepAccPx = 0f
        squashStretch = 0f
        crouch = 0f
        jumpLift = 0f
        turnTimerMs = 0L
        crouchTimerMs = 0L
        jumpTimerMs = 0L
        overlay = Overlay.NONE
    }

    /** Set or clear a state overlay. Any overlay freezes roaming. */
    fun setOverlay(o: Overlay) {
        if (overlay == o) return
        overlay = o
        if (o != Overlay.NONE) walkPhase = 0
    }

    /** Trigger the crouch-then-hop animation (play / greeting). */
    fun hop() {
        if (crouchTimerMs > 0L || jumpTimerMs > 0L) return
        crouchTimerMs = CROUCH_MS
    }

    /**
     * Advance the state machine by [dtSeconds]. Call once per frame with the
     * real frame delta (clamped by the caller to e.g. 0.1s). No allocation.
     */
    fun update(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.1f)

        // Squash decay always runs (even under overlays — the landing settles).
        if (squashStretch > 0f) {
            squashStretch = max(0f, squashStretch - dt / SQUASH_DECAY_S)
        }

        // Hop sequencing: crouch first, then lift.
        if (crouchTimerMs > 0L) {
            crouchTimerMs -= (dt * 1000f).toLong()
            crouch = (crouchTimerMs.coerceAtLeast(0L).toFloat() / CROUCH_MS).coerceIn(0f, 1f)
            if (crouchTimerMs <= 0L) {
                crouch = 0f
                jumpTimerMs = HOP_MS
            }
        } else if (jumpTimerMs > 0L) {
            jumpTimerMs -= (dt * 1000f).toLong()
            val k = 1f - (jumpTimerMs.coerceAtLeast(0L).toFloat() / HOP_MS)
            jumpLift = kotlin.math.sin(k * Math.PI.toFloat()).coerceIn(0f, 1f)
            if (jumpTimerMs <= 0L) jumpLift = 0f
        }

        // Overlays freeze roaming (but squash/hop above still settle).
        if (overlay != Overlay.NONE) return

        // Turn-around sequencing: flip facing at the halfway mark.
        if (turnTimerMs > 0L) {
            val before = turnTimerMs
            turnTimerMs -= (dt * 1000f).toLong()
            if (before > TURN_MS / 2 && turnTimerMs <= TURN_MS / 2) {
                facingRight = pendingFacingRight
            }
        }

        stateTimeLeftS -= dt
        when (state) {
            State.IDLE -> {
                walkPhase = 0
                if (stateTimeLeftS <= 0f) {
                    state = State.LOOK
                    stateTimeLeftS = LOOK_MIN_S + random.nextFloat() * (LOOK_MAX_S - LOOK_MIN_S)
                }
            }
            State.LOOK -> {
                walkPhase = 0
                if (stateTimeLeftS <= 0f) {
                    pickTarget()
                    state = State.WALK
                }
            }
            State.WALK -> moveTowardTarget(dt)
            State.PAUSE -> {
                walkPhase = 0
                if (stateTimeLeftS <= 0f) {
                    state = State.IDLE
                    stateTimeLeftS = IDLE_MIN_S + random.nextFloat() * (IDLE_MAX_S - IDLE_MIN_S)
                }
            }
        }
    }

    private fun moveTowardTarget(dt: Float) {
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist <= ARRIVE_DIST_PX) {
            x = targetX
            y = targetY
            walkPhase = 0
            state = State.PAUSE
            stateTimeLeftS = PAUSE_MIN_S + random.nextFloat() * (PAUSE_MAX_S - PAUSE_MIN_S)
            return
        }
        // Face the direction of travel; a direction change starts the turn hop.
        val wantRight = dx >= 0f
        if (wantRight != facingRight && turnTimerMs <= 0L) {
            pendingFacingRight = wantRight
            turnTimerMs = TURN_MS
            hop()
        }
        // Ease-out: full speed far away, gentle near the target.
        val ease = 0.25f + 0.75f * min(1f, dist / SLOW_RADIUS_PX)
        val speed = BASE_SPEED_PX_S * speedMultiplier * ease
        val step = speed * dt
        val travel = min(step, dist)
        x = clampX(x + dx / dist * travel)
        y = clampY(y + dy / dist * travel)
        // Walk cycle: one phase toggle per stride of distance travelled.
        stepAccPx += travel
        while (stepAccPx >= STEP_STRIDE_PX) {
            stepAccPx -= STEP_STRIDE_PX
            walkPhase = if (walkPhase == 1) 2 else 1
            squashStretch = 1f // step landing: squash & stretch pulse
        }
    }

    private fun pickTarget() {
        if (stageW <= 0f || stageH <= 0f) {
            targetX = x
            targetY = y
            return
        }
        val minDist = MIN_TARGET_DIST_FRAC * stageW
        var attempts = 0
        while (attempts < 8) {
            attempts++
            val tx = margin + random.nextFloat() * max(0f, stageW - petW - 2f * margin)
            val ty = margin + random.nextFloat() * max(0f, stageH - petH - 2f * margin)
            if (hypot(tx - x, ty - y) >= minDist || attempts == 8) {
                targetX = clampX(tx)
                targetY = clampY(ty)
                return
            }
        }
    }

    private fun clampX(v: Float): Float {
        if (stageW <= 0f) return v
        val lo = margin
        val hi = max(lo, stageW - petW - margin)
        return v.coerceIn(lo, hi)
    }

    private fun clampY(v: Float): Float {
        if (stageH <= 0f) return v
        val lo = margin
        val hi = max(lo, stageH - petH - margin)
        return v.coerceIn(lo, hi)
    }
}
