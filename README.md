# Custom HP Bar

A RuneLite plugin that replaces the native health bar with a fully custom overlay - HP numbers
drawn directly on the bar, independent styling for NPCs vs. players, precise (not bucketed) HP
tracking, and status-effect debuffs.

## Features

- **Custom-drawn HP bars** — replaces the native health bar for NPCs and players, each with its
  own fully independent size, shape, color, and font settings.
- **Precise NPC HP** — tracks exact current HP for ~4,000 NPCs from a bundled dataset, not the
  native bar's coarse ratio/scale bucket, self-correcting if it ever drifts. Falls back to a
  percentage for NPCs not in the dataset.
- **Status effect tinting and icons** — the bar changes color and shows a debuff icon while
  poisoned, envenomed, burning, diseased, or corrupted - on NPCs, yourself, and other players
  alike. Bleed additionally tints your own bar. Colors and icons are sourced from the actual
  hitsplat sprites, not guessed, and aren't user-configurable. Multiple effects at once show
  side by side.
- **NPC names** — shown above the bar, optionally at all times rather than only in combat, with
  non-attackable NPCs (bankers, shop owners, fishing spots, pets) excluded by default.
- **Prayer bar** — an optional second bar below your HP bar showing current Prayer points.
- **Hide the native health bar** — replaces the game's own overhead bar client-wide (sprite-level
  override) so only this plugin's bar shows.
- **Zoom scaling** — bars and text grow/shrink with camera zoom to match the actor model.
- **Independent persist duration** — NPCs and players each keep showing their last known HP for
  their own configurable duration after combat ends.
- **NPC filter** — hide specific NPCs by name, wildcards supported.

## Configuration

Settings are grouped into four sections.

### Target Bar (NPCs)

| Setting | Description | Default |
|---|---|---|
| Display Mode | Show HP as a raw number, a percentage, or both. Falls back to percent for NPCs with unknown max HP. | Number |
| Bar Width | Width of the bar in pixels | 50 |
| Bar Height | Height of the bar in pixels | 10 |
| Corner Radius | Rounds the corners of the bar. 0 = sharp corners, matching the native health bar. | 2 |
| Border Width | Thickness of the bar's outline in pixels. 0 = no border. | 1 |
| Border Color | Color of the bar's outline | Black (translucent) |
| Bar Color | Fill color of the bar, matching the native health bar's single green fill | Green |
| Background Color | Color of the empty portion of the bar | Dark gray (translucent) |
| Vertical Offset | Pixels to shift the bar up (positive) or down (negative) from center | 5 |
| Font | Typeface for the HP text - RuneScape options use the game's own UI font | System Default |
| Font Style | Applied on top of the chosen font. Leave Plain for "RuneScape Bold" - it's already bold. | Bold |
| Font Size | Size of the HP number text | 11 |
| Text Color | Color of the HP number | White |
| Text Outline | Full outline around the text for readability at small sizes | On |
| Text Vertical Nudge | Nudges the HP text down (positive) or up (negative) if it looks off-center | 0 |
| Show NPC Name | Draws the NPC's name above its HP bar | On |
| Always Show NPC Name | Shows the NPC name at all times, not just in combat. Requires Show NPC Name. | On |
| Only Show Combat NPC Names | Excludes non-attackable NPCs (bankers, shop owners, fishing spots, pets) from bars and names | On |
| NPC Name Color | Color of the NPC name text, independent of Text Color above | Yellow |
| Color By Status Effect | Tints the bar while poisoned, envenomed, burning, diseased, or corrupted | On |
| Show Status Icon | Shows a debuff icon beneath the bar for the same effects | On |
| Persist Duration (seconds) | How long an NPC's bar keeps showing the last known HP after the native bar fades. 0 = hide immediately. | 5 |

### Player Bar (You & Others)

| Setting | Description | Default |
|---|---|---|
| Show for Self | Draw the player bar over your own character | On |
| Self Display Mode | Display mode for your own bar | Number |
| Show for Other Players | Draw the player bar over other players | Off |
| Other Players' Display Mode | Display mode for other players' bars (always percent - their max HP isn't available) | Number |
| Bar Width | Width of the bar in pixels | 50 |
| Bar Height | Height of the bar in pixels | 10 |
| Corner Radius | Rounds the corners of the bar. 0 = sharp corners, matching the native health bar. | 2 |
| Border Width | Thickness of the bar's outline in pixels. 0 = no border. | 1 |
| Border Color | Color of the bar's outline | Black (translucent) |
| Bar Color | Fill color of the bar, matching the native health bar's single green fill | Green |
| Background Color | Color of the empty portion of the bar | Dark gray (translucent) |
| Vertical Offset | Pixels to shift the bar up (positive) or down (negative) from center | 15 |
| Font | Typeface for the HP text - RuneScape options use the game's own UI font | System Default |
| Font Style | Applied on top of the chosen font. Leave Plain for "RuneScape Bold" - it's already bold. | Bold |
| Font Size | Size of the HP number text | 11 |
| Text Color | Color of the HP number | White |
| Text Outline | Full outline around the text for readability at small sizes | On |
| Text Vertical Nudge | Nudges the HP text down (positive) or up (negative) if it looks off-center | 0 |
| Show Prayer Bar | Draws a second bar for your Prayer points beneath your HP bar. Requires Show for Self. | On |
| Color By Status Effect | Tints a player's bar (yours or others') while poisoned, envenomed, burning, diseased, or corrupted - plus bleeding, for your own bar only | On |
| Show Status Icon | Shows a debuff icon beneath a player's bar for the same effects | On |
| Persist Duration (seconds) | How long a player's bar keeps showing the last known HP after the native bar fades. 0 = hide immediately. | 5 |

### Behavior

| Setting | Description | Default |
|---|---|---|
| Scale With Zoom | Grow/shrink bars and text with camera zoom. Sizes above are exact at the zoom level you're at when the plugin starts, and scale relative to that as you zoom in/out. | Off |
| Hide Native Health Bar | Hides the game's built-in health bar for every actor, not just filtered NPCs | On |

### NPC Filter

| Setting | Description | Default |
|---|---|---|
| NPC Filter | Comma-separated NPC names to hide (blacklist, supports `skele*` wildcards). Leave blank to show all. | (blank) |

## How it works

**Precise NPC HP** is tracked by starting from the native bar's coarse ratio/scale bucket, then
adjusting that estimate by each hitsplat's exact damage/heal amount in between bucket updates -
far closer to the NPC's true HP than the bucket alone. This only works for NPCs in the bundled
~4,000-entry max-HP dataset; anything else falls back to a plain percentage.

**Status effects** - Poison, Venom, Burn, Disease, and Corruption are detected the same way for
NPCs, yourself, and other players: from recent matching hitsplats (your own Poison/Venom state
is read exactly instead, since that's directly readable for the local player). Bleed only
applies to your own bar - it doesn't affect NPCs in OSRS. An actor can have more than one effect
active at once; the debuff icon row shows all of them side by side, while the bar's tint picks a
single color in Venom > Poison > Burn > Bleed > Disease > Corruption priority, since a bar can
only show one fill color at a time.

**Zoom scaling**, when enabled, doesn't scale against a fixed reference - it captures whatever
zoom level you're at the first time the overlay renders and treats that as the 1.0x baseline, so
your configured sizes are always exactly right at whatever zoom you actually play at.

**Persist Duration** keeps a bar showing the last known HP for a set number of seconds after the
native bar itself would stop refreshing (e.g. combat ends), instead of disappearing instantly.
Re-engaging before that timer runs out resets it. NPCs and players use independent timers.

**Hide Native Health Bar** works by overriding the game's own health/prayer/shield bar sprites
client-wide - it isn't a per-actor toggle, so turning it on hides the native bar for every actor
in the game, not just NPCs matching your filter.

**NPC Filter** is a blacklist: anything matching a pattern is hidden, everything else still
shows. Leave it blank to show every attackable NPC. Non-attackable NPCs (bankers, fishing spots,
pets, etc.) are excluded separately via "Only Show Combat NPC Names" above, regardless of what's
in this filter.
