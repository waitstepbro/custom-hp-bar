# Custom HP Bar

A RuneLite plugin that replaces the native health bar with a fully custom overlay - HP numbers
drawn directly on the bar, independent styling for NPCs vs. players, precise (not bucketed) HP
tracking, and status-effect coloring for poison/venom/burn/bleed.

## Features

- **Custom HP bars** for NPCs (Target Bar) and players (Player Bar), each with independent
  width/height/corners/border/colors/font/text settings.
- **Precise NPC HP**, not the native bar's coarse ratio/scale bucket. Tracked by accumulating
  hitsplat damage against a known max HP (bundled dataset, ~4,000 NPCs, sourced from the OSRS
  Wiki), self-correcting against the native ratio if it ever drifts.
- **Status effect coloring** - the bar tints while an NPC (or you) is poisoned, envenomed,
  burning, or bleeding, the same idea as the native HP orb changing appearance. Colors are
  sampled from the actual hitsplat sprites on the OSRS Wiki, not guessed. Your own poison/venom
  state is read exactly (no guessing); everything else is inferred from recent hitsplats.
- **NPC names** above the bar, optionally shown at all times rather than only during combat.
- **Prayer Bar** - a second bar for your own Prayer points, anchored beneath your HP bar.
- **Hide the native health bar** entirely (sprite-level override), so only this plugin's bar
  shows.
- **Zoom scaling** - bars and text grow/shrink with camera zoom, matching the actor model.
- **Persist duration** - keep showing a bar for a configurable time after the native bar would
  have faded (e.g. after combat ends).
- **NPC filter** - blacklist specific NPCs by name (wildcards supported) so their bar never
  shows.

## Configuration

Settings are grouped into four sections in the plugin's config panel:

- **Target Bar (NPCs)** - size/shape/color/font for the NPC bar, NPC name display, and
  poison/venom/burn/bleed colors.
- **Player Bar (You & Others)** - same styling options for the bar drawn over players, plus
  self-only extras: Prayer Bar and status effect colors.
- **Behavior** - zoom scaling, persist duration, and hiding the native bar.
- **NPC Filter** - the blacklist of NPC names to never show a bar for.

Every option has an in-app description - open the plugin's config panel for specifics.

### Known limitations

- **Other players' status effects** (poison/venom/burn/bleed) aren't tracked - only your own
  and NPCs'. Other players' poison/venom state isn't readable from your client at all.
- **The native overhead prayer icon** (e.g. Protect from Melee) can't be moved, hidden, or
  redrawn by this plugin - it's rendered natively by the game with no exposed control surface.
  There's currently no workaround for it overlapping the Player Bar; a prior attempt (an
  automatic clearance push) made positioning worse rather than better and was removed, so for
  now the bar is positioned purely by your own Vertical Offset with no automatic adjustment.
- **Burn/Bleed timing** (when the status color turns off) is a heuristic - there's no
  "cured"/"expired" signal for either effect on any actor, so the color just times out a few
  seconds after the last matching hitsplat rather than ending exactly when the effect does.
- **Precise HP** only applies to NPCs present in the bundled max-HP dataset; anything else
  falls back to the native ratio/scale percentage.

## Development

Requires the RuneLite client on Maven (`mavenLocal`/`repo.runelite.net`) at the version pinned
in `build.gradle` (`runeLiteVersion`).

```
./gradlew runPlugin
```

Launches RuneLite with this plugin loaded via `ExternalPluginManager`, using the Maven-resolved
client jar - no `--developer-mode` needed. This is the preferred way to run/test locally.

`./gradlew check` runs the full build (compile, tests, Checkstyle) - keep this clean before
committing.
