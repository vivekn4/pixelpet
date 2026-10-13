package com.vn.pixelpet

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A user-defined real-life goal. Completing one feeds the pet.
 * Serialized with framework org.json — no libraries needed.
 */
data class Goal(
    val id: String,
    val name: String,
    val daily: Boolean,
    val streak: Int,
    /** yyyy-MM-dd of last completion, "" if never. */
    val lastDoneDate: String
) {
    /**
     * Done for the current cycle. Daily goals reset each day; one-shot goals
     * are done once completed. Mirrors the alreadyDone logic in
     * PetRepository.completeGoal. (v3 Phase 4: added — MainActivity Night
     * Watch lookup referenced this property but it did not exist.)
     */
    val completed: Boolean
        get() = if (daily) lastDoneDate == todayString() else lastDoneDate.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("daily", daily)
        .put("streak", streak)
        .put("lastDoneDate", lastDoneDate)

    companion object {
        private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun todayString(): String = DAY_FMT.format(Date())

        fun yesterdayString(): String {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_YEAR, -1)
            return DAY_FMT.format(c.time)
        }

        fun create(name: String, daily: Boolean): Goal = Goal(
            id = UUID.randomUUID().toString(),
            name = name,
            daily = daily,
            streak = 0,
            lastDoneDate = ""
        )

        fun fromJson(o: JSONObject): Goal = Goal(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Untitled goal"),
            daily = o.optBoolean("daily", true),
            streak = o.optInt("streak", 0),
            lastDoneDate = o.optString("lastDoneDate", "")
        )

        fun listToJson(goals: List<Goal>): String {
            val arr = JSONArray()
            goals.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(raw: String): List<Goal> {
            if (raw.isBlank()) return emptyList()
            val out = mutableListOf<Goal>()
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                out.add(fromJson(arr.getJSONObject(i)))
            }
            return out
        }
    }
}
