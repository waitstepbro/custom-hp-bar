package com.customhpbar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.Color;
import java.awt.Font;

@ConfigGroup("customhpbar")
public interface CustomHpBarConfig extends Config
{
	@ConfigSection(
		name = "NPC Bar — Style",
		description = "Size, shape, color, and text settings for the bar drawn over NPCs",
		position = 0,
		closedByDefault = true
	)
	String TARGET_SECTION = "target";

	@ConfigSection(
		name = "NPC Bar — Info",
		description = "NPC names, aggression, status effects, and which NPCs get a bar",
		position = 1,
		closedByDefault = true
	)
	String TARGET_NPC_SECTION = "targetNpc";

	@ConfigSection(
		name = "Player Bar — Style",
		description = "Your own bar's size, shape, color, and text - the size, shape, and font here " +
			"apply to other players too",
		position = 2,
		closedByDefault = true
	)
	String PLAYER_SECTION = "player";

	@ConfigSection(
		name = "Player Bar — Info",
		description = "The Prayer, special attack, and run energy bars, and how your stack is ordered",
		position = 3,
		closedByDefault = true
	)
	String PLAYER_INFO_SECTION = "playerInfo";

	@ConfigSection(
		name = "Other Player Bar — Style",
		description = "Color, opacity, and offset for other players' bars, independent of your own",
		position = 4,
		closedByDefault = true
	)
	String OTHER_PLAYER_SECTION = "otherPlayer";

	@ConfigSection(
		name = "Other Player Bar — Info",
		description = "Names and same-tile stacking behavior specific to other players' bars",
		position = 5,
		closedByDefault = true
	)
	String OTHER_PLAYER_INFO_SECTION = "otherPlayerInfo";

	@ConfigSection(
		name = "Behavior & Hotkeys",
		description = "Settings shared by every bar, and keybinds to show or hide names and bars instantly",
		position = 6,
		closedByDefault = true
	)
	String BEHAVIOR_SECTION = "behavior";

	// ==================== Target bar style ====================

	@ConfigItem(
		keyName = "targetDisplayMode",
		name = "Display Mode",
		description = "Show HP as a raw number, a percentage, both, or neither (bar only, no text).",
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
		keyName = "targetVerticalOffset",
		name = "Vertical Offset",
		description = "Pixels to shift the bar up (positive) or down (negative) from center",
		section = TARGET_SECTION,
		position = 5
	)
	@Range(min = -50, max = 100)
	default int targetVerticalOffset()
	{
		return 5;
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
		description = "Blends an NPC's bar from the bar color through yellow to red as HP drops, instead of one flat color.",
		section = TARGET_SECTION,
		position = 7
	)
	default boolean targetHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetBorderColor",
		name = "Border Color",
		description = "Color of the bar's outline",
		section = TARGET_SECTION,
		position = 8
	)
	default Color targetBorderColor()
	{
		return new Color(0, 0, 0, 190);
	}

	@ConfigItem(
		keyName = "targetBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of the bar",
		section = TARGET_SECTION,
		position = 9
	)
	default Color targetBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "targetBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of the bar's background, fill, and border. 100 = fully opaque; " +
			"the HP text itself is unaffected.",
		section = TARGET_SECTION,
		position = 10
	)
	@Range(min = 0, max = 100)
	default int targetBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "targetFontFamily",
		name = "Font",
		description = "Typeface for the HP text.",
		section = TARGET_SECTION,
		position = 11
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
		position = 12
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
		position = 13
	)
	@Range(min = 6, max = 20)
	default int targetFontSize()
	{
		return 11;
	}

	@ConfigItem(
		keyName = "targetTextColor",
		name = "HP Text Color",
		description = "Color of the HP number",
		section = TARGET_SECTION,
		position = 14
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
		position = 15
	)
	default boolean targetTextOutline()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetTextAlignment",
		name = "Text Alignment",
		description = "Where the HP text sits horizontally within the bar.",
		section = TARGET_SECTION,
		position = 16
	)
	default TextAlignment targetTextAlignment()
	{
		return TextAlignment.CENTER;
	}

	@ConfigItem(
		keyName = "targetHpTextSpacing",
		name = "HP Text Spacing",
		description = "Pushes the HP number and percentage apart, up to the width of the bar. Requires a " +
			"Display Mode of 'Both'.",
		section = TARGET_SECTION,
		position = 17
	)
	@Range(min = 0, max = 200)
	default int targetHpTextSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "targetTextVerticalNudge",
		name = "Text Nudge",
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
		keyName = "targetDamageTrail",
		name = "Damage Trail",
		description = "Whether a darker trail follows damage down the bar, and whether it matches the bar color or uses its own.",
		section = TARGET_SECTION,
		position = 19
	)
	default DamageTrailMode targetDamageTrail()
	{
		return DamageTrailMode.OFF;
	}

	@ConfigItem(
		keyName = "targetDamageTrailColor",
		name = "Trail Color",
		description = "Color of the health an NPC just lost. Requires a Damage Trail of 'Custom color'.",
		section = TARGET_SECTION,
		position = 20
	)
	default Color targetDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	// ==================== Target bar NPC info ====================

	@ConfigItem(
		keyName = "showNpcName",
		name = "NPC Name",
		description = "Whether NPCs get a name label: never, only while in combat with you, or always.",
		section = TARGET_NPC_SECTION,
		position = 0
	)
	default Visibility showNpcName()
	{
		return Visibility.ALWAYS;
	}

	@ConfigItem(
		keyName = "showNpcCombatLevel",
		name = "Combat Level",
		description = "Appends the NPC's combat level to its name. Requires 'NPC Name'.",
		section = TARGET_NPC_SECTION,
		position = 1
	)
	default boolean showNpcCombatLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyShowCombatNpcNames",
		name = "Combat NPCs Only",
		description = "Hides NPCs that have no combat level and no Attack option, like bankers and shop keepers.",
		section = TARGET_NPC_SECTION,
		position = 2
	)
	default boolean onlyShowCombatNpcNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcNameMaxLength",
		name = "Name Length Limit",
		description = "Shortens NPC names past this many characters and appends a period. 0 = no limit.",
		section = TARGET_NPC_SECTION,
		position = 3
	)
	@Range(min = 0, max = 50)
	default int npcNameMaxLength()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "Name Color",
		description = "Color of the NPC name text, separate from the HP number's color.",
		section = TARGET_NPC_SECTION,
		position = 4
	)
	default Color npcNameColor()
	{
		return new Color(255, 255, 0);
	}

	@ConfigItem(
		keyName = "alwaysShowNpcBar",
		name = "Always Show Bar",
		description = "Shows the HP bar on every attackable NPC, not just once engaged.",
		section = TARGET_NPC_SECTION,
		position = 5
	)
	default boolean alwaysShowNpcBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fadeNpcBarOnDeath",
		name = "Death Fade",
		description = "Fades an NPC's bar and name out when it dies instead of hiding them the instant " +
			"the killing blow lands.",
		section = TARGET_NPC_SECTION,
		position = 6
	)
	default boolean fadeNpcBarOnDeath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetPersistDuration",
		name = "Persist Duration",
		description = "How long in seconds an NPC's bar keeps showing the last known HP after the " +
			"native bar fades (0 = hide immediately).",
		section = TARGET_NPC_SECTION,
		position = 7
	)
	@Range(min = 0, max = 300)
	default int targetPersistDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "npcStackLimit",
		name = "Stack Limit",
		description = "Caps how many NPCs (bar and/or name) render on the same tile at once - which " +
			"ones is arbitrary, not distance-based. 0 = unlimited.",
		section = TARGET_NPC_SECTION,
		position = 8
	)
	@Range(min = 0, max = 30)
	default int npcStackLimit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "colorAggressiveNpcNames",
		name = "Color Aggressive NPCs",
		description = "Colors an NPC's name, bar, both or neither while it is aggressive toward you, reverting once the tolerance timer expires.",
		section = TARGET_NPC_SECTION,
		position = 9
	)
	default AggressiveHighlight colorAggressiveNpcNames()
	{
		return AggressiveHighlight.OFF;
	}

	@ConfigItem(
		keyName = "showAggressiveNpcIcon",
		name = "Aggressive Icon",
		description = "Shows an icon next to an NPC's bar while it's aggressive toward you.",
		section = TARGET_NPC_SECTION,
		position = 10
	)
	default boolean showAggressiveNpcIcon()
	{
		return false;
	}

	// keyName stays "aggressiveNpcNameColor" so saved profiles carry over - see CLAUDE.md.
	@ConfigItem(
		keyName = "aggressiveNpcNameColor",
		name = "Aggressive Color",
		description = "Shared color for the name and bar of an NPC that's currently aggressive toward " +
			"you. Requires 'Color Aggressive NPCs'.",
		section = TARGET_NPC_SECTION,
		position = 11
	)
	default Color aggressiveNpcColor()
	{
		return new Color(255, 0, 0);
	}

	@ConfigItem(
		keyName = "targetColorByStatusEffect",
		name = "Status Effects",
		description = "How poison, venom, burns, bleeds, disease and corruption show on an NPC's bar: as a tint, an icon, both, or not at all.",
		section = TARGET_NPC_SECTION,
		position = 12
	)
	default StatusEffectMode targetColorByStatusEffect()
	{
		return StatusEffectMode.BOTH;
	}

	@ConfigItem(
		keyName = "greyOutOtherPlayerDamage",
		name = "Grey Out Bars & Names",
		description = "Greys out an NPC's bar and name once another player damages it. Ironman accounts " +
			"only; bosses with shared or personal loot are exempt.",
		section = TARGET_NPC_SECTION,
		position = 13
	)
	default boolean greyOutOtherPlayerDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcWeaknessIcon",
		name = "Weakness Icon",
		description = "Whether an NPC's elemental weakness draws beside its bar, with or without the percentage.",
		section = TARGET_NPC_SECTION,
		position = 14
	)
	default WeaknessMode showNpcWeaknessIcon()
	{
		return WeaknessMode.OFF;
	}

	@ConfigItem(
		keyName = "npcWeaknessPercentColor",
		name = "Percent Color",
		description = "Color of the weakness percentage text. Requires 'Weakness Icon' set to Icon & percent.",
		section = TARGET_NPC_SECTION,
		position = 15
	)
	default Color npcWeaknessPercentColor()
	{
		return new Color(255, 255, 255);
	}

	@ConfigItem(
		keyName = "showNpcShieldBar",
		name = "Shield Bar",
		description = "Shows a shield's remaining strength on the bar while an NPC is shielded. "
			+ "Supports Doom of Mokhaiotl and Kephri.",
		section = TARGET_NPC_SECTION,
		position = 16
	)
	default boolean showNpcShieldBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcChargeBar",
		name = "Charge Bar",
		description = "Shows a second bar beneath an NPC's while it charges a special attack. "
			+ "Supports Doom of Mokhaiotl and Yama's void flares.",
		section = TARGET_NPC_SECTION,
		position = 17
	)
	default boolean showNpcChargeBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcChargeBarWidth",
		name = "Charge Width",
		description = "Width of the charge bar in pixels. 0 matches the NPC bar's width. "
			+ "Requires 'Charge Bar'.",
		section = TARGET_NPC_SECTION,
		position = 18
	)
	@Range(min = 0, max = 200)
	default int npcChargeBarWidth()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "npcChargeBarHeight",
		name = "Charge Height",
		description = "Height of the charge bar in pixels. 0 matches the NPC bar's height. "
			+ "Requires 'Charge Bar'.",
		section = TARGET_NPC_SECTION,
		position = 19
	)
	@Range(min = 0, max = 30)
	default int npcChargeBarHeight()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "npcChargeBarGap",
		name = "Charge Gap",
		description = "Pixels between the NPC's bar and the charge bar beneath it. The charge bar drops "
			+ "further when status icons need the room. Requires 'Charge Bar'.",
		section = TARGET_NPC_SECTION,
		position = 20
	)
	@Range(min = 0, max = 20)
	default int npcChargeBarGap()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "npcFilter",
		name = "Blacklist",
		description = "Comma-separated NPC names to hide. Supports * wildcards; leave blank to show all.",
		section = TARGET_NPC_SECTION,
		position = 21
	)
	default String npcFilter()
	{
		return "";
	}

	// ==================== Player bar style (self + other players) ====================

	@ConfigItem(
		keyName = "showForSelf",
		name = "Show Bar",
		description = "Whether your own bar draws: never, only while you are in combat, or always.",
		section = PLAYER_SECTION,
		position = 0
	)
	default Visibility showForSelf()
	{
		return Visibility.TRACKED;
	}

	@ConfigItem(
		keyName = "selfDisplayMode",
		name = "Display Mode",
		description = "Show your own HP as a raw number, a percentage, both, or neither " +
			"(bar only, no text). Requires 'Show Bar'.",
		section = PLAYER_SECTION,
		position = 1
	)
	default DisplayMode selfDisplayMode()
	{
		return DisplayMode.NUMBER;
	}

	@ConfigItem(
		keyName = "playerBarWidth",
		name = "Bar Width",
		description = "Width of the bar in pixels",
		section = PLAYER_SECTION,
		position = 2
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
		position = 3
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
		position = 4
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
		position = 5
	)
	@Range(min = 0, max = 4)
	default int playerBorderWidth()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "playerVerticalOffset",
		name = "Vertical Offset",
		description = "Pixels to shift your own bar up (positive) or down (negative) from center. " +
			"Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 6
	)
	@Range(min = -50, max = 100)
	default int playerVerticalOffset()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "playerBarColor",
		name = "Bar Color",
		description = "Fill color of your own bar, and the full-HP color when HP Color Gradient is on. " +
			"Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 7
	)
	default Color playerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "playerHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends your own bar from the bar color through yellow to red as HP drops, instead of one flat color.",
		section = PLAYER_SECTION,
		position = 8
	)
	default boolean playerHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerBorderColor",
		name = "Border Color",
		description = "Color of the bar's outline",
		section = PLAYER_SECTION,
		position = 9
	)
	default Color playerBorderColor()
	{
		return new Color(0, 0, 0, 190);
	}

	@ConfigItem(
		keyName = "playerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of your own bar. Other players have their own in " +
			"Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 10
	)
	default Color playerBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "playerBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of your own bar's background, fill, and border. 100 = " +
			"fully opaque. Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 11
	)
	@Range(min = 0, max = 100)
	default int playerBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "playerFontFamily",
		name = "Font",
		description = "Typeface for the HP text.",
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
		description = "Applied on top of the chosen font.",
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
		description = "Size of the HP number text.",
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
		name = "HP Text Color",
		description = "Color of your own HP number. The Prayer, special attack, and run energy numbers " +
			"have their own in Player Bar — Info.",
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
		description = "Full outline around the text for readability at small sizes.",
		section = PLAYER_SECTION,
		position = 16
	)
	default boolean playerTextOutline()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerTextAlignment",
		name = "Text Alignment",
		description = "Where each bar's number sits horizontally within it - HP, Prayer, Special, and Run.",
		section = PLAYER_SECTION,
		position = 17
	)
	default TextAlignment playerTextAlignment()
	{
		return TextAlignment.CENTER;
	}

	@ConfigItem(
		keyName = "playerHpTextSpacing",
		name = "HP Text Spacing",
		description = "Pushes the HP number and percentage apart, up to the width of the bar. Requires a " +
			"Display Mode of 'Both'.",
		section = PLAYER_SECTION,
		position = 18
	)
	@Range(min = 0, max = 200)
	default int playerHpTextSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerTextVerticalNudge",
		name = "Text Nudge",
		description = "Nudges the HP text down (positive) or up (negative) if it looks off-center.",
		section = PLAYER_SECTION,
		position = 19
	)
	@Range(min = -10, max = 10)
	default int playerTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerDamageTrail",
		name = "Damage Trail",
		description = "Whether a darker trail follows damage down the bar, and whether it matches the bar color or uses its own.",
		section = PLAYER_SECTION,
		position = 20
	)
	default DamageTrailMode playerDamageTrail()
	{
		return DamageTrailMode.OFF;
	}

	@ConfigItem(
		keyName = "playerDamageTrailColor",
		name = "Trail Color",
		description = "Color of the health you just lost. Requires a Damage Trail of 'Custom color'.",
		section = PLAYER_SECTION,
		position = 21
	)
	default Color playerDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	// ==================== Player bar player info ====================

	@ConfigItem(
		keyName = "showPrayerBar",
		name = "Prayer Bar",
		description = "Whether the prayer bar draws: never, only while praying, only while you are in combat, or always.",
		section = PLAYER_INFO_SECTION,
		position = 0
	)
	default PrayerBarVisibility showPrayerBar()
	{
		return PrayerBarVisibility.TRACKED;
	}

	@ConfigItem(
		keyName = "prayerBarColor",
		name = "Prayer Fill",
		description = "Fill color of the Prayer bar. Requires 'Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 1
	)
	default Color prayerBarColor()
	{
		return new Color(60, 130, 220);
	}

	@ConfigItem(
		keyName = "prayerTextColor",
		name = "Prayer Text",
		description = "Color of the Prayer number. Requires 'Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 2
	)
	default Color prayerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showPrayerTickTimer",
		name = "Prayer Bar Tick",
		description = "Whether the prayer tick timer draws: never, only while praying, or always.",
		section = PLAYER_INFO_SECTION,
		position = 3
	)
	default PrayerTimerVisibility showPrayerTickTimer()
	{
		return PrayerTimerVisibility.NEVER;
	}

	@ConfigItem(
		keyName = "prayerTickTimerColor",
		name = "Tick Color",
		description = "Color of the tick timer indicator. Requires 'Prayer Bar Tick'.",
		section = PLAYER_INFO_SECTION,
		position = 4
	)
	default Color prayerTickTimerColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showSpecialAttackBar",
		name = "Special Attack Bar",
		description = "Whether the special attack bar draws: never, only while you are in combat, or always.",
		section = PLAYER_INFO_SECTION,
		position = 5
	)
	default Visibility showSpecialAttackBar()
	{
		return Visibility.NEVER;
	}

	@ConfigItem(
		keyName = "specialAttackBarColor",
		name = "Special Attack Fill",
		description = "Fill color of the special attack bar. Requires 'Special Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 6
	)
	default Color specialAttackBarColor()
	{
		return new Color(3, 153, 0);
	}

	@ConfigItem(
		keyName = "specialAttackTextColor",
		name = "Special Attack Text",
		description = "Color of the special attack number. Requires 'Special Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 7
	)
	default Color specialAttackTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showRunEnergyBar",
		name = "Run Energy Bar",
		description = "Whether the run energy bar draws: never, only while it is draining or recently drained, or always.",
		section = PLAYER_INFO_SECTION,
		position = 8
	)
	default RunBarVisibility showRunEnergyBar()
	{
		return RunBarVisibility.NEVER;
	}

	@ConfigItem(
		keyName = "runEnergyBarTimeout",
		name = "Run Energy Timeout",
		description = "Hides the run energy bar this many seconds after you last ran (0 = never time " +
			"out). Requires 'Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 9
	)
	@Range(min = 0, max = 300)
	default int runEnergyBarTimeout()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "runEnergyBarColor",
		name = "Run Energy Fill",
		description = "Fill color of the run energy bar. Requires 'Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 10
	)
	default Color runEnergyBarColor()
	{
		return new Color(199, 174, 0);
	}

	@ConfigItem(
		keyName = "runEnergyTextColor",
		name = "Run Energy Text",
		description = "Color of the run energy number. Requires 'Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 11
	)
	default Color runEnergyTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "barPosition1",
		name = "Bar 1",
		description = "Which bar is drawn topmost in your stack, with any bar picked twice showing " +
			"only at its topmost pick.",
		section = PLAYER_INFO_SECTION,
		position = 12
	)
	default BarKind barPosition1()
	{
		return BarKind.HP;
	}

	@ConfigItem(
		keyName = "barPosition2",
		name = "Bar 2",
		description = "Which bar is drawn second in your stack. See 'Bar 1'.",
		section = PLAYER_INFO_SECTION,
		position = 13
	)
	default BarKind barPosition2()
	{
		return BarKind.PRAYER;
	}

	@ConfigItem(
		keyName = "barPosition3",
		name = "Bar 3",
		description = "Which bar is drawn third in your stack. See 'Bar 1'.",
		section = PLAYER_INFO_SECTION,
		position = 14
	)
	default BarKind barPosition3()
	{
		return BarKind.SPECIAL;
	}

	@ConfigItem(
		keyName = "barPosition4",
		name = "Bar 4",
		description = "Which bar is drawn bottommost in your stack, following the same rules as 'Bar 1'.",
		section = PLAYER_INFO_SECTION,
		position = 15
	)
	default BarKind barPosition4()
	{
		return BarKind.RUN;
	}

	@ConfigItem(
		keyName = "selfColorByStatusEffect",
		name = "Status Effects",
		description = "How poison, venom, burns, bleeds, disease and corruption show on your bar: as a tint, an icon, both, or not at all.",
		section = PLAYER_INFO_SECTION,
		position = 16
	)
	default StatusEffectMode selfColorByStatusEffect()
	{
		return StatusEffectMode.BOTH;
	}

	@ConfigItem(
		keyName = "showFoodHealPreview",
		// keyName stays "showFoodHealPreview" so saved profiles carry over - see CLAUDE.md.
		name = "Restore Previews",
		description = "Previews what a hovered food, potion or restore item would give, as an extra segment " +
			"on the HP, prayer or run bar. Requires your own bar to be showing.",
		section = PLAYER_INFO_SECTION,
		position = 17
	)
	default boolean showPreviews()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playerPersistDuration",
		name = "Persist Duration",
		description = "How long in seconds a player's bar keeps showing the last known HP after the " +
			"native bar fades (0 = hide immediately).",
		section = PLAYER_INFO_SECTION,
		position = 18
	)
	@Range(min = 0, max = 300)
	default int playerPersistDuration()
	{
		return 5;
	}

	// ==================== Other player bar style ====================

	@ConfigItem(
		keyName = "showForPlayers",
		name = "Show Bar",
		description = "Whether other players get a bar: never, only while they are in combat, or always.",
		section = OTHER_PLAYER_SECTION,
		position = 0
	)
	default Visibility showForPlayers()
	{
		return Visibility.NEVER;
	}

	@ConfigItem(
		keyName = "otherPlayerDisplayMode",
		name = "Display Mode",
		description = "Show other players' HP as a percentage, or neither (bar only, no text).",
		section = OTHER_PLAYER_SECTION,
		position = 1
	)
	default OtherPlayerDisplayMode otherPlayerDisplayMode()
	{
		return OtherPlayerDisplayMode.PERCENT;
	}

	@ConfigItem(
		keyName = "otherPlayerVerticalOffset",
		name = "Vertical Offset",
		description = "Pixels to shift other players' bars up (positive) or down (negative) from " +
			"center. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 2
	)
	@Range(min = -50, max = 100)
	default int otherPlayerVerticalOffset()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "otherPlayerBarColor",
		name = "Bar Color",
		description = "Fill color of other players' bars, and the full-HP color when HP Color " +
			"Gradient is on. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 3
	)
	default Color otherPlayerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "otherPlayerHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends other players' bars from the bar color through yellow to red as HP drops, instead of one flat color.",
		section = OTHER_PLAYER_SECTION,
		position = 4
	)
	default boolean otherPlayerHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherPlayerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of other players' bars. Independent of your own in " +
			"Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 5
	)
	default Color otherPlayerBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "otherPlayerBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of other players' bar background, fill, and border. 100 = " +
			"fully opaque. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 6
	)
	@Range(min = 0, max = 100)
	default int otherPlayerBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "otherPlayerTextColor",
		name = "HP Text Color",
		description = "Color of the HP number on other players' bars. Independent of your own in " +
			"Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 7
	)
	default Color otherPlayerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "otherPlayerDamageTrail",
		name = "Damage Trail",
		description = "Whether a darker trail follows damage down the bar, and whether it matches the bar color or uses its own.",
		section = OTHER_PLAYER_SECTION,
		position = 8
	)
	default DamageTrailMode otherPlayerDamageTrail()
	{
		return DamageTrailMode.OFF;
	}

	@ConfigItem(
		keyName = "otherPlayerDamageTrailColor",
		name = "Trail Color",
		description = "Color of the health another player just lost. Requires a Damage Trail of " +
			"'Custom color'.",
		section = OTHER_PLAYER_SECTION,
		position = 9
	)
	default Color otherPlayerDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	// ==================== Other player bar info ====================

	@ConfigItem(
		keyName = "showPlayerName",
		name = "Player Name",
		description = "Whether other players get a name label: never, only while they are in combat, or always.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 0
	)
	default Visibility showPlayerName()
	{
		return Visibility.TRACKED;
	}

	@ConfigItem(
		keyName = "showPlayerCombatLevel",
		name = "Combat Level",
		description = "Appends the player's combat level to their name. Requires 'Player Name'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 1
	)
	default boolean showPlayerCombatLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerNameColor",
		name = "Name Color",
		description = "Color of the player name text, separate from the HP number's color.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 2
	)
	default Color playerNameColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "highlightFriends",
		name = "Highlight Friends",
		description = "Draws a friend's name in the friend color instead of the normal one. Overrides " +
			"'Color Names By Combat Level'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 3
	)
	default boolean highlightFriends()
	{
		return true;
	}

	@ConfigItem(
		keyName = "friendNameColor",
		name = "Friend Name Color",
		description = "Color of a friend's name. Requires 'Highlight Friends'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 4
	)
	default Color friendNameColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "playerNameStackLimit",
		name = "Stack Limit",
		description = "Caps how many other players (bar and/or name) render on the same tile at once - " +
			"which ones is arbitrary, not distance-based. 0 = unlimited.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 5
	)
	@Range(min = 0, max = 30)
	default int playerNameStackLimit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerFilter",
		name = "Blacklist",
		description = "Comma-separated player names to hide. Supports * wildcards; leave blank to show all.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 6
	)
	default String playerFilter()
	{
		return "";
	}

	// ==================== Behavior and hotkeys ====================

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
		name = "Hide Native Bar",
		description = "Hides the game's own overhead health bar client-wide, so only this plugin's " +
			"bar shows. Native bars for mechanics this plugin doesn't redraw stay visible.",
		section = BEHAVIOR_SECTION,
		position = 1
	)
	default boolean hideNativeBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prioritizeSelfOnSameTile",
		name = "Prioritize Self",
		description = "When an NPC or another player shares your tile, hides their bar and name " +
			"instead of stacking it with yours.",
		section = BEHAVIOR_SECTION,
		position = 2
	)
	default boolean prioritizeSelfOnSameTile()
	{
		return true;
	}

	@ConfigItem(
		keyName = "colorCombatLevelByDifference",
		name = "Color Combat Levels",
		description = "Colors a combat level by how far it is from your own, red through yellow to green. " +
			"Requires a combat level to be showing on the NPC or player.",
		section = BEHAVIOR_SECTION,
		position = 3
	)
	default boolean colorCombatLevelByDifference()
	{
		return true;
	}

	@ConfigItem(
		keyName = "colorNamesByCombatLevel",
		name = "Color Names By Level",
		description = "Colors an NPC or player's name by how far their combat level is from your own. " +
			"Replaces the configured name color.",
		section = BEHAVIOR_SECTION,
		position = 4
	)
	default boolean colorNamesByCombatLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "toggleNamesHotkey",
		name = "Toggle Names",
		description = "Instantly shows/hides NPC and player names. Doesn't affect HP bars, Prayer/" +
			"Special/Run bars, hitsplats, chat text, or icons.",
		section = BEHAVIOR_SECTION,
		position = 5
	)
	default Keybind toggleNamesHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "toggleHpBarsHotkey",
		name = "Toggle HP Bars",
		description = "Instantly shows/hides NPC and player HP bars (including your own). Doesn't " +
			"affect names, Prayer/Special/Run bars, hitsplats, chat text, or icons.",
		section = BEHAVIOR_SECTION,
		position = 6
	)
	default Keybind toggleHpBarsHotkey()
	{
		return Keybind.NOT_SET;
	}

	enum DisplayMode
	{
		NUMBER,
		PERCENT,
		BOTH,
		NEITHER;

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
				case NEITHER:
					return "Neither";
				default:
					return name();
			}
		}
	}

	/** Other players' only real choices - see CustomHpBarOverlay.displayMode() for why NUMBER/BOTH aren't offered. */
	/** The two aggressive-NPC colour checkboxes as one choice; the icon stays its own toggle. */
	enum AggressiveHighlight
	{
		OFF,
		NAMES,
		BARS,
		BOTH;

		public boolean names()
		{
			return this == NAMES || this == BOTH;
		}

		public boolean bars()
		{
			return this == BARS || this == BOTH;
		}

		@Override
		public String toString()
		{
			return this == OFF ? "Off" : this == NAMES ? "Names" : this == BARS ? "Bars" : "Both";
		}
	}

	/** A damage trail's on/off and "match bar colour" checkboxes as one choice. */
	enum DamageTrailMode
	{
		OFF,
		MATCH_BAR,
		CUSTOM;

		public boolean shown()
		{
			return this != OFF;
		}

		@Override
		public String toString()
		{
			return this == OFF ? "Off" : this == MATCH_BAR ? "Match bar color" : "Custom color";
		}
	}

	enum WeaknessMode
	{
		OFF,
		ICON,
		ICON_AND_PERCENT;

		@Override
		public String toString()
		{
			return this == OFF ? "Off" : this == ICON ? "Icon" : "Icon & percent";
		}
	}

	enum StatusEffectMode
	{
		OFF,
		TINT,
		ICON,
		BOTH;

		public boolean tint()
		{
			return this == TINT || this == BOTH;
		}

		public boolean icon()
		{
			return this == ICON || this == BOTH;
		}

		@Override
		public String toString()
		{
			return this == OFF ? "Off" : this == TINT ? "Bar tint" : this == ICON ? "Icon" : "Both";
		}
	}

	/** The prayer bar's three former checkboxes. TRACKED is the old default: drawn, but only in combat. */
	enum PrayerBarVisibility
	{
		NEVER,
		WHILE_PRAYING,
		TRACKED,
		ALWAYS;

		public boolean attached(boolean praying)
		{
			return this != NEVER && (this != WHILE_PRAYING || praying);
		}

		@Override
		public String toString()
		{
			return this == NEVER ? "Never" : this == WHILE_PRAYING ? "While praying"
				: this == TRACKED ? "When tracked" : "Always";
		}
	}

	enum PrayerTimerVisibility
	{
		NEVER,
		WHILE_PRAYING,
		ALWAYS;

		public boolean shown(boolean praying)
		{
			return this != NEVER && (this != WHILE_PRAYING || praying);
		}

		@Override
		public String toString()
		{
			return this == NEVER ? "Never" : this == WHILE_PRAYING ? "While praying" : "Always";
		}
	}

	enum RunBarVisibility
	{
		NEVER,
		WHILE_DRAINING,
		ALWAYS;

		@Override
		public String toString()
		{
			return this == NEVER ? "Never" : this == WHILE_DRAINING ? "While draining" : "Always";
		}
	}

	/** Two former checkboxes - "show" and "always show" - as the one three-state choice they described. */
	enum Visibility
	{
		NEVER,
		TRACKED,
		ALWAYS;

		public boolean shown()
		{
			return this != NEVER;
		}

		public boolean always()
		{
			return this == ALWAYS;
		}

		@Override
		public String toString()
		{
			return this == NEVER ? "Never" : this == TRACKED ? "When tracked" : "Always";
		}
	}

	enum OtherPlayerDisplayMode
	{
		PERCENT,
		NEITHER;

		@Override
		public String toString()
		{
			return this == PERCENT ? "Percent" : "Neither";
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

	/** Horizontal placement of the HP text within the bar - see CustomHpBarOverlay.drawLabel(). */
	enum TextAlignment
	{
		LEFT,
		CENTER,
		RIGHT;

		@Override
		public String toString()
		{
			switch (this)
			{
				case LEFT:
					return "Left";
				case CENTER:
					return "Center";
				case RIGHT:
					return "Right";
				default:
					return name();
			}
		}
	}

	/** One bar in the local player's vertical stack. Only the player stacks - NPCs only ever get HP. */
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
