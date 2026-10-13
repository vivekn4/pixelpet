package com.vn.pixelpet

/**
 * The 12-species roster. Rendering stays parametric: one blob body + a feature
 * set + a palette. Adding a species = one enum entry, no art pipeline.
 */
enum class Species(
    val id: String,
    val displayName: String,
    val category: Category,
    val bodyHex: String,
    val darkHex: String,
    val features: Set<Feature>,
    /** Human-readable unlock requirement for the species picker. */
    val unlockText: String
) {
    // ---- Normal: available from the start ----
    CAT("cat", "Mochi", Category.NORMAL, "#B9A7FF", "#7A63D8",
        setOf(Feature.POINT_EARS, Feature.WHISKERS), "Starter"),
    DOG("dog", "Biscuit", Category.NORMAL, "#D8A76A", "#9A6B3F",
        setOf(Feature.FLOPPY_EARS, Feature.TONGUE), "Starter"),
    BUNNY("bunny", "Clover", Category.NORMAL, "#F2F2FA", "#B9B9D6",
        setOf(Feature.LONG_EARS), "Starter"),
    HAMSTER("hamster", "Nibbles", Category.NORMAL, "#E8B04B", "#A8762A",
        setOf(Feature.ROUND_EARS, Feature.CHEEK_POUCHES), "Starter"),

    // ---- Wild: care-streak unlocks ----
    WOLF("wolf", "Lupa", Category.WILD, "#8A9BB5", "#5A6B85",
        setOf(Feature.POINT_EARS, Feature.MUZZLE_PATCH), "7-day care streak"),
    FOX("fox", "Ember", Category.WILD, "#E8823F", "#A8541F",
        setOf(Feature.POINT_EARS, Feature.MUZZLE_PATCH), "14-day care streak"),
    TIGER("tiger", "Raja", Category.WILD, "#E8A33D", "#9A6218",
        setOf(Feature.POINT_EARS, Feature.STRIPES), "21-day care streak"),
    OWL("owl", "Hoot", Category.WILD, "#9A7B5A", "#6B543F",
        setOf(Feature.BIG_EYES, Feature.BEAK, Feature.EAR_TUFTS), "30-day care streak"),

    // ---- Mystical: achievements and/or premium ----
    DRAGON("dragon", "Sable", Category.MYSTICAL, "#5AC86A", "#2F8A3F",
        setOf(Feature.HORNS, Feature.WINGS, Feature.BACK_SPIKES), "60-day streak or 👑"),
    PHOENIX("phoenix", "Ash", Category.MYSTICAL, "#FF7A3F", "#B83A1A",
        setOf(Feature.CREST, Feature.WINGS), "100 lifetime goals or 👑"),
    UNICORN("unicorn", "Stella", Category.MYSTICAL, "#FAFAFF", "#C6C6E8",
        setOf(Feature.HORN, Feature.MANE), "90-day streak or 👑"),
    KRAKEN("kraken", "Ink", Category.MYSTICAL, "#4FD8C8", "#2A8A80",
        setOf(Feature.TENTACLES, Feature.EXTRA_EYES), "👑 Premium only");

    enum class Category { NORMAL, WILD, MYSTICAL }

    enum class Feature {
        POINT_EARS, FLOPPY_EARS, LONG_EARS, ROUND_EARS, EAR_TUFTS,
        WHISKERS, CHEEK_POUCHES, TONGUE, MUZZLE_PATCH, STRIPES,
        BIG_EYES, BEAK, HORNS, HORN, WINGS, BACK_SPIKES, CREST, MANE,
        TENTACLES, EXTRA_EYES
    }

    companion object {
        fun byId(id: String): Species = values().find { it.id == id } ?: CAT
        fun ofCategory(c: Category): List<Species> = values().filter { it.category == c }
    }
}
