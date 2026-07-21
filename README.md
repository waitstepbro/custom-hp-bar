# Custom HP Bar

A RuneLite plugin that replaces the native health bar with a fully custom overlay - HP numbers
drawn directly on the bar, independent styling for NPCs vs. players, precise (not bucketed) HP
tracking, and status-effect coloring for poison/venom/burn/bleed.

## Features

- **Custom HP bars** for NPCs (Target Bar) and players (Player Bar), each with independent
  width/height/corners/border/colors/font/text settings.
- **Precise NPC HP**, not the native bar's coarse ratio/scale bucket. Tracked by accumulating
  hitsplat damage against a known max HP (bundled dataset, ~4,000 NPCs, sourced from the OSRS
  Wiki), self-correcting against the native ratio if it ever drifts. NPCs not in the dataset
  (max HP unknown) show a percentage instead of a number, regardless of Display Mode.
- **Status effect coloring** - the bar tints while an NPC (or you) is poisoned, envenomed,
  burning, or bleeding, the same idea as the native HP orb changing appearance. Colors are
  sampled from the actual hitsplat sprites on the OSRS Wiki, not guessed. Your own poison/venom
  state is read exactly (no guessing); everything else is inferred from recent hitsplats.
- **Debuff icons** - the real Poison/Venom/Burn hitsplat icons, loaded live from the game itself,
  shown side by side beneath the bar when multiple effects are active at once.
- **NPC names** above the bar, optionally shown at all times rather than only during combat.
- **Prayer Bar** - a second bar for your own Prayer points, anchored beneath your HP bar.
- **Hide the native health bar** entirely (sprite-level override), so only this plugin's bar
  shows.
- **Zoom scaling** - bars and text grow/shrink with camera zoom, matching the actor model.
- **Persist duration** - keep showing a bar for a configurable time after the native bar would
  have faded (e.g. after combat ends), set independently for NPCs and players.
- **NPC filter** - blacklist specific NPCs by name (wildcards supported) so their bar never
  shows.

## Configuration

Settings are grouped into four sections in the plugin's config panel:

- **Target Bar (NPCs)** - size/shape/color/font for the NPC bar, persist duration, NPC name
  display, and poison/venom/burn/bleed colors.
- **Player Bar (You & Others)** - same styling options for the bar drawn over players, plus
  self-only extras: Prayer Bar and status effect colors.
- **Behavior** - zoom scaling and hiding the native bar.
- **NPC Filter** - the blacklist of NPC names to never show a bar for.

Every option has an in-app description - open the plugin's config panel for specifics.
