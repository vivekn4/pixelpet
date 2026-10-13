package com.vn.pixelpet

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Central haptics. Every pattern from ART.md §6. Respects the mute toggle and
 * the system haptic-feedback setting — never buzzes against the user's will.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun enabled(context: Context): Boolean {
        if (PetRepository.get(context).snapshot().muted) return false
        return try {
            Settings.System.getInt(
                context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
            ) == 1
        } catch (t: Throwable) {
            true
        }
    }

    private fun fire(context: Context, effect: VibrationEffect) {
        if (!enabled(context)) return
        try {
            vibrator(context)?.takeIf { it.hasVibrator() }?.vibrate(effect)
        } catch (t: Throwable) {
            // haptics must never crash the app
        }
    }

    /** Light tick: taps, petting. */
    fun tick(context: Context) =
        fire(context, VibrationEffect.createOneShot(15, 80))

    /** Soft thud: feeding, goal complete. */
    fun thud(context: Context) =
        fire(context, VibrationEffect.createOneShot(40, 160))

    /** Double-tap bounce: playing. */
    fun bounce(context: Context) =
        fire(context, VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 30), -1))

    /** Low double-buzz: sulking. */
    fun sulkBuzz(context: Context) =
        fire(context, VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))

    /** Triple fanfare: unlocks, evolution, premium. */
    fun fanfare(context: Context) =
        fire(context, VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30, 60, 60), -1))

    /** Long buzz: key rejected. */
    fun error(context: Context) =
        fire(context, VibrationEffect.createOneShot(120, 200))
}
