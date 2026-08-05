# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. Exact ToA minion HP (raid level x path level x party size) is not implemented.**
`resolveNpcMaxHp()` returns `-1` for every non-boss ToA NPC on purpose. Verified formula and
region-to-path mapping are in `CLAUDE.md` ("ToA minion HP") - ready to implement whenever an exact
number (not percent) is wanted, but not a bug on its own.

**3. FIXED (confirmed in-game): no-attack-option destructible NPCs never got a bar with
"hide native health bar" enabled** - reported on ToB Verzik's Supporting Pillars, confirmed the
same mechanism also affects The Whisperer's Floating Columns. Root cause and fix are in
`CLAUDE.md` ("no-attack-option NPCs never get tracked").

**4. TABLED - Duke Sucellus's Fermentation Vat "bar" disappears with `hideNativeBar` on, but
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

**5. BUILT (confirmed in-game): opacity slider for health bars** - a transparency config
option, per-profile (target/player) like the rest of the appearance settings. Details in
`CLAUDE.md` ("Bar Opacity").

**6. BUILT (confirmed in-game): run energy bar**, joining the player's HP/Prayer/Special stack
rather than the standalone vertical sidebar first floated here - a restore preview since the API
actually supports one for this stat (covers Stamina potion too), a second bar color that swaps in
while a Stamina potion's drain-reduction effect is active mirroring the native run orb, and a
timeout so it doesn't sit on screen indefinitely while idle. Details in `CLAUDE.md` ("Run Energy
bar" and "Run Energy Bar Timeout").

**FIXED (confirmed in-game): a lone bar in the player's stack now skips `verticalOffset`
entirely, snapping to the normal offset once a second bar joins it.** Turned out not to be a bug -
the user clarified this is a deliberate change from the original "stack top never moves" design,
specifically for this case. Details in `CLAUDE.md` ("Solo player bar offset").

**FIXED (confirmed in-game): run energy bar's post-combat lingering** - the earlier "no change in
behavior" report turned out to be a stale build (`runPlugin` doesn't hot-reload), not a logic bug;
confirmed working after a full restart. Details in `CLAUDE.md` ("FIXED: Run Energy bar post-combat
visibility").

**FIXED (confirmed in-game): run energy bar stayed visible indefinitely after zoning into an
instance (reported on the ToA lobby)** - `client.getTickCount()` is the *server's* tick count per
its own javadoc, and a raid instance is a separate server-side instance with no guaranteed
tick-count continuity with the overworld. Switched to wall-clock (`System.currentTimeMillis()`)
timing. Confirmed clearing normally after zoning in/out of the ToA lobby. Details in `CLAUDE.md`
("FIXED: Run Energy bar timing used server tick-count deltas, which broke across instance
boundaries") - also flags the pre-existing HP/NPC persist-duration logic as exposed to the same
class of risk, not yet investigated since it hasn't been reported broken.

**FIXED (confirmed in-game): run energy bar timeout only ignored passive regen within the
post-combat window, not generally** - closing a ~10s (vs. the configured timeout) gap noticed
right after the fix above. Simplified to one drain-only rule everywhere, removing the
post-combat-specific carve-out entirely (now redundant with the stricter general rule). Details in
`CLAUDE.md` ("Run Energy bar timeout: drain-only, one rule everywhere").

**FIXED (needs in-game re-test): the drain-only timeout above could make the run bar blink out
mid-fight** while HP/Prayer/Special stayed visible (e.g. tanking a stationary boss, never
running). User-specified priority system: while tracked (in combat, or within the HP bar's own
persist-duration window), Run just follows the HP bar's lifecycle - no independent timer; once
untracked, the drain-based timeout governs it as before. Details in `CLAUDE.md` ("Run Energy bar
visibility: HP lifecycle in combat, drain timeout out of combat").

**BUILT (confirmed in-game): "Hide While Full" removed entirely for both Special Attack and
Run Energy bars**, at the user's request - not defaulted off, the config items themselves are
gone. Details in `CLAUDE.md` ("Removed: 'Hide While Full' for both Special Attack and Run Energy
bars").

**7. FIXED (confirmed in-game): the player's own health bar could render underneath an NPC's
bar** - a z-order/priority fix so it doesn't get visually buried when standing near/inside other
actors' bars. Root cause and fix are in `CLAUDE.md` ("local player's own bar could render
underneath an NPC's bar").
