package com.vn.pixelpet

/**
 * The 108-variant roster (v3, PLAN_V3.md §2). A variation is ONE data entry:
 * base family + palette + feature deltas + one accessory + trait bias + unlock.
 * The parametric [PetRenderer] stays a single code path — no new art pipeline.
 *
 * Trait bias is added to the 50 baseline in Phase 2 (Personality); range -20..+20.
 */
object VariantRoster {

    enum class Family {
        CAT, DOG, BUNNY, HAMSTER, PIG, DUCK, TURTLE, FROG, PENGUIN, PANDA,
        WOLF, FOX, TIGER, OWL, OX_KIN, SNAKE, BAT,
        DRAGON, PHOENIX, UNICORN, KRAKEN, AXOLOTL, GRIFFIN
    }

    /** One accessory per variant; drawn by [PetRenderer] as the topmost layer. */
    enum class Accessory {
        NONE, BOW, SCARF, HAT, GLASSES, FLOWER, COLLAR, BANDANA, CROWN, BELL, FEATHER, STAR_PIN
    }

    /** Per-variant trait bias, added to the 50 baseline (Phase 2). Range -20..+20. */
    data class TraitBias(
        val playful: Int = 0,
        val bold: Int = 0,
        val greedy: Int = 0,
        val social: Int = 0,
        val neat: Int = 0
    )

    data class Variant(
        val id: String,
        val displayName: String,
        val family: Family,
        val category: Species.Category,
        val bodyHex: String,
        val darkHex: String,
        val features: Set<Species.Feature>,
        val accessory: Accessory,
        val traitBias: TraitBias,
        val unlockText: String
    )

    // Shorthand aliases so the roster below stays readable.
    private typealias F = Species.Feature
    private typealias C = Species.Category
    private typealias A = Accessory
    private typealias Fam = Family

    val ALL: List<Variant> = listOf(
        // ---- Normal (57): available from the start ----
        // Cats (12)
        Variant("cat-tabby", "Tabby Cat", Fam.CAT, C.NORMAL, "#C9A17A", "#8A6540",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.STRIPES), A.COLLAR,
            TraitBias(playful = 5, neat = 5), "Starter"),
        Variant("cat-tuxedo", "Tuxedo Cat", Fam.CAT, C.NORMAL, "#3A3A4A", "#1E1E2A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL), A.BOW,
            TraitBias(playful = 5, neat = 5), "Starter"),
        Variant("cat-siamese", "Siamese Cat", Fam.CAT, C.NORMAL, "#E8DCC8", "#6B5A4A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.MUZZLE_PATCH), A.BELL,
            TraitBias(playful = 5, neat = 5), "Starter"),
        Variant("cat-persian", "Persian Cat", Fam.CAT, C.NORMAL, "#F2E8E0", "#C9B8A8",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.CHEEK_POUCHES), A.CROWN,
            TraitBias(neat = 10), "Starter"),
        Variant("cat-bengal", "Bengal Cat", Fam.CAT, C.NORMAL, "#D89A4A", "#8A5A1A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.SPOTS), A.BANDANA,
            TraitBias(playful = 10, bold = 5), "Starter"),
        Variant("cat-calico", "Calico Cat", Fam.CAT, C.NORMAL, "#F0E6D8", "#B89A7A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.SPOTS), A.FLOWER,
            TraitBias(playful = 5, neat = 5), "Starter"),
        Variant("cat-black", "Black Cat", Fam.CAT, C.NORMAL, "#2E2E3A", "#14141F",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL), A.NONE,
            TraitBias(playful = 5, bold = -5, neat = 5), "Starter"),
        Variant("cat-white", "White Cat", Fam.CAT, C.NORMAL, "#FAFAFA", "#D8D8E8",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL), A.BOW,
            TraitBias(playful = 5, neat = 10), "Starter"),
        Variant("cat-ginger", "Ginger Cat", Fam.CAT, C.NORMAL, "#E89A3F", "#A85A1A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.STRIPES), A.SCARF,
            TraitBias(playful = 10, greedy = 5), "Starter"),
        Variant("cat-sphynx", "Sphynx Cat", Fam.CAT, C.NORMAL, "#E8B8A0", "#B87A5A",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL), A.GLASSES,
            TraitBias(playful = 5, social = 5), "Starter"),
        Variant("cat-maine-coon", "Maine Coon", Fam.CAT, C.NORMAL, "#8A6A4A", "#5A3E28",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.EAR_TUFTS), A.FEATHER,
            TraitBias(playful = 5, bold = 5), "Starter"),
        Variant("cat-ragdoll", "Ragdoll Cat", Fam.CAT, C.NORMAL, "#E8E0F0", "#B8A8C8",
            setOf(F.POINT_EARS, F.WHISKERS, F.TAIL, F.MUZZLE_PATCH), A.BELL,
            TraitBias(social = 10, neat = 5), "Starter"),
        // Dogs (15 breeds)
        Variant("dog-labrador", "Labrador", Fam.DOG, C.NORMAL, "#2E2E2E", "#14141F",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL), A.COLLAR,
            TraitBias(playful = 5, social = 10), "Starter"),
        Variant("dog-golden", "Golden Retriever", Fam.DOG, C.NORMAL, "#E8B84B", "#A8762A",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL, F.MANE), A.BANDANA,
            TraitBias(playful = 5, social = 10), "Starter"),
        Variant("dog-german-shepherd", "German Shepherd", Fam.DOG, C.NORMAL, "#8A6A4A", "#4A3420",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.STAR_PIN,
            TraitBias(playful = 5, bold = 5, social = 5), "Starter"),
        Variant("dog-pug", "Pug", Fam.DOG, C.NORMAL, "#D8B89A", "#8A6A4A",
            setOf(F.ROUND_EARS, F.TONGUE, F.TAIL, F.SNOUT), A.BOW,
            TraitBias(playful = 10, greedy = 10, social = 10), "Starter"),
        Variant("dog-husky", "Husky", Fam.DOG, C.NORMAL, "#D8DEE8", "#6A7A8A",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.SCARF,
            TraitBias(playful = 5, social = 10), "Starter"),
        Variant("dog-corgi", "Corgi", Fam.DOG, C.NORMAL, "#D89A4A", "#8A5A1A",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.BOW,
            TraitBias(playful = 10, greedy = 5, social = 10), "Starter"),
        Variant("dog-beagle", "Beagle", Fam.DOG, C.NORMAL, "#D8C8A8", "#8A6A4A",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL), A.BELL,
            TraitBias(playful = 5, social = 10), "Starter"),
        Variant("dog-poodle", "Poodle", Fam.DOG, C.NORMAL, "#F2F2FA", "#B9B9D6",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL, F.CREST), A.FLOWER,
            TraitBias(playful = 5, social = 5, neat = 10), "Starter"),
        Variant("dog-bulldog", "Bulldog", Fam.DOG, C.NORMAL, "#D8A87A", "#8A5A3A",
            setOf(F.ROUND_EARS, F.TONGUE, F.TAIL, F.SNOUT, F.CHEEK_POUCHES), A.BANDANA,
            TraitBias(greedy = 5, social = 5), "Starter"),
        Variant("dog-dalmatian", "Dalmatian", Fam.DOG, C.NORMAL, "#FAFAFA", "#D8D8E8",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL, F.SPOTS), A.COLLAR,
            TraitBias(playful = 10, social = 10), "Starter"),
        Variant("dog-chihuahua", "Chihuahua", Fam.DOG, C.NORMAL, "#C89A6A", "#7A4A28",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL), A.BOW,
            TraitBias(playful = 5, bold = 10, social = 5), "Starter"),
        Variant("dog-shiba", "Shiba Inu", Fam.DOG, C.NORMAL, "#E8A85A", "#A85A1A",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.BANDANA,
            TraitBias(playful = 5, bold = 5, neat = 5), "Starter"),
        Variant("dog-border-collie", "Border Collie", Fam.DOG, C.NORMAL, "#3A3A3A", "#14141F",
            setOf(F.POINT_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.STAR_PIN,
            TraitBias(playful = 10, social = 10), "Starter"),
        Variant("dog-dachshund", "Dachshund", Fam.DOG, C.NORMAL, "#6A4A28", "#3A2812",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL), A.SCARF,
            TraitBias(playful = 5, social = 10), "Starter"),
        Variant("dog-great-dane", "Great Dane", Fam.DOG, C.NORMAL, "#5A5A6A", "#2E2E3A",
            setOf(F.FLOPPY_EARS, F.TONGUE, F.TAIL, F.MUZZLE_PATCH), A.COLLAR,
            TraitBias(bold = 10), "Starter"),
        // Bunnies (5)
        Variant("bunny-white", "White Bunny", Fam.BUNNY, C.NORMAL, "#FAFAFA", "#D8D8E8",
            setOf(F.LONG_EARS, F.TAIL), A.BOW,
            TraitBias(bold = -5, neat = 5), "Starter"),
        Variant("bunny-grey", "Grey Bunny", Fam.BUNNY, C.NORMAL, "#9A9AA8", "#5A5A6A",
            setOf(F.LONG_EARS, F.TAIL), A.SCARF,
            TraitBias(bold = -5, neat = 5), "Starter"),
        Variant("bunny-brown", "Brown Bunny", Fam.BUNNY, C.NORMAL, "#A8762A", "#6A4A1A",
            setOf(F.LONG_EARS, F.TAIL, F.MUZZLE_PATCH), A.FLOWER,
            TraitBias(bold = -5, neat = 5), "Starter"),
        Variant("bunny-lop", "Lop Bunny", Fam.BUNNY, C.NORMAL, "#D8B89A", "#8A6A4A",
            setOf(F.LONG_EARS, F.TAIL, F.FLOPPY_EARS), A.BELL,
            TraitBias(bold = -5, neat = 5), "Starter"),
        Variant("bunny-angora", "Angora Bunny", Fam.BUNNY, C.NORMAL, "#F0E8F0", "#C8B8D8",
            setOf(F.LONG_EARS, F.TAIL, F.CHEEK_POUCHES), A.FLOWER,
            TraitBias(bold = -5, neat = 5), "Starter"),
        // Hamsters (4)
        Variant("hamster-golden", "Golden Hamster", Fam.HAMSTER, C.NORMAL, "#E8B04B", "#A8762A",
            setOf(F.ROUND_EARS, F.CHEEK_POUCHES), A.BOW,
            TraitBias(greedy = 5, neat = 5), "Starter"),
        Variant("hamster-panda", "Panda Hamster", Fam.HAMSTER, C.NORMAL, "#F2F2F2", "#3A3A3A",
            setOf(F.ROUND_EARS, F.CHEEK_POUCHES, F.SPOTS), A.BELL,
            TraitBias(greedy = 5, neat = 5), "Starter"),
        Variant("hamster-robo", "Robo Hamster", Fam.HAMSTER, C.NORMAL, "#D8C8A8", "#8A6A4A",
            setOf(F.ROUND_EARS, F.CHEEK_POUCHES), A.SCARF,
            TraitBias(greedy = 5, neat = 5), "Starter"),
        Variant("hamster-winter-white", "Winter White Hamster", Fam.HAMSTER, C.NORMAL, "#E8E8F2", "#A8A8C8",
            setOf(F.ROUND_EARS, F.CHEEK_POUCHES, F.MUZZLE_PATCH), A.FLOWER,
            TraitBias(greedy = 5, neat = 5), "Starter"),
        // Pigs (6)
        Variant("pig-pink", "Pink Pig", Fam.PIG, C.NORMAL, "#FFB8C8", "#E87A9A",
            setOf(F.ROUND_EARS, F.SNOUT, F.TAIL), A.BOW,
            TraitBias(greedy = 10, neat = -10), "Starter"),
        Variant("pig-spotted", "Spotted Pig", Fam.PIG, C.NORMAL, "#FFC8D8", "#C86A8A",
            setOf(F.ROUND_EARS, F.SNOUT, F.TAIL, F.SPOTS), A.BANDANA,
            TraitBias(greedy = 10, neat = -10), "Starter"),
        Variant("pig-mini", "Mini Pig", Fam.PIG, C.NORMAL, "#FFD8E0", "#F09AB0",
            setOf(F.ROUND_EARS, F.SNOUT, F.TAIL), A.BELL,
            TraitBias(playful = 5, greedy = 10, social = 5, neat = -10), "Starter"),
        Variant("pig-mud", "Mud Pig", Fam.PIG, C.NORMAL, "#B89A7A", "#7A5A3A",
            setOf(F.ROUND_EARS, F.SNOUT, F.TAIL, F.SPOTS), A.NONE,
            TraitBias(greedy = 10, neat = -10), "Starter"),
        Variant("pig-wild-boar", "Wild Boar", Fam.PIG, C.NORMAL, "#6A4A28", "#3A2812",
            setOf(F.POINT_EARS, F.SNOUT, F.TAIL), A.NONE,
            TraitBias(bold = 5, greedy = 5, neat = -10), "Starter"),
        Variant("pig-piglet", "Piglet", Fam.PIG, C.NORMAL, "#FFC8D8", "#F08AA0",
            setOf(F.ROUND_EARS, F.SNOUT, F.TAIL), A.FLOWER,
            TraitBias(playful = 10, greedy = 10, social = 10, neat = -10), "Starter"),
        // Ducks (3)
        Variant("duck-yellow", "Yellow Duck", Fam.DUCK, C.NORMAL, "#FFD84A", "#D8A81A",
            setOf(F.BILL, F.TAIL, F.WINGS), A.BOW,
            TraitBias(social = 5), "Starter"),
        Variant("duck-mallard", "Mallard Duck", Fam.DUCK, C.NORMAL, "#3A8A3A", "#1E4A1E",
            setOf(F.BILL, F.TAIL, F.WINGS), A.SCARF,
            TraitBias(social = 5), "Starter"),
        Variant("duck-white", "White Duck", Fam.DUCK, C.NORMAL, "#FAFAFA", "#D8D8E8",
            setOf(F.BILL, F.TAIL, F.WINGS), A.FLOWER,
            TraitBias(social = 5), "Starter"),
        // Turtles (3)
        Variant("turtle-green", "Green Turtle", Fam.TURTLE, C.NORMAL, "#5AC86A", "#2F8A3F",
            setOf(F.SHELL, F.TAIL), A.NONE,
            TraitBias(playful = -5, bold = -10), "Starter"),
        Variant("turtle-red-eared", "Red-Eared Turtle", Fam.TURTLE, C.NORMAL, "#4A9A5A", "#2A5A2A",
            setOf(F.SHELL, F.TAIL, F.SPOTS), A.BELL,
            TraitBias(playful = -5, bold = -10), "Starter"),
        Variant("turtle-tortoise", "Tortoise", Fam.TURTLE, C.NORMAL, "#8A7A4A", "#5A4A28",
            setOf(F.SHELL, F.TAIL, F.SCALES), A.HAT,
            TraitBias(playful = -5, bold = -10), "Starter"),
        // Frogs (3)
        Variant("frog-green", "Green Frog", Fam.FROG, C.NORMAL, "#6AC85A", "#2F8A2A",
            setOf(F.BIG_EYES), A.NONE,
            TraitBias(playful = 5, bold = -5), "Starter"),
        Variant("frog-tree", "Tree Frog", Fam.FROG, C.NORMAL, "#4AC84A", "#1E7A1E",
            setOf(F.BIG_EYES, F.SPOTS), A.FLOWER,
            TraitBias(playful = 5, bold = -5), "Starter"),
        Variant("frog-poison-dart", "Poison Dart Frog", Fam.FROG, C.NORMAL, "#3A6AE8", "#1E3A8A",
            setOf(F.BIG_EYES, F.SPOTS), A.STAR_PIN,
            TraitBias(playful = 5, bold = -5), "Starter"),
        // Penguins (3)
        Variant("penguin-emperor", "Emperor Penguin", Fam.PENGUIN, C.NORMAL, "#2E2E3A", "#14141F",
            setOf(F.FLIPPERS, F.BEAK), A.SCARF,
            TraitBias(social = 5, neat = 5), "Starter"),
        Variant("penguin-rockhopper", "Rockhopper Penguin", Fam.PENGUIN, C.NORMAL, "#3A3A4A", "#1E1E2A",
            setOf(F.FLIPPERS, F.BEAK, F.CREST), A.FEATHER,
            TraitBias(social = 5, neat = 5), "Starter"),
        Variant("penguin-baby", "Baby Penguin", Fam.PENGUIN, C.NORMAL, "#6A6A7A", "#3A3A4A",
            setOf(F.FLIPPERS, F.BEAK), A.BOW,
            TraitBias(playful = 5, social = 10, neat = 5), "Starter"),
        // Pandas (3)
        Variant("panda-giant", "Giant Panda", Fam.PANDA, C.NORMAL, "#FAFAFA", "#D8D8E8",
            setOf(F.ROUND_EARS, F.MUZZLE_PATCH, F.SPOTS), A.NONE,
            TraitBias(playful = -5, greedy = 5), "Starter"),
        Variant("panda-red", "Red Panda", Fam.PANDA, C.NORMAL, "#C85A2A", "#7A2812",
            setOf(F.ROUND_EARS, F.MUZZLE_PATCH, F.TAIL), A.FLOWER,
            TraitBias(playful = -5, greedy = 5), "Starter"),
        Variant("panda-baby", "Baby Panda", Fam.PANDA, C.NORMAL, "#F2F2F2", "#C8C8D8",
            setOf(F.ROUND_EARS, F.MUZZLE_PATCH), A.BOW,
            TraitBias(playful = 5, greedy = 5, social = 5), "Starter"),

        // ---- Wild (27): care-streak unlocks ----
        // Wolves (4)
        Variant("wolf-grey", "Grey Wolf", Fam.WOLF, C.WILD, "#8A9BB5", "#5A6B85",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.NONE,
            TraitBias(bold = 5, social = -5), "7-day care streak"),
        Variant("wolf-arctic", "Arctic Wolf", Fam.WOLF, C.WILD, "#F2F2FA", "#C8C8D8",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.SCARF,
            TraitBias(bold = 5, social = -5), "7-day care streak"),
        Variant("wolf-black", "Black Wolf", Fam.WOLF, C.WILD, "#2E2E3A", "#14141F",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.STAR_PIN,
            TraitBias(bold = 5, social = -5), "7-day care streak"),
        Variant("wolf-timber", "Timber Wolf", Fam.WOLF, C.WILD, "#6A7A8A", "#3A4A5A",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.FEATHER,
            TraitBias(bold = 5, social = -5), "7-day care streak"),
        // Foxes (4)
        Variant("fox-red", "Red Fox", Fam.FOX, C.WILD, "#E8823F", "#A8541F",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.NONE,
            TraitBias(playful = 5, bold = 5), "14-day care streak"),
        Variant("fox-arctic", "Arctic Fox", Fam.FOX, C.WILD, "#F2F2FA", "#C8C8D8",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.SCARF,
            TraitBias(playful = 5, bold = 5), "14-day care streak"),
        Variant("fox-fennec", "Fennec Fox", Fam.FOX, C.WILD, "#E8C89A", "#A87A4A",
            setOf(F.LONG_EARS, F.MUZZLE_PATCH, F.TAIL), A.FLOWER,
            TraitBias(playful = 5, bold = 5), "14-day care streak"),
        Variant("fox-silver", "Silver Fox", Fam.FOX, C.WILD, "#6A6A7A", "#2E2E3A",
            setOf(F.POINT_EARS, F.MUZZLE_PATCH, F.TAIL), A.STAR_PIN,
            TraitBias(playful = 5, bold = 5), "14-day care streak"),
        // Tigers (4)
        Variant("tiger-bengal", "Bengal Tiger", Fam.TIGER, C.WILD, "#E8A33D", "#9A6218",
            setOf(F.POINT_EARS, F.STRIPES, F.TAIL), A.NONE,
            TraitBias(playful = 5, bold = 10), "21-day care streak"),
        Variant("tiger-white", "White Tiger", Fam.TIGER, C.WILD, "#FAFAFA", "#C8C8D8",
            setOf(F.POINT_EARS, F.STRIPES, F.TAIL), A.SCARF,
            TraitBias(playful = 5, bold = 10), "21-day care streak"),
        Variant("tiger-siberian", "Siberian Tiger", Fam.TIGER, C.WILD, "#D89A3A", "#8A5A1A",
            setOf(F.POINT_EARS, F.STRIPES, F.TAIL), A.BANDANA,
            TraitBias(playful = 5, bold = 10), "21-day care streak"),
        Variant("tiger-cub", "Tiger Cub", Fam.TIGER, C.WILD, "#F0B84A", "#B87A28",
            setOf(F.POINT_EARS, F.STRIPES, F.TAIL), A.BOW,
            TraitBias(playful = 10, social = 5), "21-day care streak"),
        // Owls (4)
        Variant("owl-barn", "Barn Owl", Fam.OWL, C.WILD, "#D8C8A8", "#8A6A4A",
            setOf(F.BIG_EYES, F.BEAK, F.EAR_TUFTS), A.GLASSES,
            TraitBias(playful = -5, bold = -5, neat = 5), "30-day care streak"),
        Variant("owl-snowy", "Snowy Owl", Fam.OWL, C.WILD, "#FAFAFA", "#D8D8E8",
            setOf(F.BIG_EYES, F.BEAK, F.EAR_TUFTS), A.SCARF,
            TraitBias(playful = -5, bold = -5, neat = 5), "30-day care streak"),
        Variant("owl-horned", "Horned Owl", Fam.OWL, C.WILD, "#8A6A4A", "#4A3420",
            setOf(F.BIG_EYES, F.BEAK, F.EAR_TUFTS), A.HAT,
            TraitBias(playful = -5, bold = -5, neat = 5), "30-day care streak"),
        Variant("owl-tawny", "Tawny Owl", Fam.OWL, C.WILD, "#A8762A", "#6A4A1A",
            setOf(F.BIG_EYES, F.BEAK, F.EAR_TUFTS), A.FEATHER,
            TraitBias(playful = -5, bold = -5, neat = 5), "30-day care streak"),
        // Ox & kin (6)
        Variant("ox-ox", "Ox", Fam.OX_KIN, C.WILD, "#5A4A3A", "#2E241A",
            setOf(F.HORNS, F.SNOUT, F.TAIL), A.NONE,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        Variant("ox-bison", "Bison", Fam.OX_KIN, C.WILD, "#6A4A28", "#3A2812",
            setOf(F.HORNS, F.SNOUT, F.TAIL, F.MANE), A.BANDANA,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        Variant("ox-buffalo", "Buffalo", Fam.OX_KIN, C.WILD, "#3A3A3A", "#14141F",
            setOf(F.HORNS, F.SNOUT, F.TAIL), A.STAR_PIN,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        Variant("ox-cow", "Cow", Fam.OX_KIN, C.WILD, "#FAFAFA", "#C8C8D8",
            setOf(F.HORNS, F.SNOUT, F.TAIL, F.SPOTS), A.BELL,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        Variant("ox-yak", "Yak", Fam.OX_KIN, C.WILD, "#4A4A5A", "#24242E",
            setOf(F.HORNS, F.SNOUT, F.TAIL, F.MANE), A.SCARF,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        Variant("ox-highland", "Highland Cow", Fam.OX_KIN, C.WILD, "#A85A28", "#6A3412",
            setOf(F.HORNS, F.SNOUT, F.TAIL, F.MANE, F.CREST), A.FLOWER,
            TraitBias(bold = 5, greedy = 5, neat = -5), "30-day care streak"),
        // Snakes (3)
        Variant("snake-green", "Green Snake", Fam.SNAKE, C.WILD, "#5AC85A", "#2F8A2A",
            setOf(F.TONGUE, F.SCALES), A.NONE,
            TraitBias(bold = -5, neat = 5), "14-day care streak"),
        Variant("snake-python", "Python", Fam.SNAKE, C.WILD, "#C8A85A", "#7A5A28",
            setOf(F.TONGUE, F.SCALES, F.SPOTS), A.BANDANA,
            TraitBias(bold = -5, neat = 5), "14-day care streak"),
        Variant("snake-coral", "Coral Snake", Fam.SNAKE, C.WILD, "#E85A3A", "#A81E1E",
            setOf(F.TONGUE, F.SCALES, F.STRIPES), A.BELL,
            TraitBias(bold = -5, neat = 5), "14-day care streak"),
        // Bats (2)
        Variant("bat-fruit", "Fruit Bat", Fam.BAT, C.WILD, "#6A4A28", "#3A2812",
            setOf(F.WINGS, F.POINT_EARS), A.FLOWER,
            TraitBias(playful = 5, bold = -5), "21-day care streak"),
        Variant("bat-vampire", "Vampire Bat", Fam.BAT, C.WILD, "#3A3A4A", "#14141F",
            setOf(F.WINGS, F.POINT_EARS, F.TONGUE), A.NONE,
            TraitBias(playful = 5, bold = -5), "21-day care streak"),

        // ---- Mystical (24): achievements and/or premium ----
        // Dragons (10)
        Variant("dragon-fire", "Fire Dragon", Fam.DRAGON, C.MYSTICAL, "#E85A3A", "#A81E1E",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.CROWN,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-ice", "Ice Dragon", Fam.DRAGON, C.MYSTICAL, "#A8D8F0", "#5A8AB8",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.STAR_PIN,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-storm", "Storm Dragon", Fam.DRAGON, C.MYSTICAL, "#6A6A8A", "#3A3A5A",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.NONE,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-forest", "Forest Dragon", Fam.DRAGON, C.MYSTICAL, "#4A9A3A", "#1E5A1E",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.FLOWER,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-shadow", "Shadow Dragon", Fam.DRAGON, C.MYSTICAL, "#3A3A5A", "#14142A",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.NONE,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-gold", "Gold Dragon", Fam.DRAGON, C.MYSTICAL, "#E8B84B", "#A8762A",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.CROWN,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-water", "Water Dragon", Fam.DRAGON, C.MYSTICAL, "#3A8AC8", "#1E4A6A",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.BELL,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-ember", "Ember Dragon", Fam.DRAGON, C.MYSTICAL, "#C85A1E", "#7A2812",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.FEATHER,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-crystal", "Crystal Dragon", Fam.DRAGON, C.MYSTICAL, "#C8D8F0", "#8AA8C8",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.STAR_PIN,
            TraitBias(bold = 10, greedy = 5), "60-day streak or 👑"),
        Variant("dragon-baby", "Baby Dragon", Fam.DRAGON, C.MYSTICAL, "#8AC86A", "#4A8A3A",
            setOf(F.HORNS, F.WINGS, F.BACK_SPIKES, F.TAIL, F.SCALES), A.BOW,
            TraitBias(playful = 10, greedy = 5, social = 10), "60-day streak or 👑"),
        // Phoenix (3)
        Variant("phoenix-flame", "Flame Phoenix", Fam.PHOENIX, C.MYSTICAL, "#FF7A3F", "#B83A1A",
            setOf(F.CREST, F.WINGS, F.TAIL), A.CROWN,
            TraitBias(playful = 5, bold = 5), "100 lifetime goals or 👑"),
        Variant("phoenix-ember", "Ember Phoenix", Fam.PHOENIX, C.MYSTICAL, "#D85A1E", "#8A2812",
            setOf(F.CREST, F.WINGS, F.TAIL), A.FEATHER,
            TraitBias(playful = 5, bold = 5), "100 lifetime goals or 👑"),
        Variant("phoenix-dawn", "Dawn Phoenix", Fam.PHOENIX, C.MYSTICAL, "#FFB84A", "#D87A1E",
            setOf(F.CREST, F.WINGS, F.TAIL), A.FLOWER,
            TraitBias(playful = 5, bold = 5), "100 lifetime goals or 👑"),
        // Unicorns (3)
        Variant("unicorn-white", "White Unicorn", Fam.UNICORN, C.MYSTICAL, "#FAFAFF", "#C6C6E8",
            setOf(F.HORN, F.MANE, F.TAIL), A.CROWN,
            TraitBias(social = 5, neat = 10), "90-day streak or 👑"),
        Variant("unicorn-starlight", "Starlight Unicorn", Fam.UNICORN, C.MYSTICAL, "#E8E0FF", "#A890D8",
            setOf(F.HORN, F.MANE, F.TAIL), A.STAR_PIN,
            TraitBias(social = 5, neat = 10), "90-day streak or 👑"),
        Variant("unicorn-rainbow", "Rainbow Unicorn", Fam.UNICORN, C.MYSTICAL, "#FFC8E0", "#C878A8",
            setOf(F.HORN, F.MANE, F.TAIL), A.FLOWER,
            TraitBias(social = 5, neat = 10), "90-day streak or 👑"),
        // Kraken (3)
        Variant("kraken-teal", "Teal Kraken", Fam.KRAKEN, C.MYSTICAL, "#4FD8C8", "#2A8A80",
            setOf(F.TENTACLES, F.EXTRA_EYES), A.CROWN,
            TraitBias(playful = 5, bold = -5), "👑 Premium only"),
        Variant("kraken-deep-sea", "Deep Sea Kraken", Fam.KRAKEN, C.MYSTICAL, "#2A5A8A", "#142A4A",
            setOf(F.TENTACLES, F.EXTRA_EYES), A.NONE,
            TraitBias(playful = 5, bold = -5), "👑 Premium only"),
        Variant("kraken-baby", "Baby Kraken", Fam.KRAKEN, C.MYSTICAL, "#8AE8D8", "#4AA89A",
            setOf(F.TENTACLES, F.EXTRA_EYES), A.BOW,
            TraitBias(playful = 10, social = 10), "👑 Premium only"),
        // Axolotl (2)
        Variant("axolotl-pink", "Pink Axolotl", Fam.AXOLOTL, C.MYSTICAL, "#FFB8D8", "#E87AA8",
            setOf(F.GILLS, F.TAIL), A.BOW,
            TraitBias(bold = -10, neat = 5), "60-day streak or 👑"),
        Variant("axolotl-golden", "Golden Axolotl", Fam.AXOLOTL, C.MYSTICAL, "#FFD88A", "#D8A83A",
            setOf(F.GILLS, F.TAIL), A.STAR_PIN,
            TraitBias(bold = -10, neat = 5), "60-day streak or 👑"),
        // Griffins (3)
        Variant("griffin-gold", "Gold Griffin", Fam.GRIFFIN, C.MYSTICAL, "#C89A3A", "#7A5A1A",
            setOf(F.WINGS, F.BEAK, F.POINT_EARS, F.TAIL), A.CROWN,
            TraitBias(playful = 5, bold = 10), "90-day streak or 👑"),
        Variant("griffin-storm", "Storm Griffin", Fam.GRIFFIN, C.MYSTICAL, "#7A7A9A", "#4A4A6A",
            setOf(F.WINGS, F.BEAK, F.POINT_EARS, F.TAIL), A.FEATHER,
            TraitBias(playful = 5, bold = 10), "90-day streak or 👑"),
        Variant("griffin-forest", "Forest Griffin", Fam.GRIFFIN, C.MYSTICAL, "#5A8A3A", "#2A5A1A",
            setOf(F.WINGS, F.BEAK, F.POINT_EARS, F.TAIL), A.FLOWER,
            TraitBias(playful = 5, bold = 10), "90-day streak or 👑")
    )

    /**
     * Resolve a v2 species id ("cat") to its default v3 variant ("cat-tabby").
     * Used by Phase 3 (MainActivity power wiring). Additive — no roster change.
     */
    fun variantForSpeciesId(speciesId: String): Variant {
        byId(speciesId)?.let { return it }
        ALL.firstOrNull { it.id.startsWith("$speciesId-") }?.let { return it }
        return ALL.first()
    }

    fun byId(id: String): Variant? = ALL.find { it.id == id }

    fun ofCategory(category: Species.Category): List<Variant> =
        ALL.filter { it.category == category }

    fun ofFamily(family: Family): List<Variant> = ALL.filter { it.family == family }
}
