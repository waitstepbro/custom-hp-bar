# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. FIXED, confirmed in-game: ToA minions (baboons, scarabs, Akkha's Shadows) showed a real
number instead of percent.** Not the raid-level/path-level/party-size formula below - that's still
unimplemented and intentionally so. The `nativeHudHp()`/`TOA_BOSS_NPC_IDS` code that was supposed
to fix this was already correct, but sat dead the whole time behind the `isInsideToa()` bug fixed
in 2c below - once that was fixed, this started working with no further changes. Full reasoning in
`CLAUDE.md` ("FIXED: some ToA minions still showed a number").

**2b. Exact ToA minion HP (raid level x path level x party size) is not implemented.**
`resolveNpcMaxHp()` returns `-1` for every non-boss ToA NPC on purpose. Verified formula and
region-to-path mapping are in `CLAUDE.md` ("ToA minion HP") - ready to implement whenever an exact
number (not percent) is wanted, but not a bug on its own.

**2c. FIXED, confirmed in-game: grey bars appearing on ToA minions (reported: volatile baboon
explosions in the Path of Apmeken challenge room; also reported at Wardens P3 skulls).** Root cause
was **not** loot-taint logic malfunctioning - it was `isInsideToa()`/`isCommunalLootEncounter()`
reading the player's region via `Actor.getWorldLocation().getRegionID()` directly, which inside any
instanced content returns a raw instance-chunk number (observed: `43521`, `53505`, `54273` in the
same raid) instead of the real static region ID (`14160`, `15186`, etc.) - so it never matched
`TOA_REGION_IDS`/`TOB_REGION_IDS` and the entire "you're in a communal-loot raid, never grey this
out" exemption was silently dead code in every real raid, ToA and ToB alike. Also explains 2 above
for the same reason. Fix, full debugging trail, and why CoX was never affected (it checks a varbit,
not a region) are in `CLAUDE.md` ("FIXED: grey bars on ToA/ToB bosses").

**3. FIXED (needs in-game re-test): no-attack-option destructible NPCs never got a bar with
"hide native health bar" enabled** - reported on ToB Verzik's Supporting Pillars, confirmed the
same mechanism also affects The Whisperer's Floating Columns. Root cause and fix are in
`CLAUDE.md` ("no-attack-option NPCs never get tracked").

**3b. TABLED - Duke Sucellus's Fermentation Vat "bar" disappears with `hideNativeBar` on, but
this is a different bug from 3, not the same one.** The vat (wiki: `Fermentation_Vat`) is
**scenery, not an NPC** - object IDs `47536`/`47537` (empty/brewing) plus per-poison-combo states
`47538`-`47543`, no NPC ID, no Attack option, no hitpoints anywhere on the wiki. So this can't be
a tracking-discovery bug (fix 3 doesn't apply - there's no Actor to track). User confirmed in-game
it's a fill/progress indicator, not real HP, and confirmed toggling `hideNativeBar` off makes it
reappear. Leading theory: it reuses one of the generic `Standard*` sprite IDs already in
`NativeHealthBarSprites.ALL` (a small reused texture set, not unique per-boss), so the client-wide
sprite-transparent override catches it as collateral damage - `Client.getSpriteOverrides()` has no
per-actor/per-object scoping, so there may be no way to hide combat bars but keep this one without
knowing whether that exact ID is safe to exclude (unshared with any real boss bar) or not (shared,
so excluding it would also un-hide a real native bar somewhere). Checked `SpriteID.java` and
`ObjectID.java` from the cached `runelite-api-1.12.32-sources.jar` for anything vat/ferment/duke-
named - nothing; the object IDs aren't even mapped to friendly names in this runelite-api version,
so this is a dead end for static analysis. **Needs a live sprite-ID capture at the vat** (RuneLite
Developer Tools' widget inspector, or a one-off debug build that tints `NativeHealthBarSprites.ALL`
instead of hiding it) before any fix can be attempted - tabled until that's done.

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
