# TODO

## Bugs

**1. ToA minion HP: BUILT, MOSTLY CONFIRMED LIVE.** `resolveNpcMaxHp()` scales `npc_hp.csv`'s base
HP by raid level, path level and party size. Three solo raids on 2026-08-21 (raid 300, then two at
raid 230, the last a full clear including the Path of Het and the Wardens) tallied 342 kills through
`logToaDeathTally()`, which sums every damage hitsplat on a ToA NPC and dumps the total on the
killing blow.

Confirmed live, at two raid levels and both path levels: the raid-level term, the path-level term on
minions, both rounding bands (Ba-Ba 836 -> 840 at the 10s, Akkha's Shadow 144 -> 145 at the 5s),
`HET_SEAL_SOLO_HP` (measured exactly 119), and every static exemption that has had a kill logged -
the Apmeken wave-room baboons, Ba-Ba's own Baboon, both Scarabs and the Crocodile. The Baboon Thrall
un-exemption checks out too: predicted 3 at raid 230, measured 3 across 14 kills. The scenery NPCs
(Boulder, Rubble, Jug) are measured but deliberately rowless, so they show a percentage rather than a
number.

Still open:
- **A team raid.** All four raids were solo, so the party-size term has still never executed. The
  coefficient is not in doubt - the wiki states it verbatim, "the 2nd-3rd member will add 90% of the
  base health of bosses each, while the 4th and beyond will add 60% each", which is exactly our
  `9 * min(n-1, 2) + 6 * max(n-3, 0)` tenths. What a team settles is *where it applies*: the wiki
  reads as additive against BASE, while our code compounds it onto the already raid- and path-scaled
  total. At raid 200 / path 1 / 2-man on Ba-Ba those differ by ~30% (1400 vs 1080).
- **Tumeken's Warden.** The damage tally can't measure a multi-phase boss - Akkha's restarts at every
  elemental form, the Warden's carries across its phase change and logged 2028 against a predicted
  1690 - so the boss HP HUD is the measurement instead: `VarbitID.HPBAR_HUD_BASEHP` is the server's
  own scaled max for whatever the bar shows. `logToaBossHud()` records it. Every other boss is now
  confirmed that way (Akkha 830, Kephri 310 and 165, Zebak 1200, Ba-Ba 790, Het's Seal 119, all
  exactly as predicted at raid 230 / path 1); the Wardens just haven't been reached since the logging
  went in.
- **Egg (11728/11729) and Obelisk (11751)** remain exempt on assumption alone - still no kill logged.
  The Obelisk no longer depends on that guess for what it *displays*, though: its three ids are now in
  `TOA_BOSS_NPC_IDS`, so `nativeHudHp()` shows the boss HUD's own current/max while the bar is up, and
  the table row is only the fallback for when it isn't (a player can turn the boss HUD off in the
  game's settings). `logToaBossHud()` still records the figure, which is what says whether the 260 row
  and its exemption are right: at raid 230, 260 confirms both and ~499 says it scales.
- **Agile Scarab (11727)** is the one Kephri-room scarab with no measurement: it is currently scaled
  (predicted 61 at raid 230 / path 1) while its neighbour 11723 turned out static.

One tally caveat: a single Soldier Scarab kill once totalled 96 against 95 on the others, so the
tally can over-count by a splat. Take the repeated value as truth, not the maximum.

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

**4. CoX mobs show no health bars except Olm's hands: BUILT, NOT YET TESTED LIVE.**
([issue #34](https://github.com/waitstepbro/custom-hp-bar/issues/34)) - CoX's scaled trash has no combat
level, and "Only Show Combat NPC Names" gates on level, so everything but Olm's levelled head/claws lost
its bar. The gate is now widening-only (`level <= 0 && !isAttackableNpc(npc)`), so CoX trash passes on its
Attack option and nothing that passed before is excluded. Needs a real raid with the toggle on, plus a
Verzik check that the pillar path is untouched - an earlier, stricter version of this fix reopened that bug.

**5. Gemstone Crab greyed out and showed a number: BUILT, NOT YET TESTED LIVE.** It is a public
group encounter, so it is now exempt from `greyOutOtherPlayerDamage` via `isCommunalLootEncounter()`,
and `isPercentOnlyNpc()` withholds its max HP so the label is the boss HUD's percentage rather than a
raw number. Needs a live visit to confirm the HUD name matches "Gemstone Crab" (`nativeHudHp()` matches
by name) and that the bar still behaves with the game's boss HUD turned off.

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

**1. Decouple HP bar and name from character animation** - related to the still-tabled
player-bar-bob investigation (player bars, self and other, still move slightly with attack
animations) and Ideas item 4 below (same shaking issue, NPC-scoped there).

