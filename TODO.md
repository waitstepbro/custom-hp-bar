# TODO

## 1. Aggression timer wrongly colors all hostile NPCs yellow on expiry, not just nearby ones

When the aggression tolerance timer expires, it turns **all** hostile enemies' names yellow
(the non-aggressive/tolerant color), even ones outside the current aggression vicinity that
should never have been affected in the first place. Investigate RuneLite's core "NPC
Aggression Timer" plugin (`npcunaggroarea`) to see how it tracks the player's current area
alongside per-NPC/area timing, and compare against this plugin's `updateAggressionArea()`/
`isNpcAggressive()` (`CustomHpBarPlugin.java`) to find the actual mismatch.

## 2. Icon option as an alternative to recoloring aggressive NPC names

Instead of only recoloring the NPC name text for identified hostile enemies, consider adding a
small icon next to/above the name instead - or let the user choose between icon and recolor
(or both) via a config option.

## 3. NPC HP bar resets to full HP after reappearing, when "Always Show NPC Bar" is on

Repro: attack a target, deal damage, stop attacking and let the bar disappear after the
persist-duration window elapses, then the bar reappears at full HP instead of the last known
(damaged) HP. Only reproduces with **Always Show NPC Bar** enabled. Need to keep tracking/
retain the accumulated damage (precise HP / last known HP) so the bar reflects the NPC's real
remaining health when it reappears, instead of resetting.

## 4. Vasa Nistirio (ToA) may have wrong HP on Normal mode

Flagged during an earlier session, not yet investigated: the OSRS Wiki lists NPC ids
7566/7567 twice - once at 300 HP (presumably Normal mode) and once at 450 HP (Challenge Mode) -
suggesting Vasa Nistirio may not get separate CM-specific ids the way Tekton does. Our
`npc_hp.csv` currently maps both ids to 450, which would show the wrong (too high) HP for
Normal-mode Vasa specifically. Needs research to confirm whether the ids really are reused
across difficulties, and if so, how to distinguish Normal vs. CM Vasa some other way (or
whether it's a wiki data quirk that needs a source correction instead).
