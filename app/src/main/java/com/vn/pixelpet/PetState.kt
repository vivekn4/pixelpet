package com.vn.pixelpet

/**
 * Immutable snapshot of the active pet's state. Time fields that drive decay use
 * [SystemClock.elapsedRealtime]; wall-clock is stored only as a reboot fallback.
 *
 * v2 adds: species, attention meter, sleep, streaks/achievements, sickness.
 * All new fields have safe defaults so v1 prefs upgrade cleanly.
 */
data class PetState(
    val hunger: Int,          // 0..100
    val happiness: Int,       // 0..100
    val energy: Int,          // 0..100
    val attention: Int,       // 0..100 — drains when ignored
    val lastTickElapsed: Long,
    val lastTickWall: Long,
    val sulkUntilElapsed: Long,
    val muted: Boolean,
    val speciesId: String = "cat",
    val sleeping: Boolean = false,
    val sleepStartElapsed: Long = 0L,
    val sickSinceWall: Long = 0L,   // wall-clock when hunger hit 0; 0 = not sick
    val lastSnackElapsed: Long = 0L, // 15-min snack cooldown
    val lastPlayElapsed: Long = 0L,   // 5-min play cooldown
    // v3 Phase 3: Turtle Shell Shield — decay pauses while now < this (elapsedRealtime). 0 = no shield.
    val shieldUntilElapsed: Long = 0L
) {
    fun isSulking(nowElapsed: Long): Boolean = nowElapsed < sulkUntilElapsed
    fun isSick(nowWall: Long): Boolean =
        sickSinceWall != 0L && nowWall - sickSinceWall > 6 * 3600 * 1000L

    val species: Species get() = Species.byId(speciesId)

    companion object {
        fun fresh(speciesId: String, nowElapsed: Long, nowWall: Long): PetState = PetState(
            hunger = 80,
            happiness = 80,
            energy = 90,
            attention = 80,
            lastTickElapsed = nowElapsed,
            lastTickWall = nowWall,
            sulkUntilElapsed = 0L,
            muted = false,
            speciesId = speciesId
        )
    }
}
