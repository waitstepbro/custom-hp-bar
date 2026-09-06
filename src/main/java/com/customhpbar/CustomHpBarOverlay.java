package com.customhpbar;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.SkullIcon;
import net.runelite.api.coords.LocalPoint;
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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
class CustomHpBarOverlay extends Overlay
{
	private static final double MIN_ZOOM_SCALE = 0.4;
	private static final double MAX_ZOOM_SCALE = 3.0;

	/** Subtle top-of-bar highlight for a glossier, less flat look. */
	private static final float GRADIENT_HIGHLIGHT = 0.2f;

	/** Fixed grey for both grey-out toggles - overrides the status tint and the aggressive name color. */
	private static final Color LOOT_TAINTED_COLOR = new Color(120, 120, 120);

	/** Alpha for a bar's heal/restore preview segment - reads as "not real yet" over the bar's own color. */
	private static final int PREVIEW_ALPHA = 110;

	/** How far a matched damage trail is darkened from the bar color it copies, and the alpha it lands at. */
	private static final float TRAIL_MATCH_DARKEN = 0.45f;
	private static final int TRAIL_MATCH_ALPHA = 190;

	/** Gap between the NPC name label and the HP bar's top edge. Not configurable yet. */
	private static final int NAME_GAP = 2;

	/** Trailing whitespace left behind by a name truncation, non-breaking space included. */
	private static final Pattern TRAILING_SPACE = Pattern.compile("[\\s\\u00A0]+$");

	/**
	 * Lower bound of each band in LEVEL_DIFF_COLORS, on the target's combat level minus your own,
	 * descending. The negative half is -3/-6/-9 because these are lower bounds - the issue's screenshot
	 * labels the same three-wide bands by their upper bound, so do not "correct" them to its numbers.
	 */
	private static final int[] LEVEL_DIFF_BANDS = {10, 7, 4, 1, 0, -3, -6, -9};

	/**
	 * The game's own relative-combat-level gradient: red far above you, through yellow at parity, to green
	 * far below. Fixed rather than configurable (issue #35) - these are vanilla's own right-click stops.
	 * One entry longer than LEVEL_DIFF_BANDS; the last is the fall-through.
	 */
	private static final Color[] LEVEL_DIFF_COLORS = {
		new Color(0xFF0000), new Color(0xFF3000), new Color(0xFF7000), new Color(0xFFB000),
		new Color(0xFFFF00),
		new Color(0xC0FF00), new Color(0x80FF00), new Color(0x40FF00), new Color(0x00FF00),
	};

	/** Size of the aggressive-NPC badge icon (see showAggressiveNpcIcon), before zoom scaling. */
	private static final int AGGRESSIVE_ICON_SIZE = 12;

	/** Gap between the aggressive-NPC icon and the name label it sits above, before zoom scaling. */
	private static final int AGGRESSIVE_ICON_GAP = 2;

	/** Size of the elemental-weakness icon (see showNpcWeaknessIcon), before zoom scaling. */
	private static final int WEAKNESS_ICON_SIZE = 14;

	/** Gap between the weakness icon and the HP bar's right edge, before zoom scaling. */
	private static final int WEAKNESS_ICON_GAP = 2;

	/** Gap between the weakness icon and the percent label to its right, before zoom scaling. */
	private static final int WEAKNESS_PERCENT_GAP = 1;

	/** Gap between the overhead icon and the HP bar's top edge, before zoom scaling. */
	private static final int OVERHEAD_ICON_GAP = 3;

	/** Max simultaneous hitsplats drawn on the local player - the vanilla engine gives each actor exactly 4 slots. */
	private static final int MAX_HITSPLATS = 4;

	/** Vertical padding between bars of actors sharing the same tile, before zoom scaling. */
	private static final int STACK_PADDING = 2;

	/**
	 * How much closer to a new tile a model must be before its entry moves to that tile's stack, in local
	 * units (128 = one tile). A bare lookup flips at the boundary, half a tile before the model finishes
	 * arriving, so a name reached the stack visibly ahead of its owner. See stackTile().
	 */
	private static final int STACK_TILE_HYSTERESIS = 96;

	/** One tile in local units - the cap on how far a stack tile may lag its actor. */
	private static final int LOCAL_TILE_SIZE = Perspective.LOCAL_TILE_SIZE;

	/** Full value for a 0-100 percentage bar with no separate max - special attack and run energy. */
	private static final int FULL_PERCENT_ENERGY = 100;

	/** Width in pixels of the Prayer bar's sweeping tick-timer indicator, before zoom scaling. */
	private static final int PRAYER_TICK_TIMER_WIDTH = 2;

	/** Approximate overhead icon height reserved in a same-tile stack, in case the real sprite hasn't loaded. */
	private static final int STACK_ICON_CLEARANCE = 24;

	/** Real client sprite ID for each hitsplat's background graphic, keyed by HitsplatID's type constant. */
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

	/** Camera zoom at the first render, the "1.0x" baseline for zoom scaling - see zoomFactor(). */
	private int baselineZoom = -1;

	/** Client sprites already loaded, keyed by sprite ID + frame index - see clientSprite(). */
	private final Map<Long, BufferedImage> clientSprites = new HashMap<>();

	/** Icons bundled as plugin resources, keyed by file name - for graphics with no confirmed client SpriteID. */
	private final Map<String, BufferedImage> bundledIcons = new HashMap<>();

	/**
	 * This frame's overhead icon owner per tile - the one other player whose skull/prayer row draws there.
	 * See resolveIconOwners(), which picks them, and iconRowShown(), which suppresses everyone else.
	 */
	private Map<WorldPoint, Player> iconOwners = new HashMap<>();

	/**
	 * When each player was first seen on the tile they're on now - feeds resolveIconOwners()'s "first one
	 * there wins". Double-buffered per frame like the rest of this file's state, so a despawn needs no
	 * cleanup. Ownership only: no screen position is stored or borrowed here (see claimStackSlot()).
	 */
	private Map<Player, TileArrival> tileArrivals = new HashMap<>();

	/**
	 * Each actor's stack tile, this frame and last - stackTile() decides once per actor per frame and reads
	 * the previous answer to apply its hysteresis. Double-buffered like the rest of this file's per-frame
	 * state, so an actor that left the scene is simply not carried forward.
	 */
	private Map<Actor, WorldPoint> stackTiles = new HashMap<>();
	private Map<Actor, WorldPoint> previousStackTiles = new HashMap<>();

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
		// Antialiased shapes but not text - at these pixel sizes AA blurs the pixel-hinted fonts.
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		// Resolved lazily, at most once each per frame however many actors share a profile. playerStyle is
		// self and otherPlayerStyle everyone else - split because resolveStyle() gives each a different
		// verticalOffset, and one cache would leak whichever got resolved first.
		BarStyle targetStyle = null;
		BarStyle playerStyle = null;
		BarStyle otherPlayerStyle = null;

		Player localPlayer = client.getLocalPlayer();

		// Rotated before anything reads a tile: stackTile() fills the new map as it goes and
		// consults the old one for hysteresis.
		previousStackTiles = stackTiles;
		stackTiles = new HashMap<>();

		// "Prioritize Self on Same Tile": self's tile when the feature applies, else null. See
		// suppressedForSelfTile().
		WorldPoint selfPriorityTile = config.prioritizeSelfOnSameTile() && localPlayer != null && config.showForSelf()
			? localPlayer.getWorldLocation() : null;

		// Resolved before anything draws - drawSkullIcon()/drawOverheadIcon() read iconOwners to
		// suppress every non-owner's icon row, and the loops below defer each owner's entry.
		Map<Player, TileArrival> frameArrivals = new HashMap<>();
		Map<WorldPoint, Player> frameIconOwners = new HashMap<>();
		resolveIconOwners(frameIconOwners, frameArrivals, localPlayer, selfPriorityTile);
		iconOwners = frameIconOwners;
		tileArrivals = frameArrivals;

		// Other players whose whole entry is deferred to the end of render() so their icon row lands
		// on top of their tile's stack - see resolveIconOwners() and the deferred pass below.
		Map<Player, DeferredIconEntry> deferredIcons = new LinkedHashMap<>();

		// The local player's own bar is drawn last of everything below, so it never ends up buried
		// under an NPC's bar (map/loop order is otherwise arbitrary - see TODO.md idea 7). These
		// hold whichever path (tracked HP bar vs. untracked standalone Prayer/Run bar) applies this frame.
		Point playerAnchor = null;
		int[] playerHp = null;
		int playerMaxHp = 0;
		BarStyle playerDrawStyle = null;
		List<CustomHpBarConfig.BarKind> playerStandaloneStack = null;

		// Same-tile stacking, rebuilt each frame: tileStacks holds each tile's claimed top edge and the X its
		// entries align to, seeded by the first claimant; appliedStacks lets the "Always Show" passes reuse an
		// already-resolved anchor rather than re-deriving it.
		Map<WorldPoint, Point> tileStacks = new HashMap<>();
		Map<Actor, Point> appliedStacks = new HashMap<>();

		// npcStackCounts/npcStackDecided enforce npcStackLimit() - see npcStackAllowed().
		Map<WorldPoint, Integer> npcStackCounts = new HashMap<>();
		Map<NPC, Boolean> npcStackDecided = new HashMap<>();

		// playerStackCounts/playerStackDecided enforce playerNameStackLimit() - see playerStackAllowed().
		Map<WorldPoint, Integer> playerStackCounts = new HashMap<>();
		Map<Player, Boolean> playerStackDecided = new HashMap<>();

		// Reserves self's own stack height before either claim pass below runs, regardless of
		// iteration order - see reserveSelfStackHeight()'s own doc and CLAUDE.md, "REGRESSION #4".
		reserveSelfStackHeight(tileStacks, localPlayer);

		for (Map.Entry<Actor, Integer> entry : plugin.getTrackedActors().entrySet())
		{
			Actor actor = entry.getKey();

		// Cheapest check first, before any HP resolution or stack-limit charge - a suppressed actor is
		// invisible to the whole same-tile system, not just skipped at draw time.
			if (suppressedForSelfTile(actor, selfPriorityTile))
			{
				continue;
			}

			// Filtering already happened in CustomHpBarPlugin.isTrackedType() - nothing to re-check.
			int maxHp = resolveMaxHp(actor);
			int[] hp = resolveHp(actor, maxHp);
			if (hp == null)
			{
				continue;
			}

			// Charged once per NPC/other-player regardless of pass - a capped-out one skips entirely
			// here (no slot claimed, no reservation), not just its name. Self is never subject to
			// either cap.
			if (actor instanceof NPC && !npcStackAllowed(npcStackCounts, npcStackDecided, (NPC) actor))
			{
				continue;
			}
			if (actor instanceof Player && actor != localPlayer
				&& !playerStackAllowed(playerStackCounts, playerStackDecided, (Player) actor))
			{
				continue;
			}

			Point anchor = actorAnchor(actor);
			if (anchor == null)
			{
				continue;
			}

			BarStyle style;
			if (actor instanceof Player)
			{
				if (actor == localPlayer)
				{
					style = playerStyle != null ? playerStyle : (playerStyle = resolveStyle(actor));
				}
				else
				{
					style = otherPlayerStyle != null ? otherPlayerStyle : (otherPlayerStyle = resolveStyle(actor));
				}
			}
			else
			{
				style = targetStyle != null ? targetStyle : (targetStyle = resolveStyle(actor));
			}

			// Their tile's icon owner claims and draws in the deferred pass at the end of render()
			// instead, so their icon row ends up on top of the tile's whole stack.
			if (actor instanceof Player && actor != localPlayer && isIconOwner((Player) actor))
			{
				DeferredIconEntry deferred = new DeferredIconEntry((Player) actor, anchor, style);
				deferred.drawBar = true;
				deferred.hp = hp;
				deferred.maxHp = maxHp;
				deferredIcons.put((Player) actor, deferred);
				continue;
			}

			anchor = claimBarStackSlot(tileStacks, actor, anchor, style, zoomFactor());
			appliedStacks.put(actor, anchor);

			if (actor == localPlayer)
			{
				// Stashed, not drawn here - see the deferred draw below.
				playerAnchor = anchor;
				playerHp = hp;
				playerMaxHp = maxHp;
				playerDrawStyle = style;
				continue;
			}

			drawBar(g, actor, anchor, hp[0], hp[1], maxHp, style);
		}

		// "Fade Bar On Death": the one pass that draws an actor already gone from trackedActors, and only
		// because beginDeathFade() saw its bar on screen as it died - issue #22's gates are all untouched.
		// Runs straight after the tracked loop so a corpse claims its slot ahead of the "Always Show" passes.
		if (config.fadeNpcBarOnDeath() && !plugin.getDeathFades().isEmpty())
		{
			long now = System.currentTimeMillis();
			int fadeMs = Math.max(1, config.npcDeathFadeDuration());
			double zoom = zoomFactor();
			for (Map.Entry<Actor, Long> fade : plugin.getDeathFades().entrySet())
			{
				if (!(fade.getKey() instanceof NPC))
				{
					continue;
				}

				// isTrackedNpcCached() re-checked every frame, not just when the fade started, so
				// blacklisting an NPC (or any other config change) stops one mid-fade.
				NPC npc = (NPC) fade.getKey();
				float alpha = 1f - (now - fade.getValue()) / (float) fadeMs;
				if (alpha <= 0f || alpha > 1f || !plugin.isTrackedNpcCached(npc)
					|| suppressedForSelfTile(npc, selfPriorityTile)
					|| !npcStackAllowed(npcStackCounts, npcStackDecided, npc))
				{
					continue;
				}

				Point anchor = actorAnchor(npc);
				if (anchor == null)
				{
					continue;
				}

				targetStyle = targetStyle != null ? targetStyle : resolveStyle(npc);
				anchor = claimBarStackSlot(tileStacks, npc, anchor, targetStyle, zoom);
				appliedStacks.put(npc, anchor);

				int maxHp = resolveMaxHp(npc);
				// A corpse still reports ratio 0 through its death animation, so this normally
				// resolves live; the fallback only covers a read going invalid mid-fade, where an
				// empty bar is what was being drawn anyway.
				int[] hp = resolveHp(npc, maxHp);
				if (hp == null)
				{
					hp = new int[]{0, 1};
				}

				Graphics2D fadeGraphics = (Graphics2D) g.create();
				fadeGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				drawBar(fadeGraphics, npc, anchor, hp[0], hp[1], maxHp, targetStyle);
				// drawBar()'s name branch needs the NPC tracked and the "Always Show NPC Name" pass skips a corpse,
				// so neither draws this one - and the name has to fade with the bar rather than popping off the
				// instant it dies.
				if (config.showNpcName())
				{
					drawNpcNameOnly(fadeGraphics, npc, anchor, targetStyle, zoom);
				}
				fadeGraphics.dispose();
			}
		}

		// Independent path: shows the Prayer/Special/Run/HP bars outside combat too, per each bar's own
		// "always show" toggle or activity (playerBarStack()). Skipped if the main loop already drew the
		// player. Stashed for the same deferred-draw reason as the tracked case above.
		if (playerHp == null && localPlayer != null && config.showForSelf() && !plugin.getTrackedActors().containsKey(localPlayer))
		{
			List<CustomHpBarConfig.BarKind> stack = playerBarStack(false);
			Point anchor = stack.isEmpty() ? null : actorAnchor(localPlayer);
			if (anchor != null)
			{
				playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);

				// HP present (alwaysShowHpBar) goes through the same deferred drawBar() call as the tracked case,
				// which draws every bar kind in the stack. Without HP, drawStandaloneBarStack draws the rest
				// directly, since drawBar() is otherwise HP's own entry point.
				int maxHp = resolveMaxHp(localPlayer);
				int[] hp = stack.contains(CustomHpBarConfig.BarKind.HP) ? resolveHp(localPlayer, maxHp) : null;
				if (hp != null)
				{
					playerAnchor = anchor;
					playerHp = hp;
					playerMaxHp = maxHp;
					playerDrawStyle = playerStyle;
				}
				else
				{
					playerAnchor = anchor;
					playerDrawStyle = playerStyle;
					playerStandaloneStack = stack;
				}
			}
		}

		// Second pass for "regardless of combat" behaviors: Always Show NPC Bar/Name, one shared
		// loop so they don't double-claim same-tile stack slots.
		boolean alwaysBar = config.alwaysShowNpcBar();
		boolean alwaysName = config.showNpcName() && config.alwaysShowNpcName();
		if (alwaysBar || alwaysName)
		{
			double zoom = zoomFactor();
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				// Cached per game tick, not recomputed every frame - see CustomHpBarPlugin.isTrackedNpcCached().
				if (npc == null || !plugin.isTrackedNpcCached(npc) || suppressedForSelfTile(npc, selfPriorityTile))
				{
					continue;
				}

				// Bankers and fishing spots have no HP; talk-only NPCs have a level but no fight in them. Both can
				// still draw a name, but a fresh kill must not: hasAttackOption() reads static composition data, so
				// isConfirmedDead is what makes a corpse's name disappear in step with its bar.
				boolean confirmedDead = CustomHpBarPlugin.isConfirmedDead(npc);
				boolean drawBarForThis = alwaysBar && plugin.isAttackableNpc(npc) && !confirmedDead;
				boolean drawNameForThis = alwaysName && isDisplayableName(npc.getName()) && !confirmedDead
					&& !plugin.isPetNameHidden(npc);

				// Decided before claiming a slot: claiming one for an NPC that then draws nothing
				// would shift every other bar on its tile upwards for no visible reason.
				if (!drawBarForThis && !drawNameForThis)
				{
					continue;
				}

				// Same charge/skip as the main loop above - reuses the cached decision if this NPC
				// was already considered there (tracked case), so it's never charged twice.
				if (!npcStackAllowed(npcStackCounts, npcStackDecided, npc))
				{
					continue;
				}

				Point anchor = actorAnchor(npc);
				if (anchor == null)
				{
					continue;
				}

				targetStyle = targetStyle != null ? targetStyle : resolveStyle(npc);

				// Non-null once the main loop above already drew this NPC's bar - reuse its
				// resolved anchor rather than claiming a second slot for the same actor.
				Point applied = appliedStacks.get(npc);
				anchor = applied != null ? applied
					: (drawBarForThis
						? claimBarStackSlot(tileStacks, npc, anchor, targetStyle, zoom)
						: claimNameStackSlot(tileStacks, npc, anchor, targetStyle, zoom));

				// No live HP yet (never hit) shows a full bar until real data takes over.
				if (drawBarForThis && applied == null)
				{
					int maxHp = resolveMaxHp(npc);
					int[] hp = resolveHp(npc, maxHp);
					if (hp == null)
					{
						hp = new int[]{1, 1};
					}
					drawBar(g, npc, anchor, hp[0], hp[1], maxHp, targetStyle);
				}

				if (drawNameForThis)
				{
					drawNpcNameOnly(g, npc, anchor, targetStyle, zoom);
				}
			}
		}

		// "Always Show Player Bar"/"Always Show Player Name" in one shared loop, so they don't double-claim
		// stack slots. Runs unconditionally: it is also the only place a skulled or praying player with no
		// bar and no name gets drawn. alwaysShowPlayerName deliberately doesn't require showForPlayers.
		boolean alwaysPlayerBar = config.showForPlayers() && config.alwaysShowPlayerBar();
		boolean alwaysPlayerName = config.showPlayerName() && config.alwaysShowPlayerName();
		{
			double zoom = zoomFactor();
			for (Player other : client.getTopLevelWorldView().players())
			{
				// This loop bypasses trackedActors, so the Player Blacklist has to be re-checked here -
				// same reason the NPC pass above calls isTrackedNpcCached() rather than trusting it.
				if (other == null || other == localPlayer || !plugin.isTrackedPlayer(other))
				{
					continue;
				}

				// "Prioritize Self on Same Tile" takes their bar, name and icon row, but the render callback still
				// suppresses their whole native overhead bundle - so chat text and hitsplats have to be redrawn here
				// or they vanish outright. Eligibility gates it: redrawing a still-native one would double it up.
				if (suppressedForSelfTile(other, selfPriorityTile))
				{
					if (plugin.isOverheadEligible(other))
					{
						drawHitsplats(g, other);
						drawOverheadChatText(g, other);
					}
					continue;
				}

				// isConfirmedDead as in the NPC pass above - this loop bypasses trackedActors, so a dying player's
				// bar would otherwise keep drawing on the death animation for as long as they stay in the scene.
				boolean drawBarForThis = alwaysPlayerBar && !CustomHpBarPlugin.isConfirmedDead(other);
				boolean drawNameForThis = alwaysPlayerName && isDisplayableName(other.getName());

				// No bar and no name this frame: the only reason left to consider them is a skull or overhead icon
				// whose native version updateOverheadEligiblePlayers() already suppressed. Deliberately skips stack
				// claiming below - an icon-only player isn't part of the stack, just its own anchor.
				boolean iconOnly = !drawBarForThis && !drawNameForThis && hasOverheadIcons(other);
				if (!drawBarForThis && !drawNameForThis && !iconOnly)
				{
					continue;
				}

				Point anchor = actorAnchor(other);
				if (anchor == null)
				{
					continue;
				}

				otherPlayerStyle = otherPlayerStyle != null ? otherPlayerStyle : resolveStyle(other);

				DeferredIconEntry deferred = deferredIcons.get(other);
				boolean iconOwner = deferred != null || isIconOwner(other);

				if (iconOnly)
				{
					// An icon-only owner is deferred like any other and claims a slot sized to its icon row alone
					// (claimIconStackSlot()); staying out of the stack would punch the tile's one icon through
					// whatever the rest of it stacked above.
					if (iconOwner)
					{
						if (deferred == null)
						{
							deferredIcons.put(other, new DeferredIconEntry(other, anchor, otherPlayerStyle));
						}
						continue;
					}

					// Non-null only if another pass already drew this player this frame - shouldn't happen for an
					// icon-only one, but appliedStacks is authoritative either way.
					if (appliedStacks.get(other) == null)
					{
						drawSkullIcon(g, other, anchor, otherPlayerStyle, false);
						drawOverheadIcon(g, other, anchor, otherPlayerStyle, false);
						drawHitsplats(g, other);
						drawOverheadChatText(g, other);
					}
					continue;
				}

				// Same charge/skip as the main loop above - reuses the cached decision if this
				// player was already considered there (tracked case), so it's never charged twice.
				if (!playerStackAllowed(playerStackCounts, playerStackDecided, other))
				{
					continue;
				}

				// Non-null once the main loop above already drew this player - reuse its resolved
				// anchor rather than claiming a second slot for the same actor.
				Point applied = appliedStacks.get(other);

				// Deferred to the end of render() - the entry collects both passes' decisions, since a tracked
				// owner can also be picked up here for its "Always Show Player Name" row. Only if nothing
				// deferred it already.
				if (iconOwner)
				{
					if (deferred == null)
					{
						deferred = new DeferredIconEntry(other, anchor, otherPlayerStyle);
						deferredIcons.put(other, deferred);
					}
					deferred.drawBar |= drawBarForThis;
					deferred.drawName |= drawNameForThis;
					if (deferred.drawBar && deferred.hp == null)
					{
						int maxHp = resolveMaxHp(other);
						int[] hp = resolveHp(other, maxHp);
						deferred.hp = hp != null ? hp : new int[]{1, 1};
						deferred.maxHp = maxHp;
					}
					continue;
				}

				anchor = applied != null ? applied
					: (drawBarForThis
						? claimBarStackSlot(tileStacks, other, anchor, otherPlayerStyle, zoom)
						: claimNameStackSlot(tileStacks, other, anchor, otherPlayerStyle, zoom));

				// No live HP yet (never hit, or not visible without a native bar) shows a full bar
				// until real data takes over - same convention as the NPC always-show pass.
				if (drawBarForThis && applied == null)
				{
					int maxHp = resolveMaxHp(other);
					int[] hp = resolveHp(other, maxHp);
					if (hp == null)
					{
						hp = new int[]{1, 1};
					}
					drawBar(g, other, anchor, hp[0], hp[1], maxHp, otherPlayerStyle);
				}

				if (drawNameForThis)
				{
					drawPlayerNameOnly(g, other, anchor, otherPlayerStyle, zoom);

					// Icon/hitsplats/chat text already came from whichever drawBar() call touched this player this
					// frame - both draw them unconditionally for other players - so only draw them when neither did.
					if (applied == null && !drawBarForThis)
					{
						// isNamesVisible(), not a hardcoded true - drawNameForThis only means the
						// name is config-enabled, not that it actually drew this frame (the hotkey
						// may have hidden it). See the drawBar()-tail call site's own comment.
						boolean nameLiveShown = plugin.isNamesVisible();
						drawSkullIcon(g, other, anchor, otherPlayerStyle, nameLiveShown);
						drawOverheadIcon(g, other, anchor, otherPlayerStyle, nameLiveShown);
						drawHitsplats(g, other);
						drawOverheadChatText(g, other);
					}
				}
			}
		}

		// Every tile's icon owner, claimed after all three passes above so its slot is that tile's
		// topmost and its icon row draws clear of every name below it - see resolveIconOwners().
		// Nothing claims after this, which is also why no claim here reserves the icon row itself.
		{
			double zoom = zoomFactor();
			for (DeferredIconEntry deferred : deferredIcons.values())
			{
				Player other = deferred.player;
				Point anchor = deferred.drawBar
					? claimBarStackSlot(tileStacks, other, deferred.anchor, deferred.style, zoom)
					: deferred.drawName
						? claimNameStackSlot(tileStacks, other, deferred.anchor, deferred.style, zoom)
						: claimIconStackSlot(tileStacks, other, deferred.anchor, deferred.style, zoom);
				appliedStacks.put(other, anchor);

				if (deferred.drawBar)
				{
					drawBar(g, other, anchor, deferred.hp[0], deferred.hp[1], deferred.maxHp, deferred.style);
				}

				if (deferred.drawName)
				{
					drawPlayerNameOnly(g, other, anchor, deferred.style, zoom);
				}

				// Same split as the two passes above: drawBar() already drew all of these for a
				// player it drew a bar for, so this only covers the name-only and icon-only entries.
				if (!deferred.drawBar)
				{
					boolean nameLiveShown = deferred.drawName && plugin.isNamesVisible();
					drawSkullIcon(g, other, anchor, deferred.style, nameLiveShown);
					drawOverheadIcon(g, other, anchor, deferred.style, nameLiveShown);
					drawHitsplats(g, other);
					drawOverheadChatText(g, other);
				}
			}
		}

		// Deferred from above so it paints over every NPC bar/name already drawn this frame.
		if (playerHp != null)
		{
			drawBar(g, localPlayer, playerAnchor, playerHp[0], playerHp[1], playerMaxHp, playerDrawStyle);
		}
		else if (playerStandaloneStack != null)
		{
			drawStandaloneBarStack(g, playerAnchor, playerDrawStyle, playerStandaloneStack);
		}

		// Replacement for the native overhead prayer icon (and skull, if any), which the render
		// callback suppresses. Drawn last of all - it's anchored above the player's own bar, so
		// it's already clear of every NPC bar drawn above.
		if (localPlayer != null && config.showForSelf())
		{
			playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
			// playerAnchor is null whenever self drew neither a bar nor a standalone stack above
			// (every bar kind toggled off) - falls back to the raw anchor in that case, same as
			// every other actor with nothing else to key off of.
			Point selfIconAnchor = playerAnchor != null ? playerAnchor : actorAnchor(localPlayer);
			// nameShown false - self never gets a name row above their own bar (drawBar()'s name
			// branch explicitly excludes self; the "Always Show Player Name" pass skips them too).
			drawSkullIcon(g, localPlayer, selfIconAnchor, playerStyle, false);
			drawOverheadIcon(g, localPlayer, selfIconAnchor, playerStyle, false);
			drawHitsplats(g, localPlayer);
			drawOverheadChatText(g, localPlayer);
		}

		return null;
	}

	/** [current, max] HP for display: native boss HUD, then precise hitsplat tracking, then live/last-known. */
	private int[] resolveHp(Actor actor, int maxHp)
	{
		// Ahead of every other source: while shielded the HUD and the hitsplat tally both describe
		// Doom's hitpoints, which is a different pool than the one being drawn.
		int[] shield = plugin.doomShieldHp(actor);
		if (shield != null)
		{
			return shield;
		}

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

	/** Canvas anchor at the actor's logical height, the native bar's Y level - see the overload below. */
	private Point actorAnchor(Actor actor)
	{
		return actorAnchor(actor, actor.getLogicalHeight());
	}

	/** localToCanvas, not getCanvasTextLocation - the latter has a per-frame animation bob. */
	private Point actorAnchor(Actor actor, int height)
	{
		return Perspective.localToCanvas(client, actor.getLocalLocation(), actor.getWorldView().getPlane(), height);
	}

	/** Zoom multiplier applied to every pixel dimension so the bar scales with the actor model, not screen distance. */
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
			// Vertical offset plus the color/opacity cluster differ between self and another player -
			// everything else in the Player style is shared. See render()'s playerStyle/otherPlayerStyle
			// split, which caches this per-frame and must not conflate the two now that they diverge.
			boolean self = actor == client.getLocalPlayer();
			int verticalOffset = self ? config.playerVerticalOffset() : config.otherPlayerVerticalOffset();
			Color barColor = self ? config.playerBarColor() : config.otherPlayerBarColor();
			boolean hpColorGradient = self ? config.playerHpColorGradient() : config.otherPlayerHpColorGradient();
			Color colorMid = self ? config.playerColorMid() : config.otherPlayerColorMid();
			Color colorLow = self ? config.playerColorLow() : config.otherPlayerColorLow();
			int midpoint = self ? config.playerMidpoint() : config.otherPlayerMidpoint();
			Color barBackground = self ? config.playerBarBackground() : config.otherPlayerBarBackground();
			int barOpacity = self ? config.playerBarOpacity() : config.otherPlayerBarOpacity();
			Color textColor = self ? config.playerTextColor() : config.otherPlayerTextColor();
			boolean damageTrail = self ? config.playerDamageTrail() : config.otherPlayerDamageTrail();
			Color damageTrailColor = self ? config.playerDamageTrailColor() : config.otherPlayerDamageTrailColor();
			boolean damageTrailMatchBar = self ? config.playerDamageTrailMatchBar() : config.otherPlayerDamageTrailMatchBar();
			return new BarStyle(
				config.playerBarWidth(), config.playerBarHeight(), config.playerCornerRadius(),
				config.playerBorderWidth(), config.playerBorderColor(), barColor,
				hpColorGradient, colorMid, colorLow,
				midpoint,
				barBackground, barOpacity, damageTrail, damageTrailColor, damageTrailMatchBar, verticalOffset,
				config.playerFontFamily(), config.playerFontStyle(), config.playerFontSize(),
				textColor, config.playerTextOutline(), config.playerTextVerticalNudge(),
				config.playerTextAlignment());
		}
		return new BarStyle(
			config.targetBarWidth(), config.targetBarHeight(), config.targetCornerRadius(),
			config.targetBorderWidth(), config.targetBorderColor(), config.targetBarColor(),
			config.targetHpColorGradient(), config.targetColorMid(), config.targetColorLow(),
			config.targetMidpoint(),
			config.targetBarBackground(), config.targetBarOpacity(),
			config.targetDamageTrail(), config.targetDamageTrailColor(), config.targetDamageTrailMatchBar(),
			config.targetVerticalOffset(),
			config.targetFontFamily(), config.targetFontStyle(), config.targetFontSize(),
			config.targetTextColor(), config.targetTextOutline(), config.targetTextVerticalNudge(),
			config.targetTextAlignment());
	}

	/**
	 * The bar's on-screen rectangle, centered on anchor. Shared by drawBar() and both name drawers (and
	 * the overhead icon/chat positioning keyed off them) so labels don't jump between them. verticalOffset
	 * always applies, bar showing or not - otherwise it silently does nothing to a lone name.
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
	 * Whether actor gets no bar or name at all this frame under "Prioritize Self on Same Tile" - an NPC or
	 * other player on selfPriorityTile exactly, never self. A null tile (feature off, no local player, Show
	 * for Self off) suppresses nobody. Checked before any stack-limit charge or seeding, not at draw time.
	 */
	private boolean suppressedForSelfTile(Actor actor, WorldPoint selfPriorityTile)
	{
		return selfPriorityTile != null && actor != client.getLocalPlayer()
			&& selfPriorityTile.equals(actor.getWorldLocation());
	}

	/**
	 * Picks, per tile, the one other player whose overhead icon row draws this frame: the earliest arrival
	 * there with a skull or prayer icon, whose whole entry is then deferred to the top of that tile's
	 * stack. Earliest-arrival never changes hands under them. Self is never a candidate - its row is its own.
	 */
	private void resolveIconOwners(Map<WorldPoint, Player> owners, Map<Player, TileArrival> arrivals,
			Player localPlayer, WorldPoint selfPriorityTile)
	{
		int tick = client.getTickCount();
		Map<WorldPoint, TileArrival> best = new HashMap<>();
		for (Player other : client.getTopLevelWorldView().players())
		{
			if (other == null || other == localPlayer)
			{
				continue;
			}
			WorldPoint tile = stackTile(other);
			if (tile == null)
			{
				continue;
			}

			// Carried over as long as they're still on the same tile, so "when did they get here"
			// survives across frames without any despawn cleanup - see tileArrivals's own doc.
			TileArrival previous = tileArrivals.get(other);
			TileArrival arrival = previous != null && tile.equals(previous.tile) ? previous : new TileArrival(tile, tick);
			arrivals.put(other, arrival);

			if (suppressedForSelfTile(other, selfPriorityTile) || !hasOverheadIcons(other))
			{
				continue;
			}

			TileArrival incumbent = best.get(tile);
			if (incumbent == null || arrival.tick < incumbent.tick)
			{
				best.put(tile, arrival);
				owners.put(tile, other);
			}
		}
	}

	/** Whether player's tile picked them as its one icon-row owner this frame - see resolveIconOwners(). */
	private boolean isIconOwner(Player player)
	{
		WorldPoint tile = stackTile(player);
		return tile != null && iconOwners.get(tile) == player;
	}

	/**
	 * Whether player's overhead icon row draws at all this frame: always for self, and for the one other
	 * player per tile resolveIconOwners() picked. A non-owner shows none - the native one is already
	 * suppressed, so that is the intent rather than a side effect.
	 */
	private boolean iconRowShown(Player player)
	{
		return player == client.getLocalPlayer() || isIconOwner(player);
	}

	/** Whether player has anything in their overhead icon row at all - a PK skull, a prayer icon, or both. */
	private static boolean hasOverheadIcons(Player player)
	{
		return player.getSkullIcon() != SkullIcon.NONE || player.getOverheadIcon() != null;
	}

	/**
	 * Height of the overhead icon row (PK skull and/or prayer icon) a player draws above their
	 * bar/name row, matching drawSkullIcon()/drawOverheadIcon()'s own layout - 0 when they have
	 * neither. STACK_ICON_CLEARANCE stands in for a prayer sprite the client hasn't loaded yet.
	 */
	private int overheadRowClearance(Player player, double zoom)
	{
		int clearance = skullClearance(player, zoom);
		HeadIcon headIcon = player.getOverheadIcon();
		if (headIcon != null)
		{
			BufferedImage image = headIconImage(headIcon);
			clearance += scaled((image != null ? image.getHeight() : STACK_ICON_CLEARANCE) + OVERHEAD_ICON_GAP, zoom);
		}
		return clearance;
	}

	/**
	 * Claims self's own same-tile slot before either claim pass runs, so self is effectively first at
	 * its tile whatever the iteration order - others get pushed above it rather than overlapping it.
	 * Self is never shifted itself: claimBarStackSlot() returns its raw anchor untouched.
	 */
	private void reserveSelfStackHeight(Map<WorldPoint, Point> tileStacks, Player localPlayer)
	{
		if (localPlayer == null || !config.showForSelf())
		{
			return;
		}
		WorldPoint tile = stackTile(localPlayer);
		if (tile == null)
		{
			return;
		}

		if (playerBarStack(plugin.getTrackedActors().containsKey(localPlayer)).isEmpty())
		{
			return;
		}

		Point anchor = actorAnchor(localPlayer);
		if (anchor == null)
		{
			return;
		}

		BarStyle style = resolveStyle(localPlayer);
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		// overheadRowClearance(), same as every other player - reserving the icon row unconditionally
		// put 27px of empty space above a self with no skull and no prayer icon.
		tileStacks.put(tile, new Point(anchor.getX(), rect[1] - overheadRowClearance(localPlayer, zoom)));
	}

	/**
	 * Claims a same-tile stack slot for an actor's full bar, returning the anchor to draw at - always the
	 * actor's own, pushed up only as far as clearing the tile's existing claim needs. Self is returned
	 * untouched (reserveSelfStackHeight() claimed for it). The name row reserves barRect()'s h, not fontSize.
	 */
	private Point claimBarStackSlot(Map<WorldPoint, Point> tileStacks, Actor actor, Point anchor, BarStyle style, double zoom)
	{
		if (actor == client.getLocalPlayer())
		{
			return anchor;
		}

		WorldPoint tile = stackTile(actor);
		if (tile == null)
		{
			return anchor;
		}

		boolean nameShown = actor instanceof NPC ? config.showNpcName() : config.showPlayerName();
		int[] rect = barRect(anchor, style, zoom);
		int top = nameShown ? rect[1] - rect[3] - scaled(NAME_GAP, zoom) : rect[1];
		return claimStackSlot(tileStacks, tile, anchor, top, rect[1] + rect[3],
			stackPullLimit(actor, anchor), zoom);
	}

	/**
	 * Same as claimBarStackSlot, but for a name-only entry (the "Always Show NPC/Player Name"
	 * passes). Actor-generic - reused for both; never called for self. No bar is drawn here, so the
	 * claimed box is the name row alone, sitting NAME_GAP above where the bar would have been.
	 */
	private Point claimNameStackSlot(Map<WorldPoint, Point> tileStacks, Actor actor, Point anchor, BarStyle style, double zoom)
	{
		WorldPoint tile = stackTile(actor);
		if (tile == null)
		{
			return anchor;
		}

		int[] rect = barRect(anchor, style, zoom);
		int nameGap = scaled(NAME_GAP, zoom);
		return claimStackSlot(tileStacks, tile, anchor, rect[1] - rect[3] - nameGap, rect[1] - nameGap,
			stackPullLimit(actor, anchor), zoom);
	}

	/**
	 * Claims a slot for a player drawing neither a bar nor a name, just their overhead icon row (the
	 * icon-only branch in render()). Only ever used for a tile's icon owner - every other icon-only
	 * player stays out of the stack entirely, as they always have.
	 */
	private Point claimIconStackSlot(Map<WorldPoint, Point> tileStacks, Player player, Point anchor, BarStyle style, double zoom)
	{
		WorldPoint tile = stackTile(player);
		if (tile == null)
		{
			return anchor;
		}

		int[] rect = barRect(anchor, style, zoom);
		return claimStackSlot(tileStacks, tile, anchor, rect[1] - overheadRowClearance(player, zoom), rect[1],
			stackPullLimit(player, anchor), zoom);
	}

	/**
	 * The tile an actor stacks on: the one its model is over. getLocalLocation() via fromLocal(), since
	 * getWorldLocation() runs up to two tiles ahead of the render and fromLocalInstance()'s template
	 * coords collide across instances. Switches only past STACK_TILE_HYSTERESIS, not at the boundary.
	 */
	private WorldPoint stackTile(Actor actor)
	{
		WorldPoint decided = stackTiles.get(actor);
		if (decided != null)
		{
			return decided;
		}

		LocalPoint local = actor.getLocalLocation();
		WorldPoint tile = local == null ? actor.getWorldLocation() : WorldPoint.fromLocal(client, local);
		WorldPoint previous = previousStackTiles.get(actor);
		if (local != null && tile != null && previous != null && !previous.equals(tile)
			&& previous.getPlane() == tile.getPlane())
		{
			LocalPoint previousCentre = LocalPoint.fromWorld(actor.getWorldView(), previous);
			if (previousCentre != null)
			{
				int toPrevious = local.distanceTo(previousCentre);
				LocalPoint centre = LocalPoint.fromWorld(actor.getWorldView(), tile);
				int toTile = centre == null ? 0 : local.distanceTo(centre);
				if (toPrevious <= LOCAL_TILE_SIZE && toTile + STACK_TILE_HYSTERESIS >= toPrevious)
				{
					tile = previous;
				}
			}
		}

		if (tile != null)
		{
			stackTiles.put(actor, tile);
		}
		return tile;
	}

	/**
	 * How far an entry may be pulled DOWN toward its tile's stack (claimStackSlot()): from its own anchor to
	 * its own feet, i.e. its model height on canvas. Anchors on one tile diverge by height and by sub-tile
	 * interpolation, so this bounds the pull to the actor's own tile. 0 off-screen, i.e. push-only.
	 */
	private int stackPullLimit(Actor actor, Point anchor)
	{
		Point ground = actorAnchor(actor, 0);
		return ground == null ? 0 : Math.max(0, ground.getY() - anchor.getY());
	}

	/**
	 * Shared bookkeeping for both claim*StackSlot() methods: shifts the entry STACK_PADDING clear of its
	 * tile's topmost claimed edge (up, or down bounded by stackPullLimit()) and aligns it to the X the
	 * tile's first claimant seeded. Measured off real drawn edges, not a height counter - see CLAUDE.md.
	 */
	private static Point claimStackSlot(Map<WorldPoint, Point> tileStacks, WorldPoint tile, Point anchor,
			int top, int bottom, int maxPull, double zoom)
	{
		Point claimed = tileStacks.get(tile);
		int shift = claimed == null ? 0 : Math.max(-maxPull, bottom + scaled(STACK_PADDING, zoom) - claimed.getY());
		int x = claimed == null ? anchor.getX() : claimed.getX();
		tileStacks.put(tile, new Point(x, top - shift));
		return new Point(x, anchor.getY() - shift);
	}

	/**
	 * Whether npc may render anything this frame under npcStackLimit() - checked by both render() passes
	 * before either claims a slot, so a capped-out NPC reserves no height. One budget unit per NPC per
	 * tile however many of its own draws follow; decided once and cached in npcStackDecided.
	 */
	private boolean npcStackAllowed(Map<WorldPoint, Integer> npcStackCounts, Map<NPC, Boolean> npcStackDecided, NPC npc)
	{
		Boolean cached = npcStackDecided.get(npc);
		if (cached != null)
		{
			return cached;
		}

		int limit = config.npcStackLimit();
		boolean allowed = true;
		if (limit > 0)
		{
			WorldPoint tile = stackTile(npc);
			if (tile != null)
			{
				int count = npcStackCounts.getOrDefault(tile, 0);
				allowed = count < limit;
				if (allowed)
				{
					npcStackCounts.put(tile, count + 1);
				}
			}
		}

		npcStackDecided.put(npc, allowed);
		return allowed;
	}

	/**
	 * Whether other player may render anything (bar and/or name) this frame under
	 * playerNameStackLimit() - same shape as npcStackAllowed(), see its own doc. Self is never a
	 * caller - only "Other Players" are subject to this cap, same as always.
	 */
	private boolean playerStackAllowed(Map<WorldPoint, Integer> playerStackCounts, Map<Player, Boolean> playerStackDecided, Player player)
	{
		Boolean cached = playerStackDecided.get(player);
		if (cached != null)
		{
			return cached;
		}

		int limit = config.playerNameStackLimit();
		boolean allowed = true;
		if (limit > 0)
		{
			WorldPoint tile = stackTile(player);
			if (tile != null)
			{
				int count = playerStackCounts.getOrDefault(tile, 0);
				allowed = count < limit;
				if (allowed)
				{
					playerStackCounts.put(tile, count + 1);
				}
			}
		}

		playerStackDecided.put(player, allowed);
		return allowed;
	}

	/**
	 * Draws just the NPC name label at its would-be bar position - used by drawBar() (tracked) and the
	 * "Always Show NPC Name" pass (untracked), both of which charge npcStackAllowed() first. The single
	 * choke point for "Show Pet Names" and "Toggle Names" - purely visual, the row stays reserved.
	 */
	private void drawNpcNameOnly(Graphics2D g, NPC npc, Point anchor, BarStyle style, double zoom)
	{
		if (!plugin.isNamesVisible() || plugin.isPetNameHidden(npc))
		{
			return;
		}

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
		int level = npc.getCombatLevel();
		// Grey wins over the aggressive color and aggression over the by-level tint: both are transient facts
		// about this NPC now, while the level is a standing one. A null levelNameColor() (toggle off, or no
		// level to compare) leaves the configured color standing.
		Color nameColor;
		if (config.greyOutOtherPlayerDamageNames() && plugin.isLootTainted(npc))
		{
			nameColor = LOOT_TAINTED_COLOR;
		}
		else if (config.colorAggressiveNpcNames() && plugin.isNpcAggressive(npc))
		{
			nameColor = config.aggressiveNpcColor();
		}
		else
		{
			Color byLevel = levelNameColor(level);
			nameColor = byLevel != null ? byLevel : config.npcNameColor();
		}
		// Suffix only - shares the name's line, so it costs no stack height, and shares its color
		// unless the suffix's own toggle claims it. That claim outranks the grey/aggressive tints
		// on the suffix alone; the name itself keeps them, per the ordering just above.
		String label = truncateName(Text.removeTags(npcName));
		String levelSuffix = config.showNpcCombatLevel() && level > 0 ? " (lvl " + level + ")" : null;
		drawNameLabel(g, style, label, levelSuffix, x, y - h - nameGap, w, h, zoom, nameColor,
			levelSuffixColor(level));
	}

	/**
	 * Draws just another player's name label at its would-be bar position, from drawBar() and the "Always
	 * Show Player Name" pass, both of which charge playerStackAllowed() first. Same shape as
	 * drawNpcNameOnly() minus its truncation and aggressive/loot-tainted coloring, which stay NPC-only.
	 */
	private void drawPlayerNameOnly(Graphics2D g, Player player, Point anchor, BarStyle style, double zoom)
	{
		if (!plugin.isNamesVisible())
		{
			return;
		}

		String playerName = player.getName();
		if (!isDisplayableName(playerName))
		{
			return;
		}

		// otherPlayerVerticalOffset always applies, bar showing or not, matching drawNpcNameOnly() - a lone
		// name still moves with the offset instead of sitting at a fixed position.
		int[] rect = barRect(anchor, style, zoom);
		int x = rect[0];
		int y = rect[1];
		int w = rect[2];
		int h = rect[3];
		int nameGap = scaled(NAME_GAP, zoom);
		// Same suffix, same "no new stack height" reasoning as drawNpcNameOnly()'s. No grey or
		// aggressive tint to outrank the by-level one here - neither concept exists for players.
		int level = player.getCombatLevel();
		String levelSuffix = config.showPlayerCombatLevel() && level > 0 ? " (lvl " + level + ")" : null;
		Color byLevel = levelNameColor(level);
		drawNameLabel(g, style, Text.removeTags(playerName), levelSuffix, x, y - h - nameGap, w, h, zoom,
			byLevel != null ? byLevel : config.playerNameColor(), levelSuffixColor(level));
	}

	/** Small skull badge to the left of an NPC's HP bar, marking it as currently aggressive. */
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
	 * The elemental-weakness badge right of an NPC's HP bar, percentage beside it. Bar-anchored, so it tracks
	 * the bar's right edge with or without a name and, like the aggressive badge, appears only when a bar
	 * does. Drawn at a point rather than centered in a box, so it can't reuse drawLabel().
	 */
	private void drawWeaknessIcon(Graphics2D g, NPC npc, int barX, int barY, int barW, int barH, BarStyle style, double zoom)
	{
		if (!plugin.isWeaknessIconsVisible())
		{
			return;
		}

		NpcWeaknessTable.Weakness weakness = plugin.npcWeakness(npc);
		if (weakness == null)
		{
			return;
		}

		BufferedImage icon = weaknessIcon(weakness.getElement());
		if (icon == null)
		{
			return;
		}

		int size = scaled(WEAKNESS_ICON_SIZE, zoom);
		int iconX = barX + barW + scaled(WEAKNESS_ICON_GAP, zoom);
		int iconY = barY + barH / 2 - size / 2;
		g.drawImage(icon, iconX, iconY, size, size, null);

		if (!config.showNpcWeaknessPercent())
		{
			return;
		}

		// Same getPixelBounds + textNudge math drawLabel() uses, duplicated because this starts at a
		// point off the icon's right edge rather than centering inside a box.
		String percent = weakness.getPercent() + "%";
		Font font = resolveFont(style.fontFamily, style.fontStyle, scaled(style.fontSize, zoom));
		g.setFont(font);
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(percent, font, frc).getPixelBounds(frc, 0, 0);

		int textX = iconX + size + scaled(WEAKNESS_PERCENT_GAP, zoom) - pixelBounds.x;
		// Vertically centered on the icon, the same way drawLabel() centers within a bar's height.
		int textY = iconY + (int) Math.round((size - pixelBounds.getHeight()) / 2.0)
			- pixelBounds.y + scaled(style.textNudge, zoom);
		drawText(g, style, percent, textX, textY, config.npcWeaknessPercentColor());
	}

	/** Maps an element to its surge spell icon - the four standard-spellbook surges, asked for by name in issue #27. */
	private BufferedImage weaknessIcon(NpcWeaknessTable.Element element)
	{
		switch (element)
		{
			case AIR:
				return clientSprite(SpriteID.Magicon2.WIND_SURGE, 0);
			case WATER:
				return clientSprite(SpriteID.Magicon2.WATER_SURGE, 0);
			case EARTH:
				return clientSprite(SpriteID.Magicon2.EARTH_SURGE, 0);
			case FIRE:
				return clientSprite(SpriteID.Magicon2.FIRE_SURGE, 0);
			default:
				return null;
		}
	}

	/**
	 * The " (lvl N)" suffix's own color, or null to leave it inheriting the name's - what every
	 * level suffix did before issue #35, and still what happens with the toggle off.
	 */
	private Color levelSuffixColor(int level)
	{
		return config.colorCombatLevelByDifference() ? relativeLevelColor(level) : null;
	}

	/**
	 * The name's own by-level color, or null to leave the configured name color standing. Separate
	 * toggle from the suffix's: coloring the level number is a label on a number, coloring the whole
	 * name overrides a color the user picked, so they are not the same opt-in.
	 */
	private Color levelNameColor(int level)
	{
		return config.colorNamesByCombatLevel() ? relativeLevelColor(level) : null;
	}

	/**
	 * The relative-level color for a combat level, or null when there is nothing to compare: no combat level,
	 * no local player, or a local level not populated yet - it reads 0 for a moment on login, which would
	 * otherwise paint everything in the scene red.
	 */
	private Color relativeLevelColor(int level)
	{
		if (level <= 0)
		{
			return null;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getCombatLevel() <= 0)
		{
			return null;
		}

		return levelDiffColor(level - localPlayer.getCombatLevel());
	}

	/** The LEVEL_DIFF_COLORS band for a target's combat level minus your own. */
	private static Color levelDiffColor(int difference)
	{
		for (int i = 0; i < LEVEL_DIFF_BANDS.length; i++)
		{
			if (difference >= LEVEL_DIFF_BANDS[i])
			{
				return LEVEL_DIFF_COLORS[i];
			}
		}
		return LEVEL_DIFF_COLORS[LEVEL_DIFF_BANDS.length];
	}

	/** Cuts a name to npcNameMaxLength plus a period. The level suffix is appended after, so it never gets cut. */
	private String truncateName(String name)
	{
		int limit = config.npcNameMaxLength();
		if (!config.truncateNpcNames() || name.length() <= limit)
		{
			return name;
		}
		// NPC names carry U+00A0, which String.trim() doesn't strip - see normalizeNpcName.
		return TRAILING_SPACE.matcher(name.substring(0, limit)).replaceAll("") + ".";
	}

	/** Filters placeholder names: literal "null", or a "Category:Label" name with a colon or semicolon. */
	private static boolean isDisplayableName(String npcName)
	{
		return npcName != null && !npcName.isEmpty() && !"null".equals(npcName)
			&& npcName.indexOf(':') < 0 && npcName.indexOf(';') < 0;
	}

	private void drawBar(Graphics2D g, Actor actor, Point anchor, int ratio, int scale, int maxHp, BarStyle style)
	{
		double zoom = zoomFactor();

		// Real tracked state, not assumed true: this also runs for untracked actors via the "always show bar"
		// toggles, where a hardcoded true would let Special into self's stack and would draw a name for an
		// actor whose own name toggle is off.
		boolean self = actor == client.getLocalPlayer();
		boolean trackedNow = plugin.getTrackedActors().containsKey(actor);
		List<CustomHpBarConfig.BarKind> stack = self ? playerBarStack(trackedNow) : null;

		int[] rect = barRect(anchor, style, zoom);
		int x = rect[0];
		int y = rect[1];
		int w = rect[2];
		int h = rect[3];
		int border = scaled(style.borderWidth, zoom);
		int arc = scaled(style.cornerRadius, zoom) * 2;

		// hpY is where the HP bar actually lands once the configured order is applied - y stays the
		// top of the whole stack, which is what the name, overhead icon, and same-tile shift key off.
		int hpY = stack == null ? y : y + h * stack.indexOf(CustomHpBarConfig.BarKind.HP);

		double hpFraction = (double) ratio / scale;
		// Toggles first: isNpcAggressive allocates a stream once the tolerance window lapses, and
		// this runs per NPC per frame. One shared read feeds both the fill and the icon.
		boolean aggressive = actor instanceof NPC
			&& (config.colorAggressiveNpcBars() || config.showAggressiveNpcIcon())
			&& plugin.isNpcAggressive((NPC) actor);
		// Held separately from fillColor: null means the gradient is driving the fill, which is
		// what lets a matched trail resolve its own color per HP level below.
		Color overrideColor = plugin.isShieldedNpc(actor) ? config.npcShieldBarColor() : null;
		if (overrideColor == null)
		{
			overrideColor = config.greyOutOtherPlayerDamage() && actor instanceof NPC
				&& plugin.isLootTainted((NPC) actor) ? LOOT_TAINTED_COLOR : null;
		}
		if (overrideColor == null)
		{
			overrideColor = plugin.statusEffectColor(actor);
		}
		if (overrideColor == null && aggressive && config.colorAggressiveNpcBars())
		{
			overrideColor = config.aggressiveNpcColor();
		}
		Color fillColor = overrideColor != null ? overrideColor : hpFillColor(style, hpFraction);
		// "Toggle HP Bars" choke point - purely visual, same trade-off as "Toggle Names": the row's height and
		// every placement below are computed either way, so nothing reflows on the hotkey. Heal preview folds
		// in with the bar; the aggressive icon deliberately stays visible.
		if (plugin.isHpBarsVisible())
		{
			// Inside the visibility gate on purpose: a bar hidden by the hotkey observes nothing, so damage taken
			// while hidden snaps back rather than animating a gap the player never saw. HP bar only -
			// Prayer/Special/Run take drawSimpleBar()'s trail-less overload.
			double trailFraction = hpFraction;
			Color trailColor = null;
			if (style.damageTrail)
			{
				trailFraction = plugin.damageTrailFraction(actor, hpFraction, maxHp);
				// With the gradient off hpFillColor returns barColor at every fraction, so this
				// collapses to a darkened bar color - the native boss HUD's own treatment.
				trailColor = style.damageTrailMatchBar
					? matchedTrailColor(overrideColor != null ? overrideColor : hpFillColor(style, trailFraction))
					: style.damageTrailColor;
			}
			drawBarShape(g, style, x, hpY, w, h, border, arc, hpFraction, fillColor, trailFraction, trailColor);

			if (self && config.showFoodHealPreview())
			{
				// ratio/scale are the local player's real current/max HP already, not a bucket.
				drawHealPreview(g, x, hpY, w, h, border, ratio, maxHp, hoveredRestoreValue(Skill.HITPOINTS),
					translucent(fillColor));
			}

			String label = buildLabel(actor, hpFraction, maxHp);
			if (label != null)
			{
				drawLabel(g, style, label, x, hpY, w, h, zoom, style.textColor, hpTextSpacing(actor), style.textAlignment);
			}
		}

		if (aggressive && config.showAggressiveNpcIcon())
		{
			drawAggressiveNpcIcon(g, x, hpY, h, zoom);
		}

		if (actor instanceof NPC && config.showNpcWeaknessIcon())
		{
			drawWeaknessIcon(g, (NPC) actor, x, hpY, w, h, style, zoom);
		}

		int bottomY = y + h;

		// Resolved before the charge bar draws: the icon row owns the space under the bar, and the
		// charge bar has to clear whatever it takes. Icons are square at the bar's height.
		Set<CustomHpBarPlugin.StatusEffect> statusEffects = showStatusIcons(actor)
			? plugin.activeStatusEffects(actor) : Collections.emptySet();
		int statusRowH = statusEffects.isEmpty() ? 0 : h;

		// Beneath the HP bar rather than in the player stack: it is transient, and nothing else on the
		// target profile is ordered, so it only has to clear what already sits below the bar.
		double charge = plugin.chargeFraction(actor);
		if (charge >= 0 && plugin.isHpBarsVisible())
		{
			// Its own size, because the native charge bar is usually wider than the health bar it sits
			// under. 0 on either falls back to the HP bar's, which is what every existing profile has.
			int chargeW = config.npcChargeBarWidth() > 0 ? scaled(config.npcChargeBarWidth(), zoom) : w;
			int chargeH = config.npcChargeBarHeight() > 0 ? scaled(config.npcChargeBarHeight(), zoom) : h;
			int chargeY = bottomY + Math.max(scaled(config.npcChargeBarGap(), zoom), statusRowH);
			drawBarShape(g, style, x + (w - chargeW) / 2, chargeY, chargeW, chargeH, border, arc, charge,
				config.npcChargeBarColor());
		}

		if (stack != null)
		{
			// Flush against each other, mirroring the Player Bar profile rather than each bar
			// getting its own size/shape config.
			drawStackedBars(g, style, stack, x, y, w, h, border, arc, zoom);
			bottomY = y + h * stack.size();
		}

		if (!statusEffects.isEmpty())
		{
			// Below whichever bar is currently lowest, so it doesn't overlap the stack.
			drawStatusIcons(g, statusEffects, x, bottomY, h);
		}

		// With "Always Show Name" on, render()'s dedicated pass is the sole name source. trackedNow is the
		// other half: without it an actor shown only by its "always show bar" toggle would still get a name
		// here, which made "Always Show NPC/Player Name" look like it did nothing.
		if (actor instanceof NPC && config.showNpcName() && !config.alwaysShowNpcName() && trackedNow)
		{
			drawNpcNameOnly(g, (NPC) actor, anchor, style, zoom);
		}
		else if (actor instanceof Player && !self && config.showPlayerName() && !config.alwaysShowPlayerName() && trackedNow)
		{
			drawPlayerNameOnly(g, (Player) actor, anchor, style, zoom);
		}

		// Other player with a bar showing (tracked, or untracked via alwaysShowPlayerBar): their native
		// overhead bundle is suppressed, so redraw it here, the canonical place for any player this method
		// draws a bar for, and the "Always Show" pass below skips whatever this already did.
		if (actor instanceof Player && !self)
		{
			Player other = (Player) actor;
			// isNamesVisible() deliberately, unlike the name row's own "purely visual" hotkey gate: the skull and
			// overhead icon track what is actually on screen, so they snap down to the bar's top the instant
			// names are hotkey-hidden.
			boolean nameShown = config.showPlayerName() && isDisplayableName(other.getName()) && plugin.isNamesVisible();
			drawSkullIcon(g, other, anchor, style, nameShown);
			drawOverheadIcon(g, other, anchor, style, nameShown);
			drawHitsplats(g, other);
			drawOverheadChatText(g, other);
		}
	}

	/** Same as hoveredRestoreValue(String), keyed by a Skill's own name - the common case (HP, Prayer). */
	private int hoveredRestoreValue(Skill stat)
	{
		return hoveredRestoreValue(stat.getName());
	}

	/**
	 * Stat points the hovered inventory item would restore, or -1 if nothing applicable is hovered.
	 * Delegates to ItemStatChangesService rather than a hand-curated table, so gear/level-dependent
	 * formulas resolve correctly. Raw name, not Skill, since Run Energy's Stat isn't a Skill.
	 */
	private int hoveredRestoreValue(String statName)
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
			if (change.getTheoretical() != 0 && change.getStat().getName().equals(statName))
			{
				return change.getTheoretical();
			}
		}

		return -1;
	}

	/** Extends a bar past its fill with a preview of where the stat would land if healAmount landed now. */
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

	/** Whether the debuff icon row draws for actor, by actor type - independent of the Color By Status toggle. */
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

	/** Draws one debuff badge per active status effect, left to right, in StatusEffect.values() order. */
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

	/** Maps a status effect to its debuff icon. Disease/Corruption are bundled - no Hitmark sprite exists. */
	private BufferedImage statusIcon(CustomHpBarPlugin.StatusEffect effect)
	{
		switch (effect)
		{
			case POISON:
				return clientSprite(SpriteID.Hitmark.HITSPLAT_GREEN_POISON, 0);
			case VENOM:
				return clientSprite(SpriteID.Hitmark.HITSPLAT_DARK_GREEN_VENOM, 0);
			case BURN:
				return clientSprite(SpriteID.Hitmark.BURN_DAMAGE, 0);
			case BLEED:
				// Reuses the Bleed hitsplat sprite as a debuff icon.
				return hitsplatImage(HitsplatID.BLEED);
			case DISEASE:
				return bundledIcon("disease_hitsplat.png");
			case CORRUPTION:
				return bundledIcon("corruption_hitsplat.png");
			default:
				return null;
		}
	}

	/** The PK skull for the aggressive-NPC badge - bundled from the wiki, no confirmed live SpriteID exists. */
	private BufferedImage aggressiveIcon()
	{
		return bundledIcon("pk_skull_icon.png");
	}

	/** A client sprite, cached once loaded. A miss starts an async load and returns null; callers skip drawing. */
	private BufferedImage clientSprite(int spriteId, int frame)
	{
		long key = ((long) spriteId << 32) | (frame & 0xFFFFFFFFL);
		BufferedImage cached = clientSprites.get(key);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage loaded = spriteManager.getSprite(spriteId, frame);
		if (loaded != null)
		{
			clientSprites.put(key, loaded);
			return loaded;
		}

		spriteManager.getSpriteAsync(spriteId, frame, image ->
		{
			if (image != null)
			{
				clientSprites.put(key, image);
			}
		});
		return null;
	}

	/** An icon bundled as a plugin resource. computeIfAbsent never stores null, so a failed load is just retried. */
	private BufferedImage bundledIcon(String resourceName)
	{
		return bundledIcons.computeIfAbsent(resourceName, CustomHpBarOverlay::loadBundledIcon);
	}

	/** Loads a bundled icon off the classpath. Returns null on any failure - a missing icon just skips that badge. */
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

	/** Whether the Prayer bar is part of your stack right now - hidePrayerBarWhenInactive gates it on praying. */
	private boolean prayerBarAttached()
	{
		return config.showPrayerBar() && (!config.hidePrayerBarWhenInactive() || plugin.isPrayerActive());
	}

	/** Whether the special attack bar is part of your stack right now. */
	private boolean specialBarAttached()
	{
		return config.showSpecialAttackBar();
	}

	/**
	 * Whether the run energy bar is part of your stack right now - its own drain timeout or alwaysShowRunBar,
	 * never combat tracked state. Deliberately independent of `tracked`, unlike Prayer/Special/HP - see
	 * showRunEnergyBar()'s own description.
	 */
	private boolean runBarAttached()
	{
		if (!config.showRunEnergyBar())
		{
			return false;
		}
		return config.alwaysShowRunBar() || !plugin.isRunEnergyBarTimedOut();
	}

	/**
	 * The bars stacked over the local player right now, topmost first, from the four barPositionN
	 * dropdowns - a duplicate pick shows only at its topmost slot, and HP is force-included if the picks
	 * omit it. Each kind's "always show" toggle widens only whether it can appear while untracked.
	 */
	private List<CustomHpBarConfig.BarKind> playerBarStack(boolean tracked)
	{
		List<CustomHpBarConfig.BarKind> stack = new ArrayList<>(4);
		// Built here, not a config default method - RuneLite's config proxy only special-cases
		// @ConfigItem-annotated methods; any other method (even a default one with a real body)
		// just logs a warning and returns null. See CLAUDE.md ("config.barOrder() NPE").
		List<CustomHpBarConfig.BarKind> positions = Arrays.asList(
			config.barPosition1(), config.barPosition2(), config.barPosition3(), config.barPosition4());
		for (CustomHpBarConfig.BarKind kind : positions)
		{
			if (stack.contains(kind))
			{
				continue;
			}

			boolean visible;
			switch (kind)
			{
				case PRAYER:
					visible = prayerBarAttached() && (tracked || plugin.isPrayerActive() || config.alwaysShowPrayerBar());
					break;
				case SPECIAL:
					visible = specialBarAttached() && (tracked || config.alwaysShowSpecialBar());
					break;
				case RUN:
					visible = runBarAttached();
					break;
				case HP:
				default:
					visible = tracked || config.alwaysShowHpBar();
					break;
			}
			if (visible)
			{
				stack.add(kind);
			}
		}

		if ((tracked || config.alwaysShowHpBar()) && !stack.contains(CustomHpBarConfig.BarKind.HP))
		{
			stack.add(0, CustomHpBarConfig.BarKind.HP);
		}
		return stack;
	}

	/** Draws the Prayer/special/run bars at the HP bar's would-be position - only when self isn't tracked. */
	private void drawStandaloneBarStack(Graphics2D g, Point anchor, BarStyle style, List<CustomHpBarConfig.BarKind> stack)
	{
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int border = scaled(style.borderWidth, zoom);
		int arc = scaled(style.cornerRadius, zoom) * 2;
		drawStackedBars(g, style, stack, rect[0], rect[1], rect[2], rect[3], border, arc, zoom);
	}

	/** Draws every non-HP bar at its configured slot; HP belongs to drawBar(), so its slot is left empty. */
	private void drawStackedBars(Graphics2D g, BarStyle style, List<CustomHpBarConfig.BarKind> stack,
			int x, int stackTop, int w, int h, int border, int arc, double zoom)
	{
		for (int i = 0; i < stack.size(); i++)
		{
			int y = stackTop + h * i;
			switch (stack.get(i))
			{
				case PRAYER:
					drawPrayerBar(g, style, x, y, w, h, border, arc, zoom);
					break;
				case SPECIAL:
					drawSpecialAttackBar(g, style, x, y, w, h, border, arc, zoom);
					break;
				case RUN:
					drawRunEnergyBar(g, style, x, y, w, h, border, arc, zoom);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Draws the replacement overhead prayer icon above the bar (and name, if nameShown), and above the
	 * replacement skull when one is drawing too (skullClearance()) - native OSRS's own bottom-to-top bar,
	 * skull, icon. The native version is already suppressed for this player.
	 */
	private void drawOverheadIcon(Graphics2D g, Player player, Point anchor, BarStyle style, boolean nameShown)
	{
		HeadIcon headIcon = player.getOverheadIcon();
		if (headIcon == null || !iconRowShown(player))
		{
			return;
		}

		BufferedImage image = headIconImage(headIcon);
		if (image == null)
		{
			return;
		}

		if (anchor == null)
		{
			return;
		}

		// Drawn at the sprite's own natural size (matching how the native client draws it), with
		// zoom scaling layered on top when Scale With Zoom is on.
		double zoom = zoomFactor();
		// verticalOffset always applies now, same as drawPlayerNameOnly() - see its own comment.
		// Keeps the icon anchored to the name/bar row above it (via nameClearance below) whether or
		// not that row itself moved with the offset.
		int[] rect = barRect(anchor, style, zoom);
		int w = scaled(image.getWidth(), zoom);
		int h = scaled(image.getHeight(), zoom);
		int gap = scaled(OVERHEAD_ICON_GAP, zoom);

		// A name row (when shown) sits directly above the bar, occupying the bar's own height plus
		// NAME_GAP - see drawPlayerNameOnly()/drawNpcNameOnly(). Self never has one (see call site).
		int nameClearance = nameShown ? rect[3] + scaled(NAME_GAP, zoom) : 0;

		int x = rect[0] + (rect[2] - w) / 2;
		int y = rect[1] - nameClearance - gap - h - skullClearance(player, zoom);
		g.drawImage(image, x, y, w, h, null);
	}

	/** All 15 overhead icon graphics are sub-frames of one client sprite, indexed by HeadIcon.ordinal(). */
	private BufferedImage headIconImage(HeadIcon headIcon)
	{
		return clientSprite(SpriteID.HEADICONS_PRAYER, headIcon.ordinal());
	}

	/**
	 * Draws the replacement PK skull (Player.getSkullIcon(), not the aggressive-NPC badge that reuses the
	 * same image) above the bar and name. drawOverheadIcon() adds skullClearance() above its own row, so
	 * the two stack skull-then-prayer bottom to top like native. The native one is already suppressed.
	 */
	private void drawSkullIcon(Graphics2D g, Player player, Point anchor, BarStyle style, boolean nameShown)
	{
		BufferedImage image = skullImage(player.getSkullIcon());
		if (image == null || !iconRowShown(player))
		{
			return;
		}

		if (anchor == null)
		{
			return;
		}

		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int w = scaled(image.getWidth(), zoom);
		int h = scaled(image.getHeight(), zoom);
		int gap = scaled(OVERHEAD_ICON_GAP, zoom);
		int nameClearance = nameShown ? rect[3] + scaled(NAME_GAP, zoom) : 0;

		int x = rect[0] + (rect[2] - w) / 2;
		int y = rect[1] - nameClearance - gap - h;
		g.drawImage(image, x, y, w, h, null);
	}

	/**
	 * Extra vertical clearance drawOverheadIcon() reserves above its row when drawSkullIcon() is also drawing
	 * a skull for player - 0 if unskulled or the bundled image hasn't loaded, where the prayer icon just sits
	 * right above the bar/name as it always did.
	 */
	private int skullClearance(Player player, double zoom)
	{
		BufferedImage image = skullImage(player.getSkullIcon());
		return image == null ? 0 : scaled(image.getHeight() + OVERHEAD_ICON_GAP, zoom);
	}

	/**
	 * The PK skull graphic for a Player.getSkullIcon() value, bundled from the wiki - no confirmed live
	 * SpriteID exists for these. A loot-key skull bakes the key count into its own image, so it replaces
	 * the plain skull rather than adding to it; every other variant falls back to the plain one.
	 */
	private BufferedImage skullImage(int skullIcon)
	{
		switch (skullIcon)
		{
			case SkullIcon.NONE:
				return null;
			case SkullIcon.LOOT_KEYS_ONE:
				return bundledIcon("pk_skull_loot_key_1.png");
			case SkullIcon.LOOT_KEYS_TWO:
				return bundledIcon("pk_skull_loot_key_2.png");
			case SkullIcon.LOOT_KEYS_THREE:
				return bundledIcon("pk_skull_loot_key_3.png");
			case SkullIcon.LOOT_KEYS_FOUR:
				return bundledIcon("pk_skull_loot_key_4.png");
			case SkullIcon.LOOT_KEYS_FIVE:
				return bundledIcon("pk_skull_loot_key_5.png");
			case SkullIcon.FORINTHRY_SURGE_KEYS_ONE:
				return bundledIcon("pk_skull_surge_key_1.png");
			case SkullIcon.FORINTHRY_SURGE_KEYS_TWO:
				return bundledIcon("pk_skull_surge_key_2.png");
			case SkullIcon.FORINTHRY_SURGE_KEYS_THREE:
				return bundledIcon("pk_skull_surge_key_3.png");
			case SkullIcon.FORINTHRY_SURGE_KEYS_FOUR:
				return bundledIcon("pk_skull_surge_key_4.png");
			case SkullIcon.FORINTHRY_SURGE_KEYS_FIVE:
				return bundledIcon("pk_skull_surge_key_5.png");
			default:
				// Unskulled (NONE already returned above) never reaches here with a non-null
				// result expected; every skulled variant without its own bundled graphic yet
				// falls back to the plain skull - see this method's own doc.
				return bundledIcon("pk_skull_icon.png");
		}
	}

	/** Redraws hitsplats on player (sprite + amount), replacing the ones the render callback suppresses. */
	private void drawHitsplats(Graphics2D g, Player player)
	{
		List<OverheadHitsplat> hitsplats = plugin.getOverheadHitsplats().get(player);
		if (hitsplats == null || hitsplats.isEmpty())
		{
			return;
		}

		// Native hitsplats render at roughly chest height, not above the head like the bar/text.
		Point anchor = actorAnchor(player, player.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return;
		}

		int currentCycle = client.getGameCycle();
		List<OverheadHitsplat> visible = new ArrayList<>();
		List<BufferedImage> images = new ArrayList<>();
		for (OverheadHitsplat hitsplat : hitsplats)
		{
			if (currentCycle >= hitsplat.getDisappearsOnGameCycle())
			{
				continue;
			}
			BufferedImage image = hitsplatImage(hitsplat.getType());
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

		// Vanilla shows only the 4 most recent hits - each player's list is append-ordered, so take the tail.
		if (visible.size() > MAX_HITSPLATS)
		{
			visible = visible.subList(visible.size() - MAX_HITSPLATS, visible.size());
			images = images.subList(images.size() - MAX_HITSPLATS, images.size());
		}

		double zoom = zoomFactor();

		// RuneScape Small at native size, white with a black drop shadow, matching vanilla.
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

	/** A hitsplat's background sprite, or null if that type has no confirmed mapping (HITSPLAT_SPRITE_IDS). */
	private BufferedImage hitsplatImage(int hitsplatType)
	{
		Integer spriteId = HITSPLAT_SPRITE_IDS.get(hitsplatType);
		return spriteId != null ? clientSprite(spriteId, 0) : null;
	}

	/**
	 * Draws the replacement overhead chat text at the vanilla position - above the head via
	 * Actor.getCanvasTextLocation(), never derived from the bar/name/icon stack below it. Explicit
	 * instruction: chat keeps its default position and the stack stays clear of it, not the reverse.
	 */
	private void drawOverheadChatText(Graphics2D g, Player player)
	{
		if (player.getOverheadCycle() <= 0)
		{
			return;
		}

		String rawText = player.getOverheadText();
		if (rawText == null)
		{
			return;
		}

		String text = Text.removeFormattingTags(rawText);
		if (text.isEmpty())
		{
			return;
		}

		double zoom = zoomFactor();
		// Vanilla overhead chat uses the bold font, not the regular one.
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) scaled(16, zoom));
		g.setFont(font);

		Point location = player.getCanvasTextLocation(g, text, player.getLogicalHeight());
		if (location == null)
		{
			return;
		}

		int x = location.getX();
		int y = location.getY();

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

		Color prayerColor = config.prayerBarColor();
		int restoreValue = config.showPrayerRestorePreview() ? hoveredRestoreValue(Skill.PRAYER) : -1;
		drawSimpleBar(g, style, x, y, w, h, border, arc, zoom, current, max, prayerColor,
			config.prayerTextColor(), restoreValue);

		if (config.showPrayerTickTimer() && (!config.hidePrayerTickTimerWhenInactive() || plugin.isPrayerActive()))
		{
			drawPrayerTickTimer(g, x, y, w, h, border, zoom);
		}
	}

	/**
	 * Thin indicator that sweeps left-to-right across the Prayer bar's fill once per game tick, snapping back
	 * at the start of the next - a reference for timing flicks against the real tick boundary. Drawn last,
	 * over fill/preview/label, since the sweep is the point.
	 */
	private void drawPrayerTickTimer(Graphics2D g, int x, int y, int w, int h, int border, double zoom)
	{
		int innerW = Math.max(0, w - border * 2);
		int innerH = Math.max(0, h - border * 2);
		if (innerW <= 0 || innerH <= 0)
		{
			return;
		}

		// -cos(t)*travel/2 + travel/2, t in [0, PI): core's own PrayerBarOverlay/PrayerFlickOverlay formula, an
		// eased sweep rather than constant velocity. `travel` (innerW minus the line's width) stands in for
		// their halfBarWidth, the same adaptation their HD variant makes to stay inside the padding.
		int lineWidth = Math.min(innerW, Math.max(1, scaled(PRAYER_TICK_TIMER_WIDTH, zoom)));
		int travel = innerW - lineWidth;
		double t = plugin.tickProgress();
		int xOffset = (int) (-Math.cos(t) * travel / 2.0) + travel / 2;
		int lineX = x + border + xOffset;

		g.setColor(config.prayerTickTimerColor());
		g.fillRect(lineX, y + border, lineWidth, innerH);
	}

	/** Fills and labels a simple current/max bar (Prayer/Special/Run). restoreValue < 0 skips the preview. */
	private void drawSimpleBar(Graphics2D g, BarStyle style, int x, int y, int w, int h, int border, int arc,
			double zoom, int current, int max, Color color, Color textColor, int restoreValue)
	{
		double fraction = max > 0 ? (double) current / max : 0;
		drawBarShape(g, style, x, y, w, h, border, arc, fraction, color);

		if (restoreValue > 0)
		{
			drawHealPreview(g, x, y, w, h, border, current, max, restoreValue, translucent(color));
		}

		drawLabel(g, style, String.valueOf(current), x, y, w, h, zoom, textColor, 0, style.textAlignment);
	}

	/** No restore preview, unlike Prayer/Run: itemstats has no special-attack Stat to match on. */
	private void drawSpecialAttackBar(Graphics2D g, BarStyle style, int x, int y, int w, int h, int border, int arc, double zoom)
	{
		int current = plugin.specialAttackEnergy();
		Color specialColor = config.specialAttackBarColor();
		drawSimpleBar(g, style, x, y, w, h, border, arc, zoom, current, FULL_PERCENT_ENERGY, specialColor,
			config.specialAttackTextColor(), -1);
	}

	/** Fill swaps to runEnergyStaminaColor while a Stamina buff is active - mirrors core's own run orb. */
	private void drawRunEnergyBar(Graphics2D g, BarStyle style, int x, int y, int w, int h, int border, int arc, double zoom)
	{
		int current = plugin.runEnergy();
		Color runColor = plugin.isStaminaActive() ? config.runEnergyStaminaColor() : config.runEnergyBarColor();
		int restoreValue = config.showRunEnergyRestorePreview() ? hoveredRestoreValue("Run Energy") : -1;
		drawSimpleBar(g, style, x, y, w, h, border, arc, zoom, current, FULL_PERCENT_ENERGY, runColor,
			config.runEnergyTextColor(), restoreValue);
	}

	/** Bar Color at full HP, blending through Mid at the midpoint to Low at empty. */
	private static Color hpFillColor(BarStyle style, double fraction)
	{
		if (!style.hpColorGradient)
		{
			return style.barColor;
		}

		double percent = clamp01(fraction) * 100;
		double mid = style.midpoint;

		// Guarded rather than clamped: midpoint 100 leaves no room above it, so the whole bar is
		// the low->mid ramp. Divisors below can't be zero once this case is out of the way.
		if (mid >= 100)
		{
			return blend(style.colorLow, style.colorMid, percent / 100);
		}
		return percent >= mid
			? blend(style.colorMid, style.barColor, (percent - mid) / (100 - mid))
			: blend(style.colorLow, style.colorMid, percent / mid);
	}

	/** Linear per-channel interpolation, alpha included. t=0 is from, t=1 is to. */
	private static Color blend(Color from, Color to, double t)
	{
		double f = clamp01(t);
		return new Color(
			(int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
			(int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
			(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f),
			(int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f));
	}

	private static double clamp01(double v)
	{
		return Math.max(0, Math.min(1, v));
	}

	/** A matched damage trail's color: the bar's own color at that HP level, darkened and a little translucent. */
	private static Color matchedTrailColor(Color base)
	{
		return new Color(
			Math.round(base.getRed() * (1 - TRAIL_MATCH_DARKEN)),
			Math.round(base.getGreen() * (1 - TRAIL_MATCH_DARKEN)),
			Math.round(base.getBlue() * (1 - TRAIL_MATCH_DARKEN)),
			TRAIL_MATCH_ALPHA);
	}

	/** A bar's own fill color, at the fixed preview alpha - see PREVIEW_ALPHA. */
	private static Color translucent(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), PREVIEW_ALPHA);
	}

	/** Draws one bar's background/fill/border, shared by every bar type. style.opacity scopes to the shape. */
	private void drawBarShape(Graphics2D g, BarStyle style, int x, int y, int w, int h,
			int border, int arc, double fraction, Color fillColor)
	{
		drawBarShape(g, style, x, y, w, h, border, arc, fraction, fillColor, fraction, null);
	}

	/**
	 * As above, plus the damage trail: the same fill shape drawn once more at trailFraction and underneath the
	 * real fill, so only the segment between the two shows. A second RoundRectangle2D, not a rect over the
	 * gap, so it inherits the rounded right edge. A null trailColor skips it entirely.
	 */
	private void drawBarShape(Graphics2D g, BarStyle style, int x, int y, int w, int h,
			int border, int arc, double fraction, Color fillColor, double trailFraction, Color trailColor)
	{
		int innerW = Math.max(0, w - border * 2);
		int innerH = Math.max(0, h - border * 2);
		int fillWidth = (int) Math.round(innerW * fraction);
		fillWidth = Math.max(0, Math.min(fillWidth, innerW));
		int fillArc = Math.max(0, arc - border * 2);

		RoundRectangle2D outline = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

		// Multiplied into whatever alpha the caller already set, not replacing it - the death-fade
		// pass wraps this whole draw in an AlphaComposite of its own, and a plain setComposite here
		// would throw that away for any bar whose opacity is under 100. See render()'s fade pass.
		Composite previousComposite = g.getComposite();
		float inherited = previousComposite instanceof AlphaComposite
			? ((AlphaComposite) previousComposite).getAlpha() : 1f;
		float alpha = inherited * (style.opacity / 100f);
		if (alpha < 1f)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}

		g.setColor(style.background);
		g.fill(outline);

		if (trailColor != null && trailFraction > fraction)
		{
			int trailWidth = (int) Math.round(innerW * trailFraction);
			trailWidth = Math.max(0, Math.min(trailWidth, innerW));
			if (trailWidth > 0)
			{
				g.setColor(trailColor);
				g.fill(new RoundRectangle2D.Float(x + border, y + border, trailWidth, innerH, fillArc, fillArc));
			}
		}

		if (fillWidth > 0)
		{
			RoundRectangle2D fillShape = new RoundRectangle2D.Float(
				x + border, y + border, fillWidth, innerH, fillArc, fillArc);

			Paint previousPaint = g.getPaint();
			g.setPaint(glossPaint(fillColor, x + border, y + border, innerH));
			g.fill(fillShape);
			g.setPaint(previousPaint);
		}

		if (border > 0)
		{
			// BasicStroke draws centered on the path - inset by half the border to keep it inside (x, y, w, h).
			float half = border / 2f;
			RoundRectangle2D borderPath = new RoundRectangle2D.Float(
				x + half, y + half, w - border, h - border, Math.max(0, arc - border), Math.max(0, arc - border));
			Stroke previousStroke = g.getStroke();
			g.setStroke(new BasicStroke(border));
			g.setColor(style.borderColor);
			g.draw(borderPath);
			g.setStroke(previousStroke);
		}

		g.setComposite(previousComposite);
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
		// The NPC name label (this overload's only caller) always centers - it's a name, not a bar value, so
		// textAlignment doesn't apply. Every bar's own number goes through the other overload instead.
		drawLabel(g, style, label, x, y, w, h, zoom, textColor, 0, CustomHpBarConfig.TextAlignment.CENTER);
	}

	/**
	 * spacing pushes the label's two space-separated halves apart by that many (zoom-scaled) pixels,
	 * keeping the pair positioned as a group under alignment; 0 draws it as one undivided string.
	 * See hpTextSpacing().
	 */
	private void drawLabel(Graphics2D g, BarStyle style, String label, int x, int y, int w, int h, double zoom,
			Color textColor, int spacing, CustomHpBarConfig.TextAlignment alignment)
	{
		Font font = resolveFont(style.fontFamily, style.fontStyle, scaled(style.fontSize, zoom));
		g.setFont(font);

		// Centered on actual rendered glyph bounds (getPixelBounds), not nominal font metrics -
		// textVerticalNudge covers any residual per-font offset.
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(label, font, frc).getPixelBounds(frc, 0, 0);
		int nudge = scaled(style.textNudge, zoom);

		// Measured off the undivided label either way, so the split halves keep the exact baseline
		// and starting pen position they'd have had as one string - at spacing 0 this is identical.
		int split = spacing > 0 ? label.lastIndexOf(' ') : -1;
		int textWidth = (int) Math.round(pixelBounds.getWidth());
		int gap = split < 0 ? 0 : Math.max(0, Math.min(scaled(spacing, zoom), w - textWidth));

		// LEFT/RIGHT inset by the border thickness so the text clears the border stroke instead of
		// sitting under it; CENTER is untouched from before textAlignment existed (measured against
		// the full w, no border inset) so the default look doesn't shift for existing users.
		int textX;
		switch (alignment)
		{
			case LEFT:
				textX = x + scaled(style.borderWidth, zoom) - pixelBounds.x;
				break;
			case RIGHT:
				textX = x + w - scaled(style.borderWidth, zoom) - textWidth - gap - pixelBounds.x;
				break;
			case CENTER:
			default:
				textX = x + (int) Math.round((w - textWidth - gap) / 2.0) - pixelBounds.x;
				break;
		}
		int textY = y + (int) Math.round((h - pixelBounds.getHeight()) / 2.0) - pixelBounds.y + nudge;

		if (gap == 0)
		{
			drawText(g, style, label, textX, textY, textColor);
			return;
		}

		String head = label.substring(0, split);
		String tail = label.substring(split + 1);
		// Advance, not pixel bounds: this is where drawString would have put the tail's pen.
		int tailX = textX + (int) Math.round(font.getStringBounds(label.substring(0, split + 1), frc).getWidth()) + gap;
		drawText(g, style, head, textX, textY, textColor);
		drawText(g, style, tail, tailX, textY, textColor);
	}

	/**
	 * The name row for an NPC or another player: the name plus an optional " (lvl N)" suffix, one centered
	 * line. A null suffix, or a suffixColor equal to nameColor, keeps the pair on drawLabel()'s single-string
	 * path; only a suffix that really differs takes the split path.
	 */
	private void drawNameLabel(Graphics2D g, BarStyle style, String name, String suffix, int x, int y, int w, int h,
			double zoom, Color nameColor, Color suffixColor)
	{
		if (suffix == null)
		{
			drawLabel(g, style, name, x, y, w, h, zoom, nameColor);
		}
		else if (suffixColor == null || suffixColor.equals(nameColor))
		{
			drawLabel(g, style, name + suffix, x, y, w, h, zoom, nameColor);
		}
		else
		{
			drawSplitLabel(g, style, name, suffix, x, y, w, h, zoom, nameColor, suffixColor);
		}
	}

	/**
	 * A centered label drawn as two independently colored runs. Measured and positioned off the concatenation,
	 * so the pair lands exactly where drawLabel() would have put the same string - only the tail's color
	 * differs. CENTER only, since its one caller is the name row.
	 */
	private void drawSplitLabel(Graphics2D g, BarStyle style, String head, String tail, int x, int y, int w, int h,
			double zoom, Color headColor, Color tailColor)
	{
		Font font = resolveFont(style.fontFamily, style.fontStyle, scaled(style.fontSize, zoom));
		g.setFont(font);

		String label = head + tail;
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(label, font, frc).getPixelBounds(frc, 0, 0);

		int textWidth = (int) Math.round(pixelBounds.getWidth());
		int textX = x + (int) Math.round((w - textWidth) / 2.0) - pixelBounds.x;
		int textY = y + (int) Math.round((h - pixelBounds.getHeight()) / 2.0) - pixelBounds.y
			+ scaled(style.textNudge, zoom);

		// Advance, not pixel bounds - where drawString would have left the pen had the two been
		// drawn as one string, which is exact here because drawString applies no kerning.
		int tailX = textX + (int) Math.round(font.getStringBounds(head, frc).getWidth());
		drawText(g, style, head, textX, textY, headColor);
		drawText(g, style, tail, tailX, textY, tailColor);
	}

	/** One string with its outline/shadow, at an already-resolved pen position. */
	private static void drawText(Graphics2D g, BarStyle style, String text, int textX, int textY, Color textColor)
	{
		g.setColor(Color.BLACK);
		if (style.textOutline)
		{
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx != 0 || dy != 0)
					{
						g.drawString(text, textX + dx, textY + dy);
					}
				}
			}
		}
		else
		{
			g.drawString(text, textX + 1, textY + 1);
		}

		g.setColor(textColor);
		g.drawString(text, textX, textY);
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
		CustomHpBarConfig.DisplayMode mode = displayMode(actor);

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
				// One BOTH format for every actor type - see CLAUDE.md for why players lost their parentheses.
				return hp + " " + pct + "%";
			case NEITHER:
			default:
				// null suppresses the label; the bar itself still draws.
				return null;
		}
	}

	/**
	 * The Display Mode governing this actor: self, other players and NPCs each have their own. Other players
	 * get PERCENT/NEITHER only - no API exposes another player's max HP, so NUMBER/BOTH would silently fall
	 * back to percent in buildLabel() anyway.
	 */
	private CustomHpBarConfig.DisplayMode displayMode(Actor actor)
	{
		if (actor == client.getLocalPlayer())
		{
			return config.selfDisplayMode();
		}
		if (actor instanceof Player)
		{
			return config.otherPlayerDisplayMode() == CustomHpBarConfig.OtherPlayerDisplayMode.NEITHER
				? CustomHpBarConfig.DisplayMode.NEITHER
				: CustomHpBarConfig.DisplayMode.PERCENT;
		}
		return config.targetDisplayMode();
	}

	/** Gap between a bar's HP number and percentage, 0 for one label - BOTH only, clamped in drawLabel. */
	private int hpTextSpacing(Actor actor)
	{
		if (displayMode(actor) != CustomHpBarConfig.DisplayMode.BOTH)
		{
			return 0;
		}
		return actor instanceof Player ? config.playerHpTextSpacing() : config.targetHpTextSpacing();
	}

	/** Actor's max HP, or -1 if unknown (percent then). Native HUD first, then resolveNpcMaxHp()/skill. */
	private int resolveMaxHp(Actor actor)
	{
		int[] shield = plugin.doomShieldHp(actor);
		if (shield != null)
		{
			return shield[1];
		}

		// Percent-only NPCs suppress the number without losing the HUD's own fraction: resolveHp()
		// still reads the HUD, only the max is withheld so buildLabel() falls through to a percentage.
		if (actor instanceof NPC && plugin.isPercentOnlyNpc((NPC) actor))
		{
			return -1;
		}
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

	/** When a player was first seen on the tile they're standing on now - see tileArrivals. */
	@AllArgsConstructor
	private static final class TileArrival
	{
		final WorldPoint tile;
		final int tick;
	}

	/**
	 * One other player whose draw render() deferred to the end of the frame, so their overhead icon row lands
	 * on top of their tile's stack (resolveIconOwners()). Mutable because the tracked loop (bar) and the
	 * "Always Show Player Name" pass (name) can each pick up the same player.
	 */
	private static final class DeferredIconEntry
	{
		final Player player;
		/** Raw, unclaimed - the deferred pass claims the slot itself. */
		final Point anchor;
		final BarStyle style;
		int[] hp;
		int maxHp;
		boolean drawBar;
		boolean drawName;

		DeferredIconEntry(Player player, Point anchor, BarStyle style)
		{
			this.player = player;
			this.anchor = anchor;
			this.style = style;
		}
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
		final boolean hpColorGradient;
		final Color colorMid;
		final Color colorLow;
		final int midpoint;
		final Color background;
		/** 0-100 - the whole bar shape's transparency (background/fill/border), not the label text. */
		final int opacity;
		/** HP bar only, and only the fill - the trail lags the fill after a hit. See damageTrailFraction(). */
		final boolean damageTrail;
		final Color damageTrailColor;
		final boolean damageTrailMatchBar;
		final int verticalOffset;
		final CustomHpBarConfig.FontFamily fontFamily;
		final CustomHpBarConfig.FontStyle fontStyle;
		final int fontSize;
		final Color textColor;
		final boolean textOutline;
		final int textNudge;
		final CustomHpBarConfig.TextAlignment textAlignment;
	}
}
