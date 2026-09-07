# Custom HP Bar

A RuneLite plugin that replaces the native health bar with a fully custom overlay - HP numbers
drawn directly on the bar, independent styling for NPCs vs. players, precise HP
tracking, and status-effect debuffs.

The [wiki](https://github.com/waitstepbro/custom-hp-bar/wiki) covers setup, troubleshooting, how it works, and known limitations. Every setting and its default is below.

<p align="center">
  <img src="images/burn-example.png" width="49%">
  <img src="images/aggressive-icon-example.png" width="49%">
</p>
<p align="center">
  <img src="images/grey-bar-example.png" width="32%">
  <img src="images/grey-example-2.png" width="32%">
  <img src="images/stacking-example.png" width="32%">
</p>

## Support RuneLite

If you would like to support in some way, please consider joining the
[RuneLite Patreon](https://www.patreon.com/runelite).

## Features

- **Custom-drawn HP bars** — replaces the native health bar for NPCs and players, each with fully
  independent size, shape, color, and font settings. Your own can persist outside combat.
- **Precise NPC HP** — tracks exact current HP instead of the native bar's coarse ratio bucket,
  falling back to a percentage where max HP isn't known.
- **Shield and charge bars** — a shielded NPC's bar shows the shield's remaining strength in a
  distinct color, and a second bar beneath fills while an NPC charges a special attack.
- **HP color gradient** — optionally blend the bar from its full-HP color through yellow to red as
  HP drops, per bar type.
- **Bar opacity** — an optional transparency slider for the bar's background, fill, and border,
  configurable separately per bar type.
- **Damage trail** — optionally leaves a colored trail behind the bar when an actor takes damage,
  holding briefly before it drains to the new HP. Its color can be a darkened copy of the bar's.
- **Fade out on death** — optionally fades an NPC's bar and name out when it dies instead of
  hiding them the instant the killing blow lands.
- **Status effect tinting and icons** — tints the bar and shows a debuff icon for poison, venom,
  burn, bleed, disease, and corruption. Multiple effects show side by side.
- **NPC names** — drawn above the bar, optionally at all times rather than only in combat, and
  optionally with the combat level. Long names can be truncated, non-combat NPCs skipped, and pets
  never get a bar or a name.
- **Other players' names** — optionally drawn above their bar, at all times or only while it's
  tracked, and optionally with their combat level. Requires their bar to be showing.
- **Combat level coloring** — combat levels on NPCs and other players can be colored by how far
  each is from your own, on the game's red-to-green scale. Names can optionally take that color.
- **Always show other players' HP bars** — their bar can be set to draw on every visible player,
  not just once tracked in combat.
- **Same-tile stacking** — actors sharing a tile get their bars and names stacked vertically
  instead of overlapping.
- **Prioritize self on same tile** — optionally hide an NPC's or other player's bar/name entirely
  when they're standing on your own tile, instead of stacking with yours.
- **Always show NPC bars** — optionally show the bar on every attackable NPC, not just once you
  engage it.
- **Aggressive NPC indicator** — optionally color a known-aggressive monster's name and bar and
  show an icon by its bar, reverting once the game's tolerance timer expires.
- **Elemental weakness icon** — optionally show the matching surge spell icon beside an NPC's bar,
  with the weakness percentage next to it.
- **Ironman shared-loot warning** — optionally grey out an NPC's bar and name once another player
  damages it. Bosses with shared or personal loot are exempt.
- **Prayer bar** — an optional Prayer points bar below your HP bar. It can persist outside combat,
  be limited to while a prayer is active, and show a per-tick indicator for timing flicks.
- **Special attack bar** — an optional special attack energy bar, shown in combat alongside your HP
  bar, or persistently outside combat. Color configurable.
- **Run energy bar** — an optional run energy bar, shown regardless of combat state. It takes a
  fixed distinct color while a Stamina potion is active, and can time out after a period of not
  running.
- **Bar order** — four independent pickers choose which bar (HP, Prayer, Special Attack, or Run
  Energy) goes in each of the four stack positions.
- **Restore previews** — hovering a food/potion, Prayer-restoring item, or Stamina potion extends
  the matching HP, Prayer, or Run Energy bar with a preview of where it'll land.
- **Replaced overhead icon** — redraws overhead prayer icons, hitsplats, and chat text above the
  bar, for you and other players.
- **Hide the native health bar** — replaces the game's own overhead bar client-wide, so only this
  plugin's bar shows. Native bars tracking a mechanic rather than hitpoints stay visible.
- **Zoom scaling** — bars and text grow and shrink with camera zoom.
- **Hotkeys** — optional keybinds to instantly show/hide names or HP bars, independently of each
  other and without changing any setting.
- **Independent persist duration** — NPCs and players each keep showing their last known HP for
  their own configurable duration after combat.
- **NPC and player blacklists** — hide specific NPCs or other players by name, wildcards supported.

## Configuration

Settings are grouped into seven sections:

- NPC Bar — Style
- NPC Bar — Info
- Player Bar — Style
- Player Bar — Info
- Other Player Bar — Style
- Other Player Bar — Info
- Behavior & Hotkeys

NPCs get their own full Style section. Your own bar and other players' bars share one set of
size/shape/border/font settings (Player Bar — Style), but each gets its own colors, gradient,
background, text color, opacity, damage trail, and vertical offset - yours there, other players'
in Other Player Bar — Style.

### Style options (present in both NPC Bar — Style and Player Bar — Style)

| Setting | Description | Default |
|---|---|---|
| Bar Width | Width of the bar in pixels | 50 |
| Bar Height | Height of the bar in pixels | 10 |
| Corner Radius | Rounds the corners of the bar. 0 = sharp corners. | 2 |
| Border Width | Thickness of the bar's outline in pixels. 0 = no border. | 1 |
| Vertical Offset | Pixels to shift the bar up (positive) or down (negative) from center | NPC: 5 · Player: 15 |
| Bar Color | Fill color of the bar, and the full-HP color when HP Color Gradient is on. | Green |
| HP Color Gradient | Blends the bar from its color through yellow to red as HP drops, instead of one flat color. | Off |
| Border Color | Color of the bar's outline | Black (translucent) |
| Background Color | Color of the empty portion of the bar | Dark gray (translucent) |
| Bar Opacity | Overall transparency of the bar's background, fill, and border. 100 = fully opaque; text is unaffected. | 100 |
| Font | Typeface for the HP text | System Default |
| Font Style | Applied on top of the chosen font | Bold |
| Font Size | Size of the HP number text | 11 |
| HP Text Color | Color of the HP number. Prayer, special attack, and run energy have their own colors. | White |
| Text Outline | Full outline around the text for readability at small sizes | On |
| Text Alignment | Where each bar's text sits horizontally within it. | Center |
| HP Text Spacing | Pushes the HP number and percentage apart, up to the width of the bar. Requires a Display Mode of Both. | 0 |
| Text Nudge | Nudges the HP text down (positive) or up (negative) if it looks off-center | 0 |
| Damage Trail | Whether a darker trail follows damage down the bar: Off, Match bar color, or Custom color. Healing has no trail. | Off |
| Trail Color | Color of the health just lost. Requires a Damage Trail of Custom color. | Red |

### NPC Bar — Style (in addition to the above)

| Setting | Description | Default |
|---|---|---|
| Display Mode | Show HP as a raw number, a percentage, both, or neither (bar only, no text). | Number |

### NPC Bar — Info

| Setting | Description | Default |
|---|---|---|
| NPC Name | Whether NPCs get a name label: Never, When tracked, or Always. | Always |
| Combat Level | Appends the NPC's combat level to its name. Requires NPC Name. | Off |
| Combat NPCs Only | Excludes non-attackable NPCs from bars and names. Pets are hidden either way. | On |
| Name Length Limit | Shortens NPC names past this many characters and appends a period. 0 = no limit. | 0 |
| Name Color | Color of the NPC name text, separate from the HP number's color | Yellow |
| Always Show Bar | Shows the HP bar on every attackable NPC, not just once engaged. | Off |
| Death Fade | Fades an NPC's bar and name out when it dies instead of hiding them the instant the killing blow lands. | On |
| Persist Duration | How long in seconds the bar keeps showing the last known HP after the native bar fades (0 = hide immediately). | 5 |
| Stack Limit | Caps how many NPCs (bar and/or name) render on the same tile at once. 0 = unlimited. | 0 |
| Color Aggressive NPCs | Colors an NPC's name, bar, both or neither while it's aggressive toward you, reverting once the tolerance timer expires. | Off |
| Aggressive Icon | Shows an icon next to the bar while an NPC is aggressive | Off |
| Aggressive Color | Shared color for the name and bar of an NPC that's currently aggressive toward you. Requires Color Aggressive NPCs. | Red |
| Status Effects | How poison, venom, burns, bleeds, disease and corruption show: Off, Bar tint, Icon, or Both. | Both |
| Grey Out Bars & Names | Greys out an NPC's bar and name once another player damages it. Ironman accounts only; bosses with shared or personal loot are exempt. | On |
| Weakness Icon | Whether an NPC's elemental weakness shows beside its bar: Off, Icon, or Icon & percent. | Off |
| Percent Color | Color of the weakness percentage text. Requires Weakness Icon set to "Icon & percent". | White |
| Shield Bar | Shows a shield's remaining strength on the bar while an NPC is shielded. Supports Doom of Mokhaiotl and Kephri. | On |
| Charge Bar | Shows a second bar beneath an NPC's while it charges a special attack. Supports Doom of Mokhaiotl and Yama's void flares. | On |
| Charge Width | Width of the charge bar in pixels. 0 matches the NPC bar's width. Requires Charge Bar. | 0 |
| Charge Height | Height of the charge bar in pixels. 0 matches the NPC bar's height. Requires Charge Bar. | 0 |
| Charge Gap | Pixels between the NPC's bar and the charge bar beneath it. The charge bar drops further when status icons need the room. Requires Charge Bar. | 0 |
| Blacklist | Comma-separated NPC names to hide. Supports `*` wildcards; leave blank to show all. | (blank) |

### Player Bar — Style (in addition to the shared options)

The shared size/shape/border/font options above apply to other players too. Everything else
in this section, including the color settings in the shared table, is self-only - see
"Other Player Bar — Style" below for other players' own colors, text color, and vertical offset.

| Setting | Description | Default |
|---|---|---|
| Show Bar | Whether your own bar draws: Never, When tracked, or Always. | When tracked |
| Display Mode | Show your own HP as a raw number, a percentage, both, or neither (bar only, no text). Requires Show Bar. | Number |

### Player Bar — Info

| Setting | Description | Default |
|---|---|---|
| Prayer Bar | Whether the Prayer bar draws: Never, While praying, When tracked, or Always. | When tracked |
| Prayer Fill | Fill color of the Prayer bar. Requires Prayer Bar. | Blue |
| Prayer Text | Color of the Prayer number. Requires Prayer Bar. | White |
| Prayer Bar Tick | Whether the tick timer sweeps across the Prayer bar: Never, While praying, or Always. | Never |
| Tick Color | Color of the tick timer indicator. Requires Prayer Bar Tick. | White |
| Special Attack Bar | Whether the special attack bar draws: Never, When tracked, or Always. | Never |
| Special Attack Fill | Fill color of the special attack bar. Requires Special Attack Bar. | Green |
| Special Attack Text | Color of the special attack number. Requires Special Attack Bar. | White |
| Run Energy Bar | Whether the run energy bar draws: Never, While draining, or Always. | Never |
| Run Energy Timeout | Hides the run energy bar this many seconds after you last ran (0 = never time out). Requires Run Energy Bar. | 0 |
| Run Energy Fill | Fill color of the run energy bar. Requires Run Energy Bar. | Gold |
| Run Energy Text | Color of the run energy number. Requires Run Energy Bar. | White |
| Bar 1 / Bar 2 / Bar 3 / Bar 4 | Four independent pickers choosing which bar (HP, Prayer, Special, Run Energy) is drawn in each stack position, top to bottom. A bar picked in more than one position only shows at its topmost pick. | HP, Prayer, Special, Run Energy |
| Status Effects | How poison, venom, burns, bleeds, disease and corruption show: Off, Bar tint, Icon, or Both. | Both |
| Restore Previews | Previews what a hovered food, potion or restore item would give, as an extra segment on the HP, prayer or run bar. Requires your own bar to be showing. | On |
| Persist Duration | How long in seconds the bar keeps showing the last known HP after the native bar fades (0 = hide immediately). | 5 |

### Other Player Bar — Style

Size, shape, border, and font come from Player Bar — Style, shared with your own bar. Colors,
gradient, background, text color, opacity, damage trail, and vertical offset below are independent
of your own.

| Setting | Description | Default |
|---|---|---|
| Show Bar | Whether other players get a bar: Never, When tracked, or Always. | Never |
| Display Mode | Show other players' HP as a percentage, or neither (bar only, no text). | Percent |
| Vertical Offset | Pixels to shift other players' bars up (positive) or down (negative) from center | 15 |
| Bar Color | Fill color of other players' bars, and the full-HP color when HP Color Gradient is on | Green |
| HP Color Gradient | Blends the bar from its color through yellow to red as HP drops, instead of one flat color. | Off |
| Background Color | Color of the empty portion of other players' bars | Dark gray (translucent) |
| Bar Opacity | Overall transparency of other players' bar background, fill, and border. 100 = fully opaque | 100 |
| HP Text Color | Color of the HP number on other players' bars | White |
| Damage Trail | Whether a darker trail follows damage down the bar: Off, Match bar color, or Custom color. Healing has no trail. | Off |
| Trail Color | Color of the health another player just lost. Requires a Damage Trail of Custom color. | Red |

### Other Player Bar — Info

| Setting | Description | Default |
|---|---|---|
| Player Name | Whether other players get a name label: Never, When tracked, or Always. | When tracked |
| Combat Level | Appends the player's combat level to their name. Requires Player Name. | Off |
| Name Color | Color of the player name text, separate from the HP number's color | White |
| Stack Limit | Caps how many other players (bar and/or name) render on the same tile at once. 0 = unlimited. | 0 |
| Blacklist | Comma-separated player names to hide. Supports `*` wildcards; leave blank to show all. | (blank) |

### Behavior & Hotkeys

| Setting | Description | Default |
|---|---|---|
| Scale With Zoom | Grows and shrinks bars and text with camera zoom. | Off |
| Hide Native Bar | Hides the game's own overhead health bar client-wide, so only this plugin's bar shows. Bars that track a mechanic rather than hitpoints stay visible. | On |
| Prioritize Self | When an NPC or another player shares your tile, hides their bar and name instead of stacking it with yours. | On |
| Color Combat Levels | Colors a combat level by how far it is from your own, red through yellow to green. Requires a combat level to be showing on the NPC or player. | On |
| Color Names By Level | Colors an NPC or player's name by how far their combat level is from your own. Replaces the configured name color. | Off |
| Toggle Names | Instantly shows/hides NPC and player names. Doesn't affect HP bars, Prayer/Special/Run bars, hitsplats, chat text, or icons. | Not set |
| Toggle HP Bars | Instantly shows/hides NPC and player HP bars (including your own). Doesn't affect names, Prayer/Special/Run bars, hitsplats, chat text, or icons. | Not set |
