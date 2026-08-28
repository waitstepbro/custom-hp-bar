## Bugs

**1. ToA minion HP.** Built and confirmed at two raid levels solo; four measurements still open - a
team raid (the party-size term has never executed, and where it applies is the open question), the
Wardens, the Egg (11728/11729), and the Agile Scarab (11727). See "ToA: what is left to test".

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

Researched in full, nothing built - see "Feature backlog research pass".

- **Slayer task NPC identification.**
- **Player combat level, color-coded by difference to own level**
  ([issue #35](https://github.com/waitstepbro/custom-hp-bar/issues/35)) - fixed colors, not configurable.
- **Extra info in the name display**
  ([issue #27](https://github.com/waitstepbro/custom-hp-bar/issues/27)) - level, max hit, weakness icon.
- **Per-element text color, and hiding other players' bars**
  ([issue #32](https://github.com/waitstepbro/custom-hp-bar/issues/32)) - Prayer color and the
  blacklist are the cheap pair; HP number vs percentage coloring is the costly one.
- **Player bar number and percentage together**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - self already does this;
  confirm with the requester whether they meant other players.
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

Unscheduled, not commitments.

- **Damage-taken trail** - ghost segment lagging the fill after a hit.
- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
