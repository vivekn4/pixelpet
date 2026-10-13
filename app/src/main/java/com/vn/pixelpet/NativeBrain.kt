package com.vn.pixelpet

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native Brain (PLAN_V3.md §5) — on-device learning for the pet.
 *
 * EXPLICIT: this is NOT a language model. It is an adaptive rule system —
 * weighted scores + decay + time buckets — the same class of system pro pet
 * games use. It notices patterns and reacts to them. It will never chat,
 * write, or reason. No network, no tokens, no generation. All state lives
 * on the device as one small JSON blob (< 100 KB); deleting app data resets it.
 *
 * What it learns:
 * - Schedule: interaction counts in 6 time buckets (4-hour blocks).
 * - Favorites: per-action counts + the most-completed goal name.
 * - Neglect memory: ring buffer of the last 100 events + longest gap.
 * - Streak sensitivity: care-day history + milestone celebrations.
 *
 * Pure Kotlin: no android.* imports. Persistence is the caller's job via
 * [toJson] / [fromJson] (MainActivity owns the SharedPreferences keys).
 */
class NativeBrain private constructor(
    val variantId: String,
    val firstSeenWallMs: Long,
    private val schedule: IntArray,          // 6 buckets × 7 actions = 42
    private val events: ArrayDeque<Event>,   // ring buffer, max MAX_EVENTS
    private val careDays: MutableSet<String>,
    private val celebratedMilestones: MutableSet<Int>,
    private val goalCounts: MutableMap<String, Int>,
    private var lastInteractionWallMs: Long,
    private var longestGapMs: Long
) {
    enum class Action { PET, PLAY, FEED_GOAL, FEED_SNACK, DOOMSCROLL, WAKE, POWER }

    data class Event(val action: Action, val wallMs: Long, val elapsedMs: Long)

    /** Anticipation scores the UI reads (all 0..1 unless noted). */
    data class Anticipation(
        /** Likelihood the user wants to play right now (schedule + playful). */
        val playInvite: Float,
        /** Likelihood the user usually feeds around now. */
        val hungryFace: Float,
        /** 0 = shy after a long absence, 1 = warm and familiar. */
        val greetingWarmth: Float,
        /** True when a streak milestone was hit today and not yet celebrated. */
        val streakCelebration: Boolean,
        /** The action the user does most (null until enough data). */
        val favoriteAction: Action?
    )

    companion object {
        const val BUCKETS = 6
        const val MAX_EVENTS = 100
        /** Learning becomes visible after ~3 days of use. */
        const val LEARNING_PERIOD_MS = 3L * 24L * 3600L * 1000L
        /** Minimum events before a signal counts (avoids one-off noise). */
        const val MIN_EVENTS_FOR_SIGNAL = 3
        /** Minimum share of a bucket's action before it becomes a "learned" line. */
        const val LEARNED_SHARE = 0.5f
        const val PREFS_NAME = "pixelpet_brain"
        const val KEY_JSON = "brain_json"

        val STREAK_MILESTONES = intArrayOf(7, 14, 21, 30, 60, 90, 100)

        private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun bucketOf(wallMs: Long): Int {
            val hour = Calendar.getInstance().apply { timeInMillis = wallMs }
                .get(Calendar.HOUR_OF_DAY)
            return (hour / 4).coerceIn(0, BUCKETS - 1)
        }

        fun dateString(wallMs: Long): String = DAY_FMT.format(java.util.Date(wallMs))

        fun fresh(variantId: String, nowWallMs: Long): NativeBrain = NativeBrain(
            variantId = variantId,
            firstSeenWallMs = nowWallMs,
            schedule = IntArray(BUCKETS * Action.values().size),
            events = ArrayDeque(),
            careDays = mutableSetOf(),
            celebratedMilestones = mutableSetOf(),
            goalCounts = mutableMapOf(),
            lastInteractionWallMs = nowWallMs,
            longestGapMs = 0L
        )

        /** Null on malformed JSON — the caller falls back to [fresh]. */
        fun fromJson(json: String, variantId: String): NativeBrain? {
            return try {
                val o = JSONObject(json)
                if (o.optInt("v", 0) != 1) return null
                val schedule = IntArray(BUCKETS * Action.values().size)
                val arr = o.optJSONArray("schedule") ?: return null
                for (i in 0 until minOf(arr.length(), schedule.size)) {
                    schedule[i] = arr.optInt(i, 0)
                }
                val events = ArrayDeque<Event>()
                val evArr = o.optJSONArray("events")
                if (evArr != null) {
                    for (i in 0 until evArr.length()) {
                        val e = evArr.optJSONObject(i) ?: continue
                        val a = Action.values().getOrNull(e.optInt("a", -1)) ?: continue
                        events.addLast(
                            Event(a, e.optLong("w", 0L), e.optLong("e", 0L))
                        )
                    }
                }
                val careDays = mutableSetOf<String>()
                val cdArr = o.optJSONArray("careDays")
                if (cdArr != null) {
                    for (i in 0 until cdArr.length()) careDays.add(cdArr.optString(i))
                }
                val celebrated = mutableSetOf<Int>()
                val celArr = o.optJSONArray("celebrated")
                if (celArr != null) {
                    for (i in 0 until celArr.length()) celebrated.add(celArr.optInt(i, -1))
                }
                val goalCounts = mutableMapOf<String, Int>()
                val gcObj = o.optJSONObject("goalCounts")
                if (gcObj != null) {
                    val keys = gcObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        goalCounts[k] = gcObj.optInt(k, 0)
                    }
                }
                NativeBrain(
                    variantId = variantId,
                    firstSeenWallMs = o.optLong("firstSeen", 0L),
                    schedule = schedule,
                    events = events,
                    careDays = careDays,
                    celebratedMilestones = celebrated,
                    goalCounts = goalCounts,
                    lastInteractionWallMs = o.optLong("lastInteraction", 0L),
                    longestGapMs = o.optLong("longestGap", 0L)
                )
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun slot(bucket: Int, action: Action): Int =
        bucket * Action.values().size + action.ordinal

    /** Record one user interaction. Call from the action handlers. */
    fun record(action: Action, nowWallMs: Long, nowElapsedMs: Long) {
        val bucket = bucketOf(nowWallMs)
        schedule[slot(bucket, action)]++
        val gap = nowWallMs - lastInteractionWallMs
        if (gap > longestGapMs) longestGapMs = gap
        lastInteractionWallMs = nowWallMs
        events.addLast(Event(action, nowWallMs, nowElapsedMs))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    /** Record a care day (a goal completed that day). Prunes to ~120 days. */
    fun recordCareDay(dateString: String) {
        careDays.add(dateString)
        if (careDays.size > 150) {
            careDays.sorted().take(careDays.size - 120).forEach { careDays.remove(it) }
        }
    }

    /** Track which goal the user completes most (single most-completed name). */
    fun recordFavoriteGoal(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        goalCounts[trimmed] = (goalCounts[trimmed] ?: 0) + 1
        // Cap the map: drop the smallest count so state stays tiny.
        if (goalCounts.size > 20) {
            val min = goalCounts.entries.minByOrNull { it.value }?.key
            if (min != null) goalCounts.remove(min)
        }
    }

    /** Consecutive care days ending today or yesterday (0 when no recent care). */
    fun currentStreak(nowWallMs: Long): Int {
        var streak = 0
        // Allow "yesterday" as the streak anchor: a streak broken only by today
        // not having happened yet still counts.
        var cursor = Calendar.getInstance().apply { timeInMillis = nowWallMs }
        if (dateString(nowWallMs) !in careDays) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
            if (dateString(cursor.timeInMillis) !in careDays) return 0
        }
        while (dateString(cursor.timeInMillis) in careDays) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /** True while the pet is still in its ~3-day getting-to-know-you period. */
    fun isStillLearning(nowWallMs: Long): Boolean =
        nowWallMs - firstSeenWallMs < LEARNING_PERIOD_MS

    /** How warmly the pet should greet the user (0 = shy, 1 = warm). */
    fun greetingWarmth(nowWallMs: Long, personality: Personality?): Float {
        val gap = nowWallMs - lastInteractionWallMs
        var base = when {
            gap > 48L * 3600L * 1000L -> 0.15f
            gap > 24L * 3600L * 1000L -> 0.35f
            gap > 6L * 3600L * 1000L -> 0.6f
            else -> 0.9f
        }
        // Warm-up: recent interactions rebuild familiarity fast.
        val recent = events.count { it.wallMs > nowWallMs - 3600L * 1000L }
        base = (base + 0.08f * recent).coerceAtMost(1f)
        // Shy pets stay a little reserved even after warm-up.
        val bold = personality?.trait(Personality.Trait.BOLD) ?: 50
        val shynessFactor = 1f - (100 - bold) / 100f * 0.3f
        return (base * shynessFactor).coerceIn(0.05f, 1f)
    }

    /** Full anticipation snapshot for this moment. */
    fun anticipation(nowWallMs: Long, personality: Personality?): Anticipation {
        val bucket = bucketOf(nowWallMs)
        val playful = personality?.trait(Personality.Trait.PLAYFUL) ?: 50
        val playfulFactor = 0.75f + (playful - 50) / 100f * 0.5f

        var playTotal = 0
        var feedTotal = 0
        for (b in 0 until BUCKETS) {
            playTotal += schedule[slot(b, Action.PLAY)]
            feedTotal += schedule[slot(b, Action.FEED_GOAL)] + schedule[slot(b, Action.FEED_SNACK)]
        }
        val playInBucket = schedule[slot(bucket, Action.PLAY)]
        val feedInBucket = schedule[slot(bucket, Action.FEED_GOAL)] +
            schedule[slot(bucket, Action.FEED_SNACK)]
        val playInvite = if (playTotal < MIN_EVENTS_FOR_SIGNAL) 0f
        else (playInBucket.toFloat() / playTotal * playfulFactor).coerceIn(0f, 1f)
        val hungryFace = if (feedTotal < MIN_EVENTS_FOR_SIGNAL) 0f
        else (feedInBucket.toFloat() / feedTotal).coerceIn(0f, 1f)

        val streak = currentStreak(nowWallMs)
        val streakCelebration = streak in STREAK_MILESTONES &&
            streak !in celebratedMilestones

        val fav = favoriteAction()
        return Anticipation(
            playInvite = playInvite,
            hungryFace = hungryFace,
            greetingWarmth = greetingWarmth(nowWallMs, personality),
            streakCelebration = streakCelebration,
            favoriteAction = fav
        )
    }

    /** Mark a streak milestone as celebrated (call after the celebration plays). */
    fun markMilestoneCelebrated(milestone: Int) {
        celebratedMilestones.add(milestone)
    }

    /** The action the user does most (null until enough data). */
    fun favoriteAction(): Action? {
        val candidates = listOf(Action.PET, Action.PLAY, Action.FEED_GOAL, Action.FEED_SNACK)
        var best: Action? = null
        var bestCount = 0
        for (a in candidates) {
            var total = 0
            for (b in 0 until BUCKETS) total += schedule[slot(b, a)]
            if (total > bestCount) { bestCount = total; best = a }
        }
        return if (bestCount >= MIN_EVENTS_FOR_SIGNAL) best else null
    }

    /** Most-completed goal name + count (null until one goal hit 3 completions). */
    fun favoriteGoal(): Pair<String, Int>? {
        val top = goalCounts.entries.maxByOrNull { it.value } ?: return null
        return if (top.value >= MIN_EVENTS_FOR_SIGNAL) top.key to top.value else null
    }

    /**
     * Honest "Pip learned" lines for the Personality card. Empty while still
     * learning — the UI shows the "still getting to know you" copy instead.
     * Every line is emitted only when the data actually supports it.
     */
    fun learnedLines(nowWallMs: Long): List<String> {
        if (isStillLearning(nowWallMs)) return emptyList()
        val lines = mutableListOf<String>()
        val bucketName = arrayOf(
            "late at night", "early in the morning", "in the mornings",
            "in the afternoons", "in the evenings", "at night"
        )
        // Schedule: strongest PLAY bucket.
        var bestBucket = -1
        var bestPlay = 0
        var playTotal = 0
        for (b in 0 until BUCKETS) {
            val c = schedule[slot(b, Action.PLAY)]
            playTotal += c
            if (c > bestPlay) { bestPlay = c; bestBucket = b }
        }
        if (bestBucket >= 0 && playTotal >= 5 &&
            bestPlay.toFloat() / playTotal >= LEARNED_SHARE
        ) {
            lines.add("Pip noticed you usually play ${bucketName[bestBucket]}.")
        }
        // Schedule: strongest FEED bucket.
        var bestFeedBucket = -1
        var bestFeed = 0
        var feedTotal = 0
        for (b in 0 until BUCKETS) {
            val c = schedule[slot(b, Action.FEED_GOAL)] + schedule[slot(b, Action.FEED_SNACK)]
            feedTotal += c
            if (c > bestFeed) { bestFeed = c; bestFeedBucket = b }
        }
        if (bestFeedBucket >= 0 && feedTotal >= 5 &&
            bestFeed.toFloat() / feedTotal >= LEARNED_SHARE
        ) {
            lines.add("Pip noticed you usually feed ${bucketName[bestFeedBucket]}.")
        }
        // Favorite goal.
        favoriteGoal()?.let { (name, count) ->
            lines.add("Pip's favorite goal is \"$name\" — you've finished it $count times.")
        }
        // Neglect memory: longest absence, stated gently and factually.
        val gapDays = (longestGapMs / (24L * 3600L * 1000L)).toInt()
        if (gapDays >= 2) {
            lines.add("Pip remembers being apart for $gapDays days once — and being happy you came back.")
        }
        // Streak pride.
        val streak = currentStreak(nowWallMs)
        if (streak >= 7) {
            lines.add("Pip is proud of your $streak-day care streak.")
        }
        return lines.take(4)
    }

    /** Compact JSON for persistence (< 100 KB even at full history). */
    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("variantId", variantId)
        o.put("firstSeen", firstSeenWallMs)
        val sched = JSONArray()
        for (v in schedule) sched.put(v)
        o.put("schedule", sched)
        val evArr = JSONArray()
        for (e in events) {
            evArr.put(
                JSONObject()
                    .put("a", e.action.ordinal)
                    .put("w", e.wallMs)
                    .put("e", e.elapsedMs)
            )
        }
        o.put("events", evArr)
        val cdArr = JSONArray()
        for (d in careDays) cdArr.put(d)
        o.put("careDays", cdArr)
        val celArr = JSONArray()
        for (m in celebratedMilestones) celArr.put(m)
        o.put("celebrated", celArr)
        val gcObj = JSONObject()
        for ((k, v) in goalCounts) gcObj.put(k, v)
        o.put("goalCounts", gcObj)
        o.put("lastInteraction", lastInteractionWallMs)
        o.put("longestGap", longestGapMs)
        return o.toString()
    }
}
