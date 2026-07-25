package com.customhpbar;

import lombok.AllArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.StatsChanges;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CustomHpBarOverlay extends Overlay
{
	private static final double MIN_ZOOM_SCALE = 0.4;
	private static final double MAX_ZOOM_SCALE = 3.0;

	/** Subtle top-of-bar highlight for a glossier, less flat look. */
	private static final float GRADIENT_HIGHLIGHT = 0.2f;

	/** Fixed prayer bar fill color, matching OSRS's own prayer orb blue. Not configurable. */
	private static final Color PRAYER_COLOR = new Color(60, 130, 220);

	/**
	 * Fixed grey fill for greyOutOtherPlayerDamage (see CustomHpBarPlugin.isLootTainted) - not
	 * configurable, and overrides any status-effect tint rather than blending with it, since the
	 * loot-eligibility warning needs to read unambiguously at a glance.
	 */
	private static final Color LOOT_TAINTED_COLOR = new Color(120, 120, 120);

	/**
	 * Alpha applied to a bar's own fill color for its heal/restore preview segment - reads as
	 * "not real yet" without needing a separate configurable color (the preview always matches
	 * whatever color the bar is currently showing, status tint included).
	 */
	private static final int PREVIEW_ALPHA = 110;

	/** Gap between the NPC name label and the HP bar's top edge. Not configurable yet. */
	private static final int NAME_GAP = 2;

	/** Size of the aggressive-NPC badge icon (see showAggressiveNpcIcon), before zoom scaling. */
	private static final int AGGRESSIVE_ICON_SIZE = 12;

	/** Gap between the aggressive-NPC icon and the name label it sits above, before zoom scaling. */
	private static final int AGGRESSIVE_ICON_GAP = 2;

	/** Gap between the overhead icon and the HP bar's top edge, before zoom scaling. */
	private static final int OVERHEAD_ICON_GAP = 3;

	/** Max simultaneous hitsplats drawn on the local player - the vanilla engine gives each actor exactly 4 slots. */
	private static final int MAX_HITSPLATS = 4;

	/** Vertical padding between bars of actors sharing the same tile, before zoom scaling. */
	private static final int STACK_PADDING = 2;

	/**
	 * Approximate overhead icon height reserved when the local player's bar is in a same-tile
	 * stack (an approximation avoids depending on whether the real sprite has loaded yet). Only
	 * affects stack spacing, not the icon's own rendering.
	 */
	private static final int STACK_ICON_CLEARANCE = 24;

	/** Gap between the overhead chat text and the HP bar/icon above which it's moved, before zoom scaling. */
	private static final int CHAT_TEXT_BAR_GAP = 3;

	/**
	 * Real client sprite ID for each hitsplat's background graphic, keyed by HitsplatID's type
	 * constant - confirmed via the Nameplates plugin's own HitsplatDefaultSprite mapping. Drawing
	 * the real sprite is what "exactly the same as vanilla" requires for hitsplats specifically.
	 */
	private static final Map<Integer, Integer> HITSPLAT_SPRITE_IDS = buildHitsplatSpriteIds();

	private static Map<Integer, Integer> buildHitsplatSpriteIds()
	{
		Map<Integer, Integer> ids = new HashMap<>();
		ids.put(HitsplatID.BLOCK_ME, 1358);
		ids.put(HitsplatID.BLOCK_OTHER, 1630);
		ids.put(HitsplatID.DAMAGE_ME, 1359);
		ids.put(HitsplatID.DAMAGE_OTHER, 1631);
		ids.put(HitsplatID.POISON, 1360);
		ids.put(HitsplatID.DISEASE, 1361);
		ids.put(HitsplatID.DISEASE_BLOCKED, 1633);
		ids.put(HitsplatID.VENOM, 1632);
		ids.put(HitsplatID.HEAL, 1629);
		ids.put(HitsplatID.CYAN_UP, 3519);
		ids.put(HitsplatID.CYAN_DOWN, 3520);
		ids.put(HitsplatID.DAMAGE_ME_CYAN, 1419);
		ids.put(HitsplatID.DAMAGE_OTHER_CYAN, 1339);
		ids.put(HitsplatID.DAMAGE_ME_ORANGE, 1628);
		ids.put(HitsplatID.DAMAGE_OTHER_ORANGE, 1544);
		ids.put(HitsplatID.DAMAGE_ME_YELLOW, 1362);
		ids.put(HitsplatID.DAMAGE_OTHER_YELLOW, 1634);
		ids.put(HitsplatID.DAMAGE_ME_WHITE, 1363);
		ids.put(HitsplatID.DAMAGE_OTHER_WHITE, 1105);
		ids.put(HitsplatID.DAMAGE_MAX_ME, 3571);
		ids.put(HitsplatID.DAMAGE_MAX_ME_CYAN, 4556);
		ids.put(HitsplatID.DAMAGE_MAX_ME_ORANGE, 4557);
		ids.put(HitsplatID.DAMAGE_MAX_ME_YELLOW, 3572);
		ids.put(HitsplatID.DAMAGE_MAX_ME_WHITE, 3573);
		ids.put(HitsplatID.DAMAGE_ME_POISE, 4558);
		ids.put(HitsplatID.DAMAGE_OTHER_POISE, 4559);
		ids.put(HitsplatID.DAMAGE_MAX_ME_POISE, 4560);
		ids.put(HitsplatID.CORRUPTION, 2270);
		ids.put(HitsplatID.PRAYER_DRAIN, 4561);
		ids.put(HitsplatID.BLEED, 4564);
		ids.put(HitsplatID.SANITY_DRAIN, 4764);
		ids.put(HitsplatID.SANITY_RESTORE, 4765);
		ids.put(HitsplatID.DOOM, 4766);
		ids.put(HitsplatID.BURN, 4767);
		return ids;
	}

	private final CustomHpBarPlugin plugin;
	private final CustomHpBarConfig config;
	private final Client client;
	private final SpriteManager spriteManager;
	private final ItemStatChangesService itemStatService;

	/**
	 * Camera zoom (Client.getScale()) observed the first time we render, used as the "1.0x"
	 * baseline for zoom scaling. There's no documented universal reference zoom to calibrate
	 * against up front, so capturing whatever zoom the user is actually playing at guarantees the
	 * configured pixel sizes are exactly right there, scaling only relative to it from then on.
	 */
	private int baselineZoom = -1;

	/**
	 * Poison/Venom/Burn hitsplat sprites, loaded live from the client via SpriteManager rather
	 * than bundled. getSprite() reads its own cache and returns null until loaded;
	 * getSpriteAsync() populates that cache in the background. Cached here too so repeat frames
	 * skip SpriteManager's own lookup.
	 */
	private BufferedImage poisonIcon;
	private BufferedImage venomIcon;
	private BufferedImage burnIcon;

	/** The real client skull sprite (SpriteID.ICON_SKULL) used for the aggressive-NPC badge - see aggressiveIcon(). */
	private BufferedImage aggressiveIcon;

	/**
	 * Disease/Corruption debuff icons - unlike Poison/Venom/Burn, no confirmed SpriteID.Hitmark
	 * entry exists for either, so these are bundled resource images (from the OSRS Wiki) instead.
	 */
	private BufferedImage diseaseIcon;
	private BufferedImage corruptionIcon;

	/**
	 * All 15 overhead icon graphics are sub-frames of one client sprite (SpriteID.HEADICONS_PRAYER),
	 * indexed by HeadIcon.ordinal() - confirmed against the Nameplates plugin's own frame table.
	 */
	private final Map<HeadIcon, BufferedImage> headIconImages = new EnumMap<>(HeadIcon.class);

	/** Real hitsplat background sprites, cached per HitsplatID type once loaded (see HITSPLAT_SPRITE_IDS). */
	private final Map<Integer, BufferedImage> hitsplatImages = new HashMap<>();

	@Inject
	CustomHpBarOverlay(CustomHpBarPlugin plugin, CustomHpBarConfig config, Client client, SpriteManager spriteManager,
			ItemStatChangesService itemStatService)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.spriteManager = spriteManager;
		this.itemStatService = itemStatService;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		// Antialiased shapes, but not text: at these pixel sizes antialiasing blurs pixel-hinted
		// fonts into a gray smear rather than crisp strokes - the game's own UI text isn't
		// antialiased either, for the same reason.
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		// Resolved lazily, at most once each per frame no matter how many actors share a profile.
		BarStyle targetStyle = null;
		BarStyle playerStyle = null;

		// Same-tile stacking state, rebuilt each frame: actors on the same tile would otherwise
		// draw bars/names on top of each other. tileStacks accumulates pixels already claimed
		// above each tile; appliedStacks remembers each drawn actor's shift so the "Always Show
		// NPC Name" pass below can reuse it instead of claiming a fresh, mismatched slot.
		Map<WorldPoint, Integer> tileStacks = new HashMap<>();
		Map<Actor, Integer> appliedStacks = new HashMap<>();

		for (Map.Entry<Actor, Integer> entry : plugin.getTrackedActors().entrySet())
		{
			Actor actor = entry.getKey();

			// NPC filtering already happened in CustomHpBarPlugin.isTrackedType() before an NPC
			// was ever added to trackedActors, so nothing to re-check here.
			int maxHp = resolveMaxHp(actor);
			int[] hp = resolveHp(actor, maxHp);
			if (hp == null)
			{
				continue;
			}

			// Perspective.localToCanvas (not actor.getCanvasTextLocation) is deliberate:
			// getCanvasTextLocation has a per-frame bob baked in (fine for floating text, wrong
			// for a bar meant to sit steady like the native one). localToCanvas with ground
			// position + logical height only moves with actual world position.
			Point anchor = Perspective.localToCanvas(
				client, actor.getLocalLocation(), actor.getWorldView().getPlane(), actor.getLogicalHeight());
			if (anchor == null)
			{
				continue;
			}

			BarStyle style;
			if (actor instanceof Player)
			{
				style = playerStyle != null ? playerStyle : (playerStyle = resolveStyle(actor));
			}
			else
			{
				style = targetStyle != null ? targetStyle : (targetStyle = resolveStyle(actor));
			}

			int shift = claimBarStackSlot(tileStacks, actor, style, zoomFactor());
			if (shift > 0)
			{
				anchor = new Point(anchor.getX(), anchor.getY() - shift);
			}
			appliedStacks.put(actor, shift);

			drawBar(g, actor, anchor, hp[0], hp[1], maxHp, style);
		}

		// The Prayer bar normally only shows attached beneath the HP bar (drawBar() above, only
		// reached for tracked actors) - for the local player that means only while "in combat",
		// so praying at a bank showed nothing. This second, independent path draws the Prayer bar
		// on its own whenever a prayer is toggled on, regardless of combat state. Skipped if the
		// main loop already drew it, to avoid a double draw.
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && config.showForSelf() && config.showPrayerBar()
				&& !plugin.getTrackedActors().containsKey(localPlayer) && plugin.isAnyPrayerActive())
		{
			Point anchor = Perspective.localToCanvas(
				client, localPlayer.getLocalLocation(), localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight());
			if (anchor != null)
			{
				playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
				drawStandalonePrayerBar(g, anchor, playerStyle);
			}
		}

		// Replacement for the native overhead prayer icon, which the plugin's render callback
		// suppresses for the local player when Replace Overhead Icon is on - so this is the only
		// icon drawn, not a duplicate. Independent of combat/tracking state, same as above, since
		// the native icon it replaces also showed at all times.
		if (localPlayer != null && config.showForSelf() && config.replaceOverheadIcon())
		{
			playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
			drawOverheadIcon(g, localPlayer, playerStyle);
			drawSelfHitsplats(g, localPlayer);
			drawOverheadChatText(g, localPlayer, playerStyle);
		}

		// Second pass over every nearby NPC for the two "regardless of combat" behaviors: Always
		// Show NPC Bar and Always Show NPC Name. Both iterate the same NPC list, sharing one loop
		// to avoid double-claiming same-tile stack slots.
		boolean alwaysBar = config.alwaysShowNpcBar();
		boolean alwaysName = config.showNpcName() && config.alwaysShowNpcName();
		if (alwaysBar || alwaysName)
		{
			double zoom = zoomFactor();
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				// matchesNpcFilter() is the "could I attack this" gate (combat level, hidden-
				// mechanic exclusion, name filter), exactly the set Always Show NPC Bar means.
				if (npc == null || !plugin.matchesNpcFilter(npc))
				{
					continue;
				}

				Point anchor = Perspective.localToCanvas(
					client, npc.getLocalLocation(), npc.getWorldView().getPlane(), npc.getLogicalHeight());
				if (anchor == null)
				{
					continue;
				}

				targetStyle = targetStyle != null ? targetStyle : resolveStyle(npc);

				// Combat level 0 (bankers, shops, fishing spots, pets) has no HP to show a bar
				// for, regardless of Only Show Combat NPC Names (that's about name clutter and
				// independent of this - a hardcoded floor, not a second opt-in).
				boolean drawBarForThis = alwaysBar && npc.getCombatLevel() > 0;

				// An NPC the main loop already drew reuses that exact shift so its name/bar here
				// lands on the same slot; otherwise it claims a fresh slot sized to this pass.
				boolean barAlreadyDrawn = appliedStacks.containsKey(npc);
				Integer applied = appliedStacks.get(npc);
				int shift = applied != null ? applied
					: (drawBarForThis ? claimBarStackSlot(tileStacks, npc, targetStyle, zoom)
						: claimNameStackSlot(tileStacks, npc, targetStyle, zoom));
				if (shift > 0)
				{
					anchor = new Point(anchor.getX(), anchor.getY() - shift);
				}

				// An NPC with no live HP (idle, never hit) shows a full bar; real ratio/precise
				// data takes over as soon as it's damaged. drawBar() handles this NPC's name on
				// its own when Always Show NPC Name is off, so it's only drawn below when it's on.
				if (drawBarForThis && !barAlreadyDrawn)
				{
					int maxHp = resolveMaxHp(npc);
					int[] hp = resolveHp(npc, maxHp);
					if (hp == null)
					{
						hp = new int[]{1, 1};
					}
					drawBar(g, npc, anchor, hp[0], hp[1], maxHp, targetStyle);
				}

				if (alwaysName)
				{
					drawNpcNameOnly(g, npc, anchor, targetStyle, zoom);
				}
			}
		}

		return null;
	}

	/**
	 * Returns [current, max] HP for display, or null if unavailable. The native boss HP HUD
	 * (CustomHpBarPlugin.nativeHudHp()) wins over everything when it's showing this exact actor -
	 * it's the exact number the client is about to display. Next, an NPC's established precise
	 * estimate (hitsplat-tracked) wins over the coarse ratio/scale bucket. Otherwise delegates to
	 * CustomHpBarPlugin.readHp(), falling back to the last cached value while the native bar has
	 * faded but the actor is still within its persist window.
	 */
	private int[] resolveHp(Actor actor, int maxHp)
	{
		int[] hud = plugin.nativeHudHp(actor);
		if (hud != null)
		{
			return hud;
		}

		if (actor instanceof NPC && maxHp > 0)
		{
			Integer precise = plugin.getPreciseNpcHp().get(actor);
			if (precise != null)
			{
				return new int[]{precise, maxHp};
			}
		}

		int[] live = CustomHpBarPlugin.readHp(client, actor);
		if (live != null)
		{
			return live;
		}

		return plugin.getLastKnownHp().get(actor);
	}

	/**
	 * Zoom multiplier applied to every pixel dimension so the bar grows/shrinks with the actor
	 * model instead of staying a fixed screen size regardless of camera distance.
	 */
	private double zoomFactor()
	{
		if (!config.scaleWithZoom())
		{
			return 1.0;
		}

		int currentZoom = client.getScale();
		if (currentZoom <= 0)
		{
			return 1.0;
		}
		if (baselineZoom <= 0)
		{
			baselineZoom = currentZoom;
		}

		double raw = currentZoom / (double) baselineZoom;
		return Math.max(MIN_ZOOM_SCALE, Math.min(MAX_ZOOM_SCALE, raw));
	}

	/** Bundles every appearance/text config value for one bar type (target or player). */
	private BarStyle resolveStyle(Actor actor)
	{
		if (actor instanceof Player)
		{
			return new BarStyle(
				config.playerBarWidth(), config.playerBarHeight(), config.playerCornerRadius(),
				config.playerBorderWidth(), config.playerBorderColor(), config.playerBarColor(),
				config.playerBarBackground(), config.playerVerticalOffset(),
				config.playerFontFamily(), config.playerFontStyle(), config.playerFontSize(),
				config.playerTextColor(), config.playerTextOutline(), config.playerTextVerticalNudge());
		}
		return new BarStyle(
			config.targetBarWidth(), config.targetBarHeight(), config.targetCornerRadius(),
			config.targetBorderWidth(), config.targetBorderColor(), config.targetBarColor(),
			config.targetBarBackground(), config.targetVerticalOffset(),
			config.targetFontFamily(), config.targetFontStyle(), config.targetFontSize(),
			config.targetTextColor(), config.targetTextOutline(), config.targetTextVerticalNudge());
	}

	/**
	 * The bar's on-screen rectangle - centered on the anchor, then shifted by the configurable
	 * vertical offset (positive moves upward). Shared by drawBar() and drawNpcNameOnly() so an
	 * "Always Show NPC Name" label sits at the same position it would occupy if the bar were also
	 * showing - it shouldn't jump when an NPC enters/leaves combat.
	 */
	private int[] barRect(Point anchor, BarStyle style, double zoom)
	{
		int w = scaled(style.width, zoom);
		int h = scaled(style.height, zoom);
		int vOffset = scaled(style.verticalOffset, zoom);
		int x = anchor.getX() - w / 2;
		int y = anchor.getY() - h / 2 - vOffset;
		return new int[]{x, y, w, h};
	}

	/**
	 * Claims a same-tile stack slot for an actor's full bar, returning the upward pixel shift to
	 * apply (0 for the first actor on its tile). Covers everything this actor draws upward from
	 * its bar top (name label, or the local player's replacement overhead icon) so the next
	 * actor's bar clears it. Downward extras (prayer bar, status icons) don't matter here, since
	 * stacking only ever pushes later actors up.
	 */
	private int claimBarStackSlot(Map<WorldPoint, Integer> tileStacks, Actor actor, BarStyle style, double zoom)
	{
		WorldPoint tile = actor.getWorldLocation();
		if (tile == null)
		{
			return 0;
		}

		int shift = tileStacks.getOrDefault(tile, 0);

		int consumed = scaled(style.height + STACK_PADDING, zoom);
		if (actor instanceof NPC && config.showNpcName())
		{
			consumed += scaled(style.fontSize + NAME_GAP, zoom);
		}
		else if (actor == client.getLocalPlayer() && config.replaceOverheadIcon())
		{
			consumed += scaled(STACK_ICON_CLEARANCE + OVERHEAD_ICON_GAP, zoom);
		}

		tileStacks.put(tile, shift + consumed);
		return shift;
	}

	/** Same as claimBarStackSlot, but for a name-only entry (the "Always Show NPC Name" pass). */
	private int claimNameStackSlot(Map<WorldPoint, Integer> tileStacks, NPC npc, BarStyle style, double zoom)
	{
		WorldPoint tile = npc.getWorldLocation();
		if (tile == null)
		{
			return 0;
		}

		int shift = tileStacks.getOrDefault(tile, 0);
		tileStacks.put(tile, shift + scaled(style.fontSize + NAME_GAP + STACK_PADDING, zoom));
		return shift;
	}

	/**
	 * Draws just the NPC name label, at the position it would occupy above the HP bar if the bar
	 * were showing - used for "Always Show NPC Name" on untracked (not in combat) NPCs.
	 */
	private void drawNpcNameOnly(Graphics2D g, NPC npc, Point anchor, BarStyle style, double zoom)
	{
		String npcName = npc.getName();
		if (!isDisplayableName(npcName))
		{
			return;
		}

		int[] rect = barRect(anchor, style, zoom);
		int x = rect[0];
		int y = rect[1];
		int w = rect[2];
		int h = rect[3];
		int nameGap = scaled(NAME_GAP, zoom);
		Color nameColor = config.colorAggressiveNpcNames() && plugin.isNpcAggressive(npc)
			? config.aggressiveNpcNameColor() : config.npcNameColor();
		drawLabel(g, style, Text.removeTags(npcName), x, y - h - nameGap, w, h, zoom, nameColor);
	}

	/**
	 * Small badge to the left of an NPC's HP bar, vertically centered on it, marking it as
	 * currently aggressive - the icon alternative to (or alongside) recoloring the name text.
	 * Anchored to the bar itself so it only ever appears alongside an actual bar. Uses the real
	 * client skull sprite (see aggressiveIcon()) rather than a bespoke asset.
	 */
	private void drawAggressiveNpcIcon(Graphics2D g, int barX, int barY, int barH, double zoom)
	{
		BufferedImage icon = aggressiveIcon();
		if (icon == null)
		{
			return;
		}

		int size = scaled(AGGRESSIVE_ICON_SIZE, zoom);
		int gap = scaled(AGGRESSIVE_ICON_GAP, zoom);
		int iconX = barX - gap - size;
		int iconY = barY + barH / 2 - size / 2;
		g.drawImage(icon, iconX, iconY, size, size, null);
	}

	/**
	 * Filters out NPC names that are really internal/placeholder labels: the literal string
	 * "null" (a documented quirk on some hidden/utility NPCs, guarded against the same way core's
	 * ObjectIndicatorsOverlay does), and any "Category:Label"/"Category;Label"-style name
	 * containing a colon or semicolon (seen on internal mechanic entities like "Enraged:Blue
	 * Moon" - no real OSRS monster name contains either, making this a safe general heuristic).
	 * Only suppresses the name label; the bar itself still shows normally - the actual tracking
	 * gate is CustomHpBarPlugin.HIDDEN_MECHANIC_NPC_NAMES, which normalizes the same separators.
	 */
	private static boolean isDisplayableName(String npcName)
	{
		return npcName != null && !npcName.isEmpty() && !"null".equals(npcName)
			&& npcName.indexOf(':') < 0 && npcName.indexOf(';') < 0;
	}

	private void drawBar(Graphics2D g, Actor actor, Point anchor, int ratio, int scale, int maxHp, BarStyle style)
	{
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int x = rect[0];
		int y = rect[1];
		int w = rect[2];
		int h = rect[3];
		int border = scaled(style.borderWidth, zoom);
		int arc = scaled(style.cornerRadius, zoom) * 2;

		double hpFraction = (double) ratio / scale;
		Color fillColor = actor instanceof NPC && plugin.isLootTainted((NPC) actor) ? LOOT_TAINTED_COLOR : null;
		if (fillColor == null)
		{
			fillColor = plugin.statusEffectColor(actor);
		}
		if (fillColor == null)
		{
			fillColor = style.barColor;
		}
		drawBarShape(g, style, x, y, w, h, border, arc, hpFraction, fillColor);

		if (actor instanceof NPC && config.showAggressiveNpcIcon() && plugin.isNpcAggressive((NPC) actor))
		{
			drawAggressiveNpcIcon(g, x, y, h, zoom);
		}

		if (actor == client.getLocalPlayer() && config.showFoodHealPreview())
		{
			// ratio/scale are the local player's real current/max HP already, not a bucket - no
			// need to re-derive HP from a rounded fraction.
			drawHealPreview(g, x, y, w, h, border, ratio, maxHp, hoveredRestoreValue(Skill.HITPOINTS),
				translucent(fillColor));
		}

		String label = buildLabel(actor, hpFraction, maxHp);
		if (label != null)
		{
			drawLabel(g, style, label, x, y, w, h, zoom, style.textColor);
		}

		int bottomY = y + h;
		if (actor == client.getLocalPlayer() && config.showPrayerBar())
		{
			// Flush against the bottom edge of the HP bar, mirroring the Player Bar profile
			// rather than getting its own size/shape config.
			drawPrayerBar(g, style, x, bottomY, w, h, border, arc, zoom);
			bottomY += h;
		}

		if (showStatusIcons(actor))
		{
			// Below whichever bar is currently lowest, so it doesn't overlap the prayer bar.
			drawStatusIcons(g, plugin.activeStatusEffects(actor), x, bottomY, h);
		}

		// When "Always Show" is on, the dedicated pass in render() is the sole name source -
		// drawing it here too would just be redundant work, not wrong.
		if (actor instanceof NPC && config.showNpcName() && !config.alwaysShowNpcName())
		{
			drawNpcNameOnly(g, (NPC) actor, anchor, style, zoom);
		}
	}

	/**
	 * The stat points the item currently under the cursor would restore, for the given stat, or
	 * -1 if nothing applicable is hovered. Same hover-detection core's "Item Stats" plugin uses:
	 * the last menu entry (what the cursor is over), confirmed as an inventory item slot.
	 *
	 * Delegates the actual heal/restore math to ItemStatChangesService (Item Stats' own public
	 * API, available via @PluginDependency - see CustomHpBarPlugin) rather than a hand-curated
	 * table, so level/gear-dependent formulas (Cooked Moss Lizard, Saradomin Brew, restore
	 * potions) resolve correctly. Mirrors StatusBarsOverlay.getRestoreValue(String): find the
	 * StatChange whose stat name matches and whose getTheoretical() is non-zero.
	 */
	private int hoveredRestoreValue(Skill stat)
	{
		if (client.isMenuOpen())
		{
			return -1;
		}

		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return -1;
		}

		Widget widget = entries[entries.length - 1].getWidget();
		if (widget == null || widget.getId() != InterfaceID.Inventory.ITEMS)
		{
			return -1;
		}

		Effect effect = itemStatService.getItemStatChanges(widget.getItemId());
		if (effect == null)
		{
			return -1;
		}

		StatsChanges changes = effect.calculate(client);
		for (StatChange change : changes.getStatChanges())
		{
			if (change.getTheoretical() != 0 && change.getStat().getName().equals(stat.getName()))
			{
				return change.getTheoretical();
			}
		}

		return -1;
	}

	/**
	 * Extends a bar (HP or Prayer) past its current fill with a preview segment showing where the
	 * stat would land if healAmount were consumed now, capped at maxHp. currentHp/maxHp are raw
	 * values, not a fraction - for the local player they're already exact, so deriving them back
	 * out of a rounded fraction would just reintroduce imprecision.
	 */
	private void drawHealPreview(Graphics2D g, int x, int y, int w, int h, int border, int currentHp, int maxHp,
			int healAmount, Color previewColor)
	{
		if (healAmount <= 0 || maxHp <= 0)
		{
			return;
		}

		int innerW = Math.max(0, w - border * 2);
		int innerH = Math.max(0, h - border * 2);

		int currentFillWidth = (int) Math.round(innerW * ((double) currentHp / maxHp));
		currentFillWidth = Math.max(0, Math.min(currentFillWidth, innerW));

		int healedHp = Math.min(maxHp, currentHp + healAmount);
		int healedFillWidth = (int) Math.round(innerW * ((double) healedHp / maxHp));
		healedFillWidth = Math.max(currentFillWidth, Math.min(healedFillWidth, innerW));

		int previewWidth = healedFillWidth - currentFillWidth;
		if (previewWidth <= 0)
		{
			return;
		}

		g.setColor(previewColor);
		g.fillRect(x + border + currentFillWidth, y + border, previewWidth, innerH);
	}

	/**
	 * Whether the debuff icon row should draw for actor - independent of Color By Status Effect
	 * (split into separate toggles). By actor *type*, not "is this literally me" - other players
	 * share the Player Bar profile's toggle, same as they share its styling.
	 */
	private boolean showStatusIcons(Actor actor)
	{
		if (actor instanceof NPC)
		{
			return config.targetShowStatusIcon();
		}
		if (actor instanceof Player)
		{
			return config.selfShowStatusIcon();
		}
		return false;
	}

	/**
	 * Draws one debuff badge per active status effect, left to right from the bar's left edge,
	 * flush against its bottom edge, each bar-height wide. Iterates StatusEffect.values()
	 * (declared in venom/poison/burn/bleed order) for a consistent order regardless of which
	 * effects are active. Effects without a wired-up icon are silently skipped and reserve no
	 * space; an icon not yet loaded from SpriteManager is also silently skipped for this frame.
	 */
	private void drawStatusIcons(Graphics2D g, Set<CustomHpBarPlugin.StatusEffect> effects, int x, int bottomY, int size)
	{
		if (effects.isEmpty() || size <= 0)
		{
			return;
		}

		int iconX = x;
		for (CustomHpBarPlugin.StatusEffect effect : CustomHpBarPlugin.StatusEffect.values())
		{
			if (!effects.contains(effect))
			{
				continue;
			}
			BufferedImage icon = statusIcon(effect);
			if (icon == null)
			{
				continue;
			}
			g.drawImage(icon, iconX, bottomY, size, size, null);
			iconX += size;
		}
	}

	/**
	 * Maps a status effect to its debuff icon, or null if it has none. Poison/Venom/Burn load
	 * live via SpriteManager; Disease/Corruption use bundled images since no sprite ID exists for
	 * either. Bleed has neither - no RuneLite-confirmed Hitmark sprite ID for it, so it's not
	 * guess-assigned.
	 */
	private BufferedImage statusIcon(CustomHpBarPlugin.StatusEffect effect)
	{
		switch (effect)
		{
			case POISON:
				return poisonIcon();
			case VENOM:
				return venomIcon();
			case BURN:
				return burnIcon();
			case BLEED:
				// Reuses the hitsplat sprite cache - the Bleed hitsplat sprite (4564, confirmed
				// via HITSPLAT_SPRITE_IDS) works as a debuff icon too.
				return hitsplatImage(HitsplatID.BLEED);
			case DISEASE:
				return diseaseIcon();
			case CORRUPTION:
				return corruptionIcon();
			default:
				return null;
		}
	}

	private BufferedImage poisonIcon()
	{
		if (poisonIcon != null)
		{
			return poisonIcon;
		}
		BufferedImage cached = spriteManager.getSprite(SpriteID.Hitmark.HITSPLAT_GREEN_POISON, 0);
		if (cached != null)
		{
			poisonIcon = cached;
			return poisonIcon;
		}
		spriteManager.getSpriteAsync(SpriteID.Hitmark.HITSPLAT_GREEN_POISON, 0, loaded -> poisonIcon = loaded);
		return null;
	}

	private BufferedImage venomIcon()
	{
		if (venomIcon != null)
		{
			return venomIcon;
		}
		BufferedImage cached = spriteManager.getSprite(SpriteID.Hitmark.HITSPLAT_DARK_GREEN_VENOM, 0);
		if (cached != null)
		{
			venomIcon = cached;
			return venomIcon;
		}
		spriteManager.getSpriteAsync(SpriteID.Hitmark.HITSPLAT_DARK_GREEN_VENOM, 0, loaded -> venomIcon = loaded);
		return null;
	}

	private BufferedImage burnIcon()
	{
		if (burnIcon != null)
		{
			return burnIcon;
		}
		BufferedImage cached = spriteManager.getSprite(SpriteID.Hitmark.BURN_DAMAGE, 0);
		if (cached != null)
		{
			burnIcon = cached;
			return burnIcon;
		}
		spriteManager.getSpriteAsync(SpriteID.Hitmark.BURN_DAMAGE, 0, loaded -> burnIcon = loaded);
		return null;
	}

	/**
	 * The PK skull status icon, for the aggressive-NPC badge (drawAggressiveNpcIcon). No confirmed
	 * live SpriteID for this exists (SpriteID.ICON_SKULL was tried first and turned out to be the
	 * wrong sprite once actually seen rendered in-game - a name is not proof of appearance, same
	 * lesson as the earlier StandardPrayer/venom-color corrections), so this is a bundled resource
	 * image instead, downloaded directly from oldschool.runescape.wiki's own "Skull (status) icon"
	 * file - the exact graphic OSRS shows above a skulled player's head - same fallback pattern as
	 * diseaseIcon()/corruptionIcon() below.
	 */
	private BufferedImage aggressiveIcon()
	{
		if (aggressiveIcon == null)
		{
			aggressiveIcon = loadBundledIcon("pk_skull_icon.png");
		}
		return aggressiveIcon;
	}

	private BufferedImage diseaseIcon()
	{
		if (diseaseIcon == null)
		{
			diseaseIcon = loadBundledIcon("disease_hitsplat.png");
		}
		return diseaseIcon;
	}

	private BufferedImage corruptionIcon()
	{
		if (corruptionIcon == null)
		{
			corruptionIcon = loadBundledIcon("corruption_hitsplat.png");
		}
		return corruptionIcon;
	}

	/**
	 * Loads a debuff icon bundled as a plugin resource - only for effects with no live SpriteID
	 * to load via SpriteManager instead. Returns null on any failure rather than throwing; a
	 * missing icon just means that badge doesn't draw.
	 */
	private static BufferedImage loadBundledIcon(String resourceName)
	{
		try (InputStream in = CustomHpBarOverlay.class.getResourceAsStream(resourceName))
		{
			return in != null ? ImageIO.read(in) : null;
		}
		catch (IOException e)
		{
			return null;
		}
	}

	/**
	 * Draws just the Prayer bar, at the position the HP bar itself would occupy (barRect(), same
	 * as drawBar() uses) - reached only when the local player isn't currently tracked, so there's
	 * no HP bar drawn alongside it here.
	 */
	private void drawStandalonePrayerBar(Graphics2D g, Point anchor, BarStyle style)
	{
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int border = scaled(style.borderWidth, zoom);
		int arc = scaled(style.cornerRadius, zoom) * 2;
		drawPrayerBar(g, style, rect[0], rect[1], rect[2], rect[3], border, arc, zoom);
	}

	/**
	 * Draws our replacement of the local player's active overhead prayer icon, a few pixels above
	 * where the HP bar sits (or would sit, out of combat). Only called for the local player when
	 * Replace Overhead Icon is on, at which point the render callback has already suppressed the
	 * native icon, so this is the sole icon on screen.
	 */
	private void drawOverheadIcon(Graphics2D g, Player localPlayer, BarStyle style)
	{
		HeadIcon headIcon = localPlayer.getOverheadIcon();
		if (headIcon == null)
		{
			return;
		}

		BufferedImage image = headIconImage(headIcon);
		if (image == null)
		{
			return;
		}

		Point anchor = Perspective.localToCanvas(client, localPlayer.getLocalLocation(),
			localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight());
		if (anchor == null)
		{
			return;
		}

		// Drawn at the sprite's own natural size (matching how the native client draws it), with
		// zoom scaling layered on top when Scale With Zoom is on.
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int w = scaled(image.getWidth(), zoom);
		int h = scaled(image.getHeight(), zoom);
		int gap = scaled(OVERHEAD_ICON_GAP, zoom);

		int x = rect[0] + (rect[2] - w) / 2;
		int y = rect[1] - gap - h;
		g.drawImage(image, x, y, w, h, null);
	}

	private BufferedImage headIconImage(HeadIcon headIcon)
	{
		BufferedImage cached = headIconImages.get(headIcon);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage loaded = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, headIcon.ordinal());
		if (loaded != null)
		{
			headIconImages.put(headIcon, loaded);
			return loaded;
		}

		spriteManager.getSpriteAsync(SpriteID.HEADICONS_PRAYER, headIcon.ordinal(),
			image -> headIconImages.put(headIcon, image));
		return null;
	}

	/**
	 * Redraws hitsplats landing on the local player, replacing the native ones the plugin's
	 * render callback suppresses when Replace Overhead Icon is on. Draws the real client sprite
	 * for each hitsplat's type (HITSPLAT_SPRITE_IDS) plus the amount in white on top, matching
	 * vanilla rather than a custom shape/color. Anchored at chest height on the model, not above
	 * the head, matching where native hitsplats actually appear. At most MAX_HITSPLATS show at
	 * once, in vanilla's fixed diamond arrangement (below-center, above-center, left, right) -
	 * layout offsets mirror Nameplates' OSRSDisplayType.render() exactly. Each hitsplat's own
	 * getDisappearsOnGameCycle() controls when it stops drawing, matching native timing.
	 */
	private void drawSelfHitsplats(Graphics2D g, Player localPlayer)
	{
		List<Hitsplat> hitsplats = plugin.getSelfHitsplats();
		if (hitsplats.isEmpty())
		{
			return;
		}

		// Native hitsplats render on the body (roughly chest height), not floating above the head
		// like the bar/icon/chat text - there's no dedicated API for the exact attachment point,
		// so half of getLogicalHeight() approximates the model's vertical center.
		Point anchor = Perspective.localToCanvas(client, localPlayer.getLocalLocation(),
			localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return;
		}

		// Hitsplats with no confirmed sprite mapping, or still loading, are simply skipped.
		int currentCycle = client.getGameCycle();
		List<Hitsplat> visible = new ArrayList<>();
		List<BufferedImage> images = new ArrayList<>();
		for (Hitsplat hitsplat : hitsplats)
		{
			if (currentCycle >= hitsplat.getDisappearsOnGameCycle())
			{
				continue;
			}
			BufferedImage image = hitsplatImage(hitsplat.getHitsplatType());
			if (image != null)
			{
				visible.add(hitsplat);
				images.add(image);
			}
		}
		if (visible.isEmpty())
		{
			return;
		}

		// Vanilla replaces the oldest of its 4 slots when a fifth hit lands, so the 4 shown are
		// always the most recent - selfHitsplats is append-ordered, so that's the list's tail.
		if (visible.size() > MAX_HITSPLATS)
		{
			visible = visible.subList(visible.size() - MAX_HITSPLATS, visible.size());
			images = images.subList(images.size() - MAX_HITSPLATS, images.size());
		}

		double zoom = zoomFactor();

		// RuneScape Small at its native size (16), white with a black +1,+1 drop shadow -
		// confirmed against the actual vanilla client rendering (fontPlain11, not the bold font
		// used for chat/HP text).
		Font font = FontManager.getRunescapeSmallFont().deriveFont((float) scaled(16, zoom));
		g.setFont(font);
		FontRenderContext frc = g.getFontRenderContext();

		for (int i = 0; i < visible.size(); i++)
		{
			BufferedImage image = images.get(i);
			int w = scaled(image.getWidth(), zoom);
			int h = scaled(image.getHeight(), zoom);

			// Vanilla's fixed 4-slot diamond: slot 0 below-center, 1 above, 2 left, 3 right.
			int vertMult = i == 0 ? 1 : (i == 1 ? -1 : 0);
			int horizMult = i == 2 ? -1 : (i == 3 ? 1 : 0);
			int centerX = anchor.getX() + horizMult * (w / 2 + scaled(4, zoom));
			int centerY = anchor.getY() + Math.round((vertMult - 0.6f) * (h / 2 - scaled(2, zoom)));

			int x = centerX - w / 2;
			int y = centerY - h / 2;
			g.drawImage(image, x, y, w, h, null);

			String text = String.valueOf(visible.get(i).getAmount());
			Rectangle pixelBounds = new TextLayout(text, font, frc).getPixelBounds(frc, 0, 0);
			int textX = x + (int) Math.round((w - pixelBounds.getWidth()) / 2.0) - pixelBounds.x;
			int textY = y + (int) Math.round((h - pixelBounds.getHeight()) / 2.0) - pixelBounds.y;
			g.setColor(Color.BLACK);
			g.drawString(text, textX + 1, textY + 1);
			g.setColor(Color.WHITE);
			g.drawString(text, textX, textY);
		}
	}

	private BufferedImage hitsplatImage(int hitsplatType)
	{
		BufferedImage cached = hitsplatImages.get(hitsplatType);
		if (cached != null)
		{
			return cached;
		}

		Integer spriteId = HITSPLAT_SPRITE_IDS.get(hitsplatType);
		if (spriteId == null)
		{
			return null;
		}

		BufferedImage loaded = spriteManager.getSprite(spriteId, 0);
		if (loaded != null)
		{
			hitsplatImages.put(hitsplatType, loaded);
			return loaded;
		}

		spriteManager.getSpriteAsync(spriteId, 0, image -> hitsplatImages.put(hitsplatType, image));
		return null;
	}

	/**
	 * Redraws the local player's overhead chat text, replacing the native text suppressed
	 * alongside the rest of the overhead UI pass when Replace Overhead Icon is on.
	 * getOverheadCycle() is a client-decremented countdown, so reading it fresh each frame is
	 * enough to know whether to draw. Styled black-outline-on-yellow to match vanilla.
	 *
	 * Default position matches the native client's own overhead text spot - but the HP/Prayer bar
	 * can occupy that same space when showing, since the bar's position depends on the
	 * configurable verticalOffset. When the bar is shown, the text tucks in beneath the bar stack
	 * instead, to stay clear of the replacement overhead icon above the bar.
	 */
	private void drawOverheadChatText(Graphics2D g, Player localPlayer, BarStyle style)
	{
		if (localPlayer.getOverheadCycle() <= 0)
		{
			return;
		}

		String text = Text.removeFormattingTags(localPlayer.getOverheadText());
		if (text == null || text.isEmpty())
		{
			return;
		}

		Point anchor = Perspective.localToCanvas(client, localPlayer.getLocalLocation(),
			localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight());
		if (anchor == null)
		{
			return;
		}

		double zoom = zoomFactor();
		// RuneScape Bold at its native size (16) - vanilla overhead chat uses the bold font, not
		// the regular one, confirmed against Nameplates' own overhead text rendering.
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) scaled(16, zoom));
		g.setFont(font);
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(text, font, frc).getPixelBounds(frc, 0, 0);

		int x = anchor.getX() - (int) Math.round(pixelBounds.getWidth() / 2.0) - pixelBounds.x;

		int y;
		boolean tracked = plugin.getTrackedActors().containsKey(localPlayer);
		boolean barShown = tracked || (config.showPrayerBar() && plugin.isAnyPrayerActive());
		if (barShown)
		{
			// Tucked beneath the bar stack: the HP bar, plus the Prayer bar's extra row when it's
			// drawn attached below (the standalone prayer-only path is a single row, no second).
			int[] rect = barRect(anchor, style, zoom);
			int stackBottom = rect[1] + rect[3];
			if (tracked && config.showPrayerBar())
			{
				stackBottom += rect[3];
			}
			y = stackBottom + scaled(CHAT_TEXT_BAR_GAP, zoom) - pixelBounds.y;
		}
		else
		{
			// Actor.getCanvasTextLocation() directly - the same projection vanilla's overhead
			// chat text and hitsplats use (ruled out for the *bar* only because of its per-frame
			// animation bob, which is irrelevant for text and in fact matches native behavior here).
			Point textAnchor = localPlayer.getCanvasTextLocation(g, text, localPlayer.getLogicalHeight());
			y = textAnchor != null ? textAnchor.getY() : anchor.getY();
		}

		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.YELLOW);
		g.drawString(text, x, y);
	}

	private void drawPrayerBar(Graphics2D g, BarStyle style, int x, int y, int w, int h, int border, int arc, double zoom)
	{
		int current = client.getBoostedSkillLevel(Skill.PRAYER);
		int max = client.getRealSkillLevel(Skill.PRAYER);
		if (max <= 0)
		{
			return;
		}

		double fraction = (double) current / max;
		drawBarShape(g, style, x, y, w, h, border, arc, fraction, PRAYER_COLOR);

		if (config.showPrayerRestorePreview())
		{
			drawHealPreview(g, x, y, w, h, border, current, max, hoveredRestoreValue(Skill.PRAYER),
				translucent(PRAYER_COLOR));
		}

		drawLabel(g, style, String.valueOf(current), x, y, w, h, zoom, style.textColor);
	}

	/** A bar's own fill color, at the fixed preview alpha - see PREVIEW_ALPHA. */
	private static Color translucent(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), PREVIEW_ALPHA);
	}

	/** Draws one bar's background/fill/border - shared by the HP bar and the prayer bar. */
	private void drawBarShape(Graphics2D g, BarStyle style, int x, int y, int w, int h,
			int border, int arc, double fraction, Color fillColor)
	{
		int innerW = Math.max(0, w - border * 2);
		int innerH = Math.max(0, h - border * 2);
		int fillWidth = (int) Math.round(innerW * fraction);
		fillWidth = Math.max(0, Math.min(fillWidth, innerW));

		RoundRectangle2D outline = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

		g.setColor(style.background);
		g.fill(outline);

		if (fillWidth > 0)
		{
			int fillArc = Math.max(0, arc - border * 2);
			RoundRectangle2D fillShape = new RoundRectangle2D.Float(
				x + border, y + border, fillWidth, innerH, fillArc, fillArc);

			Paint previousPaint = g.getPaint();
			g.setPaint(glossPaint(fillColor, x + border, y + border, innerH));
			g.fill(fillShape);
			g.setPaint(previousPaint);
		}

		if (border > 0)
		{
			// BasicStroke draws centered on the path, so stroking `outline` directly would put
			// half the border outside (x, y, w, h) - over whatever's behind the bar - and half
			// inside. Insetting the stroked path by half the border width keeps the whole stroke
			// inside (x, y, w, h), fully backed by the background fill above.
			float half = border / 2f;
			RoundRectangle2D borderPath = new RoundRectangle2D.Float(
				x + half, y + half, w - border, h - border, Math.max(0, arc - border), Math.max(0, arc - border));
			Stroke previousStroke = g.getStroke();
			g.setStroke(new BasicStroke(border));
			g.setColor(style.borderColor);
			g.draw(borderPath);
			g.setStroke(previousStroke);
		}
	}

	/** Vertical gradient from a lightened highlight at the top to the base color at the bottom. */
	private Paint glossPaint(Color base, int x, int y, int height)
	{
		if (height <= 0)
		{
			return base;
		}
		Color highlight = lighten(base, GRADIENT_HIGHLIGHT);
		return new GradientPaint(x, y, highlight, x, y + height, base);
	}

	private static Color lighten(Color c, float factor)
	{
		int r = c.getRed() + (int) ((255 - c.getRed()) * factor);
		int g = c.getGreen() + (int) ((255 - c.getGreen()) * factor);
		int b = c.getBlue() + (int) ((255 - c.getBlue()) * factor);
		return new Color(
			Math.min(255, r), Math.min(255, g), Math.min(255, b), c.getAlpha());
	}

	private void drawLabel(Graphics2D g, BarStyle style, String label, int x, int y, int w, int h, double zoom, Color textColor)
	{
		Font font = resolveFont(style.fontFamily, style.fontStyle, scaled(style.fontSize, zoom));
		g.setFont(font);

		// Centered on the label's actual rendered (pixel-hinted) glyph bounds, not the font's
		// nominal ascent/descent metrics - different fonts reserve very different amounts of
		// headroom, and hinting snaps glyphs to the pixel grid in ways vector bounds don't
		// reflect. getPixelBounds() accounts for both; textVerticalNudge covers any residual offset.
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(label, font, frc).getPixelBounds(frc, 0, 0);
		int nudge = scaled(style.textNudge, zoom);
		int textX = x + (int) Math.round((w - pixelBounds.getWidth()) / 2.0) - pixelBounds.x;
		int textY = y + (int) Math.round((h - pixelBounds.getHeight()) / 2.0) - pixelBounds.y + nudge;

		g.setColor(Color.BLACK);
		if (style.textOutline)
		{
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx != 0 || dy != 0)
					{
						g.drawString(label, textX + dx, textY + dy);
					}
				}
			}
		}
		else
		{
			g.drawString(label, textX + 1, textY + 1);
		}

		g.setColor(textColor);
		g.drawString(label, textX, textY);
	}

	private Font resolveFont(CustomHpBarConfig.FontFamily family, CustomHpBarConfig.FontStyle style, float size)
	{
		Font base;
		switch (family)
		{
			case RUNESCAPE_BOLD:
				base = FontManager.getRunescapeBoldFont();
				break;
			case RUNESCAPE:
				base = FontManager.getRunescapeFont();
				break;
			case RUNESCAPE_SMALL:
				base = FontManager.getRunescapeSmallFont();
				break;
			case SYSTEM_DEFAULT:
			default:
				base = FontManager.getDefaultFont();
				break;
		}
		return base.deriveFont(style.getAwtStyle(), size);
	}

	private static int scaled(int value, double zoom)
	{
		return (int) Math.round(value * zoom);
	}

	private String buildLabel(Actor actor, double hpFraction, int maxHp)
	{
		int pct = (int) Math.round(hpFraction * 100);
		CustomHpBarConfig.DisplayMode mode;
		boolean isTarget;
		if (actor == client.getLocalPlayer())
		{
			mode = config.selfDisplayMode();
			isTarget = false;
		}
		else if (actor instanceof Player)
		{
			mode = config.playerDisplayMode();
			isTarget = false;
		}
		else
		{
			mode = config.targetDisplayMode();
			isTarget = true;
		}

		switch (mode)
		{
			case NUMBER:
				return maxHp > 0 ? String.valueOf((int) Math.round(hpFraction * maxHp)) : pct + "%";
			case PERCENT:
				return pct + "%";
			case BOTH:
				if (maxHp <= 0)
				{
					return pct + "%";
				}
				int hp = (int) Math.round(hpFraction * maxHp);
				return isTarget ? hp + " " + pct + "%" : hp + " (" + pct + "%)";
			default:
				return null;
		}
	}

	/**
	 * Returns the actor's max HP, or -1 if unknown (falls back to percent display). The native
	 * boss HP HUD wins first, whatever the actor type. Otherwise NPCs go through
	 * CustomHpBarPlugin.resolveNpcMaxHp(); the local player's real max HP comes from their
	 * Hitpoints skill level. Other players' max HP isn't obtainable client-side, so NUMBER/BOTH
	 * fall back to percent for them.
	 */
	private int resolveMaxHp(Actor actor)
	{
		int[] hud = plugin.nativeHudHp(actor);
		if (hud != null)
		{
			return hud[1];
		}
		if (actor instanceof NPC)
		{
			return plugin.resolveNpcMaxHp(((NPC) actor).getId());
		}
		if (actor == client.getLocalPlayer())
		{
			return client.getRealSkillLevel(Skill.HITPOINTS);
		}
		return -1;
	}

	@AllArgsConstructor
	private static final class BarStyle
	{
		final int width;
		final int height;
		final int cornerRadius;
		final int borderWidth;
		final Color borderColor;
		final Color barColor;
		final Color background;
		final int verticalOffset;
		final CustomHpBarConfig.FontFamily fontFamily;
		final CustomHpBarConfig.FontStyle fontStyle;
		final int fontSize;
		final Color textColor;
		final boolean textOutline;
		final int textNudge;
	}
}
