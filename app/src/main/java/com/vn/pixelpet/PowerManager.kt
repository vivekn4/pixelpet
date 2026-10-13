package com.vn.pixelpet

import org.json.JSONObject

/**
 * PowerManager (PLAN_V3.md §6) — one cute, gimmicky power per family.
 *
 * Rules: cosmetic-first, cooldowns on [SystemClock.elapsedRealtime]
 * (monotonic — a wall-clock change can never shorten a cooldown), no
 * pay-to-win. Premium changes the LOOK of a power (aura particles) only,
 * never the power itself. Powers never gate progression; they are delight,
 * not currency.
 *
 * Pure Kotlin: no android.* imports. Cooldown state is a plain
 * [Cooldowns] object the caller persists via [Cooldowns.toJson] /
 * [Cooldowns.fromJson] (MainActivity owns the SharedPreferences keys).
 */
object PowerManager {

    /** Visual effect hook the view plays when a power triggers. */
    enum class FxKind {
        HEARTS, CONFETTI, SPARKS, SNOW, BUBBLES, RAINBOW_TRAIL, INK_DOODLE,
        MUD_SPLAT, MOON_GLOW, SNEAK_FADE, POUNCE, STOMP, RING_SHIELD,
        QUACK_RIPPLES, FLY_CATCH, COIL_NAP, SKY_DIVE, ECHO_RINGS, GIFT, CLOVER
    }

    /** What the power does to pet state (applied by the caller via the repo). */
    enum class StatEffect {
        NONE,
        CURE_SICKNESS,   // Cat: clear the sick flag + happiness lift
        POWER_SNACK_10,  // Panda: +10 hunger, no snack-cooldown interaction
        STASH_SNACK,     // Hamster: stash a snack, auto-feed +5 hunger later
        SHIELD_1H,       // Turtle: pause stat decay for 1 hour
        ENERGY_25,       // Snake: +25 energy
        HAPPINESS_5,     // Bunny: tiny mood lift ("lucky")
        STREAK_RESTORE   // Phoenix: restore ONE missed streak day
    }

    /** Powers with a UI step beyond particles (handled by the caller). */
    enum class SpecialUi { NONE, NIGHT_WATCH }

    data class PowerDef(
        val family: VariantRoster.Family,
        val name: String,
        val blurb: String,
        val cooldownMs: Long,
        val fx: FxKind,
        val statEffect: StatEffect,
        val specialUi: SpecialUi = SpecialUi.NONE
    )

    const val DAY_MS = 86_400_000L
    const val SIX_H_MS = 21_600_000L
    const val WEEK_MS = 604_800_000L

    /**
     * All 23 family powers, exactly per PLAN_V3.md §6.
     * Honest deviations from the plan table, documented here:
     * - Dog Fetch Gift / Bunny Lucky Hop mention a "coin" in the plan, but the
     *   app has no coin economy (powers are delight, not currency), so the
     *   gift is a toast + confetti and the lucky hop is a happiness lift +
     *   clover confetti. No currency was invented to serve a cosmetic power.
     */
    val POWERS: Map<VariantRoster.Family, PowerDef> = mapOf(
        VariantRoster.Family.CAT to PowerDef(
            VariantRoster.Family.CAT, "Purr Heal",
            "A long purr that helps Pip feel better sooner.",
            DAY_MS, FxKind.HEARTS, StatEffect.CURE_SICKNESS
        ),
        VariantRoster.Family.DOG to PowerDef(
            VariantRoster.Family.DOG, "Fetch Gift",
            "Pip dashes off and comes back with a little gift.",
            DAY_MS, FxKind.GIFT, StatEffect.NONE
        ),
        VariantRoster.Family.BUNNY to PowerDef(
            VariantRoster.Family.BUNNY, "Lucky Hop",
            "A hop trail of clovers — a tiny lucky lift.",
            DAY_MS, FxKind.CLOVER, StatEffect.HAPPINESS_5
        ),
        VariantRoster.Family.HAMSTER to PowerDef(
            VariantRoster.Family.HAMSTER, "Cheek Stash",
            "Pip stashes a snack in those cheeks for later.",
            SIX_H_MS, FxKind.HEARTS, StatEffect.STASH_SNACK
        ),
        VariantRoster.Family.PIG to PowerDef(
            VariantRoster.Family.PIG, "Mud Bath",
            "A joyful mud splat and a happiness burst.",
            DAY_MS, FxKind.MUD_SPLAT, StatEffect.HAPPINESS_5
        ),
        VariantRoster.Family.DUCK to PowerDef(
            VariantRoster.Family.DUCK, "Quack Echo",
            "Three quacks and a set of ripple rings.",
            DAY_MS, FxKind.QUACK_RIPPLES, StatEffect.NONE
        ),
        VariantRoster.Family.TURTLE to PowerDef(
            VariantRoster.Family.TURTLE, "Shell Shield",
            "Pip tucks in — stat decay pauses for 1 hour.",
            DAY_MS, FxKind.RING_SHIELD, StatEffect.SHIELD_1H
        ),
        VariantRoster.Family.FROG to PowerDef(
            VariantRoster.Family.FROG, "Tongue Catch",
            "Pip snaps up a passing fly. Got it!",
            DAY_MS, FxKind.FLY_CATCH, StatEffect.NONE
        ),
        VariantRoster.Family.PENGUIN to PowerDef(
            VariantRoster.Family.PENGUIN, "Snow Flurry",
            "A mini snowstorm over the stage.",
            DAY_MS, FxKind.SNOW, StatEffect.NONE
        ),
        VariantRoster.Family.PANDA to PowerDef(
            VariantRoster.Family.PANDA, "Bamboo Snack",
            "Pip munches bamboo. Crunch, crunch.",
            SIX_H_MS, FxKind.HEARTS, StatEffect.POWER_SNACK_10
        ),
        VariantRoster.Family.WOLF to PowerDef(
            VariantRoster.Family.WOLF, "Moon Howl",
            "Pip howls at a little moon bubble.",
            DAY_MS, FxKind.MOON_GLOW, StatEffect.NONE
        ),
        VariantRoster.Family.FOX to PowerDef(
            VariantRoster.Family.FOX, "Sneak",
            "Pip goes semi-transparent and sneaks across the stage.",
            DAY_MS, FxKind.SNEAK_FADE, StatEffect.NONE
        ),
        VariantRoster.Family.TIGER to PowerDef(
            VariantRoster.Family.TIGER, "Pounce",
            "A playful pounce with a tiny screen shake.",
            DAY_MS, FxKind.POUNCE, StatEffect.NONE
        ),
        VariantRoster.Family.OWL to PowerDef(
            VariantRoster.Family.OWL, "Night Watch",
            "Pip shows tomorrow's first goal as a reminder.",
            DAY_MS, FxKind.MOON_GLOW, StatEffect.NONE,
            SpecialUi.NIGHT_WATCH
        ),
        VariantRoster.Family.OX_KIN to PowerDef(
            VariantRoster.Family.OX_KIN, "Stampede Stomp",
            "A happy stomp and a ring of dust.",
            DAY_MS, FxKind.STOMP, StatEffect.NONE
        ),
        VariantRoster.Family.SNAKE to PowerDef(
            VariantRoster.Family.SNAKE, "Coil Nap",
            "Pip coils up for a quick, deep rest.",
            DAY_MS, FxKind.COIL_NAP, StatEffect.ENERGY_25
        ),
        VariantRoster.Family.BAT to PowerDef(
            VariantRoster.Family.BAT, "Echo Ping",
            "Sonar rings reveal a hidden sparkle.",
            DAY_MS, FxKind.ECHO_RINGS, StatEffect.NONE
        ),
        VariantRoster.Family.DRAGON to PowerDef(
            VariantRoster.Family.DRAGON, "Ember Spark",
            "Pip breathes tiny sparkles. The screen glitters.",
            DAY_MS, FxKind.SPARKS, StatEffect.NONE
        ),
        VariantRoster.Family.PHOENIX to PowerDef(
            VariantRoster.Family.PHOENIX, "Rebirth",
            "Restores ONE missed streak day. Once a week.",
            WEEK_MS, FxKind.SPARKS, StatEffect.STREAK_RESTORE
        ),
        VariantRoster.Family.UNICORN to PowerDef(
            VariantRoster.Family.UNICORN, "Rainbow Trail",
            "Rainbow particles trail behind Pip for 30 seconds.",
            DAY_MS, FxKind.RAINBOW_TRAIL, StatEffect.NONE
        ),
        VariantRoster.Family.KRAKEN to PowerDef(
            VariantRoster.Family.KRAKEN, "Ink Doodle",
            "Pip draws a tiny ink doodle on the stage.",
            DAY_MS, FxKind.INK_DOODLE, StatEffect.NONE
        ),
        VariantRoster.Family.AXOLOTL to PowerDef(
            VariantRoster.Family.AXOLOTL, "Bubble Parade",
            "Bubbles float up for 20 seconds.",
            DAY_MS, FxKind.BUBBLES, StatEffect.NONE
        ),
        VariantRoster.Family.GRIFFIN to PowerDef(
            VariantRoster.Family.GRIFFIN, "Sky Dive",
            "Pip flies a loop across the stage.",
            DAY_MS, FxKind.SKY_DIVE, StatEffect.NONE
        )
    )

    fun powerFor(family: VariantRoster.Family): PowerDef =
        POWERS[family] ?: POWERS.getValue(VariantRoster.Family.CAT)

    /**
     * Cooldown state. All timestamps are [SystemClock.elapsedRealtime] —
     * monotonic, so a wall-clock change can never shorten a cooldown.
     * The caller persists via [toJson] / [fromJson].
     */
    class Cooldowns(
        private val lastTriggered: MutableMap<VariantRoster.Family, Long> = mutableMapOf(),
        /** Hamster stash: wall of stash time + whether it is still waiting. */
        private var stashAtElapsed: Long = 0L,
        private var stashPending: Boolean = false
    ) {
        companion object {
            const val PREFS_NAME = "pixelpet_powers"
            const val KEY_JSON = "cooldowns_json"
            /** Cheek-stash becomes due 10 minutes after stashing. */
            const val STASH_DUE_MS = 10L * 60L * 1000L
            /** An unconsumed stash expires after 6 hours. */
            const val STASH_EXPIRY_MS = SIX_H_MS

            fun fromJson(json: String): Cooldowns {
                return try {
                    val o = JSONObject(json)
                    if (o.optInt("v", 0) != 1) return Cooldowns()
                    val map = mutableMapOf<VariantRoster.Family, Long>()
                    val cd = o.optJSONObject("cooldowns")
                    if (cd != null) {
                        val keys = cd.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val fam = try {
                                VariantRoster.Family.valueOf(k)
                            } catch (t: Throwable) {
                                null
                            } ?: continue
                            map[fam] = cd.optLong(k, 0L)
                        }
                    }
                    Cooldowns(
                        map,
                        o.optLong("stashAt", 0L),
                        o.optBoolean("stashPending", false)
                    )
                } catch (t: Throwable) {
                    Cooldowns()
                }
            }
        }

        fun canTrigger(family: VariantRoster.Family, nowElapsed: Long): Boolean =
            remainingMs(family, nowElapsed) <= 0L

        /** Milliseconds left on cooldown (0 = ready). */
        fun remainingMs(family: VariantRoster.Family, nowElapsed: Long): Long {
            val last = lastTriggered[family] ?: return 0L
            val def = powerFor(family)
            return (last + def.cooldownMs - nowElapsed).coerceAtLeast(0L)
        }

        /** 1.0 = just triggered, 0.0 = ready. Drives the cooldown ring. */
        fun remainingFraction(family: VariantRoster.Family, nowElapsed: Long): Float {
            val def = powerFor(family)
            if (def.cooldownMs <= 0L) return 0f
            return (remainingMs(family, nowElapsed).toFloat() / def.cooldownMs)
                .coerceIn(0f, 1f)
        }

        /** Record a trigger. Returns null when still cooling down. */
        fun trigger(family: VariantRoster.Family, nowElapsed: Long): PowerDef? {
            if (!canTrigger(family, nowElapsed)) return null
            lastTriggered[family] = nowElapsed
            return powerFor(family)
        }

        /** Human "3h 12m" / "42m" / "25s" rendering of a remaining cooldown. */
        fun remainingLabel(family: VariantRoster.Family, nowElapsed: Long): String {
            val rem = remainingMs(family, nowElapsed)
            if (rem <= 0L) return ""
            val totalSec = rem / 1000L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return when {
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m"
                else -> "${s}s"
            }
        }

        // ---- Hamster cheek stash ----

        fun stashSnack(nowElapsed: Long) {
            stashAtElapsed = nowElapsed
            stashPending = true
        }

        /** True when the stash is due and not yet expired. */
        fun stashDue(nowElapsed: Long): Boolean {
            if (!stashPending) return false
            val age = nowElapsed - stashAtElapsed
            return age in STASH_DUE_MS..STASH_EXPIRY_MS
        }

        fun consumeStash() {
            stashPending = false
            stashAtElapsed = 0L
        }

        fun hasStashPending(): Boolean = stashPending

        fun toJson(): String {
            val o = JSONObject()
            o.put("v", 1)
            val cd = JSONObject()
            for ((fam, ts) in lastTriggered) cd.put(fam.name, ts)
            o.put("cooldowns", cd)
            o.put("stashAt", stashAtElapsed)
            o.put("stashPending", stashPending)
            return o.toString()
        }
    }
}
