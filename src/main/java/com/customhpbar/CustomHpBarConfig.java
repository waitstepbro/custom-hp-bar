package com.customhpbar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;
import java.awt.Font;

@ConfigGroup("customhpbar")
public interface CustomHpBarConfig extends Config
{
	@ConfigSection(
		name = "Target Bar (NPCs)",
		description = "Size, shape, color, and text settings for the bar drawn over NPCs",
		position = 0
	)
	String TARGET_SECTION = "target";

	@ConfigSection(
		name = "Player Bar (You & Others)",
		description = "Whether to show, and size/shape/color/text settings for, the bar drawn over players",
		position = 1
	)
	String PLAYER_SECTION = "player";

	@ConfigSection(
		name = "Behavior",
		description = "Settings shared by both bar types",
		position = 2
	)
	String BEHAVIOR_SECTION = "behavior";

	@ConfigSection(
		name = "NPC Filter",
		description = "Which NPCs to show the target bar for",
		position = 3
	)
	String FILTER_SECTION = "filter";

	// ==================== Target bar (NPCs) ====================

	@ConfigItem(
		keyName = "targetDisplayMode",
		name = "Display Mode",
		description = "Show HP as a raw number, a percentage, or both",
		section = TARGET_SECTION,
		position = 0
	)
	default DisplayMode targetDisplayMode()
	{
		return DisplayMode.NUMBER;
	}

	@ConfigItem(
		keyName = "targetBarWidth",
		name = "Bar Width",
		description = "Width of the bar in pixels (at the zoom level the plugin started at, if scaling with zoom)",
		section = TARGET_SECTION,
		position = 1
	)
	@Range(min = 20, max = 200)
	default int targetBarWidth()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "targetBarHeight",
		name = "Bar Height",
		description = "Height of the bar in pixels (at the zoom level the plugin started at, if scaling with zoom)",
		section = TARGET_SECTION,
		position = 2
	)
	@Range(min = 4, max = 30)
	default int targetBarHeight()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "targetCornerRadius",
		name = "Corner Radius",
		description = "Rounds the corners of the bar. 0 = sharp corners, matching the native health bar.",
		section = TARGET_SECTION,
		position = 3
	)
	@Range(min = 0, max = 12)
	default int targetCornerRadius()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetBorderWidth",
		name = "Border Width",
		description = "Thickness of the bar's outline in pixels. 0 = no border.",
		section = TARGET_SECTION,
		position = 4
	)
	@Range(min = 0, max = 4)
	default int targetBorderWidth()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "targetBorderColor",
		name = "Border Color",
		description = "Color of the bar's outline",
		section = TARGET_SECTION,
		position = 5
	)
	default Color targetBorderColor()
	{
		return new Color(0, 0, 0, 190);
	}

	@ConfigItem(
		keyName = "targetBarColor",
		name = "Bar Color",
		description = "Fill color of the bar, matching the native health bar's single green fill",
		section = TARGET_SECTION,
		position = 6
	)
	default Color targetBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "targetBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of the bar",
		section = TARGET_SECTION,
		position = 7
	)
	default Color targetBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "targetVerticalOffset",
		name = "Vertical Offset",
		description = "Pixels to shift the bar upward (positive) or downward (negative) from its centered position",
		section = TARGET_SECTION,
		position = 8
	)
	@Range(min = -50, max = 100)
	default int targetVerticalOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetFontFamily",
		name = "Font",
		description = "Typeface used for the HP text. The RuneScape options use the game's own bundled UI font.",
		section = TARGET_SECTION,
		position = 9
	)
	default FontFamily targetFontFamily()
	{
		return FontFamily.RUNESCAPE_BOLD;
	}

	@ConfigItem(
		keyName = "targetFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font. Leave Plain for 'RuneScape Bold' - it's already a " +
			"dedicated bold typeface, not a synthetic bolding of the regular one.",
		section = TARGET_SECTION,
		position = 10
	)
	default FontStyle targetFontStyle()
	{
		return FontStyle.PLAIN;
	}

	@ConfigItem(
		keyName = "targetFontSize",
		name = "Font Size",
		description = "Size of the HP number text (at the zoom level the plugin started at, if scaling with zoom). " +
			"Smaller sizes are harder to read - see Text Outline below.",
		section = TARGET_SECTION,
		position = 11
	)
	@Range(min = 6, max = 20)
	default int targetFontSize()
	{
		return 7;
	}

	@ConfigItem(
		keyName = "targetTextColor",
		name = "Text Color",
		description = "Color of the HP number",
		section = TARGET_SECTION,
		position = 12
	)
	default Color targetTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "targetTextOutline",
		name = "Text Outline",
		description = "Full outline around the text instead of a single drop shadow - improves readability at " +
			"small sizes or busy backgrounds. Recommended to leave on.",
		section = TARGET_SECTION,
		position = 13
	)
	default boolean targetTextOutline()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetTextVerticalNudge",
		name = "Text Vertical Nudge",
		description = "Shifts the HP text down (positive) or up (negative) if it looks off-center - text " +
			"rendering varies slightly between fonts.",
		section = TARGET_SECTION,
		position = 14
	)
	@Range(min = -10, max = 10)
	default int targetTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showNpcName",
		name = "Show NPC Name",
		description = "Draws the NPC's name above its HP bar. Uses the Target Bar's font/outline settings above " +
			"- color is separate (see NPC Name Color below).",
		section = TARGET_SECTION,
		position = 15
	)
	default boolean showNpcName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alwaysShowNpcName",
		name = "Always Show NPC Name",
		description = "Shows the NPC name at all times, not just while its HP bar is showing (in combat). " +
			"Requires 'Show NPC Name' above.",
		section = TARGET_SECTION,
		position = 16
	)
	default boolean alwaysShowNpcName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC Name Color",
		description = "Color of the NPC name text, independent of the HP number's Text Color above.",
		section = TARGET_SECTION,
		position = 17
	)
	default Color npcNameColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "targetColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints the bar while the NPC is poisoned, envenomed, burning, or bleeding - inferred from " +
			"hitsplats, since NPCs don't expose a queryable status effect state.",
		section = TARGET_SECTION,
		position = 18
	)
	default boolean targetColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetPoisonColor",
		name = "Poison Color",
		description = "Bar fill color while poisoned. Sampled from the actual Poison hitsplat sprite " +
			"(oldschool.runescape.wiki/w/Hitsplat) - pure green, no blue.",
		section = TARGET_SECTION,
		position = 19
	)
	default Color targetPoisonColor()
	{
		return new Color(0, 145, 0);
	}

	@ConfigItem(
		keyName = "targetVenomColor",
		name = "Venom Color",
		description = "Bar fill color while envenomed. Sampled from the actual Venom hitsplat sprite - a dark " +
			"teal, despite RuneLite's own constant name implying plain dark green.",
		section = TARGET_SECTION,
		position = 20
	)
	default Color targetVenomColor()
	{
		return new Color(48, 112, 95);
	}

	@ConfigItem(
		keyName = "targetBurnColor",
		name = "Burn Color",
		description = "Bar fill color while burning. Sampled from the actual Burn hitsplat sprite - a " +
			"red-orange gradient.",
		section = TARGET_SECTION,
		position = 21
	)
	default Color targetBurnColor()
	{
		return new Color(215, 85, 0);
	}

	@ConfigItem(
		keyName = "targetBleedColor",
		name = "Bleed Color",
		description = "Bar fill color while bleeding. Sampled from the actual Bleed hitsplat sprite - a vivid " +
			"red, brighter than a dark blood-red.",
		section = TARGET_SECTION,
		position = 22
	)
	default Color targetBleedColor()
	{
		return new Color(200, 0, 0);
	}

	// ==================== Player bar (self + other players) ====================

	@ConfigItem(
		keyName = "showForSelf",
		name = "Show for Self",
		description = "Draw the player bar over your own character",
		section = PLAYER_SECTION,
		position = 0
	)
	default boolean showForSelf()
	{
		return false;
	}

	@ConfigItem(
		keyName = "selfDisplayMode",
		name = "Self Display Mode",
		description = "Display mode used for your own bar. Only applies when 'Show for Self' is on.",
		section = PLAYER_SECTION,
		position = 1
	)
	default DisplayMode selfDisplayMode()
	{
		return DisplayMode.NUMBER;
	}

	@ConfigItem(
		keyName = "showForPlayers",
		name = "Show for Other Players",
		description = "Draw the player bar over other players",
		section = PLAYER_SECTION,
		position = 2
	)
	default boolean showForPlayers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerDisplayMode",
		name = "Other Players' Display Mode",
		description = "Display mode for other players' bars (requires 'Show for Other Players'). Always shows " +
			"percent regardless - other players' max HP isn't available client-side.",
		section = PLAYER_SECTION,
		position = 3
	)
	default DisplayMode playerDisplayMode()
	{
		return DisplayMode.NUMBER;
	}

	@ConfigItem(
		keyName = "playerBarWidth",
		name = "Bar Width",
		description = "Width of the bar in pixels (at the zoom level the plugin started at, if scaling with zoom)",
		section = PLAYER_SECTION,
		position = 4
	)
	@Range(min = 20, max = 200)
	default int playerBarWidth()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "playerBarHeight",
		name = "Bar Height",
		description = "Height of the bar in pixels (at the zoom level the plugin started at, if scaling with zoom)",
		section = PLAYER_SECTION,
		position = 5
	)
	@Range(min = 4, max = 30)
	default int playerBarHeight()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "playerCornerRadius",
		name = "Corner Radius",
		description = "Rounds the corners of the bar. 0 = sharp corners, matching the native health bar.",
		section = PLAYER_SECTION,
		position = 6
	)
	@Range(min = 0, max = 12)
	default int playerCornerRadius()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerBorderWidth",
		name = "Border Width",
		description = "Thickness of the bar's outline in pixels. 0 = no border.",
		section = PLAYER_SECTION,
		position = 7
	)
	@Range(min = 0, max = 4)
	default int playerBorderWidth()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "playerBorderColor",
		name = "Border Color",
		description = "Color of the bar's outline",
		section = PLAYER_SECTION,
		position = 8
	)
	default Color playerBorderColor()
	{
		return new Color(0, 0, 0, 190);
	}

	@ConfigItem(
		keyName = "playerBarColor",
		name = "Bar Color",
		description = "Fill color of the bar, matching the native health bar's single green fill",
		section = PLAYER_SECTION,
		position = 9
	)
	default Color playerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "playerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of the bar",
		section = PLAYER_SECTION,
		position = 10
	)
	default Color playerBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "playerVerticalOffset",
		name = "Vertical Offset",
		description = "Pixels to shift the bar upward (positive) or downward (negative) from its centered position",
		section = PLAYER_SECTION,
		position = 11
	)
	@Range(min = -50, max = 100)
	default int playerVerticalOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerFontFamily",
		name = "Font",
		description = "Typeface used for the HP text. The RuneScape options use the game's own bundled UI font.",
		section = PLAYER_SECTION,
		position = 12
	)
	default FontFamily playerFontFamily()
	{
		return FontFamily.RUNESCAPE_BOLD;
	}

	@ConfigItem(
		keyName = "playerFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font. Leave Plain for 'RuneScape Bold' - it's already a " +
			"dedicated bold typeface, not a synthetic bolding of the regular one.",
		section = PLAYER_SECTION,
		position = 13
	)
	default FontStyle playerFontStyle()
	{
		return FontStyle.PLAIN;
	}

	@ConfigItem(
		keyName = "playerFontSize",
		name = "Font Size",
		description = "Size of the HP number text (at the zoom level the plugin started at, if scaling with zoom). " +
			"Smaller sizes are harder to read - see Text Outline below.",
		section = PLAYER_SECTION,
		position = 14
	)
	@Range(min = 6, max = 20)
	default int playerFontSize()
	{
		return 7;
	}

	@ConfigItem(
		keyName = "playerTextColor",
		name = "Text Color",
		description = "Color of the HP number",
		section = PLAYER_SECTION,
		position = 15
	)
	default Color playerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "playerTextOutline",
		name = "Text Outline",
		description = "Full outline around the text instead of a single drop shadow - improves readability at " +
			"small sizes or busy backgrounds. Recommended to leave on.",
		section = PLAYER_SECTION,
		position = 16
	)
	default boolean playerTextOutline()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerTextVerticalNudge",
		name = "Text Vertical Nudge",
		description = "Shifts the HP text down (positive) or up (negative) if it looks off-center - text " +
			"rendering varies slightly between fonts.",
		section = PLAYER_SECTION,
		position = 17
	)
	@Range(min = -10, max = 10)
	default int playerTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showPrayerBar",
		name = "Show Prayer Bar",
		description = "Draws a second bar for your Prayer points, anchored beneath your HP bar. Mirrors the " +
			"Player Bar's size/shape/font settings above. Requires 'Show for Self'.",
		section = PLAYER_SECTION,
		position = 18
	)
	default boolean showPrayerBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "selfColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints your bar while poisoned, envenomed, burning, or bleeding. Poison/Venom use your " +
			"character's exact state; Burn/Bleed are inferred from hitsplats instead (no exact signal exists " +
			"for those). Requires 'Show for Self'.",
		section = PLAYER_SECTION,
		position = 19
	)
	default boolean selfColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selfPoisonColor",
		name = "Poison Color",
		description = "Bar fill color while poisoned. Sampled from the actual Poison hitsplat sprite " +
			"(oldschool.runescape.wiki/w/Hitsplat) - pure green, no blue.",
		section = PLAYER_SECTION,
		position = 20
	)
	default Color selfPoisonColor()
	{
		return new Color(0, 145, 0);
	}

	@ConfigItem(
		keyName = "selfVenomColor",
		name = "Venom Color",
		description = "Bar fill color while envenomed. Sampled from the actual Venom hitsplat sprite - a dark " +
			"teal, despite RuneLite's own constant name implying plain dark green.",
		section = PLAYER_SECTION,
		position = 21
	)
	default Color selfVenomColor()
	{
		return new Color(48, 112, 95);
	}

	@ConfigItem(
		keyName = "selfBurnColor",
		name = "Burn Color",
		description = "Bar fill color while burning. Sampled from the actual Burn hitsplat sprite - a " +
			"red-orange gradient.",
		section = PLAYER_SECTION,
		position = 22
	)
	default Color selfBurnColor()
	{
		return new Color(215, 85, 0);
	}

	@ConfigItem(
		keyName = "selfBleedColor",
		name = "Bleed Color",
		description = "Bar fill color while bleeding. Sampled from the actual Bleed hitsplat sprite - a vivid " +
			"red, brighter than a dark blood-red.",
		section = PLAYER_SECTION,
		position = 23
	)
	default Color selfBleedColor()
	{
		return new Color(200, 0, 0);
	}

	// ==================== Shared behavior ====================

	@ConfigItem(
		keyName = "scaleWithZoom",
		name = "Scale With Zoom",
		description = "Grow/shrink bars and text as you zoom in/out, matching how the actor model itself scales.",
		section = BEHAVIOR_SECTION,
		position = 0
	)
	default boolean scaleWithZoom()
	{
		return true;
	}

	@ConfigItem(
		keyName = "persistDuration",
		name = "Persist Duration (seconds)",
		description = "How long a bar keeps showing the last known HP after the native bar fades (0 = hide " +
			"immediately).",
		section = BEHAVIOR_SECTION,
		position = 1
	)
	@Range(min = 0, max = 300)
	default int persistDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "hideNativeBar",
		name = "Hide Native Health Bar",
		description = "Replaces the game's built-in health bar sprites with a transparent one. Applies to " +
			"every actor, not just filtered NPCs - sprite overrides are client-wide, not per-actor.",
		section = BEHAVIOR_SECTION,
		position = 2
	)
	default boolean hideNativeBar()
	{
		return false;
	}

	// ==================== NPC filter ====================

	@ConfigItem(
		keyName = "npcFilter",
		name = "NPC Filter",
		description = "Comma-separated NPC names to hide (a blacklist - everything else still shows). " +
			"Supports wildcards (skele*). Leave blank to show all.",
		section = FILTER_SECTION,
		position = 0
	)
	default String npcFilter()
	{
		return "";
	}

	enum DisplayMode
	{
		NUMBER,
		PERCENT,
		BOTH;

		@Override
		public String toString()
		{
			switch (this)
			{
				case NUMBER:
					return "Number";
				case PERCENT:
					return "Percent";
				case BOTH:
					return "Both";
				default:
					return name();
			}
		}
	}

	enum FontFamily
	{
		RUNESCAPE_BOLD,
		RUNESCAPE,
		RUNESCAPE_SMALL,
		SYSTEM_DEFAULT;

		@Override
		public String toString()
		{
			switch (this)
			{
				case RUNESCAPE_BOLD:
					return "RuneScape Bold";
				case RUNESCAPE:
					return "RuneScape";
				case RUNESCAPE_SMALL:
					return "RuneScape Small";
				case SYSTEM_DEFAULT:
					return "System Default";
				default:
					return name();
			}
		}
	}

	enum FontStyle
	{
		PLAIN(Font.PLAIN),
		BOLD(Font.BOLD),
		ITALIC(Font.ITALIC),
		BOLD_ITALIC(Font.BOLD | Font.ITALIC);

		private final int awtStyle;

		FontStyle(int awtStyle)
		{
			this.awtStyle = awtStyle;
		}

		int getAwtStyle()
		{
			return awtStyle;
		}

		@Override
		public String toString()
		{
			switch (this)
			{
				case PLAIN:
					return "Plain";
				case BOLD:
					return "Bold";
				case ITALIC:
					return "Italic";
				case BOLD_ITALIC:
					return "Bold Italic";
				default:
					return name();
			}
		}
	}
}
