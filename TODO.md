## Bugs

**1. Bar disappears with `hideNativeBar` on**
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

## Features
- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
- **Slayer task NPC identification.**

## Functional Changes

- **Move the NPC name and bar up when NPC chat appears**, so overhead text doesn't sit on top of
  them.

## Backlog

- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.