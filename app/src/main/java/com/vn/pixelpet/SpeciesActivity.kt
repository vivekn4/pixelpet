package com.vn.pixelpet

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.vn.pixelpet.widget.PixelPetWidgetProvider

/**
 * The pet roster: 12 species across Normal / Wild / Mystical.
 * Locked species show a silhouette + requirement; tapping an unlocked species
 * selects it with the evolve celebration.
 */
class SpeciesActivity : Activity() {

    private lateinit var repo: PetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_species)
        repo = PetRepository.get(this)
        SfxEngine.init(applicationContext, repo.snapshot().muted)

        val next = UnlockManager.nextUnlock(repo)
        findViewById<TextView>(R.id.next_unlock).text =
            next ?: getString(R.string.all_unlocked)

        findViewById<GridView>(R.id.species_grid).adapter = SpeciesAdapter()

        findViewById<Button>(R.id.btn_premium).setOnClickListener {
            SfxEngine.play("ui_click")
            startActivity(android.content.Intent(this, PremiumActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        (findViewById<GridView>(R.id.species_grid).adapter as SpeciesAdapter).refresh()
    }

    private inner class SpeciesAdapter : BaseAdapter() {
        private var items: List<Species> = Species.values().toList()
        fun refresh() {
            items = Species.values().toList()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Species = items[p]
        override fun getItemId(p: Int): Long = p.toLong()

        override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_species, parent, false)
            val s = getItem(pos)
            val unlocked = repo.isUnlocked(s)
            val active = repo.snapshot().speciesId == s.id
            val img = v.findViewById<ImageView>(R.id.species_img)

            if (unlocked) {
                val bmp: Bitmap = PetRenderer.render(s, PetRenderer.Mood.HAPPY, 8)
                img.setImageBitmap(bmp)
                img.alpha = 1f
            } else {
                // silhouette: render then tint black via alpha trick — draw dark overlay
                val bmp: Bitmap = PetRenderer.render(s, PetRenderer.Mood.CONTENT, 8)
                img.setImageBitmap(bmp)
                img.alpha = 0.25f
            }
            v.findViewById<TextView>(R.id.species_name).text = s.displayName
            val sub = v.findViewById<TextView>(R.id.species_sub)
            sub.text = when {
                active -> getString(R.string.species_active)
                unlocked -> s.category.name.lowercase().replaceFirstChar { it.uppercase() }
                else -> "🔒 ${s.unlockText}"
            }
            v.findViewById<View>(R.id.species_card).apply {
                isEnabled = unlocked && !active
                alpha = if (unlocked) 1f else 0.85f
                setOnClickListener(null)
                if (unlocked && !active) {
                    setOnClickListener {
                        repo.selectSpecies(s.id)
                        SfxEngine.play("evolve_fanfare")
                        Haptics.fanfare(this@SpeciesActivity)
                        Toast.makeText(
                            this@SpeciesActivity,
                            getString(R.string.species_selected, s.displayName),
                            Toast.LENGTH_SHORT
                        ).show()
                        PixelPetWidgetProvider.refreshAll(this@SpeciesActivity)
                        refresh()
                    }
                } else if (!unlocked) {
                    setOnClickListener {
                        SfxEngine.play("ui_click")
                        Toast.makeText(
                            this@SpeciesActivity,
                            "🔒 ${s.unlockText}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            return v
        }
    }
}
