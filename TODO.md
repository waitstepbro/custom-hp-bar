# TODO

## Bugs

**1. Exact ToA minion HP not implemented.** `resolveNpcMaxHp()` returns `-1` for every non-boss
ToA NPC on purpose - not a bug.

**2. Boss "bar disappears with `hideNativeBar` on"** - same symptom, different causes per boss, so
a fix for one sub-item shouldn't be assumed to cover the others.
  - **Duke Sucellus's Fermentation Vat** ([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16))
    - it's scenery, not an NPC, and needs a live sprite-ID capture before a fix is possible, with
    "Hide native health bar" as the workaround for now.
  - **Verzik Supporting Pillars** ([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16))
    - some are real NPCs that can survive the whole fight without a hitsplat before the
    phase-1-to-2 collapse, so they never enter `trackedActors` and show no bar.
  - **ToB Nylocas room "Support" pillars** - these are game objects, not NPCs, so the Verzik fix
    above can't reach them, and it's not yet confirmed whether they show the same bug.

**3. Doom of Mokhaiotl's yellow charge bar disappears with the plugin on**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - separate resource from HP,
apparently also getting overridden by `hideNativeBar`. Before/after screenshots posted on the issue
show the charge bar missing vs. visible - looks like the same hideNativeBar-suppression pattern as
the Vat/Pillars above, though that was never spelled out there.

**4. CoX mobs show no health bars except Olm's hands**
([issue #34](https://github.com/waitstepbro/custom-hp-bar/issues/34)) - reporter can't use the
plugin in raids as a result; cause not yet investigated. Asked the reporter for screenshots, still
waiting to hear back.

## Features

**1. Something to identify slayer task NPCs.**

**2. Player combat level shown, color-coded red/yellow/green by difference to own level**
([issue #35](https://github.com/waitstepbro/custom-hp-bar/issues/35)) - same idea as the existing
NPC combat-level display. Willing to build it, but the colors should be fixed rather than
configurable so it doesn't add to config bloat.

**3. Extra info in name display** ([issue #27] (https://github.com/waitstepbro/custom-hp-bar/issues/27)) - NPC level and max hit beside the name, weakness
icon (via surge-spell icons, nicer than NPC Level Overlay's rune icons) beside it, with a
configurable icon position.

**4. Per-element text color** ([issue #32](https://github.com/waitstepbro/custom-hp-bar/issues/32))
- HP, Prayer, HP number, and HP percentage colored independently, plus an option to hide other
players' HP bars (mirroring the existing NPC bar filter). A Prayer number color option is fine;
less sure about HP number/percentage, would rather not pile on config options; still need to
confirm with the requester whether "hide other players' bars" means a per-name blacklist.

**5. Player bar number and percentage shown together**, not mutually exclusive
([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - no problem adding this.

**6. Scale NPC bar length to the mob's tile size**
([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - fixed-length bars look off
on large mobs. Needs some investigation and testing before calling it doable.

## Feature Ideas

Unscheduled, not commitments. Mostly unchecked against the real API.

**1. Damage-taken trail** - ghost segment lagging the fill after a hit (`drawBarShape()` already
layers segments for the food/prayer preview).

**2. Phase markers** - tick marks at boss thresholds (Vorkath 50%, Zulrah, Hydra); needs a
per-NPC threshold table, same upkeep problem as `npc_hp.csv`.

**3. Dim non-target bars** - cut multi-combat clutter using `localPlayer.getInteracting()`.

**4. Reduce HP bar shaking above NPCs** - bars jitter on large/animated models (e.g. fire giants)
since the anchor moves with the model each frame; smooth or snap the canvas position instead.

## Functional Changes

**1. Add a 1-tick delay before overhead names are affected by player positioning.**

**2. Decouple HP bar and name from character animation** - related to the still-tabled
player-bar-bob investigation (player bars, self and other, still move slightly with attack
animations) and Ideas item 4 below (same shaking issue, NPC-scoped there).

