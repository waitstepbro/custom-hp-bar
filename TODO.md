# TODO

## Bugs

**1. Exact ToA minion HP (raid level x path level x party size) is not implemented.**
`resolveNpcMaxHp()` returns `-1` for every non-boss ToA NPC on purpose. Verified formula and
region-to-path mapping are in `CLAUDE.md` ("ToA minion HP") - ready to implement whenever an exact
number (not percent) is wanted, but not a bug on its own.

**2. UNCONFIRMED - Other players' names can bunch up near a boss when several players and a large
NPC share a tile.** Was bundled with a self-anchor detachment/snap-onto-NPC symptom, which is now
fixed and confirmed live (2026-08-12: self is forced to always be its own tile's reference actor in
`resolveReferenceActors()`, so self's bar can no longer get computed from another actor's anchor -
root cause and full history in `CLAUDE.md`, "Fourth live test (2026-08-12, Blood Moon)..."). This
symptom is different - other players' names/bars bunching together, not self's - and isn't known to
be fixed by the same change; same `tileStacks` system, plausible but untested. The diagnostic
logging built for the self-bug investigation (`multi-actor stack formed`/`stack reference actor
reassigned` in `resolveReferenceActors()`) was removed once that bug was confirmed fixed - not
meant to ship long-term, per its own comments. Re-add equivalent instrumentation when this item is
picked up; needs live confirmation with multiple real players sharing a tile with a boss.

**3. TABLED - Duke Sucellus's Fermentation Vat "bar" disappears with `hideNativeBar` on.** It's
scenery, not an NPC - no Actor to track, so the no-attack-option NPC fix above doesn't apply.
Needs a live sprite-ID capture at the vat before any fix can be attempted. Full investigation in
`CLAUDE.md` ("TABLED: Duke Sucellus's Fermentation Vat...").

**4. UNRESOLVED - Some Verzik Supporting Pillars still show no bar with `hideNativeBar` on.** Same
visible symptom as item 3, different cause: a real, trackable NPC, not scenery - but one that never
takes a hitsplat before the room's phase-1-to-2 collapse never enters `trackedActors`, so nothing
draws it once its native sprite is already overridden. Needs a live test confirming the bar-less
pillars are specifically the ones that took zero hits that phase, before writing a fix. Do not
reuse a fix for item 3 here or vice versa. Full investigation in `CLAUDE.md` ("UNRESOLVED: some
Verzik Supporting Pillars...").

**5. UNCONFIRMED - ToB Nylocas room "Support" pillars may share item 3's shape, not item 4's.**
These are game objects, not NPCs, so no amount of fixing tracking/discovery logic (item 4's fix)
could ever reach them. Not yet confirmed broken at all - the open question is whether the room's
health indicator is a HUD widget (unaffected by `hideNativeBar`, no bug) or a genuine in-world
sprite (affected, same shape as item 3 but worse - no single Actor/object to track). Full
investigation in `CLAUDE.md` ("UNCONFIRMED: ToB's Nylocas room 'Support' pillars...").

## Ideas

Unscheduled, not commitments. Mostly unchecked against the real API.

**1. Damage-taken trail** - ghost segment lagging the fill after a hit. `drawBarShape()` already
layers segments for the food/prayer preview.

**2. Phase markers** - tick marks at boss thresholds (Vorkath 50%, Zulrah, Hydra). Best of these
ideas, but needs a per-NPC threshold table with the same maintenance problem as `npc_hp.csv`.

**3. Dim non-target bars** - cuts multi-combat clutter. Target from `localPlayer.getInteracting()`.

**4. Reduce shaking of the HP bar above NPCs** - bars jitter on large/animated models (fire giants
are the obvious case) because the anchor point moves with the model each frame. Look at smoothing
or snapping the canvas position rather than following the raw per-frame value.