## Bugs

**1. ToA in a team.** Solo is settled - every minion and every boss with a HUD bar now measures
exactly where the formula puts it. A team never has been, so minions outside a solo deliberately
fall back to a percentage rather than a guessed number. Open:
  - **`toaPartySize()`'s varbit read**, never seen reporting a real team - every figure so far was
    read off the game rather than the plugin. It now decides whether a minion shows a number or a
    percent, so a misread costs numbers in a solo or invents them in a team, either way silently.

**2. Bar disappears with `hideNativeBar` on**
([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16)) - one symptom, a different
cause per boss, so a fix for one is not a fix for the others. Narrowing the override to health
sprites may already have settled the two scenery ones for free; neither has been looked at since:
  - **Duke Sucellus's Fermentation Vat** - scenery, not an NPC. If it is still blank, its bar is on
    a health sprite, and `PostHealthBarConfig` reports the front sprite ID of whatever the client is
    about to draw - the live capture this was waiting on, without the capture.
  - **Verzik Supporting Pillars** - real NPCs that can survive a whole fight without a hitsplat, so
    they never enter `trackedActors`. Also eyeball the widened "Only Show Combat NPC Names" gate
    shipped in `259d8bd` while here - the CoX confirmation did not cover Verzik.
  - **ToB Nylocas room "Support" pillars** - game objects, so the Verzik fix can't reach them, and
    it isn't confirmed they show the bug at all.

**3. Yama's void flares show a full bar until something hits them**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - reported alongside the
charge bar, untouched by that fix and a different cause. They spawn at part health, but "Always
Show NPC Bar" has no read before the first hitsplat, so `resolveHp()` returns null and the pass
draws `{1, 1}` - a full bar at whatever max it can find. Doom's Demonic larva shows the same shape.
Drawing nothing until a real read arrives would fix it and would also take away the full bar every
never-hit NPC deliberately gets, so the fix has to be narrower than that.

## Features

- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
