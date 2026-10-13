package com.vn.pixelpet

import kotlin.random.Random

/**
 * Canned speech for the v3 pet (PLAN_V3.md §5). Every line is hand-written,
 * original, and picked by weighted random — no generation, no network, no
 * tokens. Variety comes from composition: the effective pool for any
 * (archetype, mood) pair is the union of combo-specific lines + mood lines +
 * archetype lines, always >= 15 lines, with a no-repeat window of 8 so the
 * pet never says the same thing twice in a row.
 */
object SpeechBank {

    enum class Archetype {
        PLAYFUL, CALM, BOLD, SHY, GREEDY, PICKY,
        SOCIAL, INDEPENDENT, NEAT, MESSY, BALANCED
    }

    /** No-repeat window: the last N lines are excluded from the next pick. */
    const val NO_REPEAT_WINDOW = 8

    private val MOOD_LINES: Map<PetRenderer.Mood, List<String>> = mapOf(
        PetRenderer.Mood.HAPPY to listOf(
            "I could bounce all day!",
            "Best day ever, and it is still morning.",
            "My tail will not stop wagging.",
            "I feel sparkly inside.",
            "This is my happy wiggle.",
            "Everything smells like sunshine.",
            "I want to run in a big circle.",
            "You make my heart do hops."
        ),
        PetRenderer.Mood.CONTENT to listOf(
            "All is well in my little world.",
            "Just here, being cozy.",
            "I like this quiet moment.",
            "Nothing to chase. Nothing to fear.",
            "My belly is full and my heart is calm.",
            "This is a good sitting spot.",
            "I am content as a cat in sun.",
            "Soft day, soft mood."
        ),
        PetRenderer.Mood.HUNGRY to listOf(
            "My tummy is making noises.",
            "Is that a snack I smell?",
            "Feed me and I will love you forever.",
            "I am running on empty.",
            "My bowl looks very lonely.",
            "One tiny bite? Please?",
            "I could eat a whole mountain.",
            "Hungry, hungry, hungry."
        ),
        PetRenderer.Mood.SAD to listOf(
            "I feel a bit droopy.",
            "Could use a hug right now.",
            "The clouds feel heavy today.",
            "My ears are down.",
            "I miss the fun times.",
            "Everything feels slow.",
            "A little pat would help.",
            "I am not sulking, just sad."
        ),
        PetRenderer.Mood.SULKING to listOf(
            "Hmph. I saw that.",
            "I am not talking to you.",
            "Go away. ...Okay, stay.",
            "You and your doomscrolling.",
            "I demand an apology snack.",
            "My sulk is very professional.",
            "Fine. I will sit here and be upset.",
            "You have 30 minutes to fix this."
        ),
        PetRenderer.Mood.SLEEPING to listOf(
            "Zzz... five more minutes...",
            "Dreaming of snacks...",
            "Shhh... chasing dream butterflies.",
            "Zzz... do not wake the dragon...",
            "Sleepy... so sleepy...",
            "Mmm... warm blanket...",
            "Zzz... snack mountain...",
            "Do not disturb the snooze."
        ),
        PetRenderer.Mood.SICK to listOf(
            "I feel wobbly.",
            "My tummy hurts.",
            "Could you sit with me?",
            "I need rest and cuddles.",
            "Everything spins a little.",
            "I am not my best self today.",
            "Soup would be nice.",
            "Please be gentle with me."
        )
    )

    private val ARCHETYPE_LINES: Map<Archetype, List<String>> = mapOf(
        Archetype.PLAYFUL to listOf(
            "Race you to the corner!",
            "Again! Again! Again!",
            "I am a zoom machine!",
            "Tag! You are it!",
            "Let us play until we flop.",
            "I have endless wiggles.",
            "Throw it! Throw it!",
            "Playtime is the best time."
        ),
        Archetype.CALM to listOf(
            "Slow and steady wins.",
            "I like the quiet side of life.",
            "No rush. Never rush.",
            "A nap solves most things.",
            "Peaceful is my favorite speed.",
            "Let us watch the clouds.",
            "I move at cuddle pace.",
            "Calm is a superpower."
        ),
        Archetype.BOLD to listOf(
            "I fear nothing. Except vacuums.",
            "Watch me leap!",
            "Adventure! Right now!",
            "I go first. Always.",
            "Bring on the big world!",
            "I am brave and I know it.",
            "No mountain too tall.",
            "Charge!"
        ),
        Archetype.SHY to listOf(
            "Um... hello, from over here.",
            "I will peek from behind this pillow.",
            "Big rooms are scary.",
            "Can we stay close?",
            "I am brave on the inside.",
            "Small steps, big heart.",
            "I like hiding spots.",
            "Please do not sneak up on me."
        ),
        Archetype.GREEDY to listOf(
            "More snacks. Always more.",
            "I can smell treats from miles away.",
            "My stomach has no bottom.",
            "One more bite. Just one.",
            "Food is my love language.",
            "I would trade a toy for a cookie.",
            "Snack o clock, all day.",
            "Is that for me? It is for me."
        ),
        Archetype.PICKY to listOf(
            "That snack? No thank you.",
            "I only eat the good stuff.",
            "My standards are high.",
            "Hmm. I will pass.",
            "Quality over quantity.",
            "That toy is beneath me.",
            "I know what I like.",
            "Nope. Next."
        ),
        Archetype.SOCIAL to listOf(
            "Hello! Hello! Everyone, hello!",
            "Friends! I love friends!",
            "Come sit with me!",
            "The more the merrier!",
            "I missed you SO much.",
            "Let us do everything together!",
            "Hugs for everyone!",
            "You are my favorite human."
        ),
        Archetype.INDEPENDENT to listOf(
            "I am fine on my own.",
            "I like my space.",
            "Go ahead, I will be here.",
            "Solo time is good time.",
            "I do not need constant pats.",
            "I am my own pet.",
            "Alone but not lonely.",
            "I will come when I feel like it."
        ),
        Archetype.NEAT to listOf(
            "Everything in its place.",
            "I just groomed. Admire me.",
            "Tidy fur, tidy mind.",
            "No mud on these paws.",
            "I keep my corner spotless.",
            "Clean and proud.",
            "A place for everything.",
            "Mess? Never heard of it."
        ),
        Archetype.MESSY to listOf(
            "Mud is just skin care.",
            "I found a puddle!",
            "Who needs grooming?",
            "Messy is my style.",
            "I rolled in something. Again.",
            "Clean is boring.",
            "Dirt makes me happy.",
            "My fur has character."
        ),
        Archetype.BALANCED to listOf(
            "Today feels just right.",
            "A little of everything.",
            "Steady as I go.",
            "I am good, really good.",
            "Balanced and happy.",
            "Nothing too much, nothing too little.",
            "Just me, being me.",
            "Life is good."
        )
    )

    private val COMBO_LINES: Map<Pair<Archetype, PetRenderer.Mood>, List<String>> = mapOf(
        (Archetype.PLAYFUL to PetRenderer.Mood.HAPPY) to listOf(
            "Happy AND bouncy? Dangerous combo!",
            "I am a happiness rocket!",
            "Wiggle level: maximum!",
            "Catch me if you can!",
            "Joy overload! Sending hugs!",
            "Bounciest day ever!"
        ),
        (Archetype.PLAYFUL to PetRenderer.Mood.HUNGRY) to listOf(
            "Hungry but still zooming!",
            "Feed the zoom machine!",
            "I will play for snacks!",
            "Snack first, zoom later!",
            "My tummy says play, my legs say snack!",
            "Treats make me faster!"
        ),
        (Archetype.CALM to PetRenderer.Mood.CONTENT) to listOf(
            "Perfectly calm, perfectly content.",
            "This is my favorite kind of day.",
            "Quiet joy is still joy.",
            "I could sit here forever.",
            "Peaceful and pleased.",
            "Soft and satisfied."
        ),
        (Archetype.CALM to PetRenderer.Mood.SLEEPING) to listOf(
            "Sleeping like a pro.",
            "Calm dreams only.",
            "Drifting... slowly...",
            "The calmest sleeper.",
            "Shhh... napping in peace.",
            "Dreaming of still ponds."
        ),
        (Archetype.BOLD to PetRenderer.Mood.HAPPY) to listOf(
            "Brave and happy! Unstoppable!",
            "Happiness looks good on me!",
            "I am fearless AND joyful!",
            "Big smile, bigger courage!",
            "Nothing can stop this mood!",
            "Bold heart, happy heart!"
        ),
        (Archetype.SHY to PetRenderer.Mood.SAD) to listOf(
            "I feel small today.",
            "Can I hide for a bit?",
            "Everything feels too big.",
            "A quiet hug, please?",
            "I will be okay. Eventually.",
            "Small pet, big feelings."
        ),
        (Archetype.SHY to PetRenderer.Mood.SULKING) to listOf(
            "I am upset AND shy about it.",
            "Sulking quietly in my corner.",
            "I will forgive you... eventually.",
            "Please notice I am sulking.",
            "Quiet sulk in progress.",
            "I need space and snacks."
        ),
        (Archetype.GREEDY to PetRenderer.Mood.HUNGRY) to listOf(
            "EMERGENCY: snack levels critical!",
            "Feed me NOW. Please.",
            "My hunger has hunger.",
            "I would do anything for a cookie.",
            "Snack! Snack! Snack!",
            "The hungriest pet alive!"
        ),
        (Archetype.SOCIAL to PetRenderer.Mood.HAPPY) to listOf(
            "Happy with my favorite human!",
            "Let us celebrate together!",
            "Hugs! So many hugs!",
            "Best day with best friend!",
            "Joy is better shared!",
            "You + me = perfect day!"
        ),
        (Archetype.INDEPENDENT to PetRenderer.Mood.CONTENT) to listOf(
            "Content on my own terms.",
            "Alone and loving it.",
            "My own company is good company.",
            "Peaceful solitude.",
            "I am good right here.",
            "Independent and content."
        ),
        (Archetype.NEAT to PetRenderer.Mood.CONTENT) to listOf(
            "Clean, calm, content.",
            "Everything tidy, everything good.",
            "Groomed and grateful.",
            "My fur is flawless today.",
            "Neat and happy.",
            "Spotless and satisfied."
        ),
        (Archetype.MESSY to PetRenderer.Mood.HAPPY) to listOf(
            "Messy and loving it!",
            "Happy mess is the best mess!",
            "Mud + joy = perfect!",
            "I am a happy disaster!",
            "Grinning through the grime!",
            "Messy but magnificent!"
        )
    )

    /**
     * Effective pool for an (archetype, mood) pair: combo + mood + archetype,
     * deduped. Always >= 15 lines (mood 8 + archetype 8, plus 6 combo lines
     * where a combo pool exists).
     */
    fun poolFor(archetype: Archetype, mood: PetRenderer.Mood): List<String> {
        val pool = LinkedHashSet<String>()
        COMBO_LINES[archetype to mood]?.let { pool.addAll(it) }
        MOOD_LINES[mood]?.let { pool.addAll(it) }
        ARCHETYPE_LINES[archetype]?.let { pool.addAll(it) }
        return pool.toList()
    }

    /** A speaker holds the no-repeat window. One per pet view. */
    class Speaker(private val random: Random = Random.Default) {
        private val recent = ArrayDeque<String>()

        fun nextLine(archetype: Archetype, mood: PetRenderer.Mood): String {
            val pool = poolFor(archetype, mood)
            val fresh = pool.filter { it !in recent }
            val pick = (if (fresh.isNotEmpty()) fresh else pool).let { it[random.nextInt(it.size)] }
            recent.addLast(pick)
            while (recent.size > NO_REPEAT_WINDOW) recent.removeFirst()
            return pick
        }

        fun reset() { recent.clear() }
    }
}
