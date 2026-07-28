# TODO

## Bugs

**1. Aggression timer colors all hostile NPCs yellow on expiry**, not just ones in the current
vicinity. Compare `updateAggressionArea()`/`isNpcAggressive()` (`CustomHpBarPlugin.java`) against
core's `npcunaggroarea` plugin.

**2. ToA minions show percent, not a number.** `resolveNpcMaxHp()` returns `-1` because HP scales by
raid level, path level *and* party size. Verified formula and region-to-path mapping are in
`CLAUDE.md` ("ToA minion HP") - just needs implementing.

## Ideas

Unscheduled, not commitments. Mostly unchecked against the real API.

**3. Damage-dealt tally / fight DPS** - `preciseNpcHp` already has the numbers; decide display
(bar label vs infobox) and whether to count only your damage (`DAMAGE_ME` variants).

**4. Time-to-kill** - falls out of 3. Needs a rolling window, not whole-fight.

**5. Damage-taken trail** - ghost segment lagging the fill after a hit. `drawBarShape()` already
layers segments for the food/prayer preview.

**6. Phase markers** - tick marks at boss thresholds (Vorkath 50%, Zulrah, Hydra). Best of these
ideas, but needs a per-NPC threshold table with the same maintenance problem as `npc_hp.csv`.

**7. Freeze/stun timer** - countdown under the bar while frozen. Needs spotanim detection; check
core's precedent before assuming the signal exists.

**8. Special attack bar** - third bar in the stack, mirrors the Player Bar profile. Cheap, but
`claimBarStackSlot()` already under-reserves for two bars (see CLAUDE.md).

**9. Dim non-target bars** - cuts multi-combat clutter. Target from `localPlayer.getInteracting()`.

**10. Combat level on NPCs and players** - `(level 72)` beside the name. `Actor.getCombatLevel()` is
already used in the plugin, so the API is known-good. NPCs are nearly free via `drawNpcNameOnly()`;
players have no name label at all yet, so that half also needs stack-height reservation (see 8).
Own config toggle, and skip the suffix when level is 0.

**11. Elemental weakness on the bar** - air/water/earth/fire plus percentage, sharing the label area
with 10. No weakness getter exists on `NPC`/`NPCComposition` in this API version; `ParamHolder` is
the likely route. Core's `TargetWeaknessOverlay` is slayer-task weakness, not this.
