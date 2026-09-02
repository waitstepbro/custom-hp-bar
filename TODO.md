## Bugs

**1. ToA in a team.** Solo is settled; a team never has been, so minions outside a solo fall back to
a percentage. `toaPartySize()`'s varbit read has never been seen reporting a real team and it picks
which of the two shows, so a misread invents numbers in a team or drops them in a solo, silently.

**2. Bar disappears with `hideNativeBar` on**
([issue #16](https://github.com/waitstepbro/custom-hp-bar/issues/16)) - one symptom, a different
cause per boss. Narrowing the override to health sprites may have settled the scenery ones already;
none rechecked since:
  - **Duke Sucellus's Fermentation Vat** - scenery, not an NPC. If still blank, capture the front
    sprite ID `PostHealthBarConfig` reports.
  - **Verzik Supporting Pillars** - NPCs that can survive a whole fight without a hitsplat, so they
    never enter `trackedActors`. Eyeball `259d8bd`'s widened combat-name gate while here; the CoX
    confirmation didn't cover Verzik.
  - **ToB Nylocas "Support" pillars** - game objects, out of reach of the Verzik fix, and not
    confirmed broken at all.

**3. Yama's void flares show a full bar until hit**
([issue #31](https://github.com/waitstepbro/custom-hp-bar/issues/31)) - they spawn at part health,
but "Always Show NPC Bar" has no read before the first hitsplat, so the pass draws `{1, 1}`. Doom's
Demonic larva too. Any fix has to stay narrower than "draw nothing until a real read", which would
also strip the full bar every never-hit NPC deliberately gets.

## Features

- **Draw our own charge bar for Yama's void flares** - built for Doom and Kephri, not Yama. The
  probe is written but never run; see CLAUDE.md for what it captures and why 3412/12408 worked
  for Doom.
- **Slayer task NPC identification.**
- **Scale NPC bar length to the mob's tile size**
  ([issue #33](https://github.com/waitstepbro/custom-hp-bar/issues/33)) - needs a capped curve.

## Ideas

- **Phase markers** at boss HP thresholds.
- **Dim non-target bars** to cut multi-combat clutter.
- **Decouple bar and name from character animation** - the NPC bar shake and the player-bar bob are
  the same problem.
