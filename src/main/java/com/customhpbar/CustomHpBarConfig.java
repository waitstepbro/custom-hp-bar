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
		name = "Target Bar — Style",
		description = "Size, shape, color, and text settings for the bar drawn over NPCs",
		position = 0
	)
	String TARGET_SECTION = "target";

	@ConfigSection(
		name = "Target Bar — NPC Info",
		description = "NPC names, aggression, status effects, and which NPCs get a bar",
		position = 1
	)
	String TARGET_NPC_SECTION = "targetNpc";

	@ConfigSection(
		name = "Player Bar — Style",
		description = "Whether to show, and size/shape/color/text settings for, the bar drawn over players",
		position = 2
	)
	String PLAYER_SECTION = "player";

	@ConfigSection(
		name = "Player Bar — Player Info",
		description = "Prayer bar, status effects, restore previews, and the overhead icon",
		position = 3
	)
	String PLAYER_INFO_SECTION = "playerInfo";

	@ConfigSection(
		name = "Behavior",
		description = "Settings shared by both bar types",
		position = 4
	)
	String BEHAVIOR_SECTION = "behavior";

	// ==================== Target bar style ====================

	@ConfigItem(
		keyName = "targetDisplayMode",
		name = "Display Mode",
		description = "Show HP as a raw number, a percentage, or both.",
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
		description = "Rounds the corners of the bar. 0 = sharp corners.",
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
		description = "Fill color of the bar, and the full-HP color when HP Color Gradient is on.",
		section = TARGET_SECTION,
		position = 6
	)
	default Color targetBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "targetHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends the bar's fill color as HP drops. Off keeps Bar Color at all HP levels.",
		section = TARGET_SECTION,
		position = 7
	)
	default boolean targetHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetColorMid",
		name = "Mid HP Color",
		description = "Color reached at the midpoint, blended toward from both sides. Requires HP Color Gradient.",
		section = TARGET_SECTION,
		position = 8
	)
	default Color targetColorMid()
	{
		return new Color(180, 180, 0);
	}

	@ConfigItem(
		keyName = "targetMidpoint",
		name = "Midpoint",
		description = "HP percentage at which the bar is exactly Mid HP Color.",
		section = TARGET_SECTION,
		position = 9
	)
	@Range(min = 0, max = 100)
	default int targetMidpoint()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "targetColorLow",
		name = "Low HP Color",
		description = "Color reached at 0% HP. Requires HP Color Gradient.",
		section = TARGET_SECTION,
		position = 10
	)
	default Color targetColorLow()
	{
		return new Color(180, 0, 0);
	}

	@ConfigItem(
		keyName = "targetBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of the bar",
		section = TARGET_SECTION,
		position = 11
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
		position = 12
	)
	@Range(min = -50, max = 100)
	default int targetVerticalOffset()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "targetFontFamily",
		name = "Font",
		description = "Typeface for the HP text.",
		section = TARGET_SECTION,
		position = 13
	)
	default FontFamily targetFontFamily()
	{
		return FontFamily.SYSTEM_DEFAULT;
	}

	@ConfigItem(
		keyName = "targetFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font.",
		section = TARGET_SECTION,
		position = 14
	)
	default FontStyle targetFontStyle()
	{
		return FontStyle.BOLD;
	}

	@ConfigItem(
		keyName = "targetFontSize",
		name = "Font Size",
		description = "Size of the HP number text.",
		section = TARGET_SECTION,
		position = 15
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
		position = 16
	)
	default Color targetTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "targetTextOutline",
		name = "Text Outline",
		description = "Full outline around the text for readability at small sizes.",
		section = TARGET_SECTION,
		position = 17
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
		position = 18
	)
	@Range(min = -10, max = 10)
	default int targetTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetHpTextSpacing",
		name = "HP Text Spacing",
		description = "Pushes the HP number and percentage apart, up to the width of the bar. Requires a " +
			"Display Mode of 'Both'.",
		section = TARGET_SECTION,
		position = 19
	)
	@Range(min = 0, max = 200)
	default int targetHpTextSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of the bar's background, fill, and border. 100 = fully opaque; " +
			"the HP text itself is unaffected.",
		section = TARGET_SECTION,
		position = 20
	)
	@Range(min = 0, max = 100)
	default int targetBarOpacity()
	{
		return 100;
	}

	// ==================== Target bar NPC info ====================

	@ConfigItem(
		keyName = "showNpcName",
		name = "Show NPC Name",
		description = "Draws the NPC's name above its HP bar.",
		section = TARGET_NPC_SECTION,
		position = 0
	)
	default boolean showNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alwaysShowNpcName",
		name = "Always Show NPC Name",
		description = "Shows the NPC name at all times, not just in combat. Requires 'Show NPC Name'.",
		section = TARGET_NPC_SECTION,
		position = 1
	)
	default boolean alwaysShowNpcName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcCombatLevel",
		name = "Show Combat Level",
		description = "Appends the NPC's combat level to its name. Requires 'Show NPC Name'.",
		section = TARGET_NPC_SECTION,
		position = 2
	)
	default boolean showNpcCombatLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "truncateNpcNames",
		name = "Truncate Long NPC Names",
		description = "Shortens NPC names past a character limit and appends a period. Requires " +
			"'Show NPC Name'.",
		section = TARGET_NPC_SECTION,
		position = 3
	)
	default boolean truncateNpcNames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "npcNameMaxLength",
		name = "NPC Name Length Limit",
		description = "Characters to keep before the period. Requires 'Truncate Long NPC Names'.",
		section = TARGET_NPC_SECTION,
		position = 4
	)
	@Range(min = 1, max = 50)
	default int npcNameMaxLength()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "alwaysShowNpcBar",
		name = "Always Show NPC Bar",
		description = "Shows the HP bar on every attackable NPC, not just once engaged.",
		section = TARGET_NPC_SECTION,
		position = 5
	)
	default boolean alwaysShowNpcBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyShowCombatNpcNames",
		name = "Only Show Combat NPC Names",
		description = "Excludes non-attackable NPCs from bars and names.",
		section = TARGET_NPC_SECTION,
		position = 6
	)
	default boolean onlyShowCombatNpcNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC Name Color",
		description = "Color of the NPC name text, separate from the HP number's color.",
		section = TARGET_NPC_SECTION,
		position = 7
	)
	default Color npcNameColor()
	{
		return new Color(255, 255, 0);
	}

	@ConfigItem(
		keyName = "colorAggressiveNpcNames",
		name = "Color Aggressive NPC Names",
		description = "Colors an NPC's name while it's aggressive toward you, reverting once the tolerance " +
			"timer expires.",
		section = TARGET_NPC_SECTION,
		position = 8
	)
	default boolean colorAggressiveNpcNames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showAggressiveNpcIcon",
		name = "Show Aggressive NPC Icon",
		description = "Shows an icon next to an NPC's bar while it's aggressive toward you.",
		section = TARGET_NPC_SECTION,
		position = 9
	)
	default boolean showAggressiveNpcIcon()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colorAggressiveNpcBars",
		name = "Color Aggressive NPC Bars",
		description = "Fills an NPC's bar with the aggressive color while it's aggressive toward you. " +
			"A status effect tint takes precedence.",
		section = TARGET_NPC_SECTION,
		position = 10
	)
	default boolean colorAggressiveNpcBars()
	{
		return false;
	}

	// keyName stays "aggressiveNpcNameColor" so saved profiles carry over - see CLAUDE.md.
	@ConfigItem(
		keyName = "aggressiveNpcNameColor",
		name = "Aggressive NPC Color",
		description = "Shared color for the name and bar of an NPC that's currently aggressive toward " +
			"you. Applies to whichever of the options above are on.",
		section = TARGET_NPC_SECTION,
		position = 11
	)
	default Color aggressiveNpcColor()
	{
		return new Color(255, 0, 0);
	}

	@ConfigItem(
		keyName = "targetColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints the bar while poisoned, envenomed, burning, diseased, or corrupted.",
		section = TARGET_NPC_SECTION,
		position = 12
	)
	default boolean targetColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetShowStatusIcon",
		name = "Show Status Icon",
		description = "Shows a debuff icon beneath the bar while poisoned, envenomed, burning, diseased, " +
			"or corrupted.",
		section = TARGET_NPC_SECTION,
		position = 13
	)
	default boolean targetShowStatusIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetPersistDuration",
		name = "Persist Duration (seconds)",
		description = "How long an NPC's bar keeps showing the last known HP after the native bar fades " +
			"(0 = hide immediately).",
		section = TARGET_NPC_SECTION,
		position = 14
	)
	@Range(min = 0, max = 300)
	default int targetPersistDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "greyOutOtherPlayerDamage",
		name = "Grey Out Health Bars",
		description = "Greys out an NPC's bar once another player damages it, so an Ironman can tell the kill " +
			"isn't exclusively theirs. Ironman accounts only; bosses with shared or personal loot are exempt.",
		section = TARGET_NPC_SECTION,
		position = 15
	)
	default boolean greyOutOtherPlayerDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "greyOutOtherPlayerDamageNames",
		name = "Grey Out Names",
		description = "Greys out an NPC's name once another player damages it, on the same terms as 'Grey Out " +
			"Health Bars'. Independent of that setting, and overrides the aggressive name color.",
		section = TARGET_NPC_SECTION,
		position = 16
	)
	default boolean greyOutOtherPlayerDamageNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcFilter",
		name = "NPC Filter",
		description = "Comma-separated NPC names to hide. Supports * wildcards; leave blank to show all.",
		section = TARGET_NPC_SECTION,
		position = 17
	)
	default String npcFilter()
	{
		return "";
	}

	// ==================== Player bar style (self + other players) ====================

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
		description = "Display mode for other players' bars. Requires 'Show for Other Players'.",
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
		description = "Rounds the corners of the bar. 0 = sharp corners.",
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
		description = "Fill color of the bar, and the full-HP color when HP Color Gradient is on.",
		section = PLAYER_SECTION,
		position = 9
	)
	default Color playerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "playerHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends the bar's fill color as HP drops. Off keeps Bar Color at all HP levels.",
		section = PLAYER_SECTION,
		position = 10
	)
	default boolean playerHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerColorMid",
		name = "Mid HP Color",
		description = "Color reached at the midpoint, blended toward from both sides. Requires HP Color Gradient.",
		section = PLAYER_SECTION,
		position = 11
	)
	default Color playerColorMid()
	{
		return new Color(180, 180, 0);
	}

	@ConfigItem(
		keyName = "playerMidpoint",
		name = "Midpoint",
		description = "HP percentage at which the bar is exactly Mid HP Color.",
		section = PLAYER_SECTION,
		position = 12
	)
	@Range(min = 1, max = 99)
	default int playerMidpoint()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "playerColorLow",
		name = "Low HP Color",
		description = "Color reached at 0% HP. Requires HP Color Gradient.",
		section = PLAYER_SECTION,
		position = 13
	)
	default Color playerColorLow()
	{
		return new Color(180, 0, 0);
	}

	@ConfigItem(
		keyName = "playerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of the bar",
		section = PLAYER_SECTION,
		position = 14
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
		position = 15
	)
	@Range(min = -50, max = 100)
	default int playerVerticalOffset()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "playerFontFamily",
		name = "Font",
		description = "Typeface for the HP text.",
		section = PLAYER_SECTION,
		position = 16
	)
	default FontFamily playerFontFamily()
	{
		return FontFamily.SYSTEM_DEFAULT;
	}

	@ConfigItem(
		keyName = "playerFontStyle",
		name = "Font Style",
		description = "Applied on top of the chosen font.",
		section = PLAYER_SECTION,
		position = 17
	)
	default FontStyle playerFontStyle()
	{
		return FontStyle.BOLD;
	}

	@ConfigItem(
		keyName = "playerFontSize",
		name = "Font Size",
		description = "Size of the HP number text.",
		section = PLAYER_SECTION,
		position = 18
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
		position = 19
	)
	default Color playerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "playerTextOutline",
		name = "Text Outline",
		description = "Full outline around the text for readability at small sizes.",
		section = PLAYER_SECTION,
		position = 20
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
		position = 21
	)
	@Range(min = -10, max = 10)
	default int playerTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerHpTextSpacing",
		name = "HP Text Spacing",
		description = "Pushes the HP number and percentage apart, up to the width of the bar. Requires a " +
			"Display Mode of 'Both'.",
		section = PLAYER_SECTION,
		position = 22
	)
	@Range(min = 0, max = 200)
	default int playerHpTextSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of the bar's background, fill, and border, plus the Prayer " +
			"and special attack bars. 100 = fully opaque.",
		section = PLAYER_SECTION,
		position = 23
	)
	@Range(min = 0, max = 100)
	default int playerBarOpacity()
	{
		return 100;
	}

	// ==================== Player bar player info ====================

	@ConfigItem(
		keyName = "showPrayerBar",
		name = "Show Prayer Bar",
		description = "Draws a Prayer points bar beneath your HP bar, or on its own outside combat. " +
			"Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 0
	)
	default boolean showPrayerBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hidePrayerBarWhenInactive",
		name = "Hide Prayer Bar While Not Praying",
		description = "Only draws the Prayer bar while a prayer is active. Flicking keeps it up. " +
			"Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 1
	)
	default boolean hidePrayerBarWhenInactive()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prayerBarColor",
		name = "Prayer Bar Color",
		description = "Fill color of the Prayer bar. Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 2
	)
	default Color prayerBarColor()
	{
		return new Color(60, 130, 220);
	}

	@ConfigItem(
		keyName = "showSpecialAttackBar",
		name = "Show Special Attack Bar",
		description = "Draws a special attack energy bar alongside your HP bar, in combat only. " +
			"Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 3
	)
	default boolean showSpecialAttackBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "specialAttackBarColor",
		name = "Special Attack Bar Color",
		description = "Fill color of the special attack bar. Requires 'Show Special Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 4
	)
	default Color specialAttackBarColor()
	{
		return new Color(3, 153, 0);
	}

	@ConfigItem(
		keyName = "showRunEnergyBar",
		name = "Show Run Energy Bar",
		description = "Draws a run energy bar alongside your HP bar. Unlike Prayer/Special, shows " +
			"regardless of combat state. Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 5
	)
	default boolean showRunEnergyBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "runEnergyBarTimeout",
		name = "Run Energy Bar Timeout (seconds)",
		description = "Hides the run energy bar this many seconds after you last actively ran (regen and " +
			"item restores don't count). 0 = never time out. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 6
	)
	@Range(min = 0, max = 300)
	default int runEnergyBarTimeout()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "runEnergyBarColor",
		name = "Run Energy Bar Color",
		description = "Fill color of the run energy bar. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 7
	)
	default Color runEnergyBarColor()
	{
		return new Color(199, 174, 0);
	}

	@ConfigItem(
		keyName = "runEnergyStaminaColor",
		name = "Run Energy Bar Color (Stamina Active)",
		description = "Fill color of the run energy bar while a Stamina potion's drain-reduction effect " +
			"is active. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 8
	)
	default Color runEnergyStaminaColor()
	{
		return new Color(160, 124, 72);
	}

	@ConfigItem(
		keyName = "barPosition1",
		name = "Bar 1 (Top)",
		description = "Which bar is drawn first (topmost) in your stack. If a bar is picked in more than " +
			"one position, only its topmost pick is shown.",
		section = PLAYER_INFO_SECTION,
		position = 9
	)
	default BarKind barPosition1()
	{
		return BarKind.HP;
	}

	@ConfigItem(
		keyName = "barPosition2",
		name = "Bar 2",
		description = "Which bar is drawn second in your stack. See 'Bar 1 (Top)'.",
		section = PLAYER_INFO_SECTION,
		position = 10
	)
	default BarKind barPosition2()
	{
		return BarKind.PRAYER;
	}

	@ConfigItem(
		keyName = "barPosition3",
		name = "Bar 3",
		description = "Which bar is drawn third in your stack. See 'Bar 1 (Top)'.",
		section = PLAYER_INFO_SECTION,
		position = 11
	)
	default BarKind barPosition3()
	{
		return BarKind.SPECIAL;
	}

	@ConfigItem(
		keyName = "barPosition4",
		name = "Bar 4 (Bottom)",
		description = "Which bar is drawn fourth (bottommost) in your stack. See 'Bar 1 (Top)'.",
		section = PLAYER_INFO_SECTION,
		position = 12
	)
	default BarKind barPosition4()
	{
		return BarKind.RUN;
	}

	@ConfigItem(
		keyName = "selfColorByStatusEffect",
		name = "Color By Status Effect",
		description = "Tints a player's bar while poisoned, envenomed, burning, bleeding, diseased, or corrupted.",
		section = PLAYER_INFO_SECTION,
		position = 13
	)
	default boolean selfColorByStatusEffect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selfShowStatusIcon",
		name = "Show Status Icon",
		description = "Shows a debuff icon beneath a player's bar while poisoned, envenomed, burning, " +
			"bleeding, diseased, or corrupted.",
		section = PLAYER_INFO_SECTION,
		position = 14
	)
	default boolean selfShowStatusIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerPersistDuration",
		name = "Persist Duration (seconds)",
		description = "How long a player's bar keeps showing the last known HP after the native bar fades " +
			"(0 = hide immediately).",
		section = PLAYER_INFO_SECTION,
		position = 15
	)
	@Range(min = 0, max = 300)
	default int playerPersistDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "showFoodHealPreview",
		name = "Show Food Heal Preview",
		description = "Previews HP restored by a hovered food/potion as an extra bar segment. Requires " +
			"'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 16
	)
	default boolean showFoodHealPreview()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerRestorePreview",
		name = "Show Prayer Restore Preview",
		description = "Previews Prayer points restored by a hovered item as an extra bar segment. Requires " +
			"'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 17
	)
	default boolean showPrayerRestorePreview()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRunEnergyRestorePreview",
		name = "Show Run Energy Restore Preview",
		description = "Previews run energy restored by a hovered item (e.g. Stamina potion) as an extra " +
			"bar segment. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 18
	)
	default boolean showRunEnergyRestorePreview()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceOverheadIcon",
		name = "Replace Overhead Icon",
		description = "Replaces your native overhead icon, hitsplats, and chat text with a redrawn copy " +
			"positioned above your HP bar. Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 19
	)
	default boolean replaceOverheadIcon()
	{
		return true;
	}

	// ==================== Shared behavior ====================

	@ConfigItem(
		keyName = "scaleWithZoom",
		name = "Scale With Zoom",
		description = "Grows and shrinks bars and text with camera zoom.",
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

	/**
	 * One bar in the local player's vertical stack. Only the player stacks - NPCs only ever get HP.
	 * Picked per position by the four barPositionN dropdowns above; see CustomHpBarOverlay's
	 * playerBarStack() for how duplicate/missing picks are resolved.
	 */
	enum BarKind
	{
		HP("HP"),
		PRAYER("Prayer"),
		SPECIAL("Special"),
		RUN("Run Energy");

		private final String label;

		BarKind(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

}
