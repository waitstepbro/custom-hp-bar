# TODO

## 1. Aggression timer wrongly colors all hostile NPCs yellow on expiry, not just nearby ones

When the aggression tolerance timer expires, it turns **all** hostile enemies' names yellow
(the non-aggressive/tolerant color), even ones outside the current aggression vicinity that
should never have been affected in the first place. Investigate RuneLite's core "NPC
Aggression Timer" plugin (`npcunaggroarea`) to see how it tracks the player's current area
alongside per-NPC/area timing, and compare against this plugin's `updateAggressionArea()`/
`isNpcAggressive()` (`CustomHpBarPlugin.java`) to find the actual mismatch.

## 4. Vasa Nistirio may have wrong HP on Normal mode

Flagged during an earlier session, not yet investigated: the OSRS Wiki lists NPC ids
7566/7567 twice - once at 300 HP (presumably Normal mode) and once at 450 HP (Challenge Mode) -
suggesting Vasa Nistirio may not get separate CM-specific ids the way Tekton does. Our
`npc_hp.csv` currently maps both ids to 450, which would show the wrong (too high) HP for
Normal-mode Vasa specifically. Needs research to confirm whether the ids really are reused
across difficulties, and if so, how to distinguish Normal vs. CM Vasa some other way (or
whether it's a wiki data quirk that needs a source correction instead).

## 7. HP bars have a slight delay appearing on login or teleport

Reported: after logging in or teleporting, HP bars for already-in-combat/trackable actors don't
show up immediately - there's a brief lag before they appear. Needs investigation into what's
gating the delay (e.g. waiting on the first game tick, a fresh hitsplat, or client-side actor/
widget state not being ready yet right after the scene loads) and whether it can be shortened or
eliminated.
