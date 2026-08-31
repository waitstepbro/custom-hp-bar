## Bugs

**1. ToA in a team.** Solo is settled - every minion and every boss with a HUD bar now measures
exactly where the formula puts it. A team never has been, so minions outside a solo deliberately
fall back to a percentage rather than a guessed number. Open:
  - **`toaPartySize()`'s varbit read**, never seen reporting a real team - every figure so far was
    read off the game rather than the plugin. It now decides whether a minion shows a number or a
    percent, so a misread costs numbers in a solo or invents them in a team, either way silently.

**2. Bar disappears with `hideNativeBar` on**
([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16)) - one symptom, a different
cause per boss, so a fix for one is not a fix for the others:
  - **Duke Sucellus's Fermentation Vat** - scenery, not an NPC; needs a live sprite-ID capture.
  - **Verzik Supporting Pillars** - real NPCs that can survive a whole fight without a hitsplat, so
    they never enter `trackedActors`. Also eyeball the widened "Only Show Combat NPC Names" gate
    shipped in `259d8bd` while here - the CoX confirmation did not cover Verzik.
  - **ToB Nylocas room "Support" pillars** - game objects, so the Verzik fix can't reach them, and
    it isn't confirmed they show the bug at all.

**3. Doom of Mokhaiotl's yellow charge bar disappears with the plugin on**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - almost certainly the same
`hideNativeBar` over-suppression as item 2.

## Features

- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
