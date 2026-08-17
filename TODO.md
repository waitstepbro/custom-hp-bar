# TODO

## Bugs

**1. Exact ToA minion HP not implemented.** `resolveNpcMaxHp()` returns `-1` for every non-boss
ToA NPC on purpose - not a bug. Formula and mapping verified in `CLAUDE.md` ("ToA minion HP").

**2. FIX ATTEMPTED (2026-08-16), NOT YET TESTED LIVE - other players' names bunch when a shared
tile's reference player leaves and another joins the same tick**, snapping every other name/bar on
that tile onto the new player's anchor. Root cause confirmed via log + screenshot; fix blends the
tile's anchor across a reassignment instead of snapping it. Compiles and passes `checkstyleMain`
clean. `CLAUDE.md` ("FIX ATTEMPTED (2026-08-16): blend the reassignment snap instead of preventing
it (option a)"). Needs a live repro to confirm before considering this done.

**3. Boss "bar disappears with `hideNativeBar` on" - same symptom, different causes per boss. Do
not reuse a fix across sub-items.**
  - **Duke Sucellus's Fermentation Vat (TABLED)** - scenery, not an NPC. Needs a live sprite-ID
    capture before a fix is possible. `CLAUDE.md` ("TABLED: Duke Sucellus's Fermentation Vat").
    Owner responded on issue #16 (2026-08-17): aware of it, fix hopefully landing next release;
    interim workaround is disabling "Hide native health bar."
  - **Verzik Supporting Pillars (UNRESOLVED)** - real NPCs that never take a hitsplat before the
    room's phase-1-to-2 collapse, so they never enter `trackedActors`. Needs live confirmation
    before a fix. `CLAUDE.md` ("UNRESOLVED: some Verzik Supporting Pillars"). Owner gave the same
    response as the vat above on issue #16 - aware, fix hopefully next release, same interim
    workaround. A commenter also noted the wiki lists Supporting Pillar as both scenery and NPC,
    which may explain why only *some* pillars are affected.
  - **ToB Nylocas room "Support" pillars (UNCONFIRMED)** - game objects, not NPCs, so the Verzik
    fix can't reach them; likely vat-shaped instead. Not yet confirmed broken. `CLAUDE.md`
    ("UNCONFIRMED: ToB's Nylocas room 'Support' pillars").

**4. Doom of Mokhaiotl's yellow charge bar disappears with the plugin on** (issue #31) - separate
resource from HP, apparently also getting overridden by `hideNativeBar`. Owner posted before/after
screenshots on the issue (2026-08-17) showing the charge bar missing vs. visible - consistent with
the same hideNativeBar-suppression pattern confirmed for the Vat/Pillars on issue #16, though not
stated outright there.

**5. CoX mobs show no health bars except Olm's hands** (issue #34) - reporter can't use the plugin
in raids as a result; cause not yet investigated. Owner asked the reporter for screenshots
(2026-08-17); awaiting reply.

## Features

**1. Something to identify slayer task NPCs.**

**2. Player combat level shown, color-coded red/yellow/green by difference to own level**
(issue #35) - same idea as the existing NPC combat-level display. Owner confirmed on the issue
(2026-08-17): will implement, but the color scheme will be fixed, not user-configurable (avoiding
config option bloat).

**3. Extra info in name display** (issue #27) - NPC level and max hit beside the name, weakness
icon (via surge-spell icons, nicer than NPC Level Overlay's rune icons) beside it, with a
configurable icon position.

**4. Per-element text color** (issue #32) - HP, Prayer, HP number, and HP percentage colored
independently, plus an option to hide other players' HP bars (mirroring the existing NPC bar
filter). Owner responded on the issue (2026-08-17): Prayer color is a yes; HP-number/percentage
color he'll consider but isn't committing to (avoiding option bloat); asked the requester whether
"hide other players' bars" means a per-name blacklist - awaiting clarification.

**5. Player bar number and percentage shown together**, not mutually exclusive (issue #33). Owner
confirmed on the issue (2026-08-17): no issues implementing this.

**6. Scale NPC bar length to the mob's tile size** (issue #33) - fixed-length bars look off on
large mobs. Owner said on the issue (2026-08-17) this needs investigation/testing before
confirming it's feasible.

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
player-bar-bob investigation in `CLAUDE.md` ("TABLED: player bars (self and other) still move
slightly with attack animations") and Ideas item 4 below (same shaking issue, NPC-scoped there).

