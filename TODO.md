## Bugs

**1. ToA minion HP.** Confirmed solo at two raid levels; the 5-man at raid 305 settled the
party-size term and the Wardens, but every one of those readings was a boss. Open:
  - **Party scaling on minions.** `toaScaledMaxHp()` applies the party term to every ToA NPC, while
    the wiki only ever claims it for "the base health of bosses". At a party of 5 that term is x4, so
    if minions don't take it, every minion number in a team is out by up to that much. One baboon or
    scarab kill in a team with `--debug` settles it - the two predictions differ fourfold. The
    raid-level term is no longer in doubt on minions: the 2026-08-29 solo measured the scarabs
    (40 -> 82), Akkha's Shadow (70 -> 145) and the Baboon Thrall (2 -> 3) exactly.
  - **Agile Scarab (11727)**, scaled on assumption (predicted 61 at raid 230) while its neighbour
    11723 measured static. It spawned on 2026-08-29 but never died; one kill settles it.
  - **`toaPartySize()`'s varbit read**, never seen reporting a real team - every figure so far was
    read off the game rather than the plugin.
  - **Zebak tallies 21 more damage than its own HUD max** - 1221 against 1200 at raid 230 solo.
    Killing hits cap at remaining HP, so a clean single-phase tally lands exactly on max; this one
    did not. Same shape as the old Soldier Scarab 96-against-95.

**2. Bar disappears with `hideNativeBar` on**
([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16)) - one symptom, a different
cause per boss, so a fix for one is not a fix for the others:
  - **Duke Sucellus's Fermentation Vat** - scenery, not an NPC; needs a live sprite-ID capture.
  - **Verzik Supporting Pillars** - real NPCs that can survive a whole fight without a hitsplat, so
    they never enter `trackedActors`. Also eyeball the widened "Only Show Combat NPC Names" gate
    shipped in `259d8bd` while here - the CoX confirmation did not cover Verzik.
  - **ToB Nylocas room "Support" pillars** - game objects, so the Verzik fix can't reach them, and
    it isn't confirmed they show the bug at all.

**3. Doom of Mokhaiotl's yellow charge bar disappears with the plugin on**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - almost certainly the same
`hideNativeBar` over-suppression as item 2.

## Features

- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
