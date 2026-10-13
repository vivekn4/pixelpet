package com.vn.pixelpet

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.random.Random

/**
 * The Personality card (PLAN_V3.md §9): 5 trait bars + summary + "Pip learned".
 * Phase 2 shows honest "still getting to know you" copy — the Brain that fills
 * the learned section arrives in Phase 3. Nothing here is faked.
 */
class PersonalityActivity : Activity() {

    private lateinit var personality: Personality
    private lateinit var variant: VariantRoster.Variant
    private lateinit var speaker: SpeechBank.Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personality)
        val repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)

        variant = resolveVariant(repo.snapshot().speciesId)
        val prefs = getSharedPreferences(Personality.prefsName(), Context.MODE_PRIVATE)
        personality = Personality.load(prefs, variant.id)
            ?: Personality.fresh(variant, Random, System.currentTimeMillis())
        personality.decay(System.currentTimeMillis())
        personality.save(prefs)

        speaker = SpeechBank.Speaker(Random)

        findViewById<TextView>(R.id.personality_pet_name).text = variant.displayName
        findViewById<TextView>(R.id.personality_summary).text = personality.summaryLine()
        findViewById<TextView>(R.id.personality_says).text =
            getString(R.string.personality_says) + " \"" +
                speaker.nextLine(personality.archetype(), PetRenderer.Mood.CONTENT) + "\""

        val rows = findViewById<LinearLayout>(R.id.trait_rows)
        val labels = listOf(
            R.string.personality_trait_playful,
            R.string.personality_trait_bold,
            R.string.personality_trait_greedy,
            R.string.personality_trait_social,
            R.string.personality_trait_neat
        )
        for ((i, trait) in Personality.Trait.values().withIndex()) {
            rows.addView(traitRow(trait, getString(labels[i])))
        }

        findViewById<Button>(R.id.btn_personality_done).setOnClickListener {
            SfxEngine.play("ui_click")
            finish()
        }
    }

    private fun traitRow(trait: Personality.Trait, label: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.text_dim))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = personality.trait(trait)
            progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.gold))
            layoutParams = LinearLayout.LayoutParams(0, dp(14), 1f)
        }
        val valueView = TextView(this).apply {
            text = personality.trait(trait).toString()
            setTextColor(getColor(R.color.text))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(labelView)
        row.addView(bar)
        row.addView(valueView)
        row.contentDescription = "$label ${personality.trait(trait)}"
        return row
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** v2 PetState stores a Species id ("cat"); v3 variants are "cat-tabby". */
    private fun resolveVariant(speciesId: String): VariantRoster.Variant {
        VariantRoster.byId(speciesId)?.let { return it }
        VariantRoster.ALL.firstOrNull { it.id.startsWith("$speciesId-") }?.let { return it }
        return VariantRoster.ALL.first()
    }
}
