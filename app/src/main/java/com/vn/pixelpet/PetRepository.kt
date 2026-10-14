package com.vn.pixelpet

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single source of truth for ALL pet state. The app, the home-screen widget,
 * and the live wallpaper all read snapshots from here and mutate only through
 * its methods — nobody keeps stat copies (see FLOW.md §f).
 *
 * v2: 4 stats (hunger/happiness/energy/attention), sleep mode (0.1× decay),
 * care streaks, achievements, species unlocks, premium entitlements,
 * per-pet frozen states (inactive pets don't tick — "pet daycare").
 */
class PetRepository private constructor(appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()

    // ---- hardening: avoid redundant JSON parse + disk writes ----
    // snapshot() is called on hot paths (wallpaper 2x/sec, widget refresh,
    // every haptic check). The pet blob only changes via persist(), so cache
    // the last parsed state keyed by its blob: same blob -> same state, no
    // re-parse. persist() keeps the cache in sync. PetState is immutable, so
    // sharing the cached instance is safe (tick() always copies).
    @Volatile private var cachedBlob: String? = null
    private var cachedState: PetState? = null

    // ---- tuning ----
    private val hungerPerHr = 4f
    private val happyPerHr = 3f
    private val energyPerHr = 2f
    private val attentionPerHr = 6f
    private val nightRegenPerHr = 6f
    private val sleepMultiplier = 0.1f
    private val maxDecayHours = 48f

    private fun nowElapsed() = SystemClock.elapsedRealtime()
    private fun nowWall() = System.currentTimeMillis()

    // ============ active pet state ============

    private fun activeId(): String = prefs.getString(K_ACTIVE, "cat") ?: "cat"

    private fun readRaw(id: String = activeId()): PetState {
        val e = nowElapsed(); val w = nowWall()
        val blob = prefs.getString(K_PET + id, null)
        if (blob != null) {
            synchronized(this) {
                if (blob == cachedBlob && cachedState != null && cachedState?.speciesId == id) {
                    return cachedState!!
                }
            }
            try {
                val parsed = petFromJson(JSONObject(blob), e, w)
                synchronized(this) { cachedBlob = blob; cachedState = parsed }
                return parsed
            } catch (t: Throwable) { /* fall through to fresh */ }
        }
        return PetState.fresh(id, e, w)
    }

    private fun persist(s: PetState) {
        val json = petToJson(s).toString()
        prefs.edit().putString(K_PET + s.speciesId, json).apply()
        // Keep the parse cache in sync (apply() commits to in-memory prefs
        // first, so a concurrent readRaw sees the new blob and matches).
        synchronized(this) { cachedBlob = json; cachedState = s }
    }

    private fun petToJson(s: PetState): JSONObject = JSONObject()
        .put("h", s.hunger).put("ha", s.happiness).put("e", s.energy).put("a", s.attention)
        .put("te", s.lastTickElapsed).put("tw", s.lastTickWall)
        .put("sulk", s.sulkUntilElapsed).put("m", s.muted).put("sp", s.speciesId)
        .put("sl", s.sleeping).put("sse", s.sleepStartElapsed)
        .put("sick", s.sickSinceWall).put("snk", s.lastSnackElapsed).put("ply", s.lastPlayElapsed)
        .put("shld", s.shieldUntilElapsed)

    private fun petFromJson(o: JSONObject, e: Long, w: Long): PetState = PetState(
        hunger = o.optInt("h", 80), happiness = o.optInt("ha", 80),
        energy = o.optInt("e", 90), attention = o.optInt("a", 80),
        lastTickElapsed = o.optLong("te", e), lastTickWall = o.optLong("tw", w),
        sulkUntilElapsed = o.optLong("sulk", 0L), muted = o.optBoolean("m", false),
        speciesId = o.optString("sp", "cat"),
        sleeping = o.optBoolean("sl", false), sleepStartElapsed = o.optLong("sse", 0L),
        shieldUntilElapsed = o.optLong("shld", 0L),
        sickSinceWall = o.optLong("sick", 0L),
        lastSnackElapsed = o.optLong("snk", 0L), lastPlayElapsed = o.optLong("ply", 0L)
    )

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 7
    }

    /** Apply time decay; sleeping pets decay at 10%. */
    private fun tick(raw: PetState): PetState {
        val e = nowElapsed(); val w = nowWall()
        var dtH = (e - raw.lastTickElapsed) / 3600000f
        if (dtH < 0 || dtH > maxDecayHours) {
            dtH = ((w - raw.lastTickWall) / 3600000f).coerceIn(0f, maxDecayHours)
        }
        if (dtH <= 0f) return raw.copy(lastTickElapsed = e, lastTickWall = w)
        // v3 Phase 3 — Turtle Shell Shield: while shielded, stats do not move at
        // all (decay paused). Timestamps still advance so the shield can expire.
        if (e < raw.shieldUntilElapsed) return raw.copy(lastTickElapsed = e, lastTickWall = w)
        val mult = if (raw.sleeping) sleepMultiplier else 1f
        val energyDelta = if (isNight()) nightRegenPerHr * dtH else -energyPerHr * dtH * mult
        val happyRate = happyPerHr * mult * (if (raw.attention == 0) 2f else 1f)
        val wasHungry = raw.hunger > 0
        val next = raw.copy(
            hunger = (raw.hunger - hungerPerHr * dtH * mult).toInt().coerceIn(0, 100),
            happiness = (raw.happiness - happyRate * dtH).toInt().coerceIn(0, 100),
            energy = (raw.energy + energyDelta).toInt().coerceIn(0, 100),
            attention = (raw.attention - attentionPerHr * dtH * mult).toInt().coerceIn(0, 100),
            lastTickElapsed = e, lastTickWall = w
        )
        val sick = if (next.hunger == 0 && wasHungry) w
        else if (next.hunger > 0) 0L else next.sickSinceWall
        val final = next.copy(sickSinceWall = sick)
        // Hardening: skip the disk write when only the tick timestamps moved
        // (stats identical). The next tick recomputes decay from the stored
        // baseline, so skipping is decay-neutral — it just avoids a storage
        // write on every snapshot() when nothing actually changed. Callers
        // that mutate state (pet/snack/play/...) persist explicitly.
        if (final.copy(lastTickElapsed = raw.lastTickElapsed, lastTickWall = raw.lastTickWall) == raw) {
            return final
        }
        persist(final)
        return final
    }

    /** Live snapshot of the ACTIVE pet, decay applied. Main-thread safe. */
    fun snapshot(): PetState = tick(readRaw())

    // ============ care actions ============

    fun setMuted(muted: Boolean) {
        exec.execute { persist(readRaw().copy(muted = muted)) }
    }

    /** Goal completion: the main feeding loop. */
    fun feedForGoal(): PetState {
        val s = tick(readRaw())
        val next = s.copy(
            hunger = (s.hunger + 20).coerceIn(0, 100),
            happiness = (s.happiness + 15).coerceIn(0, 100),
            energy = (s.energy + 5).coerceIn(0, 100),
            attention = (s.attention + 5).coerceIn(0, 100),
            sickSinceWall = 0L
        )
        persist(next)
        recordCareDay()
        bumpTotalGoals()
        return next
    }

    /** Quick snack: +10 hunger, 15-min cooldown. Returns null if on cooldown. */
    fun snack(): PetState? {
        val s = tick(readRaw())
        if (nowElapsed() - s.lastSnackElapsed < 15 * 60 * 1000L) return null
        val next = s.copy(
            hunger = (s.hunger + 10).coerceIn(0, 100),
            lastSnackElapsed = nowElapsed(), sickSinceWall = 0L
        )
        persist(next)
        return next
    }

    /** Play: costs energy, big happiness + attention. 5-min cooldown. Null if cooling/can't. */
    fun play(): PetState? {
        val s = tick(readRaw())
        if (nowElapsed() - s.lastPlayElapsed < 5 * 60 * 1000L) return null
        if (s.energy < 10 || s.sleeping) return null
        val next = s.copy(
            energy = (s.energy - 10).coerceIn(0, 100),
            happiness = (s.happiness + 20).coerceIn(0, 100),
            attention = (s.attention + 10).coerceIn(0, 100),
            lastPlayElapsed = nowElapsed()
        )
        persist(next)
        return next
    }

    /** Petting: the attention loop. Always works (no cooldown — love is free). */
    fun pet(): PetState {
        val s = tick(readRaw())
        val next = s.copy(
            attention = (s.attention + 25).coerceIn(0, 100),
            happiness = (s.happiness + 8).coerceIn(0, 100)
        )
        persist(next)
        return next
    }

    fun logDoomscroll(): PetState {
        val s = tick(readRaw())
        val next = s.copy(
            happiness = (s.happiness - 15).coerceIn(0, 100),
            energy = (s.energy - 5).coerceIn(0, 100),
            sulkUntilElapsed = nowElapsed() + 30 * 60 * 1000L
        )
        persist(next)
        return next
    }

    // ============ v3 Phase 3: power stat effects (additive) ============

    /** Cat Purr Heal: clear the sick flag + a happiness lift. */
    fun cureSickness(): PetState {
        val s = tick(readRaw())
        val next = s.copy(
            sickSinceWall = 0L,
            happiness = (s.happiness + 10).coerceIn(0, 100)
        )
        persist(next)
        return next
    }

    /** Panda Bamboo Snack / Hamster stash: +hunger with no snack-cooldown interaction. */
    fun feedPowerBonus(amount: Int): PetState {
        val s = tick(readRaw())
        val next = s.copy(
            hunger = (s.hunger + amount).coerceIn(0, 100),
            sickSinceWall = 0L
        )
        persist(next)
        return next
    }

    /** Snake Coil Nap: fast energy restore. */
    fun feedEnergy(amount: Int): PetState {
        val s = tick(readRaw())
        val next = s.copy(energy = (s.energy + amount).coerceIn(0, 100))
        persist(next)
        return next
    }

    /** Bunny Lucky Hop / Pig Mud Bath: small mood lift. */
    fun addHappiness(amount: Int): PetState {
        val s = tick(readRaw())
        val next = s.copy(happiness = (s.happiness + amount).coerceIn(0, 100))
        persist(next)
        return next
    }

    /** Turtle Shell Shield: pause stat decay for [durationMs] (elapsedRealtime). */
    fun activateShield(durationMs: Long): PetState {
        val s = tick(readRaw())
        val next = s.copy(shieldUntilElapsed = nowElapsed() + durationMs)
        persist(next)
        return next
    }

    /**
     * Phoenix Rebirth support: true only when EXACTLY one day was missed —
     * last care was the day before yesterday. More than one missed day, or no
     * missed day, cannot be restored (the power restores ONE day).
     */
    fun canRestoreStreakDay(): Boolean {
        val last = prefs.getString(K_LAST_CARE, "") ?: ""
        if (last.isEmpty()) return false
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val twoDaysAgo = fmt.format(cal.time)
        return last == twoDaysAgo
    }

    /**
     * Phoenix Rebirth: restore exactly one missed streak day. Sets last-care
     * to yesterday and bumps the streak by one, so the next care day continues
     * the streak instead of resetting it to 1. Returns false (no change) when
     * there is no single missed day to restore.
     */
    fun restoreMissedStreakDay(): Boolean {
        if (!canRestoreStreakDay()) return false
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val yesterday = fmt.format(cal.time)
        val streak = prefs.getInt(K_STREAK, 0) + 1
        prefs.edit().putString(K_LAST_CARE, yesterday).putInt(K_STREAK, streak).apply()
        return true
    }

    // ============ sleep / wake ============

    fun enterSleep() {
        exec.execute {
            val s = tick(readRaw())
            if (!s.sleeping) persist(s.copy(sleeping = true, sleepStartElapsed = nowElapsed()))
        }
    }

    /**
     * Wake: returns a recap of what decayed while asleep, for the
     * "while you were away…" line. Empty string if barely anything happened.
     */
    fun wake(): String {
        val before = readRaw()
        val sleptMs = if (before.sleepStartElapsed > 0) nowElapsed() - before.sleepStartElapsed else 0L
        val s = tick(before.copy(sleeping = false, sleepStartElapsed = 0L))
        persist(s)
        if (sleptMs < 5 * 60 * 1000L) return ""
        val mins = (sleptMs / 60000).toInt()
        val bits = mutableListOf<String>()
        if (s.hunger < before.hunger) bits.add("hunger −${before.hunger - s.hunger}")
        if (s.attention < 25) bits.add("Pip missed you")
        val h = mins / 60; val m = mins % 60
        val dur = if (h > 0) "${h}h ${m}m" else "${m}m"
        return "While you were away ($dur): " + (bits.ifEmpty { listOf("all good") }.joinToString(", ")) + "."
    }

    // ============ streaks & achievements ============

    fun careStreak(): Int = prefs.getInt(K_STREAK, 0)
    fun totalGoals(): Int = prefs.getInt(K_TOTAL_GOALS, 0)

    private fun recordCareDay() {
        exec.execute {
            val today = Goal.todayString()
            val last = prefs.getString(K_LAST_CARE, "") ?: ""
            if (last == today) return@execute
            val streak = if (last == Goal.yesterdayString()) prefs.getInt(K_STREAK, 0) + 1 else 1
            prefs.edit().putString(K_LAST_CARE, today).putInt(K_STREAK, streak).apply()
        }
    }

    private fun bumpTotalGoals() {
        exec.execute { prefs.edit().putInt(K_TOTAL_GOALS, totalGoals() + 1).apply() }
    }

    // ============ species & unlocks ============

    fun unlockedSpecies(): Set<String> {
        val raw = prefs.getString(K_UNLOCKED, "cat,dog,bunny,hamster") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun isUnlocked(species: Species): Boolean =
        species.id in unlockedSpecies() || PremiumManager.isPremium(prefs)

    fun unlockSpecies(species: Species) {
        val set = unlockedSpecies().toMutableSet()
        if (set.add(species.id)) {
            prefs.edit().putString(K_UNLOCKED, set.joinToString(",")).apply()
        }
    }

    /** Switch active pet. Returns the (fresh or resumed) state of the new pet. */
    fun selectSpecies(id: String): PetState {
        val species = Species.byId(id)
        require(isUnlocked(species)) { "species locked" }
        persist(readRaw()) // freeze current pet ("daycare")
        prefs.edit().putString(K_ACTIVE, id).apply()
        val e = nowElapsed(); val w = nowWall()
        val existing = prefs.getString(K_PET + id, null)
        val next = if (existing != null) {
            try { tick(petFromJson(JSONObject(existing), e, w)) }
            catch (t: Throwable) { PetState.fresh(id, e, w).also { persist(it) } }
        } else {
            PetState.fresh(id, e, w).also { persist(it) }
        }
        return next
    }

    // ============ goals (v1, unchanged semantics) ============

    fun getGoals(): List<Goal> {
        val raw = prefs.getString(K_GOALS, "") ?: ""
        return try {
            Goal.listFromJson(raw)
        } catch (t: Throwable) {
            prefs.edit()
                .putString(K_GOALS_CORRUPT + System.currentTimeMillis(), raw)
                .putString(K_GOALS, "")
                .apply()
            emptyList()
        }
    }

    private fun saveGoals(goals: List<Goal>) {
        prefs.edit().putString(K_GOALS, Goal.listToJson(goals)).apply()
    }

    fun addGoal(name: String, daily: Boolean) {
        exec.execute {
            val goals = getGoals().toMutableList()
            goals.add(Goal.create(name.trim().take(60), daily))
            saveGoals(goals)
        }
    }

    fun deleteGoal(id: String) {
        exec.execute { saveGoals(getGoals().filter { it.id != id }) }
    }

    fun completeGoal(id: String): Boolean {
        val today = Goal.todayString()
        val goals = getGoals().toMutableList()
        val i = goals.indexOfFirst { it.id == id }
        if (i < 0) return false
        val g = goals[i]
        val alreadyDone = if (g.daily) g.lastDoneDate == today else g.lastDoneDate.isNotEmpty()
        if (alreadyDone) return false
        val streak = if (g.lastDoneDate == Goal.yesterdayString()) g.streak + 1 else 1
        goals[i] = g.copy(lastDoneDate = today, streak = streak)
        saveGoals(goals)
        feedForGoal()
        return true
    }

    // ============ misc prefs ============

    var autoDetectEnabled: Boolean
        get() = prefs.getBoolean(K_AUTODETECT, false)
        set(v) = prefs.edit().putBoolean(K_AUTODETECT, v).apply()

    var lastAutoLogDate: String
        get() = prefs.getString(K_AUTOLOG_DATE, "") ?: ""
        set(v) = prefs.edit().putString(K_AUTOLOG_DATE, v).apply()

    var lastAttentionNudge: Long
        get() = prefs.getLong(K_NUDGE, 0L)
        set(v) = prefs.edit().putLong(K_NUDGE, v).apply()

    /** Wake recap awaiting display; cleared when shown. */
    var lastRecap: String
        get() = prefs.getString(K_RECAP, "") ?: ""
        set(v) = prefs.edit().putString(K_RECAP, v).apply()

    /** Exposed for PremiumManager/UnlockManager entitlement checks. */
    fun prefsForPremium(): SharedPreferences = prefs

    companion object {
        private const val PREFS = "pixelpet"
        private const val K_PET = "pet_"
        private const val K_ACTIVE = "active_species"
        private const val K_UNLOCKED = "unlocked_species"
        private const val K_STREAK = "care_streak"
        private const val K_LAST_CARE = "last_care_date"
        private const val K_TOTAL_GOALS = "total_goals"
        private const val K_GOALS = "goals"
        private const val K_GOALS_CORRUPT = "goals_corrupt_"
        private const val K_AUTODETECT = "autodetect"
        private const val K_AUTOLOG_DATE = "autolog_date"
        private const val K_NUDGE = "last_nudge"
        private const val K_RECAP = "last_recap"

        @Volatile
        private var instance: PetRepository? = null

        fun get(context: Context): PetRepository =
            instance ?: synchronized(this) {
                instance ?: PetRepository(context.applicationContext).also { instance = it }
            }
    }
}
