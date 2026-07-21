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
		description = "Show HP as a raw number, a percentage, or both. Falls back to percent for NPCs with " +
			"unknown max HP.",
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
		description = "Width of the bar in pixels",
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
		description = "Height of the bar in pixels",
		section = TARGET_SECTION,
		position = 2
	)
	@Range(min = 4, max = 30)
	default int targetBarHeight()
	{
		return 10;
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
		return 2;
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
		description = "Pixels to shift the bar up (positive) or down (negative) from center",
		section = TARGET_SECTION,
		position = 8
	)
	@Range(min = -50, max = 100)
	default int targetVerticalOffset()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "targetFontFamily",
		name = "Font",
		description = "Typeface for the HP text - RuneScape options use the game's own UI font.",
		section = TARGET_SECTION,
		position = 9
	)
	default FontFamily targetFontFamily()
	{
		return FontFamily.SYSTEM_DEFAULT;
	}

	@ConfigItem(
		keyName = "targetFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font. Leave Plain for 'RuneScape Bold' - it's already bold.",
		section = TARGET_SECTION,
		position = 10
	)
	default FontStyle targetFontStyle()
	{
		return FontStyle.BOLD;
	}

	@ConfigItem(
		keyName = "targetFontSize",
		name = "Font Size",
		description = "Size of the HP number text. Smaller sizes are harder to read - see Text Outline below.",
		section = TARGET_SECTION,
		position = 11
	)
	@Range(min = 6, max = 20)
	default int targetFontSize()
	{
		return 11;
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
		description = "Full outline around the text for readability at small sizes. Recommended on.",
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
		description = "Nudges the HP text down (positive) or up (negative) if it looks off-center.",
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
		description = "Draws the NPC's name above its HP bar. Color is set separately below.",
		section = TARGET_SECTION,
		position = 15
	)
	default boolean showNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alwaysShowNpcName",
		name = "Always Show NPC Name",
		description = "Shows the NPC name at all times, not just in combat. Requires 'Show NPC Name' above.",
		section = TARGET_SECTION,
		position = 16
	)
	default boolean alwaysShowNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlyShowCombatNpcNames",
		name = "Only Show Combat NPC Names",
		description = "Excludes non-attackable NPCs (bankers, shop owners, fishing spots, pets) from bars and names.",
		section = TARGET_SECTION,
		position = 17
	)
	default boolean onlyShowCombatNpcNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC Name Color",
		description = "Color of the NPC name text, independent of the HP number's Text Color above.",
		section = TARGET_SECTION,
		position = 18
	)
	default Color npcNameColor()
	{
		return new Color(255, 255, 0);
	}

	@ConfigItem(
		keyName = "targetColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints the bar while poisoned, envenomed, burning, or bleeding.",
		section = TARGET_SECTION,
		position = 19
	)
	default boolean targetColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetShowStatusIcon",
		name = "Show Status Icon",
		description = "Shows a debuff icon beneath the bar while poisoned, envenomed, or burning.",
		section = TARGET_SECTION,
		position = 20
	)
	default boolean targetShowStatusIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetPoisonColor",
		name = "Poison Color",
		description = "Bar fill color while poisoned.",
		section = TARGET_SECTION,
		position = 21
	)
	default Color targetPoisonColor()
	{
		return new Color(0, 176, 0);
	}

	@ConfigItem(
		keyName = "targetVenomColor",
		name = "Venom Color",
		description = "Bar fill color while envenomed - a dark teal, not plain green.",
		section = TARGET_SECTION,
		position = 22
	)
	default Color targetVenomColor()
	{
		return new Color(48, 112, 95);
	}

	@ConfigItem(
		keyName = "targetBurnColor",
		name = "Burn Color",
		description = "Bar fill color while burning.",
		section = TARGET_SECTION,
		position = 23
	)
	default Color targetBurnColor()
	{
		return new Color(215, 85, 0);
	}

	@ConfigItem(
		keyName = "targetBleedColor",
		name = "Bleed Color",
		description = "Bar fill color while bleeding.",
		section = TARGET_SECTION,
		position = 24
	)
	default Color targetBleedColor()
	{
		return new Color(200, 0, 0);
	}

	@ConfigItem(
		keyName = "targetPersistDuration",
		name = "Persist Duration (seconds)",
		description = "How long an NPC's bar keeps showing the last known HP after the native bar fades " +
			"(0 = hide immediately).",
		section = TARGET_SECTION,
		position = 25
	)
	@Range(min = 0, max = 300)
	default int targetPersistDuration()
	{
		return 5;
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
		return true;
	}

	@ConfigItem(
		keyName = "selfDisplayMode",
		name = "Self Display Mode",
		description = "Display mode for your own bar. Requires 'Show for Self'.",
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
		description = "Display mode for other players' bars (always percent - their max HP isn't available). " +
			"Requires 'Show for Other Players'.",
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
		description = "Width of the bar in pixels",
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
		description = "Height of the bar in pixels",
		section = PLAYER_SECTION,
		position = 5
	)
	@Range(min = 4, max = 30)
	default int playerBarHeight()
	{
		return 10;
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
		return 2;
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
		description = "Pixels to shift the bar up (positive) or down (negative) from center",
		section = PLAYER_SECTION,
		position = 11
	)
	@Range(min = -50, max = 100)
	default int playerVerticalOffset()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "playerFontFamily",
		name = "Font",
		description = "Typeface for the HP text - RuneScape options use the game's own UI font.",
		section = PLAYER_SECTION,
		position = 12
	)
	default FontFamily playerFontFamily()
	{
		return FontFamily.SYSTEM_DEFAULT;
	}

	@ConfigItem(
		keyName = "playerFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font. Leave Plain for 'RuneScape Bold' - it's already bold.",
		section = PLAYER_SECTION,
		position = 13
	)
	default FontStyle playerFontStyle()
	{
		return FontStyle.BOLD;
	}

	@ConfigItem(
		keyName = "playerFontSize",
		name = "Font Size",
		description = "Size of the HP number text. Smaller sizes are harder to read - see Text Outline below.",
		section = PLAYER_SECTION,
		position = 14
	)
	@Range(min = 6, max = 20)
	default int playerFontSize()
	{
		return 11;
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
		description = "Full outline around the text for readability at small sizes. Recommended on.",
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
		description = "Nudges the HP text down (positive) or up (negative) if it looks off-center.",
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
		description = "Draws a second bar for your Prayer points beneath your HP bar. Requires 'Show for Self'.",
		section = PLAYER_SECTION,
		position = 18
	)
	default boolean showPrayerBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selfColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints your bar while poisoned, envenomed, burning, or bleeding. Requires 'Show for Self'.",
		section = PLAYER_SECTION,
		position = 19
	)
	default boolean selfColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selfShowStatusIcon",
		name = "Show Status Icon",
		description = "Shows a debuff icon beneath your bar while poisoned, envenomed, or burning. Requires " +
			"'Show for Self'.",
		section = PLAYER_SECTION,
		position = 20
	)
	default boolean selfShowStatusIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selfPoisonColor",
		name = "Poison Color",
		description = "Bar fill color while poisoned.",
		section = PLAYER_SECTION,
		position = 21
	)
	default Color selfPoisonColor()
	{
		return new Color(0, 145, 0);
	}

	@ConfigItem(
		keyName = "selfVenomColor",
		name = "Venom Color",
		description = "Bar fill color while envenomed - a dark teal, not plain green.",
		section = PLAYER_SECTION,
		position = 22
	)
	default Color selfVenomColor()
	{
		return new Color(48, 112, 95);
	}

	@ConfigItem(
		keyName = "selfBurnColor",
		name = "Burn Color",
		description = "Bar fill color while burning.",
		section = PLAYER_SECTION,
		position = 23
	)
	default Color selfBurnColor()
	{
		return new Color(215, 85, 0);
	}

	@ConfigItem(
		keyName = "selfBleedColor",
		name = "Bleed Color",
		description = "Bar fill color while bleeding.",
		section = PLAYER_SECTION,
		position = 24
	)
	default Color selfBleedColor()
	{
		return new Color(200, 0, 0);
	}

	@ConfigItem(
		keyName = "playerPersistDuration",
		name = "Persist Duration (seconds)",
		description = "How long a player's bar keeps showing the last known HP after the native bar fades " +
			"(0 = hide immediately).",
		section = PLAYER_SECTION,
		position = 25
	)
	@Range(min = 0, max = 300)
	default int playerPersistDuration()
	{
		return 5;
	}

	// ==================== Shared behavior ====================

	@ConfigItem(
		keyName = "scaleWithZoom",
		name = "Scale With Zoom",
		description = "Grow/shrink bars and text with camera zoom. Sizes below are exact at the zoom level " +
			"you're at when the plugin starts, and scale relative to that as you zoom in/out.",
		section = BEHAVIOR_SECTION,
		position = 0
	)
	default boolean scaleWithZoom()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideNativeBar",
		name = "Hide Native Health Bar",
		description = "Hides the game's built-in health bar for every actor, not just filtered NPCs.",
		section = BEHAVIOR_SECTION,
		position = 1
	)
	default boolean hideNativeBar()
	{
		return true;
	}

	// ==================== NPC filter ====================

	@ConfigItem(
		keyName = "npcFilter",
		name = "NPC Filter",
		description = "Comma-separated NPC names to hide (blacklist, supports skele* wildcards). Leave blank " +
			"to show all.",
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
