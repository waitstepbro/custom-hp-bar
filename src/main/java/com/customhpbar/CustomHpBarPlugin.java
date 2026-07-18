package com.customhpbar;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@PluginDescriptor(
	name = "Custom HP Bar",
	description = "Draws a custom health bar overlay with HP numbers directly on the bar",
	tags = {"hp", "health", "bar", "overlay", "npc", "combat"}
)
public class CustomHpBarPlugin extends Plugin
{
	/** OSRS game tick length, for converting the configurable persist duration to ticks. */
	private static final double MS_PER_TICK = 600.0;

	/**
	 * Hitsplat types that represent actual HP damage, for precise HP tracking. Deliberately
	 * conservative - excludes hitsplat types whose target resource isn't confirmed to be HP
	 * (PRAYER_DRAIN, SANITY_DRAIN/RESTORE, CYAN_UP/DOWN, CORRUPTION, DOOM). Missing a real
	 * damage type here just means an occasional recalibration snap next ratio update (see
	 * updatePreciseHp) rather than silently wrong numbers, so being conservative is the safe
	 * direction to be wrong in.
	 */
	private static final Set<Integer> DAMAGE_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_OTHER,
		HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_OTHER_CYAN,
		HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_OTHER_ORANGE,
		HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_OTHER_YELLOW,
		HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_OTHER_WHITE,
		HitsplatID.DAMAGE_MAX_ME, HitsplatID.DAMAGE_MAX_ME_CYAN,
		HitsplatID.DAMAGE_MAX_ME_ORANGE, HitsplatID.DAMAGE_MAX_ME_YELLOW, HitsplatID.DAMAGE_MAX_ME_WHITE,
		HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_OTHER_POISE, HitsplatID.DAMAGE_MAX_ME_POISE,
		HitsplatID.POISON, HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.DISEASE_BLOCKED,
		HitsplatID.BURN, HitsplatID.BLEED, HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER
	));

	/**
	 * NPC IDs confirmed to be deliberately invisible per-boss mechanic entities, never meant to
	 * be shown at all - excluded from tracking entirely (no bar, no name), not just name display.
	 * Found after a user report of "Enraged Blue Moon" appearing on a 0%-HP bar right after the
	 * real Blue Moon boss died: RuneLite's own NpcID constants name these PMOON_*_BOSS_INVIS -
	 * the "_INVIS" suffix confirms intentional invisibility, despite each having a misleadingly
	 * boss-like doc comment ("Enraged Blood/Blue/Eclipse Moon"). The OSRS Wiki has no "enraged"
	 * phase documented for any of the three Moons of Peril bosses, and a wiki search for "Enraged
	 * Blue Moon" specifically redirects back to the plain Blue Moon article, calling it "possibly
	 * a scrapped mechanic or outdated name" - external confirmation this isn't real, intended
	 * boss content. A name-string filter alone can't reliably catch this: it showed once as
	 * "Enraged:Blue Moon" (caught by the colon check in CustomHpBarOverlay.isDisplayableName) and
	 * once as "Enraged Blue Moon" with no colon at all (not caught by that check) - the exact
	 * format apparently isn't consistent, but the NPC ID always is.
	 */
	private static final Set<Integer> HIDDEN_MECHANIC_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.PMOON_BLOOD_BOSS_INVIS, NpcID.PMOON_BLUE_BOSS_INVIS, NpcID.PMOON_ECLIPSE_BOSS_INVIS
	));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CustomHpBarOverlay overlay;

	@Inject
	private CustomHpBarConfig config;

	/**
	 * Actors whose bars are active. Value = tick count of the last valid health-ratio read.
	 * ConcurrentHashMap: game events (game thread) write; overlay render (EDT) reads.
	 */
	@Getter
	private final Map<Actor, Integer> trackedActors = new ConcurrentHashMap<>();

	/**
	 * Most-recently seen [current, max] HP per actor. The overlay falls back to this while the
	 * native bar has faded but the actor is still within its persistDuration window (see
	 * onGameTick's eviction logic - this map's own lifecycle is entirely tick-driven, not read
	 * by anything here).
	 */
	@Getter
	private final Map<Actor, int[]> lastKnownHp = new ConcurrentHashMap<>();

	/**
	 * Precise current HP per NPC (only populated for NPCs with a known max HP from
	 * NpcMaxHpTable). getHealthRatio()/getHealthScale() are coarse - Jagex only sends a
	 * low-resolution bucket, not exact HP - so between bucket-changing ratio updates, this is
	 * kept in sync by subtracting/adding hitsplat damage/heal amounts instead, for a much
	 * closer match to the NPC's true current HP. See updatePreciseHp()/applyHitsplatDamage().
	 */
	@Getter
	private final Map<NPC, Integer> preciseNpcHp = new ConcurrentHashMap<>();

	/**
	 * Tick of the most recent hitsplat of each status-effect type, per actor. Used to infer
	 * "is this actor currently affected" for bar tinting (see statusEffectColor()) wherever no
	 * better signal exists. For NPCs, this is the only signal available at all - they don't
	 * expose a queryable status effect state the way the local player does. For the local
	 * player, Poison/Venom have an exact signal instead (VarPlayerID.POISON, the same one
	 * RuneLite's own Poison/Status Bars plugins read), so lastPoisonTick/lastVenomTick are only
	 * actually consulted for NPCs - but Burn/Bleed have no such varp for either actor type, so
	 * lastBurnTick/lastBleedTick are used for both.
	 */
	private final Map<Actor, Integer> lastPoisonTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastVenomTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastBurnTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastBleedTick = new ConcurrentHashMap<>();

	/** Cached compiled filter patterns to avoid regex compilation on every tracking check. */
	private String cachedFilterString = "";
	private List<Pattern> cachedPatterns = new ArrayList<>();

	@Provides
	CustomHpBarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomHpBarConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		if (config.hideNativeBar())
		{
			clientThread.invokeLater(() -> applySpriteOverride(NativeHealthBarSprites.ALL));
		}
		if (config.showPrayerBar())
		{
			clientThread.invokeLater(() -> applySpriteOverride(NativeHealthBarSprites.PRAYER));
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		trackedActors.clear();
		lastKnownHp.clear();
		preciseNpcHp.clear();
		clientThread.invoke(() -> removeSpriteOverride(NativeHealthBarSprites.ALL));
		clientThread.invoke(() -> removeSpriteOverride(NativeHealthBarSprites.PRAYER));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"customhpbar".equals(event.getGroup()))
		{
			return;
		}

		if ("hideNativeBar".equals(event.getKey()))
		{
			if (config.hideNativeBar())
			{
				clientThread.invokeLater(() -> applySpriteOverride(NativeHealthBarSprites.ALL));
			}
			else
			{
				clientThread.invokeLater(() -> removeSpriteOverride(NativeHealthBarSprites.ALL));
			}
		}
		else if ("showPrayerBar".equals(event.getKey()))
		{
			// The native prayer bar is a separate purple-colored variant of the same overhead-
			// bar system the HP bar uses, shown whenever prayer points change - independent of
			// hideNativeBar, which is about the hitpoints-colored bars. Without this, enabling
			// our own prayer bar still left the native one appearing on top of/next to it.
			if (config.showPrayerBar())
			{
				clientThread.invokeLater(() -> applySpriteOverride(NativeHealthBarSprites.PRAYER));
			}
			else
			{
				clientThread.invokeLater(() -> removeSpriteOverride(NativeHealthBarSprites.PRAYER));
			}
		}
	}

	/**
	 * Overrides every sprite ID in spriteIds with a transparent 1x1 pixel. This is a client-wide
	 * sprite swap, not a per-actor toggle, so it hides native bars for every actor while active.
	 */
	private void applySpriteOverride(int[] spriteIds)
	{
		SpritePixels transparent = client.createSpritePixels(new int[]{0}, 1, 1);
		for (int spriteId : spriteIds)
		{
			client.getSpriteOverrides().put(spriteId, transparent);
		}
		client.resetHealthBarCaches();
	}

	private void removeSpriteOverride(int[] spriteIds)
	{
		for (int spriteId : spriteIds)
		{
			client.getSpriteOverrides().remove(spriteId);
		}
		client.resetHealthBarCaches();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();

		// Only HP-relevant hitsplats should trigger tracking/caching - a hitsplat existing at
		// all doesn't mean HP changed (e.g. PRAYER_DRAIN fires its own hitsplat-style number
		// when praying at an altar or a prayer draining, with nothing to do with HP).
		if (!isHpRelevantHitsplat(hitsplat.getHitsplatType()))
		{
			return;
		}

		if (isTrackedType(actor))
		{
			trackedActors.put(actor, client.getTickCount());
			cacheHp(actor);
		}

		trackStatusEffect(actor, hitsplat.getHitsplatType());

		if (actor instanceof NPC)
		{
			applyHitsplatDamage((NPC) actor, hitsplat);
		}
	}

	/**
	 * Records the tick a status-effect hitsplat landed, for any actor - not gated to NPCs, since
	 * the local player can bleed/burn/be poisoned too and (for Burn/Bleed) has no better signal
	 * than this either. Harmless to record for actors that never end up consulting these maps
	 * (e.g. other players, whose status color isn't implemented) - just a few unused entries,
	 * cleared normally by evict().
	 */
	private void trackStatusEffect(Actor actor, int hitsplatType)
	{
		int currentTick = client.getTickCount();
		if (hitsplatType == HitsplatID.VENOM)
		{
			lastVenomTick.put(actor, currentTick);
		}
		else if (hitsplatType == HitsplatID.POISON)
		{
			lastPoisonTick.put(actor, currentTick);
		}
		else if (hitsplatType == HitsplatID.BURN)
		{
			lastBurnTick.put(actor, currentTick);
		}
		else if (hitsplatType == HitsplatID.BLEED)
		{
			lastBleedTick.put(actor, currentTick);
		}
	}

	private static boolean isHpRelevantHitsplat(int hitsplatType)
	{
		return hitsplatType == HitsplatID.HEAL || DAMAGE_HITSPLATS.contains(hitsplatType);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Actor source = event.getSource();
		Actor target = event.getTarget();

		// Also fires when the player's interacting reference is cleared (target == null) -
		// e.g. observed happening on zone transitions, unrelated to combat. Only a non-null
		// target means the player actually started interacting with something.
		//
		// getInteracting() is also set by non-combat interactions - dialogue with an NPC,
		// trading, following, or a transport NPC's own menu option (e.g. clicking "Travel" on a
		// Quetzal) - which fire this exact same event with a non-null target, with nothing in
		// the event itself distinguishing them from an actual attack. Reported symptom: the
		// self bar appearing just from clicking Travel on a Quetzal, not from any combat at
		// all. getCombatLevel() > 0 filters these out - the same signal core RuneLite's own
		// InteractHighlight plugin uses for exactly this "was this an attack" distinction
		// (confirmed in its source: `attacked = target != null && target.getCombatLevel() > 0`).
		// Every non-combat NPC (Quetzal, bankers, quest NPCs, other transport NPCs, etc.) has
		// combat level 0, so this excludes all of them while never excluding a real target -
		// attackable NPCs and players always report a positive combat level.
		if (source != client.getLocalPlayer() || target == null || target.getCombatLevel() <= 0)
		{
			return;
		}

		// Track whatever the player is attacking...
		if (isTrackedType(target))
		{
			trackedActors.put(target, client.getTickCount());
			cacheHp(target);
		}

		// ...and the player themselves, so "Show for Self" reflects entering combat
		// immediately rather than waiting for the first hitsplat the player receives.
		if (isTrackedType(source))
		{
			trackedActors.put(source, client.getTickCount());
			cacheHp(source);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int currentTick = client.getTickCount();
		int persistTicks = (int) Math.round(config.persistDuration() * (1000.0 / MS_PER_TICK));

		// The local player (and whoever they're fighting) may need to start being tracked
		// without a fresh HitsplatApplied/InteractingChanged event ever firing - e.g. "Show
		// for Self" was just turned on while already mid-fight. Cheap to check every tick
		// since it's a single actor, not a scan of everything nearby.
		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			// Same non-combat-interaction exclusion as onInteractingChanged (see its comment) -
			// this fallback would otherwise keep re-tracking a lingering interacting reference
			// left over from a Quetzal/dialogue/etc. interaction on every subsequent tick.
			Actor interacting = localPlayer.getInteracting();
			if (interacting != null && interacting.getCombatLevel() > 0
					&& isTrackedType(interacting) && !trackedActors.containsKey(interacting))
			{
				trackedActors.put(interacting, currentTick);
				cacheHp(interacting);
			}
			if (isTrackedType(localPlayer) && isInCombat(localPlayer) && !trackedActors.containsKey(localPlayer))
			{
				trackedActors.put(localPlayer, currentTick);
				cacheHp(localPlayer);
			}
		}

		// ConcurrentHashMap.forEach allows safe reads and puts/removes during iteration.
		trackedActors.forEach((actor, lastSeen) ->
		{
			// A config toggle (Show for Self/Other Players) may have turned this actor's bar
			// off since it was tracked - evict immediately instead of waiting out the persist
			// timer, so unchecking the toggle takes effect right away rather than on a delay.
			if (!isTrackedType(actor))
			{
				evict(actor);
				return;
			}

			if (isInCombat(actor))
			{
				trackedActors.put(actor, currentTick);
				cacheHp(actor);
			}
			else if (currentTick - lastSeen > persistTicks)
			{
				evict(actor);
			}
		});
	}

	private void evict(Actor actor)
	{
		trackedActors.remove(actor);
		lastKnownHp.remove(actor);
		lastPoisonTick.remove(actor);
		lastVenomTick.remove(actor);
		lastBurnTick.remove(actor);
		lastBleedTick.remove(actor);
		if (actor instanceof NPC)
		{
			preciseNpcHp.remove(actor);
		}
	}

	/**
	 * True if the actor's native health bar is actively refreshing. Governs whether the
	 * persist-duration eviction clock keeps getting reset.
	 *
	 * This used to also treat the local player as "in combat" whenever Actor.getInteracting()
	 * was non-null, to work around getHealthRatio() supposedly going stale between hits. That
	 * was wrong and caused a worse bug: getInteracting() stays set well after a fight has
	 * actually ended (it doesn't clear promptly), so it kept resetting lastSeen every tick and
	 * the persist timer never actually counted down - the bar persisted far longer than
	 * persistDuration, sometimes effectively indefinitely. getHealthRatio() alone, the same
	 * signal every other actor uses, is the correct one.
	 */
	private boolean isInCombat(Actor actor)
	{
		return actor.getHealthRatio() != -1;
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		evict(event.getNpc());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		evict(event.getPlayer());
	}

	private void cacheHp(Actor actor)
	{
		int[] hp = readHp(client, actor);
		if (hp != null)
		{
			lastKnownHp.put(actor, hp);
			if (actor instanceof NPC)
			{
				updatePreciseHp((NPC) actor, hp[0], hp[1]);
			}
		}
	}

	/**
	 * Establishes or sanity-checks the precise HP baseline for an NPC from a fresh
	 * getHealthRatio()/getHealthScale() reading. Only overwrites an existing estimate if it has
	 * drifted outside the range of true HP values that bucket could represent - e.g. we joined
	 * a fight already in progress, or missed a non-hitsplat heal - otherwise the hitsplat-
	 * accumulated value (finer-grained than this ratio/scale bucket) is left alone.
	 *
	 * ratio == 0 and ratio == scale are treated as hard floor/ceiling, not fuzzy bucket
	 * tolerance: unlike intermediate buckets, "empty" and "full" aren't ranges, they're exact.
	 * Without this, an NPC that died still showed a sliver of HP/fill on its last frames
	 * whenever accumulated hitsplat damage didn't land on exactly 0 (e.g. from an incorrect
	 * NpcMaxHpTable entry throwing the whole running estimate off by a constant amount).
	 */
	private void updatePreciseHp(NPC npc, int ratio, int scale)
	{
		int maxHp = NpcMaxHpTable.getMaxHp(npc.getId());
		if (maxHp <= 0)
		{
			preciseNpcHp.remove(npc);
			return;
		}

		if (ratio == 0)
		{
			preciseNpcHp.put(npc, 0);
			return;
		}
		if (ratio == scale)
		{
			preciseNpcHp.put(npc, maxHp);
			return;
		}

		int ratioEstimate = (int) Math.round((double) ratio / scale * maxHp);
		Integer current = preciseNpcHp.get(npc);
		if (current == null)
		{
			preciseNpcHp.put(npc, ratioEstimate);
			return;
		}

		int bucketWidth = Math.max(1, maxHp / scale);
		if (Math.abs(current - ratioEstimate) > bucketWidth)
		{
			preciseNpcHp.put(npc, ratioEstimate);
		}
	}

	/**
	 * Adjusts an NPC's precise HP estimate by a hitsplat's damage/heal amount. No-ops if we
	 * don't have a baseline yet for this NPC (set by updatePreciseHp on the next ratio read).
	 */
	private void applyHitsplatDamage(NPC npc, Hitsplat hitsplat)
	{
		Integer current = preciseNpcHp.get(npc);
		if (current == null)
		{
			return;
		}

		int type = hitsplat.getHitsplatType();
		int amount = hitsplat.getAmount();
		int delta;
		if (type == HitsplatID.HEAL)
		{
			delta = amount;
		}
		else if (DAMAGE_HITSPLATS.contains(type))
		{
			delta = -amount;
		}
		else
		{
			return;
		}

		int maxHp = NpcMaxHpTable.getMaxHp(npc.getId());
		int updated = current + delta;
		updated = Math.max(0, maxHp > 0 ? Math.min(updated, maxHp) : updated);
		preciseNpcHp.put(npc, updated);
	}

	/**
	 * Ticks after a status-effect hitsplat that its color stays active for bar tinting, wherever
	 * a hitsplat is the only signal available (see lastPoisonTick's doc comment) - i.e. how long
	 * a cured/expired effect can keep showing its color before this catches up. Deliberately
	 * shorter than PoisonPlugin.POISON_TICK_MILLIS (18200ms, the known player poison-tick cadence,
	 * used as the initial estimate) - tightened to ~5s on request, trading a bit of flicker risk
	 * (if an actual tick interval turns out longer than this) for less lag after the effect
	 * really ends, since no confirmed NPC/Burn/Bleed tick cadence exists to tune against anyway.
	 */
	private static final int STATUS_EFFECT_TICKS = 8;

	/** VarPlayerID.POISON value at and above which the player is envenomed rather than poisoned - matches PoisonPlugin's own VENOM_THRESHOLD. */
	private static final int VENOM_THRESHOLD = 1_000_000;

	/**
	 * Bar fill color for an actor currently affected by a status effect, or null if none applies
	 * (or the relevant config toggle is off). Only NPCs and the local player are handled - other
	 * players' status coloring wasn't asked for, so it's out of scope here (their poison/venom
	 * varp isn't readable from this client anyway; only Burn/Bleed could work the same way NPCs
	 * do, via hitsplats, if that's ever wanted).
	 */
	Color statusEffectColor(Actor actor)
	{
		int currentTick = client.getTickCount();

		if (actor instanceof NPC)
		{
			if (!config.targetColorByStatusEffect())
			{
				return null;
			}
			return hitsplatStatusColor(actor, currentTick,
				config.targetVenomColor(), config.targetPoisonColor(),
				config.targetBurnColor(), config.targetBleedColor());
		}

		if (actor == client.getLocalPlayer())
		{
			if (!config.selfColorByStatusEffect())
			{
				return null;
			}

			// Poison/Venom have an exact signal for the local player - prefer it over the
			// hitsplat heuristic used for every other case in this method.
			int poison = client.getVarpValue(VarPlayerID.POISON);
			if (poison >= VENOM_THRESHOLD)
			{
				return config.selfVenomColor();
			}
			if (poison > 0)
			{
				return config.selfPoisonColor();
			}

			return hitsplatStatusColor(actor, currentTick, null, null, config.selfBurnColor(), config.selfBleedColor());
		}

		return null;
	}

	/**
	 * Checks the four status hitsplat maps in venom/poison/burn/bleed priority order, returning
	 * the first one still within STATUS_EFFECT_TICKS of its last hitsplat. Passing null for a
	 * color skips that check entirely (used to let the local player's exact poison/venom signal
	 * take priority without this method needing to know why).
	 */
	private Color hitsplatStatusColor(Actor actor, int currentTick, Color venomColor, Color poisonColor, Color burnColor, Color bleedColor)
	{
		if (venomColor != null && withinStatusWindow(lastVenomTick.get(actor), currentTick))
		{
			return venomColor;
		}
		if (poisonColor != null && withinStatusWindow(lastPoisonTick.get(actor), currentTick))
		{
			return poisonColor;
		}
		if (burnColor != null && withinStatusWindow(lastBurnTick.get(actor), currentTick))
		{
			return burnColor;
		}
		if (bleedColor != null && withinStatusWindow(lastBleedTick.get(actor), currentTick))
		{
			return bleedColor;
		}
		return null;
	}

	private static boolean withinStatusWindow(Integer lastTick, int currentTick)
	{
		return lastTick != null && currentTick - lastTick <= STATUS_EFFECT_TICKS;
	}

	/**
	 * Returns live [current, max] HP for the actor, or null if none is available right now.
	 * For the local player this bypasses getHealthRatio()/getHealthScale() entirely and reads
	 * their Hitpoints skill directly - those are always live and accurate, whereas ratio/scale
	 * mirror the native combat health bar's data and don't refresh from non-combat HP changes
	 * (eating food) while that native bar isn't actively displaying, which made our own bar
	 * show stale HP after eating until the next combat-triggered ratio update.
	 */
	static int[] readHp(Client client, Actor actor)
	{
		if (actor == client.getLocalPlayer())
		{
			return new int[]{client.getBoostedSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.HITPOINTS)};
		}

		int ratio = actor.getHealthRatio();
		int scale = actor.getHealthScale();
		if (ratio >= 0 && scale > 0)
		{
			return new int[]{ratio, scale};
		}
		return null;
	}

	private boolean isTrackedType(Actor actor)
	{
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			return !HIDDEN_MECHANIC_NPC_IDS.contains(npc.getId()) && matchesFilter(npc.getName());
		}
		if (!(actor instanceof Player))
		{
			return false;
		}
		return actor == client.getLocalPlayer() ? config.showForSelf() : config.showForPlayers();
	}

	/**
	 * Whether npc matches the configured NPC filter, independent of whether it's currently
	 * tracked (in combat). Used by the overlay's "Always Show NPC Name" path, which needs to
	 * check untracked NPCs that never went through isTrackedType()/onHitsplatApplied at all.
	 */
	boolean matchesNpcFilter(NPC npc)
	{
		return !HIDDEN_MECHANIC_NPC_IDS.contains(npc.getId()) && matchesFilter(npc.getName());
	}

	/**
	 * Returns true if the NPC should be tracked at all. Pure blacklist: empty filter = show all,
	 * and any matching entry hides that NPC - there's no whitelist mode (an earlier version
	 * flipped into "only show listed NPCs" whenever a non-negated pattern was present, which
	 * matched a name to a filter entry and hid it wrongly given the user's actual intent - always
	 * meant as an exclude list, not an include list). Patterns are comma-separated,
	 * case-insensitive, and support '*' wildcards. A trailing ':n' is still accepted and stripped
	 * for backward compatibility with filters written under the old dual-mode behavior, but no
	 * longer changes anything - every entry excludes, with or without it.
	 *
	 * Checked here (gating isTrackedType, so a filtered-out NPC is never added to trackedActors
	 * at all) rather than only at render time: filtering only in the overlay still left every
	 * NPC's hitsplats accumulating into preciseNpcHp and cluttering trackedActors/lastKnownHp
	 * for NPCs that could never actually be shown, which is wasted work and wasted memory for
	 * anyone with a narrow filter.
	 */
	private boolean matchesFilter(String npcName)
	{
		String filterStr = config.npcFilter().trim();
		if (filterStr.isEmpty() || npcName == null)
		{
			return true;
		}

		// Recompile patterns only when the filter string changes.
		if (!filterStr.equals(cachedFilterString))
		{
			cachedFilterString = filterStr;
			cachedPatterns = compilePatterns(filterStr);
		}

		String nameLower = npcName.toLowerCase(Locale.ROOT);
		for (Pattern pattern : cachedPatterns)
		{
			if (pattern.matcher(nameLower).matches())
			{
				return false;
			}
		}
		return true;
	}

	private static List<Pattern> compilePatterns(String filterStr)
	{
		List<Pattern> entries = new ArrayList<>();
		for (String raw : filterStr.split(","))
		{
			String token = raw.trim();
			if (token.endsWith(":n"))
			{
				token = token.substring(0, token.length() - 2).trim();
			}

			if (token.isEmpty())
			{
				continue;
			}

			Pattern pat = Pattern.compile(
				"\\Q" + token.replace("*", "\\E.*\\Q") + "\\E",
				Pattern.CASE_INSENSITIVE);
			entries.add(pat);
		}
		return entries;
	}
}
