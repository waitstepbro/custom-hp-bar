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

**5. Other player names/bars snap to the stack a tick before the model arrives, and stacked names
sit in large gaps.** Two symptoms of the same design: stacking is keyed on `getWorldLocation()`,
which snaps to the destination tile while `getLocalLocation()` is still interpolating, and
`claimStackSlot()` only ever pushed an entry up, never pulled it down. Players on one `WorldPoint`
can sit tens of pixels apart on screen, so one whose own anchor already floats above the stack -
taller model, mid-animation, mounted - was left there rather than pulled down to `STACK_PADDING`.
Live screenshots show an oversized gap under the topmost name only, the names below it tight; the
snap was reported separately, live. Only the gap half is fixed (below) - the snap is still open.

**Tried and reverted (2026-08-20): dropping the shift for players entirely.** Players reserved their
tile slot but never received one, so every player entry rendered at its own model anchor - self's
existing treatment, generalized. That does fix the snap, and it is the wrong trade: live testing
showed other players' names simply overlapping each other again, the exact case `6d88898` fixed.
Overlap avoidance has to survive whatever fixes the snap, so a future attempt has to keep stacking
and change what it is keyed on or how it is timed, not remove it.

**The gap half: FIXED, NOT YET CONFIRMED LIVE.** Reported live that the gap appears when a player
*leaves* the stack, which pins the mechanism: `claimStackSlot()` only ever pushed up
(`Math.max(0, ...)`). While the departing entry was there it raised the tile's claimed top enough to
push everyone above it; once it goes, whoever is left with a naturally higher anchor - taller model,
mid-interpolation - clears the remaining claim on its own, takes shift 0, and stays wherever its own
anchor puts it. Nothing ever pulled it back down, so the gap it had been hiding behind the departed
entry becomes visible and stays.

The clamp is now `Math.max(-maxPull, ...)`, with `stackPullLimit()` bounding the pull to that actor's
own anchor-to-feet distance (`actorAnchor(actor, 0)`), so a row can be pulled level with its tile's
stack but never below its owner's own tile. It reads only that actor's own position, so it is not the
shared reference anchor behind "REGRESSION #4". Needs a live crowd - players joining and leaving a
shared tile, mixed model heights - and a re-check that names still sit above their owner's head when
the pull binds. The snap half above is untouched by this. The overlay has no debug logging right now.

**Bar-less other players on self's own tile overlapped each other: FIXED, NOT YET CONFIRMED LIVE.**
Reported live with a screenshot of three names drawn on top of each other while self shared their
tile. `nameOnlySharingSelfTile` kept a bar-less other player on self's tile out of the same-tile
stack entirely, so none of them reserved room against anything - self was stacked around, but they
were not stacked against each other. That exemption existed because self's claim included a fat
unconditional icon row (see below) that would have thrown a stacked name well above its owner's head;
with the reservation now exact and the pull-down in place, a claimed slot lands right where the name
belongs, so the exemption is deleted and they stack like any other tile. Live check: self plus two or
more bar-less players on one tile, then the same with a bar showing.

**Self's 27px over-reserve: FIXED, NOT YET CONFIRMED LIVE.** `reserveSelfStackHeight()` always
reserved `STACK_ICON_CLEARANCE + OVERHEAD_ICON_GAP` (27px) for an overhead icon row self may not
have, while every other player goes through `overheadRowClearance()`, which returns 0 with no skull
and no prayer icon - so anything stacking on your own tile sat 27px too high. Self now uses
`overheadRowClearance()` too. Never caused the other-player gaps above, but the pull-down fix would
have turned it into a constant gap above your own head. Check it live with and without a skull/
overhead prayer active, since that is exactly what the reservation now varies on.

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

