# TODO

## 0. Leftovers from the cleanup pass (low priority, not blocking)

The cleanup/Moons batch on `refactor-code-base-for-efficiency-and-deduplication`
(`7292418`..`b39e4cd`) is **verified in-game and good** - bar stacking, Blood jaguar (bar shows
percent, no grey-out), the Moons communal-loot exemption, bleed, and status icons were all
confirmed working. Branch is ready to merge to `master`.

Still open from that pass, neither urgent (details in `CLAUDE.md`'s "Second cleanup pass" section):
- `claimBarStackSlot()` under-reserves for your own bar when the prayer bar is on - it draws `2h`
  tall but reserves `h`. Only shows if another actor shares your exact tile. One-line fix, but it
  shifts hand-tuned layout, so it needs a deliberate call.
- `Client.isPrayerActive()` is deprecated. `Prayer.getVarbit()` is the presumed replacement but
  equivalence couldn't be confirmed from bytecode, and it drives the standalone prayer bar.

Not open, recorded so they aren't re-raised: **Eclipse Moon's clones have never been an issue** in
play, so no clone handling is wanted. Icicles are already covered by the combat-level gate, and the
"Frozen weapons" ice block is genuinely damageable so its bar is correct. The one Moons item that
could still surface is the "Frozen weapons" block greying out on another player's damage - chase it
only if actually seen.

## 1. Aggression timer wrongly colors all hostile NPCs yellow on expiry, not just nearby ones

When the aggression tolerance timer expires, it turns **all** hostile enemies' names yellow
(the non-aggressive/tolerant color), even ones outside the current aggression vicinity that
should never have been affected in the first place. Investigate RuneLite's core "NPC
Aggression Timer" plugin (`npcunaggroarea`) to see how it tracks the player's current area
alongside per-NPC/area timing, and compare against this plugin's `updateAggressionArea()`/
`isNpcAggressive()` (`CustomHpBarPlugin.java`) to find the actual mismatch.

## 2. HP bars aren't greying out via the built-in detection mechanism

Use the built-in detection mechanism to grey out bars when we see it - it is not greying the
bar as of now.

## 3. ToA minion HP shows percent only - implement verified raid/path-level/party-size scaling

Bosses show correct HP (native boss HP HUD), but minions (Akkha's Shadows, Kephri's scarabs,
etc.) currently fall back to percent-only display rather than a number, since real ToA scales
every enemy's HP by raid level *and* path level (Walk the Path/Pathseeker/etc. invocations)
*and* party size - not raid level alone, which an earlier attempt this session got wrong.
A verified formula (confirmed against a real reference plugin, `LlemonDuck/tombs-of-amascut`'s
`AkkhaShadowHealth.java`) and the region-to-path mapping needed are both written up in
`CLAUDE.md`'s "ToA minion HP" section - ready to implement in `resolveNpcMaxHp()` once trusted
enough to ship as an exact number instead of the current `-1`/percent fallback.
