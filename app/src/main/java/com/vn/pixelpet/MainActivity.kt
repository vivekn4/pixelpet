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
    // v3 Phase 3: on-device learning + family powers
    private var brain: NativeBrain? = null
    private var cooldowns: PowerManager.Cooldowns = PowerManager.Cooldowns()
    private var powerButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)
        NotificationHelper.ensureChannel(this)

        // Sleep/wake tracking (dynamic registration — manifest does not work for these).
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
        findViewById<Button>(R.id.btn_personality).setOnClickListener {
            SfxEngine.play("ui_click")
            startActivity(Intent(this, PersonalityActivity::class.java))
        }
        // v3 Phase 3: load brain + cooldowns, wire the power button
        loadBrain()
        loadCooldowns()
        powerButton = findViewById(R.id.btn_power)
        powerButton?.setOnClickListener { doPower() }
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
            recordBrain(NativeBrain.Action.WAKE)
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
        // v3 Phase 4: aura for premium users on ALL variants, with the chosen skin.
        val auraSkin = AuraSkin.effective(repo.prefsForPremium())
        petView.aura = auraSkin != null
        petView.auraStyle = auraSkin ?: AuraSkin.GOLD_ORBIT
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

        // v3 Phase 3: power button state + hamster stash
        refreshPowerButton()
        checkStash()
    }

    private fun doSnack() {
        val s = repo.snack()
        if (s == null) {
            Toast.makeText(this, getString(R.string.snack_cooldown), Toast.LENGTH_SHORT).show()
            return
        }
        recordBrain(NativeBrain.Action.FEED_SNACK)
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
        recordBrain(NativeBrain.Action.PLAY)
        petView.playAnim()
        SfxEngine.play("play_giggle")
        Haptics.bounce(this)
        refresh()
    }

    private fun doPet() {
        repo.pet()
        recordBrain(NativeBrain.Action.PET)
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
                recordBrain(NativeBrain.Action.DOOMSCROLL)
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

    // ================= v3 Phase 3: brain + powers =================

    /** Load (or start fresh) the Native Brain for the current variant. */
    private fun loadBrain() {
        val variantId = VariantRoster.variantForSpeciesId(repo.snapshot().speciesId).id
        val prefsB = getSharedPreferences(NativeBrain.PREFS_NAME, MODE_PRIVATE)
        val saved = prefsB.getString(NativeBrain.KEY_JSON, null)
        brain = saved?.let { NativeBrain.fromJson(it, variantId) }
            ?: NativeBrain.fresh(variantId, System.currentTimeMillis())
    }

    private fun saveBrain() {
        val b = brain ?: return
        getSharedPreferences(NativeBrain.PREFS_NAME, MODE_PRIVATE)
            .edit().putString(NativeBrain.KEY_JSON, b.toJson()).apply()
    }

    /** Record one interaction into the Brain and persist. */
    private fun recordBrain(action: NativeBrain.Action) {
        val b = brain ?: return
        b.record(action, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        saveBrain()
    }

    private fun loadCooldowns() {
        val prefsP = getSharedPreferences(
            PowerManager.Cooldowns.PREFS_NAME, MODE_PRIVATE
        )
        val saved = prefsP.getString(PowerManager.Cooldowns.KEY_JSON, null)
        cooldowns = saved?.let { PowerManager.Cooldowns.fromJson(it) }
            ?: PowerManager.Cooldowns()
    }

    private fun saveCooldowns() {
        getSharedPreferences(PowerManager.Cooldowns.PREFS_NAME, MODE_PRIVATE)
            .edit().putString(PowerManager.Cooldowns.KEY_JSON, cooldowns.toJson()).apply()
    }

    /** Update the power button label / disabled state from the cooldown. */
    private fun refreshPowerButton() {
        val btn = powerButton ?: return
        val variant = VariantRoster.variantForSpeciesId(repo.snapshot().speciesId)
        val def = PowerManager.powerFor(variant.family)
        val now = SystemClock.elapsedRealtime()
        val remaining = cooldowns.remainingMs(variant.family, now)
        if (remaining > 0L) {
            // Short label on the button (it is 1/3-width); the full sentence
            // is used in the toast when the user taps it while cooling down.
            btn.text = "\u23F3 ${cooldowns.remainingLabel(variant.family, now)}"
            btn.alpha = 0.45f
            btn.isEnabled = false
        } else {
            btn.text = "\u2728 ${def.name}"
            btn.alpha = 1f
            btn.isEnabled = true
        }
    }

    /**
     * Hamster Cheek Stash: once the stash is due and the pet is not full,
     * auto-feed +5 hunger and clear the stash.
     */
    private fun checkStash() {
        val now = SystemClock.elapsedRealtime()
        if (!cooldowns.stashDue(now)) return
        val s = repo.snapshot()
        if (s.hunger >= 95) return // wait until there is room
        repo.feedPowerBonus(5)
        cooldowns.consumeStash()
        saveCooldowns()
        Toast.makeText(this, R.string.power_stash_munched, Toast.LENGTH_SHORT).show()
        refresh()
    }

    /** Trigger the current pet family power. */
    private fun doPower() {
        val variant = VariantRoster.variantForSpeciesId(repo.snapshot().speciesId)
        val def = PowerManager.powerFor(variant.family)
        val now = SystemClock.elapsedRealtime()

        // Phoenix Rebirth: only consume the weekly cooldown when there IS a
        // missed day to restore — otherwise it would burn a week for nothing.
        if (def.statEffect == PowerManager.StatEffect.STREAK_RESTORE &&
            !repo.canRestoreStreakDay()
        ) {
            Toast.makeText(this, R.string.power_streak_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        if (cooldowns.trigger(variant.family, now) == null) {
            Toast.makeText(
                this,
                getString(R.string.power_cooldown, cooldowns.remainingLabel(variant.family, now)),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        saveCooldowns()
        recordBrain(NativeBrain.Action.POWER)

        // Stat effect first (repo-side), then the visual hook.
        when (def.statEffect) {
            PowerManager.StatEffect.CURE_SICKNESS -> repo.cureSickness()
            PowerManager.StatEffect.POWER_SNACK_10 -> repo.feedPowerBonus(10)
            PowerManager.StatEffect.STASH_SNACK -> {
                cooldowns.stashSnack(now)
                saveCooldowns()
            }
            PowerManager.StatEffect.SHIELD_1H -> repo.activateShield(3600000L)
            PowerManager.StatEffect.ENERGY_25 -> repo.feedEnergy(25)
            PowerManager.StatEffect.HAPPINESS_5 -> repo.addHappiness(5)
            PowerManager.StatEffect.STREAK_RESTORE -> {
                if (repo.restoreMissedStreakDay()) {
                    Toast.makeText(this, R.string.power_streak_restored, Toast.LENGTH_SHORT).show()
                }
            }
            PowerManager.StatEffect.NONE -> Unit
        }
        // v3 Phase 4: premium sparkle on any variant (look only).
        val aura = AuraSkin.effective(repo.prefsForPremium()) != null
        petView.powerFx(def.fx, premium = aura)
        SfxEngine.play("power_chime")

        // Dog Fetch Gift: a cosmetic toast gift (no coin economy exists).
        if (variant.family == VariantRoster.Family.DOG) {
            val gifts = listOf("a shiny pebble", "a pinecone", "a feather", "a tiny stick")
            val gift = gifts[(now / 1000L % gifts.size).toInt()]
            Toast.makeText(this, getString(R.string.power_fetch_gift, gift), Toast.LENGTH_SHORT).show()
        }

        // Owl Night Watch: tomorrow first goal as a reminder.
        if (def.specialUi == PowerManager.SpecialUi.NIGHT_WATCH) {
            val goals = repo.getGoals()
            val first = goals.firstOrNull { !it.completed }
            val msg = if (first != null) {
                getString(R.string.power_night_watch_msg, first.name)
            } else {
                getString(R.string.power_night_watch_empty)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.power_night_watch_title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        refresh()
    }
}
