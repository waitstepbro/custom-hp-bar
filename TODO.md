# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. ToA minions show percent, not a number.** `resolveNpcMaxHp()` returns `-1` because HP scales by
raid level, path level *and* party size. Verified formula and region-to-path mapping are in
`CLAUDE.md` ("ToA minion HP") - just needs implementing.

**3. Precise NPC HP pins at 0 while the NPC is still alive.** Reported on Corrupted Gauntlet
demi-bosses (bar reads `0`, boss alive at 1-3 HP); the Hunllef is worse, up to 34 HP. Fully
diagnosed with a repro simulation and the intended fix written up in `CLAUDE.md` ("OPEN BUG:
precise NPC HP pins at 0"). Two causes: `updatePreciseHp()` uses `round(ratio/scale*maxHp)`, which
is the wrong inverse of the server's `1 + (scale-1)*hp/maxHp`, and the drift check's
`> maxHp/scale` dead zone is exactly as wide as that estimator's own error, so the drift never
self-corrects. Fix is to clamp into core's bounded `[minHealth, maxHealth]` interval instead.
Read the CLAUDE.md section first - it flags an `onHitsplatApplied` call-order question that must
be confirmed in-game rather than flipped blind. **Not a data problem, `npc_hp.csv` is correct.**

**4. Players who hit a 0 on an NPC don't trigger the grey bar/name detection.** A 0 from another
player is a `BLOCK_OTHER` (13) hitsplat, not `DAMAGE_OTHER` - and `OTHER_PLAYER_DAMAGE_HITSPLATS`
in `CustomHpBarPlugin.java` lists only the six `DAMAGE_OTHER*` types, so `otherPlayerDamaged`
never gets the NPC. Note `DAMAGE_HITSPLATS` (the HP-accumulation allowlist) *does* already include
`BLOCK_ME`/`BLOCK_OTHER`, so the two sets disagree on what counts as another player's hit. Lead
only, not verified in-game.

**5. Duke health bars on barrels not showing with "hide native health bar" enabled.** Our custom
bar doesn't draw for the barrels in the Duke Sucellus fight, so with `hideNativeBar` on there's no
overhead HP indicator left at all - the exact failure mode called out in `CLAUDE.md`'s
`NativeHealthBarSprites` notes. Check whether the barrels pass `isTrackedType()`/`isTrackable()`
(they may have no attack option and never report a `getHealthRatio()`), and whether the sprite
swap is hitting a bar we then fail to replace. Not investigated yet.

## Ideas

Unscheduled, not commitments. Mostly unchecked against the real API.

**1. Damage-taken trail** - ghost segment lagging the fill after a hit. `drawBarShape()` already
layers segments for the food/prayer preview.

**2. Phase markers** - tick marks at boss thresholds (Vorkath 50%, Zulrah, Hydra). Best of these
ideas, but needs a per-NPC threshold table with the same maintenance problem as `npc_hp.csv`.

**3. Dim non-target bars** - cuts multi-combat clutter. Target from `localPlayer.getInteracting()`.

**4. Player name label** - players have no name label at all, so this needs a new draw path plus
stack-height reservation. Would carry the combat level the same way NPC names now do
(`showNpcCombatLevel`). The NPC half of this item is done.

**5. Reduce shaking of the HP bar above NPCs** - bars jitter on large/animated models (fire giants
are the obvious case) because the anchor point moves with the model each frame. Look at smoothing
or snapping the canvas position rather than following the raw per-frame value.
