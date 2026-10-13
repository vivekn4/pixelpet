package com.vn.pixelpet

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.vn.pixelpet.widget.PixelPetWidgetProvider

class GoalsActivity : Activity() {

    private lateinit var repo: PetRepository
    private lateinit var adapter: GoalAdapter
    private var goals: List<Goal> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)

        findViewById<TextView>(R.id.goals_title).text =
            getString(R.string.goals_title, repo.snapshot().species.displayName)

        adapter = GoalAdapter()
        findViewById<ListView>(R.id.goals_list).adapter = adapter

        findViewById<Button>(R.id.btn_add_goal).setOnClickListener {
            SfxEngine.play("ui_click")
            showAddDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        goals = repo.getGoals()
        adapter.notifyDataSetChanged()
        val empty = findViewById<TextView>(R.id.goals_empty)
        if (goals.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.text = getString(R.string.goals_empty, repo.snapshot().species.displayName)
        } else {
            empty.visibility = View.GONE
        }
    }

    private fun showAddDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_add_goal, null)
        val input = v.findViewById<EditText>(R.id.input_goal_name)
        val daily = v.findViewById<CheckBox>(R.id.check_daily)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_add_goal))
            .setView(v)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.addGoal(name, daily.isChecked)
                    SfxEngine.play("ui_click")
                    // addGoal writes on a background thread; re-read shortly after.
                    v.postDelayed({ reload() }, 250)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private inner class GoalAdapter : BaseAdapter() {
        override fun getCount(): Int = goals.size
        override fun getItem(pos: Int): Goal = goals[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false)
            val g = getItem(pos)
            v.findViewById<TextView>(R.id.goal_name).text = g.name
            val today = Goal.todayString()
            val doneToday = if (g.daily) g.lastDoneDate == today else g.lastDoneDate.isNotEmpty()
            v.findViewById<TextView>(R.id.goal_streak).text =
                if (g.streak > 0) getString(R.string.streak_fmt, g.streak)
                else if (g.daily) "daily" else "one-shot"
            val doneBtn = v.findViewById<Button>(R.id.goal_done_btn)
            doneBtn.isEnabled = !doneToday
            doneBtn.alpha = if (doneToday) 0.35f else 1f
            doneBtn.setOnClickListener {
                if (repo.completeGoal(g.id)) {
                    SfxEngine.play("goal_complete")
                    v.postDelayed({ SfxEngine.play("feed_munch") }, 350)
                    Toast.makeText(
                        this@GoalsActivity,
                        getString(R.string.toast_goal_done, repo.snapshot().species.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                    PixelPetWidgetProvider.refreshAll(this@GoalsActivity)
                    v.postDelayed({ reload() }, 300)
                }
            }
            v.findViewById<Button>(R.id.goal_delete_btn).setOnClickListener {
                SfxEngine.play("ui_click")
                repo.deleteGoal(g.id)
                v.postDelayed({ reload() }, 250)
            }
            return v
        }
    }
}
