# Custom HP Bar

A RuneLite plugin that replaces the native health bar with a fully custom overlay - HP numbers
drawn directly on the bar, independent styling for NPCs vs. players, precise (not bucketed) HP
tracking, and status-effect debuffs.

## Features

- **Custom HP bars** for NPCs and players, styled completely independently (size, shape, color,
  font, text).
- **Precise NPC HP**, not the native bar's coarse ratio/scale bucket - tracked from hitsplat
  damage against a bundled ~4,000-NPC max-HP dataset, self-correcting if it ever drifts. Falls
  back to percentage for NPCs not in the dataset.
- **Status effects** - the bar tints and shows a debuff icon while poisoned, envenomed, burning,
  diseased, or corrupted (NPCs, players, and other players alike), plus bleeding on your own bar
  only (Bleed doesn't affect NPCs in OSRS). Colors and icons are sourced from the actual hitsplat
  sprites, not guessed, and aren't user-configurable. Multiple effects at once show side by side.
- **NPC names** above the bar, optionally shown at all times rather than only during combat -
  automatically excludes non-attackable NPCs (bankers, shop owners, fishing spots, pets).
- **Prayer Bar** for your own Prayer points, anchored beneath your HP bar.
- **Hide the native health bar** entirely (sprite-level override), so only this plugin's bar
  shows.
- **Zoom scaling**, independent **persist duration** for NPCs vs. players, and an **NPC filter**
  blacklist (wildcards supported).

## Configuration

Four sections in the plugin's config panel. Every option has its own in-app description - this
is just an index of what's there.

**Target Bar (NPCs)**
Display Mode · Bar Width/Height · Corner Radius · Border Width/Color · Bar/Background Color ·
Vertical Offset · Font/Style/Size · Text Color/Outline/Nudge · Show NPC Name · Always Show NPC
Name · Only Show Combat NPC Names · NPC Name Color · Color By Status Effect · Show Status Icon ·
Persist Duration

**Player Bar (You & Others)**
Show for Self/Other Players · Display Mode (self and other players independently) · Bar
Width/Height · Corner Radius · Border Width/Color · Bar/Background Color · Vertical Offset ·
Font/Style/Size · Text Color/Outline/Nudge · Show Prayer Bar · Color By Status Effect (applies to
other players too) · Show Status Icon · Persist Duration

**Behavior**
Scale With Zoom · Hide Native Health Bar

**NPC Filter**
Comma-separated NPC names to hide, supports `*` wildcards (e.g. `skele*`)
