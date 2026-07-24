# TODO

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
