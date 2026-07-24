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

## 6. Grey out an NPC's bar once another player damages it (Ironman loot-eligibility)

Ironman accounts lose loot eligibility on a monster once another player deals damage to it.
Requested: an option to grey out (or otherwise visually distinguish) an NPC's HP bar once we
detect it's taken a hit from someone other than the local player, so an Ironman can tell at a
glance the kill is no longer "theirs."

Needs research before implementing - open question is *when/how this can actually be detected*:
`onHitsplatApplied()` (`CustomHpBarPlugin.java`) receives a `Hitsplat` per actor, but the type
(DAMAGE_ME/DAMAGE_OTHER/etc.) only distinguishes "hit landed on me" vs. "hit landed on someone/
something else" - it doesn't identify *who dealt* the hit. Need to figure out whether attribution
is derivable client-side at all (e.g. cross-referencing nearby players' `Actor.getInteracting()`
against the tracked NPC at the moment its hitsplat lands), and whether that only works while
already watching/engaged with that specific fight, or could also require being engaged in combat
with the NPC ourselves to reliably catch it - not yet clear which. Should also confirm this only
applies to Ironman account types (so regular accounts, where shared loot isn't a concern, aren't
affected) - likely via `Client.getAccountType()` or similar.
