package com.vn.pixelpet

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.vn.pixelpet.widget.PixelPetWidgetProvider
import java.text.DateFormat
import java.util.Date

/**
 * Premium activation via license-key entry (Gumroad manual-delivery pattern).
 * Price: TODO — user's call (see README).
 */
class PremiumActivity : Activity() {

    private lateinit var repo: PetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)
        refreshState()
    }

    private fun refreshState() {
        val premium = PremiumManager.isPremium(repo.prefsForPremium())
        findViewById<View>(R.id.premium_locked).visibility =
            if (premium) View.GONE else View.VISIBLE
        findViewById<View>(R.id.premium_active).visibility =
            if (premium) View.VISIBLE else View.GONE
        if (premium) {
            findViewById<TextView>(R.id.premium_key_id).text =
                getString(R.string.premium_key_id, PremiumManager.keyId(repo.prefsForPremium()))
        }
        findViewById<Button>(R.id.btn_activate).setOnClickListener {
            val input = findViewById<EditText>(R.id.input_key).text.toString()
            when (val r = PremiumManager.activate(repo.prefsForPremium(), input)) {
                is PremiumManager.Result.Ok -> {
                    SfxEngine.play("key_success")
                    Haptics.fanfare(this)
                    // Premium unlocks all premium-path species immediately.
                    val newly = UnlockManager.checkUnlocks(repo)
                    Toast.makeText(
                        this,
                        getString(R.string.premium_welcome, newly.size),
                        Toast.LENGTH_LONG
                    ).show()
                    PixelPetWidgetProvider.refreshAll(this)
                    refreshState()
                }
                is PremiumManager.Result.LockedOut -> {
                    SfxEngine.play("key_error")
                    Haptics.error(this)
                    val whenStr = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(r.untilMs))
                    Toast.makeText(
                        this,
                        getString(R.string.premium_locked_out, whenStr),
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    SfxEngine.play("key_error")
                    Haptics.error(this)
                    Toast.makeText(this, getString(R.string.premium_bad_key), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
