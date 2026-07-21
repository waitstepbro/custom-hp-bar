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
import net.runelite.api.events.MenuOptionClicked;
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
import java.util.EnumSet;
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
	 * (PRAYER_DRAIN, SANITY_DRAIN/RESTORE, CYAN_UP/DOWN, DOOM). Missing a real damage type here
	 * just means an occasional recalibration snap next ratio update (see updatePreciseHp) rather
	 * than silently wrong numbers, so being conservative is the safe direction to be wrong in.
	 *
	 * DISEASE/DISEASE_BLOCKED and CORRUPTION are deliberately NOT here, unlike an earlier version
	 * of this set - per the OSRS Wiki's Hitsplat page, Disease "drains a player's stats...
	 * excluding Hitpoints" and Corruption "drains prayer points," neither actually costs HP, so
	 * treating them as damage would have applyHitsplatDamage() incorrectly subtract from an NPC's
	 * tracked precise HP for something that never touched it. They're still tracked for status-
	 * effect purposes via STATUS_ONLY_HITSPLATS below, just not treated as damage.
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
		HitsplatID.POISON, HitsplatID.VENOM, HitsplatID.BURN, HitsplatID.BLEED,
		HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER
	));

	/**
	 * Hitsplats relevant for status-effect tracking (see trackStatusEffect) but that don't
	 * represent HP damage, so they're excluded from DAMAGE_HITSPLATS above - kept as a separate
	 * set rather than just added to DAMAGE_HITSPLATS so isTrackableHitsplat() can still admit
	 * them without applyHitsplatDamage() treating them as damage. DISEASE_BLOCKED isn't included
	 * here - it means a disease application was prevented, the opposite of actually being
	 * diseased, so it shouldn't feed lastDiseaseTick at all.
	 */
	private static final Set<Integer> STATUS_ONLY_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DISEASE, HitsplatID.CORRUPTION
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
	 * "is this actor currently affected" for bar tinting/icons (see activeStatusEffects())
	 * wherever no better signal exists. For NPCs and other players, this is the only signal
	 * available at all - only the local player exposes a queryable status effect state. For the
	 * local player, Poison/Venom have an exact signal instead (VarPlayerID.POISON, the same one
	 * RuneLite's own Poison/Status Bars plugins read), so lastPoisonTick/lastVenomTick are only
	 * actually consulted for NPCs and other players - but Burn/Disease/Corruption have no such
	 * varp for any actor type, and Bleed is local-player-only (doesn't affect NPCs in OSRS), so
	 * those are used more selectively - see activeStatusEffects() for exactly which actor types
	 * consult which maps.
	 */
	private final Map<Actor, Integer> lastPoisonTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastVenomTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastBurnTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastBleedTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastDiseaseTick = new ConcurrentHashMap<>();
	private final Map<Actor, Integer> lastCorruptionTick = new ConcurrentHashMap<>();

	/** Cached compiled filter patterns to avoid regex compilation on every tracking check. */
	private String cachedFilterString = "";
	private List<Pattern> cachedPatterns = new ArrayList<>();

	/**
	 * The actor targeted by the player's most recent actor-targeted menu click, and whether that
	 * click was "Attack" specifically - see isGenuineAttackTarget()'s doc comment for why this
	 * exists. Always just the single most recent click; naturally overwritten (not explicitly
	 * cleared) by the next one, so a later real Attack click on the same actor un-suppresses it.
	 */
	private Actor pendingClickActor;
	private boolean pendingClickIsAttack;

	@Provides
	CustomHpBarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomHpBarConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invokeLater(this::syncNativeBarOverrides);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		trackedActors.clear();
		lastKnownHp.clear();
		preciseNpcHp.clear();
		clientThread.invoke(() -> removeSpriteOverride(NativeHealthBarSprites.ALL));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"customhpbar".equals(event.getGroup()))
		{
			return;
		}

		if ("hideNativeBar".equals(event.getKey()) || "showPrayerBar".equals(event.getKey()))
		{
			clientThread.invokeLater(this::syncNativeBarOverrides);
		}
	}

	/**
	 * Recomputes the full native-sprite-override state from hideNativeBar and showPrayerBar
	 * together, rather than each toggle independently adding/removing just its own sprite set.
	 * NativeHealthBarSprites.ALL already includes PRAYER's sprites (which include SHIELD's), so
	 * an earlier version where each toggle only touched its own array had a real bug: turning
	 * showPrayerBar off while hideNativeBar stayed on removed the Prayer/Shield sprites from the
	 * override map entirely, making the native prayer and shield bars reappear even though
	 * hideNativeBar still wanted every native bar hidden (and the same bug in reverse for
	 * toggling hideNativeBar off while showPrayerBar stayed on). Clearing everything first and
	 * reapplying exactly what both flags currently want avoids that - one source of truth for
	 * the whole override map instead of two toggles independently poking it.
	 */
	private void syncNativeBarOverrides()
	{
		removeSpriteOverride(NativeHealthBarSprites.ALL);
		if (config.hideNativeBar())
		{
			applySpriteOverride(NativeHealthBarSprites.ALL);
		}
		else if (config.showPrayerBar())
		{
			applySpriteOverride(NativeHealthBarSprites.PRAYER);
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

		// Only trackable hitsplats should trigger tracking/caching - a hitsplat existing at all
		// doesn't mean HP changed (e.g. PRAYER_DRAIN fires its own hitsplat-style number when
		// praying at an altar or a prayer draining, with nothing to do with HP).
		if (!isTrackableHitsplat(hitsplat.getHitsplatType()))
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
	 * Records the tick a status-effect hitsplat landed, for any actor - not gated to NPCs or the
	 * local player, since other players' hitsplats are visible too and this is the only signal
	 * available for their status effects at all (no varp is readable for anyone but the local
	 * player). Harmless to record for actors that never end up consulting a given map (e.g.
	 * lastBleedTick for an NPC, since Bleed is local-player-only) - just a few unused entries,
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
		else if (hitsplatType == HitsplatID.DISEASE)
		{
			lastDiseaseTick.put(actor, currentTick);
		}
		else if (hitsplatType == HitsplatID.CORRUPTION)
		{
			lastCorruptionTick.put(actor, currentTick);
		}
	}

	private static boolean isTrackableHitsplat(int hitsplatType)
	{
		return hitsplatType == HitsplatID.HEAL
			|| DAMAGE_HITSPLATS.contains(hitsplatType)
			|| STATUS_ONLY_HITSPLATS.contains(hitsplatType);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Actor actor = event.getMenuEntry().getActor();
		if (actor != null)
		{
			pendingClickActor = actor;
			pendingClickIsAttack = "attack".equalsIgnoreCase(event.getMenuOption());
		}
	}

	/**
	 * Whether actor should be treated as a genuine attack target right now. getCombatLevel() > 0
	 * excludes actors that can never be attacked at all (bankers, Quetzals, quest NPCs, etc.) -
	 * see onInteractingChanged's original reasoning below - but it can't tell an actual Attack
	 * click apart from any other menu option on an actor that *can* also be attacked. Reported
	 * symptom: the target bar appearing from clicking Pickpocket on a Man, which has a positive
	 * combat level (it's a real, if weak, combat target) despite the click having nothing to do
	 * with combat. pendingClickActor/pendingClickIsAttack (set from the actual clicked menu
	 * option in onMenuOptionClicked) catches this: if the most recent actor-targeted click was on
	 * this exact actor and wasn't "Attack", that overrides the combat-level signal. This only
	 * ever suppresses a click-driven false positive - it does nothing when the player never
	 * clicked this actor at all (e.g. being aggroed and auto-retaliating), since pendingClickActor
	 * wouldn't be this actor in that case, so that path still falls through to the combat-level
	 * check unaffected.
	 */
	private boolean isGenuineAttackTarget(Actor actor)
	{
		if (actor.getCombatLevel() <= 0)
		{
			return false;
		}
		return actor != pendingClickActor || pendingClickIsAttack;
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
		// trading, following, pickpocketing, or a transport NPC's own menu option (e.g. clicking
		// "Travel" on a Quetzal) - which fire this exact same event with a non-null target, with
		// nothing in the event itself distinguishing them from an actual attack. isGenuineAttack
		// Target() filters these out (see its doc comment) - both the "never attackable at all"
		// case (Quetzal, bankers) and the "attackable, but this click wasn't Attack" case
		// (Pickpocket on a Man).
		if (source != client.getLocalPlayer() || target == null || !isGenuineAttackTarget(target))
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

		// The local player (and whoever they're fighting) may need to start being tracked
		// without a fresh HitsplatApplied/InteractingChanged event ever firing - e.g. "Show
		// for Self" was just turned on while already mid-fight. Cheap to check every tick
		// since it's a single actor, not a scan of everything nearby.
		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			// Same isGenuineAttackTarget() exclusion as onInteractingChanged (see its doc comment)
			// - this fallback would otherwise keep re-tracking a lingering interacting reference
			// left over from a Quetzal/Pickpocket/dialogue/etc. interaction on every subsequent
			// tick, undoing onInteractingChanged's own suppression of exactly that click.
			Actor interacting = localPlayer.getInteracting();
			if (interacting != null && isGenuineAttackTarget(interacting)
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
			else if (currentTick - lastSeen > persistTicks(actor))
			{
				evict(actor);
			}
		});
	}

	/** NPCs and players persist independently - see targetPersistDuration/playerPersistDuration's config descriptions. */
	private int persistTicks(Actor actor)
	{
		int seconds = actor instanceof NPC ? config.targetPersistDuration() : config.playerPersistDuration();
		return (int) Math.round(seconds * (1000.0 / MS_PER_TICK));
	}

	private void evict(Actor actor)
	{
		trackedActors.remove(actor);
		lastKnownHp.remove(actor);
		lastPoisonTick.remove(actor);
		lastVenomTick.remove(actor);
		lastBurnTick.remove(actor);
		lastBleedTick.remove(actor);
		lastDiseaseTick.remove(actor);
		lastCorruptionTick.remove(actor);
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
	 * Ticks after a Poison/Venom/Bleed hitsplat that its color/icon stays active for bar tinting,
	 * wherever a hitsplat is the only signal available (see lastPoisonTick's doc comment) - i.e.
	 * how long a cured/expired effect can keep showing before this catches up. Went through two
	 * rounds of "still too quick" reports: originally matched PoisonPlugin.POISON_TICK_MILLIS
	 * (18200ms, the known player poison-tick cadence) exactly, tightened to 8 ticks (~4.8s) on an
	 * earlier request, then loosened to 15 ticks (~9s) as a middle ground when 8 proved too
	 * aggressive - still not enough. Settled back on the original cadence-matched value (31
	 * ticks, ~18.6s) rather than guessing at another intermediate number - it's the one value
	 * here actually backed by a confirmed real interval, rather than picked by feel.
	 *
	 * Deliberately not used for Burn (see BURN_STATUS_TICKS) - Burn is a short, instantly-applied
	 * DoT that only lasts a handful of ticks, nothing like Poison/Venom's long between-hit
	 * cadence, so sharing this window meant Burn's indicator stayed lit far longer than the
	 * effect actually did.
	 */
	private static final int STATUS_EFFECT_TICKS = 31;

	/**
	 * Ticks after a Burn hitsplat that its color/icon stays active - much shorter than
	 * STATUS_EFFECT_TICKS since Burn applies instantly and only lasts a handful of ticks, not
	 * Poison/Venom's long between-hit cadence.
	 */
	private static final int BURN_STATUS_TICKS = 8;

	/** VarPlayerID.POISON value at and above which the player is envenomed rather than poisoned - matches PoisonPlugin's own VENOM_THRESHOLD. */
	private static final int VENOM_THRESHOLD = 1_000_000;

	enum StatusEffect
	{
		VENOM, POISON, BURN, BLEED, DISEASE, CORRUPTION
	}

	/**
	 * Every status effect currently active for actor at once (or empty if none apply) - pure
	 * detection, independent of whether the bar-tint or debuff-icon config toggles are on (each
	 * consumer checks its own toggle before calling this, since the two are independently
	 * configurable - see statusEffectColor and CustomHpBarOverlay's showStatusIcons). The single
	 * source of truth both consumers build on, so they can never disagree about what's actually
	 * active. An actor can genuinely have more than one at once (e.g. burned while envenomed),
	 * which the icon row shows side by side - the bar tint can only show one color, so
	 * currentStatusEffect() picks a single winner in venom > poison > burn > bleed > disease >
	 * corruption priority from this set.
	 *
	 * NPCs, the local player, and other players are all handled, but not identically: the local
	 * player has an exact Poison/Venom signal (VarPlayerID.POISON) unavailable for anyone else,
	 * so NPCs and other players both fall back to the same hitsplat heuristic used for Burn/
	 * Disease/Corruption on every actor type. Bleed is local-player-only - it doesn't affect NPCs
	 * in OSRS, and there's no confirmation it can land on another player as visible to this
	 * client either, so it's not guessed at for either.
	 */
	Set<StatusEffect> activeStatusEffects(Actor actor)
	{
		int currentTick = client.getTickCount();
		EnumSet<StatusEffect> active = EnumSet.noneOf(StatusEffect.class);

		if (actor == client.getLocalPlayer())
		{
			// Poison/Venom have an exact signal for the local player - prefer it over the
			// hitsplat heuristic used for every other actor type. Mutually exclusive by
			// construction (the varp can't be both at once), unlike everything else here.
			int poison = client.getVarpValue(VarPlayerID.POISON);
			if (poison >= VENOM_THRESHOLD)
			{
				active.add(StatusEffect.VENOM);
			}
			else if (poison > 0)
			{
				active.add(StatusEffect.POISON);
			}

			addIfActive(active, StatusEffect.BLEED, lastBleedTick.get(actor), currentTick);
		}
		else if (actor instanceof NPC || actor instanceof Player)
		{
			addIfActive(active, StatusEffect.VENOM, lastVenomTick.get(actor), currentTick);
			addIfActive(active, StatusEffect.POISON, lastPoisonTick.get(actor), currentTick);
		}
		else
		{
			return active;
		}

		addIfActive(active, StatusEffect.BURN, lastBurnTick.get(actor), currentTick);
		addIfActive(active, StatusEffect.DISEASE, lastDiseaseTick.get(actor), currentTick);
		addIfActive(active, StatusEffect.CORRUPTION, lastCorruptionTick.get(actor), currentTick);
		return active;
	}

	private static void addIfActive(EnumSet<StatusEffect> active, StatusEffect effect, Integer lastTick, int currentTick)
	{
		int window = effect == StatusEffect.BURN ? BURN_STATUS_TICKS : STATUS_EFFECT_TICKS;
		if (withinStatusWindow(lastTick, currentTick, window))
		{
			active.add(effect);
		}
	}

	private static boolean withinStatusWindow(Integer lastTick, int currentTick, int windowTicks)
	{
		return lastTick != null && currentTick - lastTick <= windowTicks;
	}

	/**
	 * The single highest-priority effect from activeStatusEffects (venom > poison > burn > bleed
	 * > disease > corruption), for the bar tint - which can only show one color, unlike the icon
	 * row. StatusEffect is declared in exactly this priority order, so values() already iterates
	 * it correctly.
	 */
	private StatusEffect currentStatusEffect(Actor actor)
	{
		Set<StatusEffect> active = activeStatusEffects(actor);
		for (StatusEffect effect : StatusEffect.values())
		{
			if (active.contains(effect))
			{
				return effect;
			}
		}
		return null;
	}

	/**
	 * Every status effect color is fixed, sampled directly from the actual hitsplat sprites (see
	 * the color-sampling history in CLAUDE.md) - not configurable, at the user's explicit request
	 * to remove that option for all of them, Disease/Corruption included even though those were
	 * only just added as configurable. Target and Player profiles keep their own separate values
	 * (matching what were previously their separate configurable defaults) rather than being
	 * unified into one shared color.
	 */
	private static final Color TARGET_POISON_COLOR = new Color(0, 176, 0);
	private static final Color TARGET_VENOM_COLOR = new Color(48, 112, 95);
	private static final Color TARGET_BURN_COLOR = new Color(215, 85, 0);
	private static final Color TARGET_DISEASE_COLOR = new Color(207, 149, 9);
	private static final Color TARGET_CORRUPTION_COLOR = new Color(127, 61, 205);
	private static final Color SELF_POISON_COLOR = new Color(0, 145, 0);
	private static final Color SELF_VENOM_COLOR = new Color(48, 112, 95);
	private static final Color SELF_BURN_COLOR = new Color(215, 85, 0);
	private static final Color SELF_BLEED_COLOR = new Color(200, 0, 0);
	private static final Color SELF_DISEASE_COLOR = new Color(207, 149, 9);
	private static final Color SELF_CORRUPTION_COLOR = new Color(127, 61, 205);

	/**
	 * Bar fill color for an actor's current status effect (see currentStatusEffect), or null if
	 * none applies or the relevant Color By Status Effect toggle is off. That toggle only gates
	 * the tint - CustomHpBarOverlay's own showStatusIcons() gates the debuff icon row separately,
	 * since the two were split into independent toggles at the user's request.
	 *
	 * Color profile selection is by actor *type* (any Player vs. NPC), not "is this literally
	 * me" - other players are drawn with the Player Bar's style same as the local player (see
	 * CustomHpBarOverlay.resolveStyle()), so their status colors should come from the same
	 * Player-profile values, not the Target profile's.
	 */
	Color statusEffectColor(Actor actor)
	{
		boolean isPlayer = actor instanceof Player;
		boolean tintEnabled = isPlayer ? config.selfColorByStatusEffect() : config.targetColorByStatusEffect();
		if (!tintEnabled)
		{
			return null;
		}

		StatusEffect effect = currentStatusEffect(actor);
		if (effect == null)
		{
			return null;
		}

		switch (effect)
		{
			case VENOM:
				return isPlayer ? SELF_VENOM_COLOR : TARGET_VENOM_COLOR;
			case POISON:
				return isPlayer ? SELF_POISON_COLOR : TARGET_POISON_COLOR;
			case BURN:
				return isPlayer ? SELF_BURN_COLOR : TARGET_BURN_COLOR;
			case BLEED:
				// Local-player-only (see activeStatusEffects) - Bleed doesn't affect NPCs, and
				// isn't confirmed to work for other players either.
				return SELF_BLEED_COLOR;
			case DISEASE:
				return isPlayer ? SELF_DISEASE_COLOR : TARGET_DISEASE_COLOR;
			case CORRUPTION:
				return isPlayer ? SELF_CORRUPTION_COLOR : TARGET_CORRUPTION_COLOR;
			default:
				return null;
		}
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
			return isTrackedNpc((NPC) actor);
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
		return isTrackedNpc(npc);
	}

	/**
	 * Combat level 0 excludes every non-attackable NPC - bankers, shop owners, fishing spots,
	 * pets (yours or another player's) - without needing to name/ID them individually. This is
	 * the same signal used in onInteractingChanged above to distinguish real combat from
	 * non-combat interactions like a Quetzal's "Travel" option, confirmed against
	 * InteractHighlightPlugin's own source. Gated behind onlyShowCombatNpcNames() (default on)
	 * rather than being unconditional - a blanket hardcoded exclusion turned out to be more
	 * opinionated than wanted, so it's a toggle instead. Checked before the user-configurable
	 * npcFilter blacklist either way.
	 */
	private boolean isTrackedNpc(NPC npc)
	{
		return (!config.onlyShowCombatNpcNames() || npc.getCombatLevel() > 0)
			&& !HIDDEN_MECHANIC_NPC_IDS.contains(npc.getId())
			&& matchesFilter(npc.getName());
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
