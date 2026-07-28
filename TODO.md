# TODO

## 1. Aggression timer wrongly colors all hostile NPCs yellow on expiry, not just nearby ones

When the aggression tolerance timer expires, it turns **all** hostile enemies' names yellow
(the non-aggressive/tolerant color), even ones outside the current aggression vicinity that
should never have been affected in the first place. Investigate RuneLite's core "NPC
Aggression Timer" plugin (`npcunaggroarea`) to see how it tracks the player's current area
alongside per-NPC/area timing, and compare against this plugin's `updateAggressionArea()`/
`isNpcAggressive()` (`CustomHpBarPlugin.java`) to find the actual mismatch.

## 2. ToA minion HP shows percent only - implement verified raid/path-level/party-size scaling

Bosses show correct HP (native boss HP HUD), but minions (Akkha's Shadows, Kephri's scarabs,
etc.) currently fall back to percent-only display rather than a number, since real ToA scales
every enemy's HP by raid level *and* path level (Walk the Path/Pathseeker/etc. invocations)
*and* party size - not raid level alone, which an earlier attempt this session got wrong.
A verified formula (confirmed against a real reference plugin, `LlemonDuck/tombs-of-amascut`'s
`AkkhaShadowHealth.java`) and the region-to-path mapping needed are both written up in
`CLAUDE.md`'s "ToA minion HP" section - ready to implement in `resolveNpcMaxHp()` once trusted
enough to ship as an exact number instead of the current `-1`/percent fallback.
