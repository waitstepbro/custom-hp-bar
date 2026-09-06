## Bugs

**1. ToA rows that are missing or wrong.** Scaling is settled - a 3-man at three raid levels
confirmed minions take the party term - so what is left is the rows themselves:
  - **Baboon Thrall `11718`** reads high - measured 8/8/9 at raid 170/185/200 in a 3-man against
    9/11/11 predicted, and no integer base row fits the formula in either rounding direction.

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

## Features

## Functional Changes

- **Move the NPC name and bar up when NPC chat appears**, so overhead text doesn't sit on top of
  them.

## Backlog

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.