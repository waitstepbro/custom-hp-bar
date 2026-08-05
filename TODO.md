# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. Exact ToA minion HP (raid level x path level x party size) is not implemented.**
`resolveNpcMaxHp()` returns `-1` for every non-boss ToA NPC on purpose. Verified formula and
region-to-path mapping are in `CLAUDE.md` ("ToA minion HP") - ready to implement whenever an exact
number (not percent) is wanted, but not a bug on its own.

**3. TABLED - Duke Sucellus's Fermentation Vat "bar" disappears with `hideNativeBar` on.** It's
scenery, not an NPC - no Actor to track, so the no-attack-option NPC fix above doesn't apply.
Needs a live sprite-ID capture at the vat before any fix can be attempted. Full investigation in
`CLAUDE.md` ("TABLED: Duke Sucellus's Fermentation Vat...").

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
