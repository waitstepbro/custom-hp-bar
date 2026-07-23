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

	/**
	 * Fixed prayer bar fill color, matching OSRS's own prayer point/orb blue. Not configurable
	 * (see showPrayerBar's config description) - only its shape/border/font mirror the Player
	 * Bar profile.
	 */
	private static final Color PRAYER_COLOR = new Color(60, 130, 220);

	/**
	 * Alpha applied to a bar's own fill color for its heal/restore preview segment - distinct
	 * enough from the solid current-value fill to read as "not real yet" without needing a
	 * separate configurable color (the preview always matches whatever color the bar itself is
	 * currently showing, status-effect tint included, rather than a fixed independent color).
	 */
	private static final int PREVIEW_ALPHA = 110;

	/** Gap between the NPC name label and the HP bar's top edge. Not configurable yet. */
	private static final int NAME_GAP = 2;

	/** Gap between the overhead icon and the HP bar's top edge, before zoom scaling. */
	private static final int OVERHEAD_ICON_GAP = 3;

	/**
	 * Max simultaneous hitsplats drawn on the local player - the vanilla engine gives each actor
	 * exactly 4 hitsplat slots (confirmed via Nameplates' CappedDisplayType default, decompiled).
	 */
	private static final int MAX_HITSPLATS = 4;

	/** Vertical padding between bars of actors sharing the same tile, before zoom scaling. */
	private static final int STACK_PADDING = 2;

	/**
	 * Approximate overhead icon height reserved when the local player's bar is in a same-tile
	 * stack (the real sprites are ~this size; using an approximation avoids depending on
	 * whether one has loaded yet). Only affects stack spacing, not the icon's own rendering.
	 */
	private static final int STACK_ICON_CLEARANCE = 24;

	/** Gap between the overhead chat text and the HP bar/icon above which it's moved, before zoom scaling. */
	private static final int CHAT_TEXT_BAR_GAP = 3;

	/**
	 * Real client sprite ID for every hitsplat's actual background graphic, keyed by
	 * HitsplatID's own type constant - not a guess: confirmed by decompiling the Nameplates Hub
	 * plugin's HitsplatDefaultSprite enum, which pairs its own (hitsplatType, spriteId) constants
	 * 1:1 against HitsplatID's real values (cross-checked directly against the decompiled
	 * HitsplatID class - e.g. its BLOCK_ME=12 entry maps to sprite 1358, matching
	 * SpriteID.Hitmark.HITSPLAT_BLUE_MISS=1358 exactly). Drawing the real sprite instead of a
	 * custom color/shape is what "exactly the same as vanilla" requires - unlike the debuff
	 * icons elsewhere in this file, every hitsplat variant relevant here has a confirmed ID, so
	 * there's no guessing involved.
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
	 * baseline for zoom scaling. There's no documented universal reference zoom value to
	 * calibrate against up front, and a wrong guess inflates every bar/font size uniformly
	 * (this is what happened with an earlier hardcoded 512 guess) — capturing whatever zoom
	 * the user is actually playing at guarantees the configured pixel sizes are exactly right
	 * at that zoom, and only scale relative to it as the user zooms in/out from there.
	 */
	private int baselineZoom = -1;

	/**
	 * The real Poison/Venom/Burn hitsplat sprites, loaded live from the running client via
	 * SpriteManager rather than bundled as plugin resources. SpriteManager.getSprite() reads from
	 * its own cache and returns null until the sprite has actually loaded; getSpriteAsync() below
	 * populates that cache in the background. Cached into these fields too once loaded so repeat
	 * frames don't need to go back through SpriteManager's own cache lookup at all.
	 */
	private BufferedImage poisonIcon;
	private BufferedImage venomIcon;
	private BufferedImage burnIcon;

	/**
	 * Disease/Corruption debuff icons - unlike Poison/Venom/Burn above, these have no confirmed
	 * SpriteID.Hitmark entry to load live via SpriteManager, so they're bundled resource images
	 * instead (downloaded from the OSRS Wiki's Disease/Corruption hitsplat pages, at the user's
	 * explicit request after the SpriteManager-only approach was raised as a concern). Loaded
	 * once via loadBundledIcon() and cached the same way as the SpriteManager-backed icons.
	 */
	private BufferedImage diseaseIcon;
	private BufferedImage corruptionIcon;

	/**
	 * All 15 overhead icon graphics live as sub-frames of one client sprite
	 * (SpriteID.HEADICONS_PRAYER = 440), indexed by HeadIcon.ordinal() - confirmed by decompiling
	 * the Nameplates Hub plugin, whose hardcoded per-icon frame table matches HeadIcon's declared
	 * enum order 1:1. Cached per icon type once loaded, same lazy-load-then-cache pattern as the
	 * status effect icons above.
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
		// Antialiased shapes (smooth rounded corners) but non-antialiased text: at the small
		// sizes this bar uses, antialiasing blurs pixel-style fonts (including the default
		// RuneScape Bold font) into a mushy gray smear rather than crisp readable strokes -
		// the game's own UI text isn't antialiased either, for the same reason.
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		// Resolved lazily, at most once each per frame no matter how many actors share a
		// profile - every tracked NPC used to rebuild an identical BarStyle (a dozen-odd config
		// reads plus an allocation) from scratch every frame.
		BarStyle targetStyle = null;
		BarStyle playerStyle = null;

		// Same-tile stacking state, rebuilt each frame: actors standing on the exact same tile
		// would otherwise draw their bars/names directly on top of each other (multiple stacked
		// NPCs was the reported case). tileStacks accumulates the pixels already claimed above
		// each tile; appliedStacks remembers each drawn actor's shift so the "Always Show NPC
		// Name" pass below can place a tracked NPC's name on its (shifted) bar instead of
		// re-deriving a fresh, mismatched slot for it.
		Map<WorldPoint, Integer> tileStacks = new HashMap<>();
		Map<Actor, Integer> appliedStacks = new HashMap<>();

		for (Map.Entry<Actor, Integer> entry : plugin.getTrackedActors().entrySet())
		{
			Actor actor = entry.getKey();

			// NPC filtering happens in CustomHpBarPlugin.isTrackedType() now, before an NPC is
			// ever added to trackedActors - a filtered-out NPC never reaches this loop at all,
			// so there's nothing to re-check here.
			int maxHp = resolveMaxHp(actor);
			int[] hp = resolveHp(actor, maxHp);
			if (hp == null)
			{
				continue;
			}

			// Project the top of the actor model to screen space. Perspective.localToCanvas
			// (not actor.getCanvasTextLocation) is deliberate: getCanvasTextLocation is the
			// same call overhead chat/hitsplat text uses, which has a per-frame bob baked in
			// (a legacy effect for floating text) - fine for text, wrong for a bar that's
			// supposed to sit rock-steady like the native one. localToCanvas with the actor's
			// ground position + logical height (the same pattern the core Party/Prayer plugins'
			// own overhead status overlays use) only moves with actual world position.
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
		// reached for actors in trackedActors) - which for the local player means only while
		// "in combat" (see CustomHpBarPlugin.isInCombat/onGameTick), so praying at a bank or
		// before a fight starts showed nothing at all. This second, independent path draws the
		// Prayer bar on its own, at the same position the HP bar would occupy, whenever a
		// prayer is actually toggled on - regardless of combat state. Skipped entirely if the
		// main loop above already drew it (localPlayer in trackedActors) to avoid a double draw.
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

		// Our replacement for the native overhead prayer icon, which CustomHpBarPlugin's render
		// callback suppresses for the local player when Replace Overhead Icon is on (see its
		// renderCallback field) - so this copy is the only icon on screen, not a duplicate.
		// Drawn independent of combat/tracking state, same reasoning as the standalone Prayer
		// bar above: the native icon it replaces showed at all times, so this must too.
		if (localPlayer != null && config.showForSelf() && config.replaceOverheadIcon())
		{
			playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
			drawOverheadIcon(g, localPlayer, playerStyle);
			drawSelfHitsplats(g, localPlayer);
			drawOverheadChatText(g, localPlayer, playerStyle);
		}

		// Second pass over every nearby NPC, for the two "regardless of combat" behaviors:
		// Always Show NPC Bar (draw a bar on any attackable NPC even before it's engaged) and
		// Always Show NPC Name (the sole name source when it's on - see below). Both iterate the
		// same NPC list, so they share one loop to avoid double-claiming same-tile stack slots.
		boolean alwaysBar = config.alwaysShowNpcBar();
		boolean alwaysName = config.showNpcName() && config.alwaysShowNpcName();
		if (alwaysBar || alwaysName)
		{
			double zoom = zoomFactor();
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				// matchesNpcFilter() is the "could I attack this" gate (combat level, hidden-
				// mechanic exclusion, name filter - see CustomHpBarPlugin.isTrackedNpc), so it's
				// exactly the set of NPCs Always Show NPC Bar means by "every NPC you could attack."
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

				// An NPC the main loop already drew (in appliedStacks) reuses that exact shift so
				// its name/bar here lands on the same slot; otherwise it claims a fresh slot sized
				// to whatever this pass will draw (a full bar, or just a name).
				boolean barAlreadyDrawn = appliedStacks.containsKey(npc);
				Integer applied = appliedStacks.get(npc);
				int shift = applied != null ? applied
					: (alwaysBar ? claimBarStackSlot(tileStacks, npc, targetStyle, zoom)
						: claimNameStackSlot(tileStacks, npc, targetStyle, zoom));
				if (shift > 0)
				{
					anchor = new Point(anchor.getX(), anchor.getY() - shift);
				}

				// Draw the bar for any attackable NPC not already drawn by the main loop. An NPC
				// with no live HP (idle, never hit - getHealthRatio() == -1) shows a full bar,
				// since a full-health NPC should read as full; real ratio/precise data takes over
				// as soon as it (or anyone) damages it. drawBar() also handles this NPC's name on
				// its own when Always Show NPC Name is off, so we only draw the name below when
				// it's on (matching the tracked-actor path in the main loop).
				if (alwaysBar && !barAlreadyDrawn)
				{
					int maxHp = resolveMaxHp(npc);
					int[] hp = resolveHp(npc, maxHp);
					if (hp == null)
					{
						hp = new int[]{1, 1};
					}
					drawBar(g, npc, anchor, hp[0], hp[1], maxHp, targetStyle);
				}

				// Always Show NPC Name is the sole name source when on, for both tracked NPCs
				// (drawBar skipped their name) and untracked ones - see the long-standing note
				// below in drawBar about why "tracked" never implies "name already drawn."
				if (alwaysName)
				{
					drawNpcNameOnly(g, npc, anchor, targetStyle, zoom);
				}
			}
		}

		return null;
	}

	/**
	 * Returns [current, max] HP for display, or null if no HP data is available at all.
	 *
	 * For NPCs with an established precise estimate (hitsplat-tracked, see CustomHpBarPlugin
	 * .preciseNpcHp), that always wins over the coarse getHealthRatio()/getHealthScale() bucket
	 * - it's strictly finer-grained once established. Otherwise delegates to CustomHpBarPlugin
	 * .readHp(), which always returns live data for the local player (see its doc comment) and
	 * falls back to the last cached values for everyone else while the native bar has faded but
	 * the actor is still within its persist window (governed by the plugin's tick-based
	 * eviction, not here).
	 */
	private int[] resolveHp(Actor actor, int maxHp)
	{
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
	 * Zoom multiplier applied to every pixel dimension (bar size, border, corner radius,
	 * font size, vertical offset) so the bar grows/shrinks along with the actor model
	 * instead of staying a fixed screen size regardless of camera distance.
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
	 * The bar's on-screen rectangle for a given anchor/style/zoom - centered on the anchor point,
	 * then shifted by the configurable vertical offset (positive moves the bar upward, i.e.
	 * decreasing Y in screen space). Shared by drawBar() and drawNpcNameOnly() so an "Always Show
	 * NPC Name" label sits at exactly the same position the name would occupy if the bar were
	 * also showing - it shouldn't jump when an NPC enters/leaves combat.
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
	 * apply (0 for the first actor on its tile, so the feature is invisible unless actors are
	 * actually stacked). The consumed height covers everything this actor draws *upward* from
	 * its bar top - the NPC name label, or the local player's replacement overhead icon - so the
	 * next actor's bar clears it. Downward extras (prayer bar, status icons) don't matter here,
	 * since stacking only ever pushes later actors up, never down.
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
	 * were showing - used for "Always Show NPC Name" on NPCs that aren't currently tracked (i.e.
	 * not in combat), so there's no bar to draw.
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
	 * Filters out NPC names that are really internal/placeholder labels, not something meant for
	 * a player to see. Two confirmed patterns so far, both surfaced by "Always Show NPC Name"
	 * iterating every matching NPC regardless of combat relevance (the old combat-gated-only
	 * behavior never encountered either, since neither of these NPCs is normally attacked):
	 *
	 * - The literal string "null" (not an absent/null reference) - a documented quirk RuneLite's
	 *   own core ObjectIndicatorsOverlay already guards against the same way
	 *   ("null".equals(composition.getName())). Common on hidden/utility NPCs used purely for
	 *   mechanics (e.g. a marker NPC standing on a boss room's "stand here" tile).
	 * - A "Category:Label" style name (e.g. "Enraged:Blue Moon", seen on a 0%-HP NPC right after
	 *   the real Blue Moon boss died, despite that boss having no actual enraged phase per the
	 *   OSRS Wiki) - looks like an internal categorization label, not a display name. No real
	 *   OSRS monster name contains a colon, so treating any colon as "not a display name" is a
	 *   safe, general heuristic rather than hardcoding this one boss's specific string - it
	 *   should also catch similar leaks on other bosses' hidden phase/transition NPCs.
	 *
	 * Only suppresses the name label - the bar itself (if the NPC is otherwise trackable) still
	 * shows normally either way.
	 */
	private static boolean isDisplayableName(String npcName)
	{
		return npcName != null && !npcName.isEmpty() && !"null".equals(npcName) && npcName.indexOf(':') < 0;
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
		Color fillColor = plugin.statusEffectColor(actor);
		if (fillColor == null)
		{
			fillColor = style.barColor;
		}
		drawBarShape(g, style, x, y, w, h, border, arc, hpFraction, fillColor);

		if (actor == client.getLocalPlayer() && config.showFoodHealPreview())
		{
			// ratio/scale are the local player's real current/max HP (see CustomHpBarPlugin
			// .readHp), not a bucket - using them directly here avoids re-deriving HP from a
			// rounded fraction.
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
			// Flush against the bottom edge of the HP bar, same width/style - "mirrors" the
			// Player Bar profile per the request, rather than getting its own size/shape config.
			drawPrayerBar(g, style, x, bottomY, w, h, border, arc, zoom);
			bottomY += h;
		}

		if (showStatusIcons(actor))
		{
			// Below whichever bar is currently lowest (HP bar, or the prayer bar beneath it for
			// the local player) rather than always at the HP bar's own edge, so it doesn't
			// overlap the prayer bar when both are showing.
			drawStatusIcons(g, plugin.activeStatusEffects(actor), x, bottomY, h);
		}

		// When "Always Show" is on, the dedicated pass in render() is the sole source of NPC
		// names (see its comment) - drawing it here too would just be redundant, not wrong, but
		// there's no reason to do the work twice.
		if (actor instanceof NPC && config.showNpcName() && !config.alwaysShowNpcName())
		{
			drawNpcNameOnly(g, (NPC) actor, anchor, style, zoom);
		}
	}

	/**
	 * The amount of stat points the item currently under the cursor in the player's inventory
	 * would restore, for the given stat (Hitpoints or Prayer), or -1 if nothing applicable is
	 * hovered. Same hover-detection RuneLite's own core "Item Stats" plugin uses
	 * (ItemStatOverlay): check the last entry in the current menu (the one the cursor is
	 * actually over), confirm it's an inventory item slot specifically
	 * (InterfaceID.Inventory.ITEMS), then look up its item ID.
	 *
	 * Delegates the actual heal/restore math to ItemStatChangesService (the core "Item Stats"
	 * plugin's own public API, made available here via @PluginDependency(ItemStatPlugin.class) -
	 * see CustomHpBarPlugin) rather than a hand-curated per-item table: this handles every food
	 * and potion correctly, including level/gear-dependent formulas (e.g. Cooked Moss Lizard's
	 * Cooking/Hunter-scaled heal, Saradomin Brew's Prayer restore, Prayer/Super Restore potions),
	 * which a fixed-value lookup table could never cover completely. Mirrors
	 * StatusBarsOverlay.getRestoreValue(String) exactly (confirmed via decompile): find the
	 * StatChange whose Stat.getName() matches the target skill's name and whose getTheoretical()
	 * is non-zero, returning that value (0 stays unmatched, so unrelated items or stats the item
	 * doesn't affect naturally fall through to -1 below).
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
	 * stat would land if healAmount were consumed right now, capped at maxHp since neither stat
	 * can overheal past its max. currentHp/maxHp are passed as raw values (not a fraction) since
	 * for the local player they're already exact real numbers (see CustomHpBarPlugin.readHp for
	 * HP; client.getBoostedSkillLevel/getRealSkillLevel for Prayer) - deriving them back out of a
	 * rounded fraction here would just reintroduce imprecision for no reason.
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
	 * Whether the debuff icon row should draw at all for actor - independent of Color By Status
	 * Effect, per the request to split the two into separate toggles rather than one gating both.
	 * By actor *type* (any Player vs. NPC), not "is this literally me" - other players share the
	 * Player Bar profile's toggle, same as they share its styling (see resolveStyle()).
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
	 * Draws one debuff badge per currently active status effect, left to right starting at the
	 * bar's left edge and flush against its bottom edge, each one bar-height wide - so e.g. Venom
	 * and Bleed active at once show as two adjacent icons instead of overlapping. Iterates
	 * StatusEffect.values() (declared in venom/poison/burn/bleed order) so the left-to-right
	 * order is always consistent regardless of which effects happen to be active. Effects without
	 * a wired-up icon (Bleed currently) are silently skipped, and don't reserve any space - only
	 * effects that actually draw something advance iconX. Uses whatever's currently cached from
	 * SpriteManager for each icon - silently skips one that hasn't loaded yet.
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
	 * Maps a status effect to its debuff icon, or null if it doesn't have one (or none is
	 * active). Poison/Venom/Burn load live via SpriteManager (real, confirmed SpriteID.Hitmark
	 * entries exist for all three); Disease/Corruption use bundled resource images instead, since
	 * no such sprite ID exists for either. Bleed has neither: of Hitmark's 54 total sprite IDs,
	 * only 7 have a RuneLite-confirmed name, and Bleed isn't one of them - the other ~47 are
	 * unnamed `_N` entries with no verified meaning, not something to guess-assign without
	 * visually confirming one in-game first, and no bundled image was requested for it either.
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
				// Reuses the hitsplat sprite cache - Bleed had no confirmed SpriteID.Hitmark
				// entry when the other icons were wired up, but the hitsplat sprite mapping
				// added later (HITSPLAT_SPRITE_IDS, verified via Nameplates' decompiled
				// HitsplatDefaultSprite) confirmed 4564 as the real Bleed hitsplat sprite,
				// unblocking this. Same live-from-client loading as Poison/Venom/Burn.
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
	 * Loads a debuff icon bundled as a plugin resource (src/main/resources/com/customhpbar/) -
	 * used only for effects with no confirmed live SpriteID to load via SpriteManager instead
	 * (see poisonIcon()/venomIcon()/burnIcon() for the preferred approach). Returns null on any
	 * failure (missing resource, corrupt file) rather than throwing - a missing debuff icon just
	 * means that badge doesn't draw, not a reason to break the whole overlay.
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
	 * Draws just the Prayer bar, at the position the HP bar itself would normally occupy
	 * (barRect(), same as drawBar() uses) rather than flush beneath it - there's no HP bar drawn
	 * alongside it in this path, since it's only reached when the local player isn't currently
	 * tracked (see render()).
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
	 * Draws our replacement copy of the local player's active overhead prayer icon, centered a
	 * few pixels above where the HP bar sits (or would sit - barRect() is position, not presence,
	 * so this works out of combat too). Only ever called for the local player, and only when
	 * Replace Overhead Icon is on - in which case the plugin's render callback has already
	 * suppressed the native icon, so this is the sole icon on screen rather than a duplicate.
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

		// Drawn at the sprite's own natural dimensions rather than a hardcoded badge size, so it
		// matches the native icon this is replacing exactly (the native client draws these
		// sprites unscaled too). Zoom scaling still applies when Scale With Zoom is on, same as
		// every other element - zoomFactor() is 1.0 otherwise, leaving the natural size intact.
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
	 * Redraws hitsplats landing on the local player, replacing the native ones that
	 * CustomHpBarPlugin's render callback suppresses along with the rest of the overhead UI pass
	 * when Replace Overhead Icon is on (Hitsplat isn't its own Renderable, so it can't be kept
	 * while suppressing the health bar/icon - see that field's own doc comment). Draws the real
	 * client sprite for each hitsplat's type (HITSPLAT_SPRITE_IDS) at its own natural size, plus
	 * the amount in white on top - matching vanilla exactly rather than a custom shape/color,
	 * per explicit request. Anchored at roughly chest height on the character model, not above
	 * the head like the health bar/icon/chat text - matching where native hitsplats actually
	 * appear (on the character, not floating above it). At most MAX_HITSPLATS (4, the same
	 * number of simultaneous hitsplat slots the vanilla engine gives an actor) show at once, in
	 * vanilla's fixed diamond arrangement (below-center, above-center, left, right) rather than
	 * a row that grows with every hit - an earlier row-based version stretched across the whole
	 * screen when many NPCs attacked at once. Layout offsets mirror Nameplates' vanilla-replica
	 * OSRSDisplayType.render() exactly (decompiled): x = anchor + horizMult * (w/2 + 4),
	 * y = anchor + (vertMult - 0.6) * (h/2 - 2), cap 4 (its CappedDisplayType default). Each
	 * hitsplat's own Hitsplat.getDisappearsOnGameCycle() controls exactly when it stops being
	 * drawn, matching native timing rather than an arbitrary duration.
	 */
	private void drawSelfHitsplats(Graphics2D g, Player localPlayer)
	{
		List<Hitsplat> hitsplats = plugin.getSelfHitsplats();
		if (hitsplats.isEmpty())
		{
			return;
		}

		// Native hitsplats render on the character's body (roughly chest height), not floating
		// above the head the way the health bar/overhead icon/chat text do - getLogicalHeight()
		// (the same anchor those use) is explicitly documented as "roughly where the health bar
		// is drawn," which is too high for this. There's no dedicated API for the exact chest
		// attachment point the native renderer uses, so half of getLogicalHeight() approximates
		// it as the vertical center of the model instead of its top.
		Point anchor = Perspective.localToCanvas(client, localPlayer.getLocalLocation(),
			localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return;
		}

		// Hitsplats with no confirmed sprite mapping or still loading asynchronously are simply
		// skipped rather than drawn as a blank slot.
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

		// RuneScape Small at its default size (16, FontManager's own baked-in native size for
		// all three RuneScape TTFs), white with a black +1,+1 drop shadow, centered on the splat.
		// Verified against the actual vanilla client rendering (317 reference, Game.java): it draws
		// the number with fontPlain11 (the small/"p11" plain font = getRunescapeSmallFont here, NOT
		// the bold font used for chat/HP text), a black shadow, and a white pass offset so the
		// shadow sits +1,+1 down-right of it - exactly this. Only scaled further with Scale With Zoom.
		Font font = FontManager.getRunescapeSmallFont().deriveFont((float) scaled(16, zoom));
		g.setFont(font);
		FontRenderContext frc = g.getFontRenderContext();

		for (int i = 0; i < visible.size(); i++)
		{
			BufferedImage image = images.get(i);
			int w = scaled(image.getWidth(), zoom);
			int h = scaled(image.getHeight(), zoom);

			// Vanilla's fixed 4-slot diamond: slot 0 sits below-center, 1 above, 2 left, 3 right
			// (see the method doc comment for the decompiled source of these exact offsets).
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
	 * Redraws the local player's overhead chat text (speech bubble), replacing the native text
	 * suppressed alongside the rest of the overhead UI pass when Replace Overhead Icon is on.
	 * Actor.getOverheadCycle() is a countdown (client ticks remaining until the text times out,
	 * per its own javadoc), decremented by the client itself - reading it fresh each frame is
	 * enough to know whether to draw, no local tracking needed the way hitsplats required.
	 * Styled black-outline-on-yellow to match the native chat overhead convention, same
	 * black/text-color outline approach drawLabel() uses for the HP number.
	 *
	 * Default position matches the native client's own overhead text spot - but the HP/Prayer
	 * bar can occupy exactly that space when it's actually showing, since the bar's position
	 * depends on the configurable verticalOffset. When the bar is shown, the text tucks in
	 * beneath the bar stack instead (below rather than above, to stay clear of the replacement
	 * overhead icon that sits above the bar).
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
		// RuneScape Bold at its default size (16, FontManager's baked-in native size) - vanilla
		// overhead chat is drawn with the bold font, not the regular one, confirmed against
		// Nameplates' own drawOverheadTexts (decompiled: sets getRunescapeBoldFont() before
		// drawing its black-shadow+yellow overhead text, the same style being replicated here).
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
			// Tucked beneath the bar stack: the HP bar, plus the Prayer bar's extra row when
			// it's drawn attached below (only in the tracked/combat case - the standalone
			// prayer-only path draws a single bar at the HP bar's own position, no second row).
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
			// Native default position: the model's logical height plus a small 15 world-unit
			// offset, projected to screen - the same "height + 15" Nameplates' own
			// drawOverheadText passes to getCanvasTextLocation (decompiled), which is where the
			// vanilla client draws overhead chat. A world-space offset, not a fixed pixel one -
			// it naturally shrinks/grows with camera distance the way the native text does.
			Point textAnchor = Perspective.localToCanvas(client, localPlayer.getLocalLocation(),
				localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight() + 15);
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
			// BasicStroke draws centered on the given path, so stroking `outline` directly would
			// put half the border width outside (x, y, w, h) entirely - over whatever's behind
			// the bar, never touched by the background fill above - and half inside it. Since
			// the default border color is translucent, that outer half blends with the game
			// scene while the inner half blends with Background Color, producing a visibly
			// mismatched ring that shifts whenever Background Color changes. Insetting the
			// stroked path by half the border width makes the stroke span exactly [x, x+border]
			// (etc. on each side) - fully inside (x, y, w, h) and fully backed by the background
			// fill, matching what innerW/innerH above already assume.
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

		// Center on the label's actual rendered (pixel-hinted) glyph bounds, not the font's
		// nominal ascent/descent metrics. Different fonts - especially the bitmap-style
		// RuneScape ones vs. a generic system font - reserve very different amounts of unused
		// headroom in their ascent, and hinting snaps glyphs to the pixel grid in ways a pure
		// vector bounding box doesn't reflect. getPixelBounds() accounts for both. A residual
		// per-font/per-user offset is still possible, hence the manual textVerticalNudge.
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
		if (actor == client.getLocalPlayer())
		{
			mode = config.selfDisplayMode();
		}
		else if (actor instanceof Player)
		{
			mode = config.playerDisplayMode();
		}
		else
		{
			mode = config.targetDisplayMode();
		}

		switch (mode)
		{
			case NUMBER:
				return maxHp > 0 ? String.valueOf((int) Math.round(hpFraction * maxHp)) : pct + "%";
			case PERCENT:
				return pct + "%";
			case BOTH:
				return maxHp > 0 ? (int) Math.round(hpFraction * maxHp) + " (" + pct + "%)" : pct + "%";
			default:
				return null;
		}
	}

	/**
	 * Returns the actor's max HP, or -1 if unknown (falls back to percent display).
	 * NPCs come from the static ID lookup table. The local player's real max HP is
	 * available via their Hitpoints skill level. Other players' max HP isn't obtainable
	 * client-side at all, so NUMBER/BOTH modes fall back to percent for them.
	 */
	private int resolveMaxHp(Actor actor)
	{
		if (actor instanceof NPC)
		{
			return NpcMaxHpTable.getMaxHp(((NPC) actor).getId());
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
