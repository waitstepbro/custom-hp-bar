# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. Exact ToA minion HP (raid level x path level x party size) is not implemented.**
`resolveNpcMaxHp()` returns `-1` for every non-boss ToA NPC on purpose. Verified formula and
region-to-path mapping are in `CLAUDE.md` ("ToA minion HP") - ready to implement whenever an exact
number (not percent) is wanted, but not a bug on its own.

**3. TABLED - Duke Sucellus's Fermentation Vat "bar" disappears with `hideNativeBar` on** - not the
same mechanism as the (fixed) no-attack-option NPC bug. The vat (wiki: `Fermentation_Vat`) is
**scenery, not an NPC** - object IDs `47536`/`47537` (empty/brewing) plus per-poison-combo states
`47538`-`47543`, no NPC ID, no Attack option, no hitpoints anywhere on the wiki. So this can't be
a tracking-discovery bug - there's no Actor to track. User confirmed in-game it's a fill/progress
indicator, not real HP, and confirmed toggling `hideNativeBar` off makes it reappear. Leading
theory: it reuses one of the generic `Standard*` sprite IDs already in `NativeHealthBarSprites.ALL`
(a small reused texture set, not unique per-boss), so the client-wide sprite-transparent override
catches it as collateral damage - `Client.getSpriteOverrides()` has no per-actor/per-object
scoping, so there may be no way to hide combat bars but keep this one without knowing whether that
exact ID is safe to exclude (unshared with any real boss bar) or not (shared, so excluding it would
also un-hide a real native bar somewhere). Checked `SpriteID.java` and `ObjectID.java` from the
cached `runelite-api-1.12.32-sources.jar` for anything vat/ferment/duke-named - nothing; the object
IDs aren't even mapped to friendly names in this runelite-api version, so this is a dead end for
static analysis. **Needs a live sprite-ID capture at the vat** (RuneLite Developer Tools' widget
inspector, or a one-off debug build that tints `NativeHealthBarSprites.ALL` instead of hiding it)
before any fix can be attempted - tabled until that's done.

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
