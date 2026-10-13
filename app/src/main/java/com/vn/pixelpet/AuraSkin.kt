package com.vn.pixelpet

import android.content.SharedPreferences

/**
 * Premium aura skins (v3 Phase 4, PLAN_V3.md §10).
 *
 * Premium changes LOOK only, never powers or progression (v3 rule). An aura
 * skin restyles the glow particles [PetRenderer] draws around the pet — and
 * nothing else. Stats, cooldowns, unlocks, and progression are untouched.
 *
 * In v3 the aura applies to ALL 108 variants for premium users (v2 limited
 * it to Mystical species). Non-premium users get no aura at all.
 *
 * Selection is stored in the same SharedPreferences file the repo uses
 * ("pixelpet"). Key validation lives in [PremiumManager] and is untouched.
 */
enum class AuraSkin(val label: String) {
    /** v2 default: 8 gold dots orbiting the pet. */
    GOLD_ORBIT("Gold Orbit"),
    /** Dots cycle through the rainbow. */
    RAINBOW("Rainbow Trail"),
    /** Warm orange/red ember dots, slightly larger. */
    EMBER("Ember Glow"),
    /** White/blue sparkles. */
    STARLIGHT("Starlight"),
    /** Premium users can turn the aura off. */
    NONE("No Aura");

    companion object {
        private const val KEY_AURA_SKIN = "aura_skin"

        /** Stored selection, or [GOLD_ORBIT] when nothing is stored yet. */
        fun selected(prefs: SharedPreferences): AuraSkin {
            val raw = prefs.getString(KEY_AURA_SKIN, null) ?: return GOLD_ORBIT
            return values().find { it.name == raw } ?: GOLD_ORBIT
        }

        /** Store a selection. Honored only for premium users (see [effective]). */
        fun set(prefs: SharedPreferences, skin: AuraSkin) {
            prefs.edit().putString(KEY_AURA_SKIN, skin.name).apply()
        }

        /**
         * The skin to actually draw, or null when no aura should be drawn
         * (not premium, or the user picked [NONE]). Callers gate on null.
         */
        fun effective(prefs: SharedPreferences): AuraSkin? {
            if (!PremiumManager.isPremium(prefs)) return null
            val skin = selected(prefs)
            return if (skin == NONE) null else skin
        }
    }
}
