# PixelPet — Flow Charts (v2)

> GitHub renders Mermaid natively. Each diagram matches the v2 spec in
> [PLAN.md](PLAN.md). The animation bible lives in [ART.md](ART.md).

## (a) Pet lifecycle state machine

```mermaid
stateDiagram-v2
    [*] --> SELECT: first launch
    SELECT --> ACTIVE: choose species
    SELECT --> ACTIVE: premium key unlocks Mystical
    ACTIVE --> SLEEP: screen OFF (AOD active)
    ACTIVE --> SICK: hunger = 0 for 6h
    ACTIVE --> ATTENTION_NEEDED: attention < 25
    ATTENTION_NEEDED --> ACTIVE: petted / played
    ATTENTION_NEEDED --> ACTIVE: attention recovers ≥ 25
    SLEEP --> WAKE: screen ON
    WAKE --> ACTIVE: wake animation + recap
    SICK --> ACTIVE: fed (hunger > 0)
    SICK --> SLEEP: screen OFF
    ACTIVE --> EVOLVING: species unlocked
    EVOLVING --> ACTIVE: celebration done
    ACTIVE --> [*]: (no permadeath in v2)
```

## (b) Care loop — feed / play / attention

```mermaid
flowchart TD
    A[User opens app / taps widget / touches wallpaper] --> B{Care action?}
    B -->|Complete goal| C[Feed: hunger +20, happiness +15, energy +5]
    B -->|Snack / quick-feed| D[Feed: hunger +10 — 15 min cooldown]
    B -->|Play button / double-tap| E[Play: energy −10, happiness +20, attention +10 — 5 min cooldown]
    B -->|Tap-drag pet| F[Pet: attention +25, happiness +8]
    B -->|Log doomscroll| G[Sulk: happiness −15, energy −5, sulk 30 min]
    C --> H[Confetti + arpeggio SFX + jump VFX]
    D --> I[Munch SFX + hearts]
    E --> J[Giggle SFX + spin VFX]
    F --> K[Purr SFX + hearts + haptic tick]
    G --> L[Slide-whistle SFX + shake + rain cloud]
    H --> M[UnlockManager: streaks & achievements?]
    I --> M
    J --> M
    K --> M
    M -->|requirement met| N[Evolve celebration: unlock species]
    M -->|nothing| O[Widget + wallpaper refresh]
    N --> O
```

## (c) Sleep / wake cycle (screen on/off)

```mermaid
flowchart TD
    subgraph Device
        OFF[ACTION_SCREEN_OFF<br/>screen off / AOD takes over]
        ON[ACTION_SCREEN_ON<br/>user wakes phone]
    end
    OFF --> S1[PetRepository.enterSleep]
    S1 --> S2[decay multiplier → 0.1×]
    S2 --> S3[record sleepStart timestamp]
    S3 --> S4[Wallpaper engine stops draw loop]
    S4 --> S5[Widget renders SLEEPING face]
    ON --> W1[PetRepository.wake]
    W1 --> W2[Compute away-delta at 0.1× decay]
    W2 --> W3[Wake animation + shimmer SFX]
    W3 --> W4["Recap line:<br/>'While you were away… hunger −2.<br/>Pip missed you!'"]
    W4 --> W5{attention < 25?}
    W5 -->|yes| W6[Attention nudge]
    W5 -->|no| W7[Resume ACTIVE]
    W6 --> W7

    note1["Honest limits:<br/>• No drawing on AOD (system)<br/>• Receivers registered dynamically<br/>(manifest registration does not work)<br/>• Tracking lives while wallpaper<br/>engine or app process is alive"]
```

## (d) Unlock progression (Normal → Wild → Mystical)

```mermaid
flowchart LR
    subgraph Normal [Normal — from the start]
        N1[Cat] --- N2[Dog] --- N3[Bunny] --- N4[Hamster]
    end
    subgraph Wild [Wild — care streaks]
        W1[Wolf<br/>7-day streak] --- W2[Fox<br/>14-day streak]
        W2 --- W3[Tiger<br/>21-day streak] --- W4[Owl<br/>30-day streak]
    end
    subgraph Mystical [Mystical — achievements OR premium]
        M1[Dragon<br/>60-day streak<br/>or 👑]
        M2[Phoenix<br/>100 lifetime goals<br/>or 👑]
        M3[Unicorn<br/>90-day streak<br/>or 👑]
        M4[Kraken<br/>👑 only]
    end
    Normal -->|consistent care| Wild
    Wild -->|long-term play| Mystical
    PREM((👑 Premium key)) -.->|instant| Wild
    PREM -.->|instant| Mystical

    style PREM fill:#FFD54A,stroke:#333,color:#000
```

Care-day rule: **≥1 goal completed in a calendar day** extends the streak.
Streaks use wall-clock dates (a human concept); stat decay uses elapsedRealtime
(a physics concept). The split is deliberate and documented.

## (e) Premium purchase → key activation → entitlement check

```mermaid
flowchart TD
    P1[User buys on Gumroad<br/>price: TODO — user's call] --> P2[Seller manually delivers key]
    P2 --> P3["Key format:<br/>VNPP-XXXXXX-XXXXXX-XXXXXX<br/>(Crockford base32)"]
    P3 --> P4[User enters key in Premium screen]
    P4 --> P5{Rate limit:<br/>5 tries → 1h lockout}
    P5 -->|locked| P6[Show cooldown]
    P5 -->|ok| P7["Local validation:<br/>split payload → HMAC-SHA256<br/>(id, embedded secret)<br/>constant-time compare"]
    P7 -->|invalid| P8[Error SFX + shake<br/>increment attempts]
    P7 -->|valid| P9[Store key id + activated flag<br/>never store the key itself]
    P9 --> P10[Evolve celebration<br/>Wild + Mystical unlocked<br/>exclusive skins & aura VFX]
    P10 --> P11[Every premium-gated access<br/>re-checks entitlement flag]

    note2["Honest note: local HMAC validation is<br/>obfuscation-tier, not DRM.<br/>Right-sized for indie Gumroad sales.<br/>Play Billing = documented future."]
```

## (f) Sync — wallpaper, widget, app (single source of truth)

```mermaid
flowchart TD
    subgraph Truth [Single source of truth]
        REPO[(PetRepository<br/>SharedPreferences<br/>single-thread executor)]
    end

    APP[MainActivity / Species / Premium<br/>reads snapshot → renders<br/>actions → repo mutations]
    WP[PixelWallpaperService.Engine<br/>reads snapshot @ ~30fps<br/>touch → repo.pet / repo.play]
    WDG[WidgetProvider + UpdateJob<br/>reads snapshot → face bitmap<br/>feed button → repo.snack]
    RCV[ScreenReceiver<br/>SCREEN_OFF/ON →<br/>repo.enterSleep / wake]

    APP <--> REPO
    WP --> REPO
    WDG --> REPO
    RCV --> REPO

    REPO -->|broadcast ACTION_REFRESH_WIDGET| WDG
    REPO -.->|snapshot| APP
    REPO -.->|snapshot| WP

    note3["Rule: nobody keeps stat copies.<br/>Wallpaper, widget, app all re-read.<br/>Mutations only through the repo."]
```

---

*Diagrams: (a) lifecycle · (b) care loop · (c) sleep/wake · (d) unlocks ·
(e) premium · (f) sync. Spec: [PLAN.md](PLAN.md). Animation bible: [ART.md](ART.md).*
