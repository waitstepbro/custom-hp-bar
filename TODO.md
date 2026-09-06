## Bugs

**1. ToA rooms with no HP rows.** The Wardens' statue forms, Zebak's Tail, Osmumten and the puzzle
scenery (Boulder, Rubble, Jug, Wave, Blood Cloud, the orbs) have no `npc_hp.csv` rows, so they draw
a percentage. The HUD covers the Wardens and the Palms; the rest would need rows.

**2. Bar disappears with `hideNativeBar` on**
([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16)) - one symptom, a different
cause per boss. Narrowing the override to health sprites may have settled the scenery ones already;
none rechecked since:
  - **Duke Sucellus's Fermentation Vat** - scenery, not an NPC. If still blank, capture the front
    sprite ID `PostHealthBarConfig` reports.
  - **Verzik Supporting Pillars** - NPCs that can survive a whole fight without a hitsplat, so they
    never enter `trackedActors`. Eyeball `259d8bd`'s widened combat-name gate while here; the CoX
    confirmation didn't cover Verzik.
  - **ToB Nylocas "Support" pillars** - game objects, out of reach of the Verzik fix, and not
    confirmed broken at all.

**3. Yama's void flares show a full bar until hit**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - they spawn at part health,
but "Always Show NPC Bar" has no read before the first hitsplat, so the pass draws `{1, 1}`. Doom's
Demonic larva too. Any fix has to stay narrower than "draw nothing until a real read", which would
also strip the full bar every never-hit NPC deliberately gets.

## Features

- **Draw our own shield/charge bar** instead of leaving Doom's and Yama's to the native UI.
- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
