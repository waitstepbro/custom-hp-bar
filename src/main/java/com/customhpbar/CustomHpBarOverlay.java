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

	/** Fixed grey for both grey-out toggles - overrides the status-effect tint and the aggressive name color so it reads unambiguously. */
	private static final Color LOOT_TAINTED_COLOR = new Color(120, 120, 120);

	/** Alpha for a bar's heal/restore preview segment - reads as "not real yet" over whatever color the bar is showing. */
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

	/** Approximate overhead icon height reserved in a same-tile stack (avoids depending on whether the real sprite has loaded). */
	private static final int STACK_ICON_CLEARANCE = 24;

	/** Gap between the overhead chat text and the HP bar/icon above which it's moved, before zoom scaling. */
	private static final int CHAT_TEXT_BAR_GAP = 3;

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

	/** Camera zoom observed the first time we render, used as the "1.0x" baseline for zoom scaling - see zoomFactor(). */
	private int baselineZoom = -1;

	/** Client sprites already loaded, keyed by sprite ID + frame index - see clientSprite(). */
	private final Map<Long, BufferedImage> clientSprites = new HashMap<>();

	/** Icons bundled as plugin resources, keyed by file name - for graphics with no confirmed client SpriteID. */
	private final Map<String, BufferedImage> bundledIcons = new HashMap<>();

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

		// Resolved lazily, at most once each per frame no matter how many actors share a profile.
		BarStyle targetStyle = null;
		BarStyle playerStyle = null;

		// Same-tile stacking, rebuilt each frame: tileStacks tracks claimed pixels per tile;
		// appliedStacks lets the "Always Show NPC Name" pass below reuse an already-drawn shift.
		Map<WorldPoint, Integer> tileStacks = new HashMap<>();
		Map<Actor, Integer> appliedStacks = new HashMap<>();

		for (Map.Entry<Actor, Integer> entry : plugin.getTrackedActors().entrySet())
		{
			Actor actor = entry.getKey();

			// Filtering already happened in CustomHpBarPlugin.isTrackedType() - nothing to re-check.
			int maxHp = resolveMaxHp(actor);
			int[] hp = resolveHp(actor, maxHp);
			if (hp == null)
			{
				continue;
			}

			// localToCanvas, not getCanvasTextLocation - the latter has a per-frame animation bob.
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

		// Independent path: shows the Prayer bar outside combat too (e.g. at a bank), skipped if
		// the main loop above already drew it.
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && config.showForSelf() && config.showPrayerBar()
				&& !plugin.getTrackedActors().containsKey(localPlayer) && plugin.isPrayerActive())
		{
			Point anchor = Perspective.localToCanvas(
				client, localPlayer.getLocalLocation(), localPlayer.getWorldView().getPlane(), localPlayer.getLogicalHeight());
			if (anchor != null)
			{
				playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
				drawStandalonePrayerBar(g, anchor, playerStyle);
			}
		}

		// Replacement for the native overhead prayer icon, which the render callback suppresses.
		if (localPlayer != null && config.showForSelf() && config.replaceOverheadIcon())
		{
			playerStyle = playerStyle != null ? playerStyle : resolveStyle(localPlayer);
			drawOverheadIcon(g, localPlayer, playerStyle);
			drawSelfHitsplats(g, localPlayer);
			drawOverheadChatText(g, localPlayer, playerStyle);
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
				if (npc == null || !plugin.isTrackedNpc(npc))
				{
					continue;
				}

				// Bankers and fishing spots have no HP to show; talk-only NPCs have a level but no
				// fight in them. Either way the name below can still draw.
				boolean drawBarForThis = alwaysBar && plugin.isAttackableNpc(npc);
				boolean drawNameForThis = alwaysName && isDisplayableName(npc.getName());

				// Decided before claiming a slot: claiming one for an NPC that then draws nothing
				// would shift every other bar on its tile upwards for no visible reason.
				if (!drawBarForThis && !drawNameForThis)
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

				// Non-null once the main loop above already drew this NPC's bar - reuse its shift
				// rather than claiming a second slot for the same actor.
				Integer applied = appliedStacks.get(npc);
				int shift = applied != null ? applied
					: (drawBarForThis ? claimBarStackSlot(tileStacks, npc, targetStyle, zoom)
						: claimNameStackSlot(tileStacks, npc, targetStyle, zoom));
				if (shift > 0)
				{
					anchor = new Point(anchor.getX(), anchor.getY() - shift);
				}

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

		return null;
	}

	/** [current, max] HP for display: native boss HUD wins, then precise hitsplat tracking, then live/last-known reads. */
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

	/** The bar's on-screen rectangle, centered on anchor. Shared by drawBar()/drawNpcNameOnly() so labels don't jump between them. */
	private int[] barRect(Point anchor, BarStyle style, double zoom)
	{
		int w = scaled(style.width, zoom);
		int h = scaled(style.height, zoom);
		int vOffset = scaled(style.verticalOffset, zoom);
		int x = anchor.getX() - w / 2;
		int y = anchor.getY() - h / 2 - vOffset;
		return new int[]{x, y, w, h};
	}

	/** Claims a same-tile stack slot for an actor's full bar, returning the upward pixel shift to apply (0 for the first actor). */
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

	/** Draws just the NPC name label at its would-be bar position - used for "Always Show NPC Name" on untracked NPCs. */
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
		// Grey wins over the aggressive color, same as it overrides the bar's status tint.
		Color nameColor;
		if (config.greyOutOtherPlayerDamageNames() && plugin.isLootTainted(npc))
		{
			nameColor = LOOT_TAINTED_COLOR;
		}
		else
		{
			nameColor = config.colorAggressiveNpcNames() && plugin.isNpcAggressive(npc)
				? config.aggressiveNpcNameColor() : config.npcNameColor();
		}
		drawLabel(g, style, Text.removeTags(npcName), x, y - h - nameGap, w, h, zoom, nameColor);
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

	/** Filters internal/placeholder names: literal "null", or a "Category:Label" name with a colon/semicolon. Label only, not the bar. */
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
		Color fillColor = config.greyOutOtherPlayerDamage() && actor instanceof NPC
			&& plugin.isLootTainted((NPC) actor) ? LOOT_TAINTED_COLOR : null;
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
			// ratio/scale are the local player's real current/max HP already, not a bucket.
			drawHealPreview(g, x, y, w, h, border, ratio, maxHp, hoveredRestoreValue(Skill.HITPOINTS),
				translucent(fillColor));
		}

		String label = buildLabel(actor, hpFraction, maxHp);
		if (label != null)
		{
			drawLabel(g, style, label, x, y, w, h, zoom, style.textColor);
		}

		int bottomY = y + h;
		if (actor == client.getLocalPlayer() && prayerBarAttached())
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

	/** Extends a bar past its current fill with a preview segment showing where the stat would land if healAmount landed now. */
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

	/** Whether the debuff icon row should draw for actor, by actor type - independent of the Color By Status Effect toggle. */
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

	/**
	 * Maps a status effect to its debuff icon, or null if it has none. Disease/Corruption are
	 * bundled resources rather than client sprites, since no confirmed SpriteID.Hitmark entry
	 * exists for either.
	 */
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

	/**
	 * The PK skull status icon, for the aggressive-NPC badge (drawAggressiveNpcIcon). No confirmed
	 * live SpriteID for this exists (SpriteID.ICON_SKULL was tried first and turned out to be the
	 * wrong sprite once actually seen rendered in-game - a name is not proof of appearance, same
	 * lesson as the earlier StandardPrayer/venom-color corrections), so this is a bundled resource
	 * image instead, downloaded directly from oldschool.runescape.wiki's own "Skull (status) icon"
	 * file - the exact graphic OSRS shows above a skulled player's head.
	 */
	private BufferedImage aggressiveIcon()
	{
		return bundledIcon("pk_skull_icon.png");
	}

	/**
	 * A client sprite, cached once loaded. SpriteManager only serves an already-cached sprite
	 * synchronously, so the first miss starts an async load and returns null - callers skip
	 * drawing that badge for the frame and pick it up once it arrives.
	 */
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

	/** Whether the Prayer bar draws beneath your HP bar right now - hidePrayerBarWhenInactive gates it on praying. */
	private boolean prayerBarAttached()
	{
		return config.showPrayerBar() && (!config.hidePrayerBarWhenInactive() || plugin.isPrayerActive());
	}

	/** Draws just the Prayer bar at the HP bar's would-be position - reached only when the local player isn't tracked. */
	private void drawStandalonePrayerBar(Graphics2D g, Point anchor, BarStyle style)
	{
		double zoom = zoomFactor();
		int[] rect = barRect(anchor, style, zoom);
		int border = scaled(style.borderWidth, zoom);
		int arc = scaled(style.cornerRadius, zoom) * 2;
		drawPrayerBar(g, style, rect[0], rect[1], rect[2], rect[3], border, arc, zoom);
	}

	/** Draws the replacement overhead prayer icon above the HP bar - the render callback has already suppressed the native one. */
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

	/** All 15 overhead icon graphics are sub-frames of one client sprite, indexed by HeadIcon.ordinal(). */
	private BufferedImage headIconImage(HeadIcon headIcon)
	{
		return clientSprite(SpriteID.HEADICONS_PRAYER, headIcon.ordinal());
	}

	/** Redraws hitsplats on the local player (real sprite + amount), replacing the ones the render callback suppresses. */
	private void drawSelfHitsplats(Graphics2D g, Player localPlayer)
	{
		List<Hitsplat> hitsplats = plugin.getSelfHitsplats();
		if (hitsplats.isEmpty())
		{
			return;
		}

		// Native hitsplats render at roughly chest height, not above the head like the bar/text.
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

		// Vanilla shows only the 4 most recent hits - selfHitsplats is append-ordered, so take the tail.
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

	/** A hitsplat's background sprite, or null if that type has no confirmed sprite mapping (see HITSPLAT_SPRITE_IDS). */
	private BufferedImage hitsplatImage(int hitsplatType)
	{
		Integer spriteId = HITSPLAT_SPRITE_IDS.get(hitsplatType);
		return spriteId != null ? clientSprite(spriteId, 0) : null;
	}

	/** Redraws the local player's overhead chat text, replacing the native text; tucks under the bar stack when one is shown. */
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
		// Vanilla overhead chat uses the bold font, not the regular one.
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) scaled(16, zoom));
		g.setFont(font);
		FontRenderContext frc = g.getFontRenderContext();
		Rectangle pixelBounds = new TextLayout(text, font, frc).getPixelBounds(frc, 0, 0);

		int x = anchor.getX() - (int) Math.round(pixelBounds.getWidth() / 2.0) - pixelBounds.x;

		int y;
		boolean tracked = plugin.getTrackedActors().containsKey(localPlayer);
		boolean barShown = tracked || (config.showPrayerBar() && plugin.isPrayerActive());
		if (barShown)
		{
			// Tucked beneath the bar stack: the HP bar, plus the Prayer bar's row if attached.
			int[] rect = barRect(anchor, style, zoom);
			int stackBottom = rect[1] + rect[3];
			if (tracked && prayerBarAttached())
			{
				stackBottom += rect[3];
			}
			y = stackBottom + scaled(CHAT_TEXT_BAR_GAP, zoom) - pixelBounds.y;
		}
		else
		{
			// Same projection vanilla's overhead chat text uses (its per-frame bob is fine for text).
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

		// Centered on actual rendered glyph bounds (getPixelBounds), not nominal font metrics -
		// textVerticalNudge covers any residual per-font offset.
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

	/** Actor's max HP, or -1 if unknown (falls back to percent). Native HUD wins first, then resolveNpcMaxHp()/Hitpoints skill. */
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
