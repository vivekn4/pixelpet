package com.vn.pixelpet

import android.content.SharedPreferences
import kotlin.random.Random

/**
 * Five trait axes (PLAN_V3.md §4). Values hard-clamped to 10–90 so no pet can
 * drift into an unreadable extreme. Each variant seeds from its
 * [VariantRoster.TraitBias]; interactions nudge traits a little; traits decay
 * toward their baseline over ~7 days.
 *
 * Persisted via SharedPreferences (self-contained — no PetRepository changes).
 * Phase 3 (NativeBrain) will read these traits for anticipation scores.
 */
class Personality private constructor(
    val variantId: String,
    val baseline: IntArray,
    val traits: IntArray,
    var lastDecayWallMs: Long
) {
    enum class Trait { PLAYFUL, BOLD, GREEDY, SOCIAL, NEAT }

    enum class Interaction { PET, PLAY, FEED_GOAL, FEED_SNACK, DOOMSCROLL, IGNORE_TICK, WAKE }

    companion object {
        const val MIN_TRAIT = 10
        const val MAX_TRAIT = 90
        const val BASELINE_DEFAULT = 50
        const val SPREAD = 6
        const val DECAY_DAYS_FULL = 7
        const val DAY_MS = 24L * 3600L * 1000L
        private const val PREFS_NAME = "pixelpet_personality"

        fun fresh(variant: VariantRoster.Variant, random: Random, nowWallMs: Long): Personality {
            val base = IntArray(5) { i -> clamp(BASELINE_DEFAULT + biasOf(variant.traitBias, i)) }
            val traits = IntArray(5) { i -> clamp(base[i] + random.nextInt(-SPREAD, SPREAD + 1)) }
            return Personality(variant.id, base, traits, nowWallMs)
        }

        fun prefsName(): String = PREFS_NAME

        fun load(prefs: SharedPreferences, variantId: String): Personality? {
            val raw = prefs.getString("personality_$variantId", null) ?: return null
            val parts = raw.split("|")
            if (parts.size != 3) return null
            val traits = parseInts(parts[0], 5) ?: return null
            val base = parseInts(parts[1], 5) ?: return null
            val decay = parts[2].toLongOrNull() ?: return null
            if (traits.any { it < MIN_TRAIT || it > MAX_TRAIT }) return null
            if (base.any { it < MIN_TRAIT || it > MAX_TRAIT }) return null
            return Personality(variantId, base, traits, decay)
        }

        private fun biasOf(bias: VariantRoster.TraitBias, index: Int): Int = when (index) {
            0 -> bias.playful
            1 -> bias.bold
            2 -> bias.greedy
            3 -> bias.social
            else -> bias.neat
        }

        private fun parseInts(s: String, expected: Int): IntArray? {
            val vals = s.split(",").mapNotNull { it.toIntOrNull() }
            if (vals.size != expected) return null
            return vals.toIntArray()
        }

        private fun clamp(v: Int): Int = v.coerceIn(MIN_TRAIT, MAX_TRAIT)
    }

    fun trait(t: Trait): Int = traits[t.ordinal]

    /** Nudge traits by interaction type (plan §4: ±1–3). Always clamped. */
    fun nudge(interaction: Interaction) {
        when (interaction) {
            Interaction.PET -> { add(Trait.SOCIAL, 2); add(Trait.NEAT, 1) }
            Interaction.PLAY -> { add(Trait.PLAYFUL, 3); add(Trait.SOCIAL, 1) }
            Interaction.FEED_GOAL -> { add(Trait.GREEDY, -1); add(Trait.BOLD, 1) }
            Interaction.FEED_SNACK -> add(Trait.GREEDY, 2)
            Interaction.DOOMSCROLL -> { add(Trait.BOLD, -3); add(Trait.SOCIAL, -2) }
            Interaction.IGNORE_TICK -> add(Trait.SOCIAL, -1)
            Interaction.WAKE -> add(Trait.SOCIAL, 1)
        }
    }

    /**
     * Decay traits toward baseline: each full day moves each trait 1/7 of its
     * gap toward baseline (at least 1 step if any gap remains). Call with the
     * current wall clock whenever the pet is loaded. Returns true if changed.
     */
    fun decay(nowWallMs: Long): Boolean {
        val days = (nowWallMs - lastDecayWallMs) / DAY_MS
        if (days < 1L) return false
        var changed = false
        for (i in traits.indices) {
            val gap = baseline[i] - traits[i]
            if (gap == 0) continue
            var move = (gap * days / DECAY_DAYS_FULL).toInt()
            if (move == 0) move = if (gap > 0) 1 else -1
            val next = clamp(traits[i] + move)
            if (next != traits[i]) { traits[i] = next; changed = true }
        }
        lastDecayWallMs = nowWallMs
        return changed
    }

    /** Walk speed multiplier the engine reads: 0.76 (calm) … 1.24 (playful). */
    val walkSpeedMultiplier: Float
        get() = 0.7f + (trait(Trait.PLAYFUL) / 100f) * 0.6f

    /** Roam frequency the engine reads: 0.6 (calm/shy) … 1.4 (playful/bold). */
    val roamFrequency: Float
        get() = 0.5f + ((trait(Trait.PLAYFUL) + trait(Trait.BOLD)) / 200f)

    /** Attention-seeking rate: 0.10 (independent) … 0.90 (social). */
    val attentionSeekRate: Float
        get() = trait(Trait.SOCIAL) / 100f

    /** Dominant trait: the axis furthest from neutral, or null when balanced. */
    fun dominantTrait(): Trait? {
        var best: Trait? = null
        var bestDev = 12
        for (t in Trait.values()) {
            val dev = kotlin.math.abs(trait(t) - BASELINE_DEFAULT)
            if (dev > bestDev) { bestDev = dev; best = t }
        }
        return best
    }

    /** SpeechBank archetype for this personality. */
    fun archetype(): SpeechBank.Archetype {
        val dom = dominantTrait() ?: return SpeechBank.Archetype.BALANCED
        val high = trait(dom) > BASELINE_DEFAULT
        return when (dom) {
            Trait.PLAYFUL -> if (high) SpeechBank.Archetype.PLAYFUL else SpeechBank.Archetype.CALM
            Trait.BOLD -> if (high) SpeechBank.Archetype.BOLD else SpeechBank.Archetype.SHY
            Trait.GREEDY -> if (high) SpeechBank.Archetype.GREEDY else SpeechBank.Archetype.PICKY
            Trait.SOCIAL -> if (high) SpeechBank.Archetype.SOCIAL else SpeechBank.Archetype.INDEPENDENT
            Trait.NEAT -> if (high) SpeechBank.Archetype.NEAT else SpeechBank.Archetype.MESSY
        }
    }

    /** One-line summary for the Personality card. */
    fun summaryLine(): String {
        val dom = dominantTrait() ?: return "Pip is feeling balanced today"
        val high = trait(dom) > BASELINE_DEFAULT
        val word = when (dom) {
            Trait.PLAYFUL -> if (high) "Playful" else "Calm"
            Trait.BOLD -> if (high) "Bold" else "Shy"
            Trait.GREEDY -> if (high) "Hungry for more" else "Picky"
            Trait.SOCIAL -> if (high) "Social" else "Independent"
            Trait.NEAT -> if (high) "Tidy" else "Messy"
        }
        return "Pip is feeling $word today"
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(
                "personality_$variantId",
                traits.joinToString(",") + "|" + baseline.joinToString(",") + "|" + lastDecayWallMs
            )
            .apply()
    }

    private fun add(t: Trait, delta: Int) {
        traits[t.ordinal] = clamp(traits[t.ordinal] + delta)
    }
}
