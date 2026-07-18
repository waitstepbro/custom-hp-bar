package com.customhpbar;

import lombok.AllArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

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
import java.util.Map;

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

	/** Gap between the NPC name label and the HP bar's top edge. Not configurable yet. */
	private static final int NAME_GAP = 2;

	private final CustomHpBarPlugin plugin;
	private final CustomHpBarConfig config;
	private final Client client;

	/**
	 * Camera zoom (Client.getScale()) observed the first time we render, used as the "1.0x"
	 * baseline for zoom scaling. There's no documented universal reference zoom value to
	 * calibrate against up front, and a wrong guess inflates every bar/font size uniformly
	 * (this is what happened with an earlier hardcoded 512 guess) — capturing whatever zoom
	 * the user is actually playing at guarantees the configured pixel sizes are exactly right
	 * at that zoom, and only scale relative to it as the user zooms in/out from there.
	 */
	private int baselineZoom = -1;

	@Inject
	CustomHpBarOverlay(CustomHpBarPlugin plugin, CustomHpBarConfig config, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
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

			drawBar(g, actor, anchor, hp[0], hp[1], maxHp, style);
		}

		if (config.showNpcName() && config.alwaysShowNpcName())
		{
			double zoom = zoomFactor();
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				// This pass is the sole source of NPC names whenever "Always Show" is on - it
				// deliberately does NOT skip NPCs already in trackedActors (drawBar() skips its
				// own name draw in that case - see below). Tracked-but-no-HP-yet is a real,
				// common state: clicking to attack (even from out of range) adds the NPC to
				// trackedActors via onInteractingChanged well before any hitsplat gives it HP
				// data, and the main loop above bails out entirely on missing HP (hp == null -
				// continue), skipping drawBar() and, with it, the name draw that used to live
				// inside it. Relying on "already tracked" to mean "name already drawn" produced
				// exactly that gap - the name would vanish the instant you clicked to attack and
				// only come back once a hit actually landed. Iterating every matching NPC here
				// unconditionally has no such gap, since it never depends on HP/tracking state.
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
				drawNpcNameOnly(g, npc, anchor, targetStyle, zoom);
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
		drawLabel(g, style, Text.removeTags(npcName), x, y - h - nameGap, w, h, zoom, config.npcNameColor());
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
		Color statusColor = plugin.statusEffectColor(actor);
		drawBarShape(g, style, x, y, w, h, border, arc, hpFraction, statusColor != null ? statusColor : style.barColor);

		String label = buildLabel(actor, hpFraction, maxHp);
		if (label != null)
		{
			drawLabel(g, style, label, x, y, w, h, zoom, style.textColor);
		}

		if (actor == client.getLocalPlayer() && config.showPrayerBar())
		{
			// Flush against the bottom edge of the HP bar, same width/style - "mirrors" the
			// Player Bar profile per the request, rather than getting its own size/shape config.
			drawPrayerBar(g, style, x, y + h, w, h, border, arc, zoom);
		}

		// When "Always Show" is on, the dedicated pass in render() is the sole source of NPC
		// names (see its comment) - drawing it here too would just be redundant, not wrong, but
		// there's no reason to do the work twice.
		if (actor instanceof NPC && config.showNpcName() && !config.alwaysShowNpcName())
		{
			drawNpcNameOnly(g, (NPC) actor, anchor, style, zoom);
		}
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
		drawLabel(g, style, String.valueOf(current), x, y, w, h, zoom, style.textColor);
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
