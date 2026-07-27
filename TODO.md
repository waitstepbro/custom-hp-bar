# TODO

## 0. UNTESTED IN-GAME: verify the 5 pushed cleanup/Moons commits

Pushed deliberately untested on branch `refactor-code-base-for-efficiency-and-deduplication`
(`7292418`..`b39e4cd`). All build clean and pass checkstyle; none has been seen running in-game.
**Verify before merging to master.** In rough order of risk:

1. **`7292418` bar stacking** - the only visually observable change of the batch. Two+ actors on
   one tile should stack without overlapping, and bars should no longer shift upward for NPCs that
   draw nothing (an NPC named `null`, or a combat-level-0 one with names off).
2. **`d203849` + `7d7f3b9` Blood jaguar** - one Blood Moon trip covers both: the jaguar's bar
   should appear again, show a **percentage** (not a number), and stay its normal color when
   someone else damages it.
3. **`b39e4cd` communal-loot exemption** - on any Moons boss with another player hitting it, an
   Ironman's bar must **not** grey out. This is the nbsp normalization fix; it was silently failing.
4. **`c3a940f` bleed** - your own bar still tints/badges on bleed; NPCs never do.
5. **`7292418` status icons** - poison/venom/burn/disease/corruption badges still load and draw
   (the sprite cache was consolidated; a mistake there shows up as missing icons).

Also still open from that pass, not yet acted on (details in `CLAUDE.md`'s "Second cleanup pass"
and "Multi-combat / 3+ player Moons" sections): the `claimBarStackSlot()` prayer-bar
under-reservation, and the deprecated `Client.isPrayerActive()` call.

Not open, recorded so they aren't re-raised: **Eclipse Moon's clones have never been an issue** in
play, so no clone handling is wanted. Icicles are already covered by the combat-level gate, and the
"Frozen weapons" ice block is genuinely damageable so its bar is correct.

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
