package com.vn.pixelpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * Draws Pip the pixel-art pet, parametric across species × moods
 * (12 v2 species + 108 v3 variants).
 * Everything is rects on a coarse grid with anti-aliasing OFF — crisp pixels
 * at any upscale. Shared by the app, the widget, and the live wallpaper.
 */
object PetRenderer {

    enum class Mood { HAPPY, CONTENT, HUNGRY, SAD, SULKING, SLEEPING, SICK }

    private const val GW = 24
    private const val GH = 24 // extra room for ears/horns/tentacles

    private fun paint(hex: String) = Paint().apply { isAntiAlias = false; color = Color.parseColor(hex) }

    /** Derive mood purely from stats. Sulk overrides everything except sleep/sick. */
    fun moodFor(state: PetState, nowElapsed: Long, nowWall: Long): Mood {
        if (state.sleeping || state.energy < 15) return Mood.SLEEPING
        if (state.isSick(nowWall)) return Mood.SICK
        if (state.isSulking(nowElapsed)) return Mood.SULKING
        if (state.hunger < 25) return Mood.HUNGRY
        if (state.happiness < 30) return Mood.SAD
        if (state.happiness > 70 && state.hunger > 55) return Mood.HAPPY
        return Mood.CONTENT
    }

    /**
     * Render a v2 [species] in [mood]. [wavePhase] animates tentacles/aura;
     * [aura] draws the mystical glow orbit (premium). [walkPhase] (0 = standing,
     * 1/2 = walk frames) and [legs] only take effect when [legs] is true, and
     * [accessory] defaults to NONE, so a default call renders pixel-identically
     * to the v2 renderer.
     */
    fun render(
        species: Species,
        mood: Mood,
        cellPx: Int,
        blink: Boolean = false,
        aura: Boolean = false,
        wavePhase: Float = 0f,
        walkPhase: Int = 0,
        legs: Boolean = false,
        accessory: VariantRoster.Accessory = VariantRoster.Accessory.NONE
    ): Bitmap = renderInternal(
        bodyHexParam = species.bodyHex,
        darkHexParam = species.darkHex,
        features = species.features,
        accessory = accessory,
        mood = mood,
        cellPx = cellPx,
        blink = blink,
        aura = aura,
        wavePhase = wavePhase,
        walkPhase = walkPhase,
        legs = legs
    )

    /**
     * Render a v3 [VariantRoster.Variant] in [mood]. Same grid, same moods, same
     * face code as [render] — only the palette, feature set, and accessory differ.
     * [legs] defaults to true here: variants are drawn with walk-capable legs.
     */
    fun renderVariant(
        variant: VariantRoster.Variant,
        mood: Mood,
        cellPx: Int,
        blink: Boolean = false,
        aura: Boolean = false,
        wavePhase: Float = 0f,
        walkPhase: Int = 0,
        legs: Boolean = true
    ): Bitmap = renderInternal(
        bodyHexParam = variant.bodyHex,
        darkHexParam = variant.darkHex,
        features = variant.features,
        accessory = variant.accessory,
        mood = mood,
        cellPx = cellPx,
        blink = blink,
        aura = aura,
        wavePhase = wavePhase,
        walkPhase = walkPhase,
        legs = legs
    )

    /**
     * Shared implementation for [render] and [renderVariant]. With v2 defaults
     * (no v3 features in the set, legs = false, accessory = NONE) the output is
     * pixel-identical to the v2 renderer.
     */
    private fun renderInternal(
        bodyHexParam: String,
        darkHexParam: String,
        features: Set<Species.Feature>,
        accessory: VariantRoster.Accessory,
        mood: Mood,
        cellPx: Int,
        blink: Boolean = false,
        aura: Boolean = false,
        wavePhase: Float = 0f,
        walkPhase: Int = 0,
        legs: Boolean = false
    ): Bitmap {
        val bmp = Bitmap.createBitmap(GW * cellPx, GH * cellPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val u = cellPx.toFloat()
        val F = features

        var bodyHex = bodyHexParam
        var darkHex = darkHexParam
        if (mood == Mood.SICK) { // pale tint
            bodyHex = mix(bodyHex, "#FFFFFF", 0.45f)
            darkHex = mix(darkHex, "#FFFFFF", 0.3f)
        }
        val body = paint(bodyHex)
        val dark = paint(darkHex)
        val light = paint(mix(bodyHexParam, "#FFFFFF", 0.55f))
        val black = paint("#14141F")
        val white = paint("#FFFFFF")
        val cheek = paint("#FF9DC6")
        val tear = paint("#6AC8FF")
        val cloud = paint("#5A5A7A")
        val gold = paint("#FFD54A")
        val cream = paint("#FFF3D6")

        fun r(x: Int, y: Int, w: Int, h: Int, p: Paint) {
            c.drawRect(x * u, y * u, (x + w) * u, (y + h) * u, p)
        }

        // ---- aura (behind everything) ----
        if (aura) {
            val glow = Paint().apply { isAntiAlias = false; color = Color.parseColor("#FFD54A"); alpha = 90 }
            for (i in 0 until 8) {
                val a = wavePhase + i * (Math.PI.toFloat() / 4f)
                val ox = (12 + 9 * kotlin.math.cos(a)).toInt()
                val oy = (12 + 8 * sin(a)).toInt()
                r(ox, oy, 1, 1, glow)
            }
        }

        // ---- back features (behind body) ----
        if (Feature.WINGS in F) {
            r(1, 10, 3, 4, dark); r(1, 10, 2, 3, body)
            r(20, 10, 3, 4, dark); r(21, 10, 2, 3, body)
        }
        if (Feature.BACK_SPIKES in F) {
            r(9, 5, 1, 2, dark); r(12, 5, 1, 2, dark); r(15, 5, 1, 2, dark)
        }
        if (Feature.TENTACLES in F) {
            for ((i, tx) in listOf(6, 10, 14, 17).withIndex()) {
                val sway = (sin(wavePhase * 2 + i * 1.3f) * 1.2f).toInt()
                r(tx + sway, 19, 2, 3, dark)
                r(tx + sway, 19, 2, 3, body)
            }
        }
        if (Feature.TAIL in F) {
            r(20, 16, 3, 2, dark); r(20, 16, 3, 2, body)
        }
        if (Feature.SHELL in F) {
            // Dome cap peeking above the body + side rims; the face stays clear.
            r(5, 4, 14, 4, dark); r(6, 5, 12, 3, body)
            r(4, 9, 2, 6, dark); r(18, 9, 2, 6, dark)
        }

        // ---- body blob ----
        val rows = listOf(
            7 to (8..16), 8 to (6..18), 9 to (5..19), 10 to (5..19),
            11 to (4..20), 12 to (4..20), 13 to (4..20), 14 to (4..20),
            15 to (5..19), 16 to (5..19), 17 to (6..18), 18 to (8..16)
        )
        for ((y, xs) in rows) {
            r(xs.first - 1, y, xs.last - xs.first + 3, 1, dark)
            r(xs.first, y, xs.last - xs.first + 1, 1, body)
        }
        r(8, 6, 9, 1, dark)
        if (!legs) {
            r(7, 19, 3, 1, dark); r(14, 19, 3, 1, dark)
            r(7, 19, 3, 1, body); r(14, 19, 3, 1, body)
        } else {
            // Procedural walk legs: phase 0 = standing, 1/2 = alternating strides.
            val leftLift = if (walkPhase == 1) 1 else 0
            val rightLift = if (walkPhase == 2) 1 else 0
            r(7, 19 - leftLift, 3, 2, dark); r(7, 19 - leftLift, 3, 2, body)
            r(14, 19 - rightLift, 3, 2, dark); r(14, 19 - rightLift, 3, 2, body)
        }
        if (Feature.SPOTS in F) {
            r(6, 9, 2, 2, dark); r(16, 12, 2, 2, dark); r(9, 16, 2, 1, dark)
        }
        if (Feature.SCALES in F) {
            r(8, 16, 8, 1, dark); r(9, 18, 6, 1, dark)
        }

        // ---- head features ----
        if (Feature.POINT_EARS in F) {
            r(6, 4, 3, 3, dark); r(6, 4, 3, 3, body); r(7, 3, 1, 1, dark)
            r(15, 4, 3, 3, dark); r(15, 4, 3, 3, body); r(16, 3, 1, 1, dark)
        }
        if (Feature.FLOPPY_EARS in F) {
            r(3, 9, 2, 5, dark); r(3, 9, 2, 5, body)
            r(19, 9, 2, 5, dark); r(19, 9, 2, 5, body)
        }
        if (Feature.LONG_EARS in F) {
            r(9, 1, 2, 6, dark); r(9, 1, 2, 6, body); r(9, 2, 2, 4, cheek)
            r(13, 1, 2, 6, dark); r(13, 1, 2, 6, body); r(13, 2, 2, 4, cheek)
        }
        if (Feature.ROUND_EARS in F) {
            r(6, 5, 2, 2, dark); r(6, 5, 2, 2, body)
            r(16, 5, 2, 2, dark); r(16, 5, 2, 2, body)
        }
        if (Feature.EAR_TUFTS in F) {
            r(5, 5, 2, 1, dark); r(17, 5, 2, 1, dark)
        }
        if (Feature.HORNS in F) {
            r(7, 3, 2, 3, cream); r(15, 3, 2, 3, cream)
        }
        if (Feature.HORN in F) {
            r(11, 2, 2, 5, gold)
        }
        if (Feature.CREST in F) {
            r(10, 2, 1, 3, dark); r(12, 1, 1, 4, dark); r(14, 2, 1, 3, dark)
        }
        if (Feature.MANE in F) {
            r(18, 7, 2, 9, cheek)
        }
        if (Feature.STRIPES in F) {
            r(8, 6, 1, 2, dark); r(12, 6, 1, 2, dark); r(16, 6, 1, 2, dark)
        }
        if (Feature.WHISKERS in F) {
            r(1, 12, 3, 1, white); r(1, 14, 3, 1, white)
            r(20, 12, 3, 1, white); r(20, 14, 3, 1, white)
        }
        if (Feature.CHEEK_POUCHES in F) {
            r(4, 13, 3, 2, light); r(17, 13, 3, 2, light)
        }
        if (Feature.MUZZLE_PATCH in F) {
            r(9, 13, 6, 2, light)
        }
        if (Feature.SNOUT in F) {
            r(9, 14, 6, 3, light)
            r(10, 15, 1, 1, dark); r(13, 15, 1, 1, dark)
        }
        if (Feature.GILLS in F) {
            r(2, 9, 2, 2, cheek); r(2, 12, 2, 2, cheek); r(2, 15, 2, 2, cheek)
            r(20, 9, 2, 2, cheek); r(20, 12, 2, 2, cheek); r(20, 15, 2, 2, cheek)
        }
        if (Feature.FLIPPERS in F) {
            r(2, 12, 2, 5, dark); r(2, 12, 2, 5, body)
            r(20, 12, 2, 5, dark); r(20, 12, 2, 5, body)
        }

        val ex = if (mood == Mood.SULKING) 1 else 0
        val bigEyes = Feature.BIG_EYES in F
        val ew = if (bigEyes) 3 else 2
        val eh = if (bigEyes) 4 else 3
        val ey = if (bigEyes) 10 else 10

        // ---- eyes ----
        when {
            mood == Mood.HAPPY -> {
                r(7, 11, 1, 2, black); r(8, 10, 1, 1, black); r(9, 11, 1, 2, black)
                r(14, 11, 1, 2, black); r(15, 10, 1, 1, black); r(16, 11, 1, 2, black)
            }
            mood == Mood.SLEEPING || blink -> {
                r(7, 12, 3, 1, black); r(14, 12, 3, 1, black)
            }
            else -> {
                r(7 + ex, ey, ew, eh, white); r(14 + ex, ey, ew, eh, white)
                val px = if (mood == Mood.SULKING) 1 else 0
                r(7 + ex + px, ey + 1, 1, 1, black); r(14 + ex + px, ey + 1, 1, 1, black)
                if (mood == Mood.HUNGRY) {
                    r(7 + ex, ey, ew, 1, body); r(14 + ex, ey, ew, 1, body)
                }
            }
        }
        if (Feature.EXTRA_EYES in F && mood != Mood.SLEEPING && !blink) {
            r(4, 11, 1, 2, white); r(4, 12, 1, 1, black)
            r(19, 11, 1, 2, white); r(19, 12, 1, 1, black)
        }

        // ---- cheeks ----
        if (mood == Mood.HAPPY || mood == Mood.CONTENT) {
            if (Feature.CHEEK_POUCHES !in F) {
                r(5, 13, 2, 1, cheek); r(17, 13, 2, 1, cheek)
            }
        }

        // ---- mouth / beak / bill ----
        if (Feature.BILL in F) {
            r(9, 13, 6, 2, gold); r(9, 14, 6, 1, dark)
        } else if (Feature.BEAK in F) {
            r(11, 13, 2, 2, gold)
        } else when (mood) {
            Mood.HAPPY -> {
                r(10, 14, 4, 1, black); r(9, 13, 1, 1, black); r(14, 13, 1, 1, black)
                if (Feature.TONGUE in F) r(11, 15, 2, 1, cheek)
            }
            Mood.CONTENT -> r(10, 14, 4, 1, black)
            Mood.HUNGRY -> { r(10, 14, 4, 2, black); r(11, 14, 2, 2, dark) }
            Mood.SAD -> { r(10, 15, 4, 1, black); r(9, 14, 1, 1, black); r(14, 14, 1, 1, black) }
            Mood.SULKING -> r(10, 15, 4, 1, black)
            Mood.SLEEPING -> r(11, 14, 2, 1, black)
            Mood.SICK -> { r(10, 14, 2, 1, black); r(13, 14, 2, 1, black) } // wavy
        }

        // ---- mood extras ----
        when (mood) {
            Mood.SAD -> r(6, 13, 1, 2, tear)
            Mood.HUNGRY -> { r(3, 9, 1, 1, white); r(2, 11, 1, 1, white); r(3, 13, 1, 1, white) }
            Mood.SULKING -> {
                r(8, 2, 9, 2, cloud); r(10, 1, 5, 1, cloud)
                r(9, 4, 1, 1, tear); r(12, 4, 1, 1, tear); r(15, 4, 1, 1, tear)
            }
            Mood.SLEEPING -> {
                val zp = Paint().apply { isAntiAlias = false; color = Color.parseColor("#B9B9E8") }
                zp.textSize = 3f * u
                c.drawText("Z", 18 * u, 5 * u, zp)
            }
            Mood.SICK -> { r(3, 8, 2, 1, tear); r(20, 9, 2, 1, tear) } // sickly puffs
            else -> {}
        }

        // ---- accessory (topmost layer) ----
        when (accessory) {
            VariantRoster.Accessory.NONE -> {}
            VariantRoster.Accessory.BOW -> {
                r(17, 4, 2, 2, cheek); r(19, 4, 2, 2, cheek); r(18, 5, 1, 1, dark)
            }
            VariantRoster.Accessory.SCARF -> {
                r(7, 17, 10, 2, gold); r(15, 18, 2, 2, gold)
            }
            VariantRoster.Accessory.HAT -> {
                r(9, 1, 6, 2, dark); r(8, 3, 8, 1, dark)
            }
            VariantRoster.Accessory.GLASSES -> {
                r(6, 10, 4, 3, dark); r(7, 11, 2, 1, white)
                r(14, 10, 4, 3, dark); r(15, 11, 2, 1, white)
                r(10, 11, 4, 1, dark)
            }
            VariantRoster.Accessory.FLOWER -> {
                r(16, 3, 2, 2, cheek); r(17, 4, 1, 1, gold)
            }
            VariantRoster.Accessory.COLLAR -> {
                r(8, 18, 8, 1, gold); r(11, 18, 1, 1, dark)
            }
            VariantRoster.Accessory.BANDANA -> {
                r(6, 6, 12, 2, cheek)
            }
            VariantRoster.Accessory.CROWN -> {
                r(9, 1, 6, 2, gold); r(9, 0, 1, 1, gold); r(12, 0, 1, 1, gold); r(14, 0, 1, 1, gold)
            }
            VariantRoster.Accessory.BELL -> {
                r(11, 17, 2, 2, gold); r(11, 19, 2, 1, dark)
            }
            VariantRoster.Accessory.FEATHER -> {
                r(16, 2, 1, 4, cheek)
            }
            VariantRoster.Accessory.STAR_PIN -> {
                r(17, 8, 2, 2, gold); r(17, 9, 4, 1, gold)
            }
        }
        return bmp
    }

    /** Draw [src] scaled with nearest-neighbor (crisp pixels) into [dst]. */
    fun drawScaled(c: Canvas, src: Bitmap, dst: RectF) {
        val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        c.drawBitmap(src, null, dst, p)
    }

    /** Draws the "!" attention bubble above the pet; caller positions via [cx]/[top]. */
    fun drawAttentionBubble(c: Canvas, cx: Float, top: Float, cellPx: Float, pulse: Float) {
        val bubble = Paint().apply { isAntiAlias = false; color = Color.parseColor("#FFD54A") }
        val dark = Paint().apply { isAntiAlias = false; color = Color.parseColor("#14141F") }
        val s = cellPx * (1 + 0.12f * sin(pulse))
        val w = 5 * s; val h = 6 * s
        val x0 = cx - w / 2
        c.drawRoundRect(RectF(x0, top, x0 + w, top + h), 4f, 4f, bubble)
        // "!"
        val bx = cx - s / 2
        c.drawRect(bx, top + s, bx + s, top + 3.4f * s, dark)
        c.drawRect(bx, top + 4f * s, bx + s, top + 5f * s, dark)
        // tail
        c.drawRect(cx - s / 2, top + h - 1, cx + s / 2, top + h + s, bubble)
    }

    private fun mix(hex1: String, hex2: String, t: Float): String {
        val c1 = Color.parseColor(hex1); val c2 = Color.parseColor(hex2)
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }
}

private typealias Feature = Species.Feature
