package com.vn.pixelpet

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * All sound effects are SYNTHESIZED in code at first launch — the APK ships
 * zero audio files. Tones are rendered to 16-bit mono WAVs in cacheDir, then
 * played through SoundPool. Generation runs on a background thread; [play]
 * is a no-op until loading completes, so the UI can never block on audio.
 */
object SfxEngine {

    private const val SAMPLE_RATE = 44100

    private enum class Wave { SINE, SQUARE, TRIANGLE, NOISE }

    private data class Note(
        val freqStart: Double,
        val freqEnd: Double,
        val durMs: Int,
        val wave: Wave,
        val vol: Double
    )

    // name -> soundId
    private val sounds = mutableMapOf<String, Int>()
    private var pool: SoundPool? = null
    private val ready = AtomicBoolean(false)
    @Volatile private var muted = false
    @Volatile private var started = false

    /** Idempotent; safe to call from every Activity's onCreate. */
    @Synchronized
    fun init(appContext: Context, isMuted: Boolean) {
        muted = isMuted
        if (started) return
        started = true
        Thread({
            try {
                val dir = File(appContext.cacheDir, "sfx").apply { mkdirs() }
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
                pool = sp
                var loaded = 0
                val total = SOUND_DEFS.size
                sp.setOnLoadCompleteListener { _, _, _ ->
                    loaded++
                    if (loaded >= total) ready.set(true)
                }
                for ((name, notes) in SOUND_DEFS) {
                    val f = File(dir, "$name.wav")
                    if (!f.exists()) writeWav(f, notes)
                    sounds[name] = sp.load(f.absolutePath, 1)
                }
            } catch (t: Throwable) {
                // Audio must never break the app.
                t.printStackTrace()
            }
        }, "sfx-gen").start()
    }

    fun setMuted(m: Boolean) { muted = m }

    fun play(name: String) {
        if (muted || !ready.get()) return
        val id = sounds[name] ?: return
        try {
            pool?.play(id, 0.8f, 0.8f, 1, 0, 1.0f)
        } catch (t: Throwable) {
            // never crash for a blip
        }
    }

    // ---------------- synthesis ----------------

    private val SOUND_DEFS: Map<String, List<Note>> = mapOf(
        "ui_click" to listOf(
            Note(1200.0, 1200.0, 40, Wave.SQUARE, 0.45)
        ),
        "goal_complete" to listOf(
            Note(523.25, 523.25, 90, Wave.SQUARE, 0.5),
            Note(659.25, 659.25, 90, Wave.SQUARE, 0.5),
            Note(783.99, 783.99, 90, Wave.SQUARE, 0.5),
            Note(1046.5, 1046.5, 170, Wave.SQUARE, 0.55)
        ),
        "feed_munch" to listOf(
            Note(300.0, 180.0, 80, Wave.SQUARE, 0.5),
            Note(260.0, 150.0, 80, Wave.SQUARE, 0.5),
            Note(220.0, 120.0, 110, Wave.SQUARE, 0.5)
        ),
        "happy_chirp" to listOf(
            Note(880.0, 1320.0, 120, Wave.SINE, 0.5),
            Note(1320.0, 1760.0, 140, Wave.SINE, 0.5)
        ),
        "sulk_slide" to listOf(
            Note(600.0, 200.0, 500, Wave.SINE, 0.6)
        ),
        "sad_blip" to listOf(
            Note(220.0, 220.0, 150, Wave.SQUARE, 0.4)
        ),
        "widget_boop" to listOf(
            Note(880.0, 880.0, 80, Wave.SINE, 0.5)
        ),
        "sleep_shimmer" to listOf(
            Note(440.0, 660.0, 400, Wave.TRIANGLE, 0.4)
        ),
        // ---- v2 additions (see ART.md §5) ----
        "wake_chime" to listOf(
            Note(660.0, 660.0, 110, Wave.SINE, 0.5),
            Note(880.0, 880.0, 110, Wave.SINE, 0.5),
            Note(1320.0, 1320.0, 160, Wave.SINE, 0.5)
        ),
        "play_giggle" to listOf(
            Note(900.0, 1100.0, 70, Wave.SINE, 0.5),
            Note(1100.0, 900.0, 70, Wave.SINE, 0.5),
            Note(1000.0, 1300.0, 70, Wave.SINE, 0.5),
            Note(1300.0, 1100.0, 70, Wave.SINE, 0.5),
            Note(1200.0, 1400.0, 90, Wave.SINE, 0.5)
        ),
        "pet_purr" to listOf(
            // 90 Hz triangle tremolo: alternating volumes fake the AM
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.55),
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.25),
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.55),
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.25),
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.55),
            Note(90.0, 90.0, 80, Wave.TRIANGLE, 0.25),
            Note(90.0, 95.0, 100, Wave.TRIANGLE, 0.5)
        ),
        "evolve_fanfare" to listOf(
            Note(523.25, 523.25, 110, Wave.SQUARE, 0.5),
            Note(659.25, 659.25, 110, Wave.SQUARE, 0.5),
            Note(783.99, 783.99, 110, Wave.SQUARE, 0.5),
            Note(1046.5, 1046.5, 140, Wave.SQUARE, 0.55),
            Note(1318.5, 1568.0, 220, Wave.TRIANGLE, 0.5)
        ),
        "unlock_chime" to listOf(
            Note(1320.0, 1760.0, 160, Wave.SINE, 0.5),
            Note(1760.0, 2093.0, 140, Wave.SINE, 0.45)
        ),
        "key_success" to listOf(
            Note(523.25, 523.25, 120, Wave.TRIANGLE, 0.55),
            Note(659.25, 659.25, 120, Wave.TRIANGLE, 0.55),
            Note(783.99, 783.99, 120, Wave.TRIANGLE, 0.55),
            Note(1046.5, 1046.5, 200, Wave.TRIANGLE, 0.6)
        ),
        "key_error" to listOf(
            Note(200.0, 150.0, 250, Wave.SQUARE, 0.45)
        ),
        "heartbeat" to listOf(
            Note(55.0, 55.0, 120, Wave.SINE, 0.7),
            Note(0.0, 0.0, 180, Wave.SINE, 0.0), // rest
            Note(55.0, 55.0, 120, Wave.SINE, 0.7)
        ),
        "attention_ping" to listOf(
            Note(1568.0, 1568.0, 120, Wave.SINE, 0.5),
            Note(1568.0, 1568.0, 140, Wave.SINE, 0.5)
        )
    )

    private fun waveSample(wave: Wave, phase: Double): Double = when (wave) {
        Wave.SINE -> sin(phase)
        Wave.SQUARE -> if (sin(phase) >= 0) 0.8 else -0.8
        Wave.TRIANGLE -> 2.0 * kotlin.math.abs(2.0 * (phase / (2 * PI) % 1.0) - 1.0) - 1.0
        Wave.NOISE -> Random.nextDouble(-1.0, 1.0)
    }

    private fun writeWav(file: File, notes: List<Note>) {
        val samples = mutableListOf<Short>()
        for (n in notes) {
            val count = (SAMPLE_RATE * n.durMs / 1000)
            var phase = 0.0
            for (i in 0 until count) {
                if (n.freqStart == 0.0) { samples.add(0); continue } // rest
                val t = i.toDouble() / count
                val freq = n.freqStart + (n.freqEnd - n.freqStart) * t
                phase += 2 * PI * freq / SAMPLE_RATE
                // 5ms attack, exponential-ish decay envelope
                val attack = (i.toDouble() / (SAMPLE_RATE * 0.005)).coerceIn(0.0, 1.0)
                val decay = 1.0 - 0.7 * t
                val v = waveSample(n.wave, phase) * n.vol * attack * decay
                samples.add((v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
            }
            // 10ms gap between notes
            repeat(SAMPLE_RATE / 100) { samples.add(0) }
        }
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // PCM header size
        buf.putShort(1) // PCM format
        buf.putShort(1) // mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2) // byte rate
        buf.putShort(2) // block align
        buf.putShort(16) // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        for (s in samples) buf.putShort(s)
        FileOutputStream(file).use { it.write(buf.array()) }
    }
}
