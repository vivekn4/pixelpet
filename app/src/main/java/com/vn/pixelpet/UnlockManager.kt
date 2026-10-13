package com.vn.pixelpet

/**
 * Evaluates care streaks and achievements against unlock rules.
 * Call [checkUnlocks] after any care action; it returns the species that
 * became unlocked by THIS call so the UI can celebrate each one.
 */
object UnlockManager {

    /**
     * Wild unlock thresholds: species -> required care-streak days.
     * Mystical achievement paths (premium handled by PremiumManager).
     */
    private val wildStreaks = mapOf(
        Species.WOLF to 7,
        Species.FOX to 14,
        Species.TIGER to 21,
        Species.OWL to 30
    )

    private const val DRAGON_STREAK = 60
    private const val UNICORN_STREAK = 90
    private const val PHOENIX_GOALS = 100

    data class Unlock(val species: Species, val reason: String)

    fun checkUnlocks(repo: PetRepository): List<Unlock> {
        val out = mutableListOf<Unlock>()
        val streak = repo.careStreak()
        val goals = repo.totalGoals()
        val premium = PremiumManager.isPremium(repo.prefsForPremium())

        fun grant(s: Species, reason: String) {
            if (!repo.isUnlocked(s)) {
                repo.unlockSpecies(s)
                out.add(Unlock(s, reason))
            }
        }

        for ((species, need) in wildStreaks) {
            if (streak >= need) grant(species, "$need-day care streak!")
        }
        if (streak >= DRAGON_STREAK) grant(Species.DRAGON, "$DRAGON_STREAK-day streak — legendary!")
        if (streak >= UNICORN_STREAK) grant(Species.UNICORN, "$UNICORN_STREAK-day streak — mythical!")
        if (goals >= PHOENIX_GOALS) grant(Species.PHOENIX, "$PHOENIX_GOALS lifetime goals!")
        if (premium) {
            // Premium shortcut for every premium-path species.
            for (s in listOf(Species.DRAGON, Species.PHOENIX, Species.UNICORN, Species.KRAKEN)) {
                grant(s, "Premium 👑")
            }
            for ((species, _) in wildStreaks) grant(species, "Premium 👑")
        }
        return out
    }

    /** Next unlock to show as "progress" in the species picker. Null if all done. */
    fun nextUnlock(repo: PetRepository): String? {
        val streak = repo.careStreak()
        val goals = repo.totalGoals()
        val pending = mutableListOf<Pair<Int, String>>()
        for ((s, need) in wildStreaks) {
            if (!repo.isUnlocked(s)) pending.add(need - streak to "${s.displayName}: ${need - streak}d streak to go")
        }
        if (!repo.isUnlocked(Species.DRAGON)) pending.add(DRAGON_STREAK - streak to "Dragon: ${DRAGON_STREAK - streak}d streak (or 👑)")
        if (!repo.isUnlocked(Species.UNICORN)) pending.add(UNICORN_STREAK - streak to "Unicorn: ${UNICORN_STREAK - streak}d streak (or 👑)")
        if (!repo.isUnlocked(Species.PHOENIX)) pending.add(PHOENIX_GOALS - goals to "Phoenix: ${PHOENIX_GOALS - goals} goals (or 👑)")
        if (!repo.isUnlocked(Species.KRAKEN)) pending.add(Int.MAX_VALUE to "Kraken: 👑 Premium only")
        return pending.minByOrNull { it.first }?.second
    }
}
