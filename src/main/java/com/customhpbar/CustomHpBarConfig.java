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
		description = "Whether to show your own bar; its size/shape/border/text settings, shared with " +
			"other players; and its own fill/gradient/background color, opacity, and vertical offset - " +
			"other players use independent versions of those in Other Player Bar — Style",
		position = 2,
		closedByDefault = true
	)
	String PLAYER_SECTION = "player";

	@ConfigSection(
		name = "Player Bar — Info",
		description = "Prayer bar, status effects, restore previews, and the overhead icon",
		position = 3,
		closedByDefault = true
	)
	String PLAYER_INFO_SECTION = "playerInfo";

	@ConfigSection(
		name = "Other Player Bar — Style",
		description = "Whether to show other players' bars, and the fill/gradient/background color, " +
			"opacity, and vertical offset settings independent of your own",
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
		name = "Behavior",
		description = "Settings shared by both bar types",
		position = 6,
		closedByDefault = true
	)
	String BEHAVIOR_SECTION = "behavior";

	@ConfigSection(
		name = "Hotkeys",
		description = "Keybinds to instantly show/hide HP bars or names, independent of every other setting",
		position = 7,
		closedByDefault = true
	)
	String HOTKEY_SECTION = "hotkeys";

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
		name = "HP Text Color",
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
		keyName = "targetTextAlignment",
		name = "Text Alignment",
		description = "Where the HP text sits horizontally within the bar.",
		section = TARGET_SECTION,
		position = 19
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
		position = 20
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
		position = 21
	)
	@Range(min = 0, max = 100)
	default int targetBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "targetDamageTrail",
		name = "Damage Trail",
		description = "Leaves a colored trail behind the bar when an NPC takes damage, draining to " +
			"the new HP a moment later. Healing has no trail.",
		section = TARGET_SECTION,
		position = 22
	)
	default boolean targetDamageTrail()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetDamageTrailColor",
		name = "Damage Trail Color",
		description = "Color of the health an NPC just lost. Timing is shared by every bar, in " +
			"Behavior.",
		section = TARGET_SECTION,
		position = 23
	)
	default Color targetDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "targetDamageTrailMatchBar",
		name = "Match Bar Color",
		description = "Colors the trail from the bar's own color at the health it is draining " +
			"from, darkened. Replaces Damage Trail Color.",
		section = TARGET_SECTION,
		position = 24
	)
	default boolean targetDamageTrailMatchBar()
	{
		return false;
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
		keyName = "showPetNames",
		name = "Show Pet Names",
		description = "Draws names above pets. Requires 'Show NPC Name'.",
		section = TARGET_NPC_SECTION,
		position = 7
	)
	default boolean showPetNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC Name Color",
		description = "Color of the NPC name text, separate from the HP number's color.",
		section = TARGET_NPC_SECTION,
		position = 8
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
		position = 9
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
		position = 10
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
		position = 11
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
		position = 12
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
		position = 13
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
		position = 14
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
		position = 15
	)
	@Range(min = 0, max = 300)
	default int targetPersistDuration()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "greyOutOtherPlayerDamage",
		name = "Grey Out Health Bars",
		description = "Greys out an NPC's bar once another player damages it. Ironman accounts only; " +
			"bosses with shared or personal loot are exempt.",
		section = TARGET_NPC_SECTION,
		position = 16
	)
	default boolean greyOutOtherPlayerDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "greyOutOtherPlayerDamageNames",
		name = "Grey Out Names",
		description = "Greys out an NPC's name on the same terms as 'Grey Out Health Bars'. " +
			"Independent of that setting, and overrides the aggressive name color.",
		section = TARGET_NPC_SECTION,
		position = 17
	)
	default boolean greyOutOtherPlayerDamageNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fadeNpcBarOnDeath",
		name = "Fade Bar On Death",
		description = "Fades an NPC's bar and name out when it dies instead of hiding them the instant " +
			"the killing blow lands.",
		section = TARGET_NPC_SECTION,
		position = 18
	)
	default boolean fadeNpcBarOnDeath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcDeathFadeDuration",
		name = "Death Fade Duration (ms)",
		description = "How long an NPC's bar takes to fade out after it dies. Ends early if the corpse " +
			"despawns first. Requires Fade Bar On Death.",
		section = TARGET_NPC_SECTION,
		position = 19
	)
	@Range(min = 0, max = 2000)
	default int npcDeathFadeDuration()
	{
		return 600;
	}

	@ConfigItem(
		keyName = "showNpcShieldBar",
		name = "Show Shield Bar",
		description = "Shows a shield's remaining strength on the bar while an NPC is shielded. "
			+ "Supports Doom of Mokhaiotl and Kephri.",
		section = TARGET_NPC_SECTION,
		position = 23
	)
	default boolean showNpcShieldBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcShieldBarColor",
		name = "Shield Bar Color",
		description = "Fill color for the bar while an NPC is shielded.",
		section = TARGET_NPC_SECTION,
		position = 24
	)
	default Color npcShieldBarColor()
	{
		return new Color(60, 130, 220);
	}

	@ConfigItem(
		keyName = "showNpcChargeBar",
		name = "Show Charge Bar",
		description = "Shows a second bar beneath an NPC's while it charges a special attack. "
			+ "Supports Doom of Mokhaiotl.",
		section = TARGET_NPC_SECTION,
		position = 25
	)
	default boolean showNpcChargeBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcChargeBarColor",
		name = "Charge Bar Color",
		description = "Fill color for the charge bar.",
		section = TARGET_NPC_SECTION,
		position = 26
	)
	default Color npcChargeBarColor()
	{
		return new Color(235, 195, 40);
	}

	@ConfigItem(
		keyName = "npcFilter",
		name = "NPC Blacklist",
		description = "Comma-separated NPC names to hide. Supports * wildcards; leave blank to show all.",
		section = TARGET_NPC_SECTION,
		position = 27
	)
	default String npcFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "npcStackLimit",
		name = "NPC Stack Limit",
		description = "Caps how many NPCs (bar and/or name) render on the same tile at once - which " +
			"ones is arbitrary, not distance-based. 0 = unlimited.",
		section = TARGET_NPC_SECTION,
		position = 18
	)
	@Range(min = 0, max = 30)
	default int npcStackLimit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showNpcWeaknessIcon",
		name = "Show Weakness Icon",
		description = "Shows the surge spell icon for an NPC's elemental weakness beside its HP bar. " +
			"Nothing is drawn for an NPC with no weakness.",
		section = TARGET_NPC_SECTION,
		position = 20
	)
	default boolean showNpcWeaknessIcon()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showNpcWeaknessPercent",
		name = "Show Weakness Percent",
		description = "Draws the weakness percentage beside the icon. Requires 'Show Weakness Icon'.",
		section = TARGET_NPC_SECTION,
		position = 21
	)
	default boolean showNpcWeaknessPercent()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcWeaknessPercentColor",
		name = "Weakness Percent Color",
		description = "Color of the weakness percentage text. Requires 'Show Weakness Percent'.",
		section = TARGET_NPC_SECTION,
		position = 22
	)
	default Color npcWeaknessPercentColor()
	{
		return new Color(255, 255, 255);
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
		description = "Display mode for your own bar - number, percentage, both, or neither " +
			"(bar only, no text). Requires 'Show for Self'.",
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
		position = 7
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
		position = 8
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
		position = 9
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
		position = 10
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
		position = 11
	)
	default Color playerBorderColor()
	{
		return new Color(0, 0, 0, 190);
	}

	@ConfigItem(
		keyName = "playerBarColor",
		name = "Bar Color",
		description = "Fill color of your own bar, and the full-HP color when HP Color Gradient is " +
			"on. Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 12
	)
	default Color playerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "playerHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends your own bar's fill color as HP drops. Off keeps Bar Color at all HP " +
			"levels. Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 13
	)
	default boolean playerHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerColorMid",
		name = "Mid HP Color",
		description = "Color reached at the midpoint, blended toward from both sides. Requires HP " +
			"Color Gradient. Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 14
	)
	default Color playerColorMid()
	{
		return new Color(180, 180, 0);
	}

	@ConfigItem(
		keyName = "playerMidpoint",
		name = "Midpoint",
		description = "HP percentage at which your own bar is exactly Mid HP Color. Other players " +
			"have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 15
	)
	@Range(min = 1, max = 99)
	default int playerMidpoint()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "playerColorLow",
		name = "Low HP Color",
		description = "Color reached at 0% HP. Requires HP Color Gradient. Other players have their " +
			"own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 16
	)
	default Color playerColorLow()
	{
		return new Color(180, 0, 0);
	}

	@ConfigItem(
		keyName = "playerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of your own bar. Other players have their own in " +
			"Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 17
	)
	default Color playerBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "playerVerticalOffset",
		name = "Vertical Offset (Self)",
		description = "Pixels to shift your own bar up (positive) or down (negative) from center. " +
			"Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 18
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
		position = 19
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
		position = 20
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
		position = 21
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
		position = 22
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
		position = 23
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
		position = 24
	)
	@Range(min = -10, max = 10)
	default int playerTextVerticalNudge()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerTextAlignment",
		name = "Text Alignment",
		description = "Where each bar's number sits horizontally within it - HP, Prayer, Special, and Run.",
		section = PLAYER_SECTION,
		position = 25
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
		position = 26
	)
	@Range(min = 0, max = 200)
	default int playerHpTextSpacing()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "playerBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of your own bars' background, fill, and border. 100 = " +
			"fully opaque. Other players have their own in Other Player Bar — Style.",
		section = PLAYER_SECTION,
		position = 27
	)
	@Range(min = 0, max = 100)
	default int playerBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "playerDamageTrail",
		name = "Damage Trail",
		description = "Leaves a colored trail behind your HP bar when you take damage, draining to " +
			"your new HP a moment later. Healing has no trail.",
		section = PLAYER_SECTION,
		position = 28
	)
	default boolean playerDamageTrail()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerDamageTrailColor",
		name = "Damage Trail Color",
		description = "Color of the health you just lost. Timing is shared by every bar, in " +
			"Behavior.",
		section = PLAYER_SECTION,
		position = 29
	)
	default Color playerDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "playerDamageTrailMatchBar",
		name = "Match Bar Color",
		description = "Colors the trail from the bar's own color at the health it is draining " +
			"from, darkened. Replaces Damage Trail Color.",
		section = PLAYER_SECTION,
		position = 30
	)
	default boolean playerDamageTrailMatchBar()
	{
		return false;
	}

	// ==================== Player bar player info ====================

	@ConfigItem(
		keyName = "alwaysShowHpBar",
		name = "Always Show HP Bar",
		description = "Shows your HP bar even when not tracked in combat. Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 0
	)
	default boolean alwaysShowHpBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showPrayerBar",
		name = "Show Prayer Bar",
		description = "Draws a Prayer points bar beneath your HP bar, or on its own outside combat. " +
			"Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 1
	)
	default boolean showPrayerBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alwaysShowPrayerBar",
		name = "Always Show Prayer Bar",
		description = "Shows the Prayer bar even when not tracked in combat. Still subject to 'Hide " +
			"Prayer Bar While Not Praying'. Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 2
	)
	default boolean alwaysShowPrayerBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hidePrayerBarWhenInactive",
		name = "Hide Prayer Bar While Not Praying",
		description = "Only draws the Prayer bar while a prayer is active. Flicking keeps it up. " +
			"Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 3
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
		position = 4
	)
	default Color prayerBarColor()
	{
		return new Color(60, 130, 220);
	}

	@ConfigItem(
		keyName = "prayerTextColor",
		name = "Prayer Text Color",
		description = "Color of the Prayer number. Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 5
	)
	default Color prayerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showPrayerTickTimer",
		name = "Show Prayer Tick Timer",
		description = "Draws an indicator that sweeps across the Prayer bar once per game tick, for timing " +
			"prayer flicks. Requires 'Show Prayer Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 6
	)
	default boolean showPrayerTickTimer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hidePrayerTickTimerWhenInactive",
		name = "Hide Tick Timer While Not Praying",
		description = "Only draws the tick timer while a prayer is active, same as 'Hide Prayer Bar While " +
			"Not Praying'. Requires 'Show Prayer Tick Timer'.",
		section = PLAYER_INFO_SECTION,
		position = 7
	)
	default boolean hidePrayerTickTimerWhenInactive()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prayerTickTimerColor",
		name = "Prayer Tick Timer Color",
		description = "Color of the tick timer indicator. Requires 'Show Prayer Tick Timer'.",
		section = PLAYER_INFO_SECTION,
		position = 8
	)
	default Color prayerTickTimerColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showSpecialAttackBar",
		name = "Show Special Attack Bar",
		description = "Draws a special attack energy bar alongside your HP bar, in combat only. " +
			"Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 9
	)
	default boolean showSpecialAttackBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alwaysShowSpecialBar",
		name = "Always Show Special Attack Bar",
		description = "Shows the special attack bar even when not tracked in combat. Requires 'Show Special " +
			"Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 10
	)
	default boolean alwaysShowSpecialBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "specialAttackBarColor",
		name = "Special Attack Bar Color",
		description = "Fill color of the special attack bar. Requires 'Show Special Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 11
	)
	default Color specialAttackBarColor()
	{
		return new Color(3, 153, 0);
	}

	@ConfigItem(
		keyName = "specialAttackTextColor",
		name = "Special Attack Text Color",
		description = "Color of the special attack number. Requires 'Show Special Attack Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 12
	)
	default Color specialAttackTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showRunEnergyBar",
		name = "Show Run Energy Bar",
		description = "Draws a run energy bar alongside your HP bar. Unlike Prayer/Special, shows " +
			"regardless of combat state. Requires 'Show for Self'.",
		section = PLAYER_INFO_SECTION,
		position = 13
	)
	default boolean showRunEnergyBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alwaysShowRunBar",
		name = "Always Show Run Energy Bar",
		description = "Shows the run energy bar even when not tracked in combat, ignoring the timeout below. " +
			"Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 14
	)
	default boolean alwaysShowRunBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "runEnergyBarTimeout",
		name = "Run Energy Bar Timeout (seconds)",
		description = "Hides the run energy bar this many seconds after you last ran. 0 = never time " +
			"out. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 15
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
		position = 16
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
		position = 17
	)
	default Color runEnergyStaminaColor()
	{
		return new Color(160, 124, 72);
	}

	@ConfigItem(
		keyName = "runEnergyTextColor",
		name = "Run Energy Text Color",
		description = "Color of the run energy number. Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 18
	)
	default Color runEnergyTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "barPosition1",
		name = "Bar 1 (Top)",
		description = "Which bar is drawn first (topmost) in your stack. If a bar is picked in more than " +
			"one position, only its topmost pick is shown.",
		section = PLAYER_INFO_SECTION,
		position = 19
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
		position = 20
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
		position = 21
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
		position = 22
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
		position = 23
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
		position = 24
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
		position = 25
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
		position = 26
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
		position = 27
	)
	default boolean showPrayerRestorePreview()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRunEnergyRestorePreview",
		name = "Show Run Energy Restore Preview",
		description = "Previews run energy restored by a hovered item as an extra bar segment. " +
			"Requires 'Show Run Energy Bar'.",
		section = PLAYER_INFO_SECTION,
		position = 28
	)
	default boolean showRunEnergyRestorePreview()
	{
		return true;
	}

	// ==================== Other player bar style ====================

	@ConfigItem(
		keyName = "showForPlayers",
		name = "Show for Other Players",
		description = "Draws the health bar over other players. Names can still show without this if " +
			"'Always Show Player Name' is on.",
		section = OTHER_PLAYER_SECTION,
		position = 0
	)
	default boolean showForPlayers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alwaysShowPlayerBar",
		name = "Always Show Player HP Bar",
		description = "Shows other players' HP bar at all times, not just when tracked in combat. " +
			"Requires 'Show for Other Players'.",
		section = OTHER_PLAYER_SECTION,
		position = 1
	)
	default boolean alwaysShowPlayerBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherPlayerDisplayMode",
		name = "Display Mode",
		description = "Show other players' HP as a percentage, or neither (bar only, no text).",
		section = OTHER_PLAYER_SECTION,
		position = 2
	)
	default OtherPlayerDisplayMode otherPlayerDisplayMode()
	{
		return OtherPlayerDisplayMode.PERCENT;
	}

	@ConfigItem(
		keyName = "otherPlayerVerticalOffset",
		name = "Vertical Offset (Other Players)",
		description = "Pixels to shift other players' bars up (positive) or down (negative) from " +
			"center. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 3
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
		position = 4
	)
	default Color otherPlayerBarColor()
	{
		return new Color(0, 180, 0);
	}

	@ConfigItem(
		keyName = "otherPlayerHpColorGradient",
		name = "HP Color Gradient",
		description = "Blends other players' bar fill color as HP drops. Off keeps Bar Color at all " +
			"HP levels. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 5
	)
	default boolean otherPlayerHpColorGradient()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherPlayerColorMid",
		name = "Mid HP Color",
		description = "Color reached at the midpoint, blended toward from both sides. Requires HP " +
			"Color Gradient. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 6
	)
	default Color otherPlayerColorMid()
	{
		return new Color(180, 180, 0);
	}

	@ConfigItem(
		keyName = "otherPlayerMidpoint",
		name = "Midpoint",
		description = "HP percentage at which other players' bars are exactly Mid HP Color. " +
			"Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 7
	)
	@Range(min = 1, max = 99)
	default int otherPlayerMidpoint()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "otherPlayerColorLow",
		name = "Low HP Color",
		description = "Color reached at 0% HP. Requires HP Color Gradient. Independent of your own in " +
			"Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 8
	)
	default Color otherPlayerColorLow()
	{
		return new Color(180, 0, 0);
	}

	@ConfigItem(
		keyName = "otherPlayerBarBackground",
		name = "Background Color",
		description = "Color of the empty portion of other players' bars. Independent of your own in " +
			"Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 9
	)
	default Color otherPlayerBarBackground()
	{
		return new Color(40, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "otherPlayerTextColor",
		name = "HP Text Color",
		description = "Color of the HP number on other players' bars. Independent of your own in " +
			"Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 10
	)
	default Color otherPlayerTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "otherPlayerBarOpacity",
		name = "Bar Opacity",
		description = "Overall transparency of other players' bar background, fill, and border. 100 = " +
			"fully opaque. Independent of your own in Player Bar — Style.",
		section = OTHER_PLAYER_SECTION,
		position = 11
	)
	@Range(min = 0, max = 100)
	default int otherPlayerBarOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "otherPlayerDamageTrail",
		name = "Damage Trail",
		description = "Leaves a colored trail behind another player's bar when they take damage, " +
			"draining to their new HP a moment later. Healing has no trail.",
		section = OTHER_PLAYER_SECTION,
		position = 12
	)
	default boolean otherPlayerDamageTrail()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherPlayerDamageTrailColor",
		name = "Damage Trail Color",
		description = "Color of the health another player just lost. Timing is shared by every bar, " +
			"in Behavior.",
		section = OTHER_PLAYER_SECTION,
		position = 13
	)
	default Color otherPlayerDamageTrailColor()
	{
		return new Color(200, 40, 40, 220);
	}

	@ConfigItem(
		keyName = "otherPlayerDamageTrailMatchBar",
		name = "Match Bar Color",
		description = "Colors the trail from the bar's own color at the health it is draining " +
			"from, darkened. Replaces Damage Trail Color.",
		section = OTHER_PLAYER_SECTION,
		position = 14
	)
	default boolean otherPlayerDamageTrailMatchBar()
	{
		return false;
	}

	// ==================== Other player bar info ====================

	@ConfigItem(
		keyName = "showPlayerName",
		name = "Show Player Name",
		description = "Draws a name label above other players' bars. Without 'Always Show Player " +
			"Name', only shows for players tracked via 'Show for Other Players'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 0
	)
	default boolean showPlayerName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alwaysShowPlayerName",
		name = "Always Show Player Name",
		description = "Shows the name at all times, not just when the bar is tracked. Requires 'Show Player " +
			"Name'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 1
	)
	default boolean alwaysShowPlayerName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showPlayerCombatLevel",
		name = "Show Combat Level",
		description = "Appends the player's combat level to their name. Requires 'Show Player Name'.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 2
	)
	default boolean showPlayerCombatLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "playerNameColor",
		name = "Player Name Color",
		description = "Color of the player name text, separate from the HP number's color.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 3
	)
	default Color playerNameColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "playerFilter",
		name = "Player Blacklist",
		description = "Comma-separated player names to hide. Supports * wildcards; leave blank to show all.",
		section = OTHER_PLAYER_INFO_SECTION,
		position = 4
	)
	default String playerFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "playerNameStackLimit",
		name = "Player Stack Limit",
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
		description = "Hides the game's own overhead health bar client-wide, so only this plugin's " +
			"bar shows. Bars that track a mechanic rather than hitpoints stay visible.",
		section = BEHAVIOR_SECTION,
		position = 1
	)
	default boolean hideNativeBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prioritizeSelfOnSameTile",
		name = "Prioritize Self on Same Tile",
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
		name = "Color Names By Combat Level",
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
		keyName = "damageTrailHold",
		name = "Damage Trail Hold (ms)",
		description = "How long a damage trail stays put before it starts draining. Requires Damage " +
			"Trail on at least one bar.",
		section = BEHAVIOR_SECTION,
		position = 5
	)
	@Range(min = 0, max = 2000)
	default int damageTrailHold()
	{
		return 400;
	}

	@ConfigItem(
		keyName = "damageTrailDrain",
		name = "Damage Trail Drain (ms)",
		description = "How long a damage trail takes to drain down to the current HP once it starts. " +
			"Requires Damage Trail on at least one bar.",
		section = BEHAVIOR_SECTION,
		position = 6
	)
	@Range(min = 0, max = 2000)
	default int damageTrailDrain()
	{
		return 250;
	}

	@ConfigItem(
		keyName = "toggleNamesHotkey",
		name = "Toggle Names",
		description = "Instantly shows/hides NPC and player names. Doesn't affect HP bars, Prayer/" +
			"Special/Run bars, hitsplats, chat text, or icons.",
		section = HOTKEY_SECTION,
		position = 0
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
		section = HOTKEY_SECTION,
		position = 1
	)
	default Keybind toggleHpBarsHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "toggleWeaknessIconsHotkey",
		name = "Toggle Weakness Icons",
		description = "Instantly shows/hides the elemental weakness icon and its percentage. Doesn't "
			+ "affect names, HP bars, or any other icon.",
		section = HOTKEY_SECTION,
		position = 2
	)
	default Keybind toggleWeaknessIconsHotkey()
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
