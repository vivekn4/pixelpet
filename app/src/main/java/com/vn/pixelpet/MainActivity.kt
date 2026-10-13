package com.vn.pixelpet

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.vn.pixelpet.doomscroll.DoomscrollMonitor
import com.vn.pixelpet.util.NotificationHelper
import com.vn.pixelpet.util.ScreenReceiver
import com.vn.pixelpet.widget.PixelPetWidgetProvider

class MainActivity : Activity() {

    private lateinit var repo: PetRepository
    private lateinit var petView: PetView
    private lateinit var moodLabel: TextView
    private lateinit var petNameLabel: TextView
    private lateinit var barHunger: ProgressBar
    private lateinit var barHappiness: ProgressBar
    private lateinit var barEnergy: ProgressBar
    private lateinit var barAttention: ProgressBar
    private lateinit var btnMute: Button
    private val screenReceiver = ScreenReceiver()
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)
        NotificationHelper.ensureChannel(this)

        // Sleep/wake tracking (dynamic registration — manifest doesn't work for these).
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })

        petView = findViewById(R.id.pet_view)
        moodLabel = findViewById(R.id.mood_label)
        petNameLabel = findViewById(R.id.pet_name)
        barHunger = findViewById(R.id.bar_hunger)
        barHappiness = findViewById(R.id.bar_happiness)
        barEnergy = findViewById(R.id.bar_energy)
        barAttention = findViewById(R.id.bar_attention)
        btnMute = findViewById(R.id.btn_mute)

        // Tap = pet, double-tap = play.
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                doPet()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                doPlay()
                return true
            }
        })
        petView.setOnTouchListener { v, e ->
            gestures.onTouchEvent(e)
            v.performClick()
            true
        }

        findViewById<Button>(R.id.btn_goals).setOnClickListener {
            SfxEngine.play("ui_click"); Haptics.tick(this)
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_snack).setOnClickListener { doSnack() }
        findViewById<Button>(R.id.btn_play).setOnClickListener { doPlay() }
        findViewById<Button>(R.id.btn_pet).setOnClickListener { doPet() }
        findViewById<Button>(R.id.btn_doomscroll).setOnClickListener {
            SfxEngine.play("ui_click")
            confirmDoomscroll()
        }
        findViewById<Button>(R.id.btn_species).setOnClickListener {
            SfxEngine.play("ui_click")
            startActivity(Intent(this, SpeciesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_premium).setOnClickListener {
            SfxEngine.play("ui_click")
            startActivity(Intent(this, PremiumActivity::class.java))
        }
        findViewById<Button>(R.id.btn_about).setOnClickListener {
            SfxEngine.play("ui_click")
            startActivity(Intent(this, AboutActivity::class.java))
        }
        btnMute.setOnClickListener {
            val m = !repo.snapshot().muted
            repo.setMuted(m)
            SfxEngine.setMuted(m)
            updateMuteIcon(m)
            if (!m) SfxEngine.play("ui_click")
        }

        val autoSwitch = findViewById<Switch>(R.id.switch_autodetect)
        autoSwitch.isChecked = repo.autoDetectEnabled
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !DoomscrollMonitor.hasPermission(this)) {
                autoSwitch.isChecked = false
                Toast.makeText(this, getString(R.string.auto_detect_grant), Toast.LENGTH_LONG).show()
                DoomscrollMonitor.requestPermission(this)
                return@setOnCheckedChangeListener
            }
            repo.autoDetectEnabled = checked
            SfxEngine.play("ui_click")
        }

        PixelPetWidgetProvider.requestPinIfNeeded(this)
        requestNotifPermissionIfNeeded()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (t: Throwable) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Wake recap ("while you were away…").
        val recap = repo.lastRecap
        if (recap.isNotEmpty()) {
            repo.lastRecap = ""
            SfxEngine.play("wake_chime")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.wake_title))
                .setMessage(recap)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        refresh()
        maybeAttentionNudge()

        val mins = DoomscrollMonitor.maybeAutoLog(this, repo)
        if (mins != null) {
            Toast.makeText(
                this,
                getString(R.string.notif_auto_doom, mins, petName()),
                Toast.LENGTH_LONG
            ).show()
            petView.sulkAnim()
            SfxEngine.play("sulk_slide")
            Haptics.sulkBuzz(this)
            refresh()
        }
    }

    private fun petName(): String = repo.snapshot().species.displayName

    private fun refresh() {
        val state = repo.snapshot()
        val now = SystemClock.elapsedRealtime()
        val mood = PetRenderer.moodFor(state, now, System.currentTimeMillis())
        petView.species = state.species
        petView.aura = PremiumManager.isPremium(repo.prefsForPremium()) &&
            state.species.category == Species.Category.MYSTICAL
        petView.setMood(mood)
        petView.showAttentionBubble = state.attention < 25 && !state.sleeping
        petNameLabel.text = state.species.displayName
        moodLabel.text = when (mood) {
            PetRenderer.Mood.HAPPY -> getString(R.string.mood_happy)
            PetRenderer.Mood.CONTENT -> getString(R.string.mood_content)
            PetRenderer.Mood.HUNGRY -> getString(R.string.mood_hungry)
            PetRenderer.Mood.SAD -> getString(R.string.mood_sad)
            PetRenderer.Mood.SULKING -> getString(R.string.mood_sulking)
            PetRenderer.Mood.SLEEPING -> getString(R.string.mood_sleeping)
            PetRenderer.Mood.SICK -> getString(R.string.mood_sick)
        }
        tweenBar(barHunger, state.hunger)
        tweenBar(barHappiness, state.happiness)
        tweenBar(barEnergy, state.energy)
        tweenBar(barAttention, state.attention)
        updateMuteIcon(state.muted)
        if (state.attention >= 25) NotificationHelper.cancelAttention(this)
        PixelPetWidgetProvider.refreshAll(this)

        // Celebrate any unlocks earned by recent care.
        val unlocks = UnlockManager.checkUnlocks(repo)
        if (unlocks.isNotEmpty()) {
            val u = unlocks.first()
            petView.evolveAnim()
            SfxEngine.play("evolve_fanfare")
            Haptics.fanfare(this)
            Toast.makeText(
                this,
                getString(R.string.unlocked_toast, u.species.displayName, u.reason),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun doSnack() {
        val s = repo.snack()
        if (s == null) {
            Toast.makeText(this, getString(R.string.snack_cooldown), Toast.LENGTH_SHORT).show()
            return
        }
        petView.eatAnim()
        petView.feedAnim()
        SfxEngine.play("feed_munch")
        Haptics.thud(this)
        refresh()
    }

    private fun doPlay() {
        val s = repo.play()
        if (s == null) {
            SfxEngine.play("sad_blip")
            Toast.makeText(this, getString(R.string.play_tired), Toast.LENGTH_SHORT).show()
            return
        }
        petView.playAnim()
        SfxEngine.play("play_giggle")
        Haptics.bounce(this)
        refresh()
    }

    private fun doPet() {
        repo.pet()
        petView.feedAnim() // hearts
        SfxEngine.play("pet_purr")
        Haptics.tick(this)
        refresh()
    }

    private fun maybeAttentionNudge() {
        val state = repo.snapshot()
        if (state.attention >= 25 || state.sleeping) return
        val now = System.currentTimeMillis()
        if (now - repo.lastAttentionNudge < 4 * 3600 * 1000L) return
        repo.lastAttentionNudge = now
        SfxEngine.play("attention_ping")
        NotificationHelper.notifyAttentionNeeded(this, petName())
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
    }

    private fun tweenBar(bar: ProgressBar, to: Int) {
        val from = bar.progress
        if (from == to) return
        ValueAnimator.ofInt(from, to).apply {
            duration = 450
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }
    }

    private fun updateMuteIcon(muted: Boolean) {
        btnMute.text = if (muted) getString(R.string.btn_unmute) else getString(R.string.btn_mute)
    }

    private fun confirmDoomscroll() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.doom_title))
            .setMessage(getString(R.string.doom_msg, petName()))
            .setPositiveButton(getString(R.string.doom_confirm)) { _, _ ->
                repo.logDoomscroll()
                petView.sulkAnim()
                SfxEngine.play("sulk_slide")
                Haptics.sulkBuzz(this)
                Toast.makeText(this, getString(R.string.toast_doom, petName()), Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
