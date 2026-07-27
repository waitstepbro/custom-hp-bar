package com.customhpbar;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Renderable;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
	name = "Custom HP Bar",
	description = "Draws a custom health bar overlay with HP numbers directly on the bar",
	tags = {"hp", "health", "bar", "overlay", "npc", "combat"}
)
// Lets ItemStatChangesService be @Inject-ed below for the food/prayer restore hover previews.
@PluginDependency(ItemStatPlugin.class)
public class CustomHpBarPlugin extends Plugin
{
	/** OSRS game tick length, for converting the configurable persist duration to ticks. */
	private static final double MS_PER_TICK = 600.0;

	/** Aggression tolerance window in ticks (10 minutes), matching core's NPC Aggression Timer. */
	private static final int AGGRESSION_TICKS = 1000;

	/** Safe-area radius (tiles), matching core's NPC Aggression Timer plugin exactly - see updateAggressionArea(). */
	private static final int AGGRESSION_SAFE_RADIUS = 10;

	/** Hitsplat types that represent real HP damage, for precise HP tracking. */
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

	/** "_OTHER" hitsplats mean damage dealt by someone other than the local player - powers greyOutOtherPlayerDamage. */
	private static final Set<Integer> OTHER_PLAYER_DAMAGE_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DAMAGE_OTHER, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_OTHER_ORANGE,
		HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_OTHER_POISE
	));

	/** Hitsplat type -> the status effect it signals. Also the source of truth for which hitsplats are status-relevant. */
	private static final Map<Integer, StatusEffect> STATUS_HITSPLAT_EFFECTS = buildStatusHitsplatEffects();

	private static Map<Integer, StatusEffect> buildStatusHitsplatEffects()
	{
		Map<Integer, StatusEffect> effects = new HashMap<>();
		effects.put(HitsplatID.VENOM, StatusEffect.VENOM);
		effects.put(HitsplatID.POISON, StatusEffect.POISON);
		effects.put(HitsplatID.BURN, StatusEffect.BURN);
		effects.put(HitsplatID.BLEED, StatusEffect.BLEED);
		effects.put(HitsplatID.DISEASE, StatusEffect.DISEASE);
		effects.put(HitsplatID.CORRUPTION, StatusEffect.CORRUPTION);
		return effects;
	}

	/** NPC IDs for non-real Moons of Peril mechanic entities (scrapped "Enraged" ghosts, the Blue Moon tornado hazard) - untracked entirely. */
	private static final Set<Integer> HIDDEN_MECHANIC_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.PMOON_BLOOD_BOSS_INVIS, NpcID.PMOON_BLUE_BOSS_INVIS, NpcID.PMOON_ECLIPSE_BOSS_INVIS,
		NpcID.PMOON_BOSS_WINTER_STORM
	));

	/** Normalized names for the same "Enraged" entities, matched as a backup to the ID list above. */
	private static final Set<String> HIDDEN_MECHANIC_NPC_NAMES = new HashSet<>(Arrays.asList(
		"enraged blood moon", "enraged blue moon", "enraged eclipse moon"
	));

	/**
	 * NPC IDs with no drop table of their own - tracked and shown normally, but never greyed out by
	 * greyOutOtherPlayerDamage(), since there's no loot for another player's damage to taint.
	 * Blood Moon's "Blood jaguar" is a real summoned minion, not scrapped content; the boss itself
	 * is already exempt via COMMUNAL_LOOT_NAMES, but that's a name match and the jaguar has its own.
	 */
	private static final Set<Integer> LOOTLESS_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.PMOON_BOSS_JAGUAR
	));

	/** Precompiled for normalizeNpcName - it runs for every NPC in the scene every frame, so per-call Pattern.compile is too costly. */
	private static final Pattern NAME_SEPARATORS = Pattern.compile("[:;]");
	private static final Pattern NAME_WHITESPACE = Pattern.compile("\\s+");

	/** Normalizes tags/non-breaking-spaces/colon-semicolon/whitespace (via Text.standardize()) so name comparisons are reliable. */
	private static String normalizeNpcName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String separated = NAME_SEPARATORS.matcher(Text.standardize(name)).replaceAll(" ").trim();
		return NAME_WHITESPACE.matcher(separated).replaceAll(" ");
	}

	/** Doom of Mokhaiotl's three combat-form NPC IDs (no gameval constants exist for these). */
	private static final Set<Integer> DOOM_NPC_IDS = new HashSet<>(Arrays.asList(14707, 14708, 14709));

	/** Vasa's two combat-form IDs; max HP depends on the Challenge Mode varbit, not a static table - see resolveNpcMaxHp(). */
	private static final Set<Integer> VASA_NPC_IDS = new HashSet<>(Arrays.asList(7566, 7567));
	private static final int VASA_NORMAL_HP = 300;
	private static final int VASA_CM_HP = 450;

	/** NPC IDs for encounters where loot is based on personal participation, not "who dealt the kill" - exempt from greyOutOtherPlayerDamage. */
	private static final Set<Integer> COMMUNAL_LOOT_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.HUEY_HEAD, NpcID.HUEY_HEAD_RESPAWN_PLACEHOLDER, NpcID.HUEY_HEAD_INVULNERABLE,
		NpcID.HUEY_HEAD_DEFEATED, NpcID.HUEY_HEAD_ENRAGED,
		NpcID.HUEY_TAIL, NpcID.HUEY_TAIL_BROKEN,
		NpcID.HUEY_BODY_PART, NpcID.HUEY_BODY_PART_BROKEN,
		NpcID.ZALCANO, NpcID.ZALCANO_WEAK
	));

	/** Names for the rest of the OSRS Wiki's Ironman group-loot exemption list not covered by ID above. */
	private static final Set<String> COMMUNAL_LOOT_NAMES = new HashSet<>(Arrays.asList(
		"callisto", "venenatis", "vet'ion",
		"blood moon", "blue moon", "eclipse moon",
		"nex", "the nightmare", "phosani's nightmare",
		"branda the fire queen", "eldric the ice king",
		"yama", "tempoross", "wintertodt"
	));

	/** Region IDs for every room of Theatre of Blood - blanket communal-loot exemption, see isCommunalLootEncounter(). */
	private static final Set<Integer> TOB_REGION_IDS = new HashSet<>(Arrays.asList(
		14642, 12869, 12613, 13125, 13122, 13123, 13379, 12612, 12611, 12867
	));

	/** Region IDs for every room of Tombs of Amascut - same rationale as TOB_REGION_IDS. */
	private static final Set<Integer> TOA_REGION_IDS = new HashSet<>(Arrays.asList(
		14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, 14672
	));

	/** Doom of Mokhaiotl's max HP per delve level (index 0 = level 1); deep delves (9+) use DOOM_DEEP_DELVE_HP instead. */
	private static final int[] DOOM_DELVE_HP = {525, 550, 575, 600, 625, 650, 650, 675};
	private static final int DOOM_DEEP_DELVE_HP = 625;

	/** Matches the "Delve level: N duration: ..." end-of-fight message (deep delves report the real level in group 2). */
	private static final Pattern DOOM_DELVE_MESSAGE = Pattern.compile(
		"^Delve level: (\\d+)(?:\\+ \\((\\d+)\\))? duration:");

	/** How long prayer keeps counting as active after the last "on" sighting - see CLAUDE.md's prayer-flick section. */
	private static final int PRAYER_FLICK_GRACE_TICKS = 1;

	/** Prayer varbit IDs, for spotting prayer toggles among all other VarbitChanged traffic. */
	private static final Set<Integer> PRAYER_VARBITS = buildPrayerVarbits();

	private static Set<Integer> buildPrayerVarbits()
	{
		Set<Integer> varbits = new HashSet<>();
		for (Prayer prayer : Prayer.values())
		{
			varbits.add(prayer.getVarbit());
		}
		return varbits;
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private CustomHpBarOverlay overlay;

	@Inject
	private CustomHpBarConfig config;

	/** Cached "replaceOverheadIcon && showForSelf" - volatile since onConfigChanged isn't guaranteed on the client thread. */
	private volatile boolean suppressSelfOverheads;

	/** Suppresses the client's native overhead UI (health bar, prayer icon, hitsplats, chat text) for the local player only. */
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			return !(ui && suppressSelfOverheads && renderable == client.getLocalPlayer());
		}
	};

	/** Hitsplats on the local player, redrawn since renderCallback suppresses the native ones. */
	@Getter
	private final List<Hitsplat> selfHitsplats = new CopyOnWriteArrayList<>();

	/** Actors whose bars are active; value = tick of last valid health-ratio read. */
	@Getter
	private final Map<Actor, Integer> trackedActors = new ConcurrentHashMap<>();

	/** Most-recent [current, max] HP per actor, used while a bar persists past the last live read. */
	@Getter
	private final Map<Actor, int[]> lastKnownHp = new ConcurrentHashMap<>();

	/** Precise current HP per NPC, kept in sync between coarse ratio/scale buckets via hitsplat deltas - see updatePreciseHp(). */
	@Getter
	private final Map<NPC, Integer> preciseNpcHp = new ConcurrentHashMap<>();

	/** NPCs damaged by someone other than the local player since last evicted - powers greyOutOtherPlayerDamage. */
	private final Set<NPC> otherPlayerDamaged = ConcurrentHashMap.newKeySet();

	/** Tick of the most recent status-effect hitsplat, per effect per actor - the only signal for NPCs/other players (see activeStatusEffects()). */
	private final Map<Actor, Map<StatusEffect, Integer>> statusEffectTicks = new ConcurrentHashMap<>();

	/** Cached compiled filter patterns to avoid regex compilation on every tracking check. */
	private String cachedFilterString = "";
	private List<Pattern> cachedPatterns = new ArrayList<>();

	/** Actor + whether the last actor-targeted menu click was "Attack" - see isGenuineAttackTarget(). */
	private Actor pendingClickActor;
	private boolean pendingClickIsAttack;

	/** Tick the aggression tolerance window expires; aggressionSafeCenters holds the two anchor positions it's relative to. */
	private int aggressionEndTick;
	private final WorldPoint[] aggressionSafeCenters = new WorldPoint[2];

	/** Current Doom of Mokhaiotl delve level, indexes DOOM_DELVE_HP - advanced via onChatMessage/DOOM_DELVE_MESSAGE. */
	private int doomDelveLevel = 1;

	/** Tick a prayer was last seen active, counting mid-tick flicks caught by onVarbitChanged. */
	private int lastPrayerActiveTick = Integer.MIN_VALUE;

	/** Live HP/boss name from the game's own native boss HP HUD - preferred over every other HP source, see nativeHudHp(). */
	private String nativeHudBossName;
	private int nativeHudCurrentHp;
	private int nativeHudMaxHp;

	@Provides
	CustomHpBarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomHpBarConfig.class);
	}

	@Override
	protected void startUp()
	{
		suppressSelfOverheads = config.replaceOverheadIcon() && config.showForSelf();
		overlayManager.add(overlay);
		renderCallbackManager.register(renderCallback);
		clientThread.invokeLater(this::syncNativeBarOverrides);
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(renderCallback);
		overlayManager.remove(overlay);
		trackedActors.clear();
		lastKnownHp.clear();
		preciseNpcHp.clear();
		otherPlayerDamaged.clear();
		statusEffectTicks.clear();
		selfHitsplats.clear();
		pendingClickActor = null;
		aggressionEndTick = 0;
		Arrays.fill(aggressionSafeCenters, null);
		doomDelveLevel = 1;
		nativeHudBossName = null;
		lastPrayerActiveTick = Integer.MIN_VALUE;
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

		if ("replaceOverheadIcon".equals(event.getKey()) || "showForSelf".equals(event.getKey()))
		{
			suppressSelfOverheads = config.replaceOverheadIcon() && config.showForSelf();
		}
	}

	/** Recomputes native-sprite overrides from hideNativeBar + showPrayerBar together, rather than each toggle its own set. */
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

	/** Overrides every sprite ID with a transparent pixel - client-wide, not per-actor. */
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

		// Captured regardless of hitsplat type (unlike HP tracking below) - a redrawn hitsplat
		// should show for anything the native client would show one for, e.g. PRAYER_DRAIN.
		if (actor == client.getLocalPlayer())
		{
			selfHitsplats.add(hitsplat);
		}

		// A hitsplat existing at all doesn't mean HP changed (e.g. PRAYER_DRAIN), so only
		// trackable types drive HP tracking/caching below.
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
			if (OTHER_PLAYER_DAMAGE_HITSPLATS.contains(hitsplat.getHitsplatType()))
			{
				otherPlayerDamaged.add((NPC) actor);
			}
		}
	}

	/**
	 * Records the tick a status-effect hitsplat landed, for any actor - other players' status
	 * effects have no varp to read, so this hitsplat signal is all that's available for them too.
	 */
	private void trackStatusEffect(Actor actor, int hitsplatType)
	{
		StatusEffect effect = STATUS_HITSPLAT_EFFECTS.get(hitsplatType);
		if (effect == null)
		{
			return;
		}

		// Bleed only affects players in OSRS, and activeStatusEffects() only ever reads it for the
		// local player - so recording it for anyone else just accumulates state nothing consumes.
		if (effect == StatusEffect.BLEED && actor != client.getLocalPlayer())
		{
			return;
		}

		statusEffectTicks.computeIfAbsent(actor, k -> new EnumMap<>(StatusEffect.class))
			.put(effect, client.getTickCount());
	}

	private static boolean isTrackableHitsplat(int hitsplatType)
	{
		return hitsplatType == HitsplatID.HEAL
			|| DAMAGE_HITSPLATS.contains(hitsplatType)
			|| STATUS_HITSPLAT_EFFECTS.containsKey(hitsplatType);
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

	/** Whether actor is a genuine attack target - combat level plus the last Attack-click override, see pendingClickActor. */
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

		// target == null when the interacting reference is just cleared (e.g. zone transitions).
		if (source != client.getLocalPlayer() || target == null || !isGenuineAttackTarget(target))
		{
			return;
		}

		// Track whatever the player is attacking, and the player themselves, so "Show for Self"
		// reflects entering combat immediately rather than waiting for the first hitsplat.
		if (isTrackedType(target))
		{
			trackedActors.put(target, client.getTickCount());
			cacheHp(target);
		}

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

		// Tick-boundary sample, mirroring core's Prayer plugin; onVarbitChanged covers the rest.
		if (isAnyPrayerActive())
		{
			lastPrayerActiveTick = currentTick;
		}

		// The overlay's render-time check against getDisappearsOnGameCycle() is what actually
		// controls when a hitsplat stops drawing; this just bounds the list's size between prunes.
		int currentCycle = client.getGameCycle();
		selfHitsplats.removeIf(h -> currentCycle >= h.getDisappearsOnGameCycle());

		updateAggressionArea(currentTick);

		// onScriptPostFired only fires while the native HUD is actively updating, so this clears
		// a stale boss name once its widget is hidden/absent - otherwise it would linger forever.
		if (nativeHudBossName != null)
		{
			Widget hudWidget = client.getWidget(InterfaceID.HpbarHud.HP);
			if (hudWidget == null || hudWidget.isHidden())
			{
				nativeHudBossName = null;
			}
		}

		// Covers starting/toggling tracking mid-fight, without waiting for a fresh
		// HitsplatApplied/InteractingChanged event.
		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			// Same isGenuineAttackTarget() exclusion as onInteractingChanged, so this doesn't
			// keep re-tracking a lingering non-combat interacting reference every tick.
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

			// The other direction: an NPC already attacking the player (aggro, or resuming combat
			// after login/teleport) never fires InteractingChanged for the player's own side.
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npc.getInteracting() == localPlayer
						&& isTrackedType(npc) && !trackedActors.containsKey(npc))
				{
					trackedActors.put(npc, currentTick);
					cacheHp(npc);
				}
			}
		}

		// ConcurrentHashMap.forEach allows safe reads and puts/removes during iteration.
		trackedActors.forEach((actor, lastSeen) ->
		{
			// A config toggle may have turned this actor's bar off since it was tracked - evict
			// immediately rather than waiting out the persist timer.
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
				stopTracking(actor);
			}
		});
	}

	/** Ends active tracking on persist-timeout without clearing lastKnownHp/preciseNpcHp - still needed by "Always Show NPC Bar". */
	private void stopTracking(Actor actor)
	{
		trackedActors.remove(actor);
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
		statusEffectTicks.remove(actor);
		if (actor instanceof NPC)
		{
			preciseNpcHp.remove(actor);
			otherPlayerDamaged.remove(actor);
		}
	}

	/** True while actor's native health bar is actively refreshing - governs the persist-duration eviction clock. */
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

	/** Advances doomDelveLevel from the "Delve level: N duration:" end-of-fight message. */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		Matcher matcher = DOOM_DELVE_MESSAGE.matcher(event.getMessage());
		if (!matcher.find())
		{
			return;
		}

		int completedLevel = matcher.group(2) != null
			? Integer.parseInt(matcher.group(2))
			: Integer.parseInt(matcher.group(1));
		doomDelveLevel = completedLevel + 1;
	}

	/** Refreshes nativeHudBossName/Current/MaxHp from the native boss HP HUD widget - fires on ScriptID.HP_HUD_UPDATE. */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.HP_HUD_UPDATE)
		{
			return;
		}

		int maxHp = client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP);
		if (maxHp <= 0)
		{
			nativeHudBossName = null;
			return;
		}

		Widget nameWidget = client.getWidget(InterfaceID.HpbarHud.CREATURE_NAME);
		String name = nameWidget != null ? Text.removeTags(nameWidget.getText()) : null;
		if (name == null || name.isEmpty())
		{
			nativeHudBossName = null;
			return;
		}

		nativeHudBossName = name;
		nativeHudCurrentHp = client.getVarbitValue(VarbitID.HPBAR_HUD_HP);
		nativeHudMaxHp = maxHp;
	}

	/** [currentHp, maxHp] from the native boss HP HUD if it's showing this actor (matched by name), else null. */
	int[] nativeHudHp(Actor actor)
	{
		if (nativeHudBossName == null)
		{
			return null;
		}

		String actorName = actor.getName();
		if (actorName == null || !nativeHudBossName.equalsIgnoreCase(Text.removeTags(actorName)))
		{
			return null;
		}

		return new int[]{nativeHudCurrentHp, nativeHudMaxHp};
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

	/** Single chokepoint for an NPC's max HP - Doom/Vasa special-cased first; ToA minions return -1 (see CLAUDE.md). */
	int resolveNpcMaxHp(int npcId)
	{
		if (DOOM_NPC_IDS.contains(npcId))
		{
			return doomDelveLevel <= DOOM_DELVE_HP.length ? DOOM_DELVE_HP[doomDelveLevel - 1] : DOOM_DEEP_DELVE_HP;
		}
		if (VASA_NPC_IDS.contains(npcId))
		{
			return client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1 ? VASA_CM_HP : VASA_NORMAL_HP;
		}
		if (isInsideToa())
		{
			return -1;
		}
		return NpcMaxHpTable.getMaxHp(npcId);
	}

	/** Whether the local player is currently inside Tombs of Amascut - see TOA_REGION_IDS. */
	private boolean isInsideToa()
	{
		Player localPlayer = client.getLocalPlayer();
		return localPlayer != null && TOA_REGION_IDS.contains(localPlayer.getWorldLocation().getRegionID());
	}

	/** Establishes/sanity-checks the precise HP baseline from a fresh ratio/scale read - only overwrites if it's drifted out of bucket range. */
	private void updatePreciseHp(NPC npc, int ratio, int scale)
	{
		int maxHp = resolveNpcMaxHp(npc.getId());
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
	 * Adjusts an NPC's precise HP estimate by a hitsplat's damage/heal amount. No-ops if there's
	 * no baseline yet (set by updatePreciseHp on the next ratio read).
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

		int maxHp = resolveNpcMaxHp(npc.getId());
		int updated = current + delta;
		updated = Math.max(0, maxHp > 0 ? Math.min(updated, maxHp) : updated);
		preciseNpcHp.put(npc, updated);
	}

	/** Ticks a Poison/Venom/Bleed tint stays active, matching PoisonPlugin's cadence. Not used for Burn - see BURN_STATUS_TICKS. */
	private static final int STATUS_EFFECT_TICKS = 31;

	/** Ticks a Burn hitsplat's tint/icon stays active - short, since Burn applies instantly and fades fast. */
	private static final int BURN_STATUS_TICKS = 8;

	/** VarPlayerID.POISON value at and above which the player is envenomed rather than poisoned - matches PoisonPlugin's own VENOM_THRESHOLD. */
	private static final int VENOM_THRESHOLD = 1_000_000;

	enum StatusEffect
	{
		VENOM, POISON, BURN, BLEED, DISEASE, CORRUPTION
	}

	/** Every status effect currently active for actor; local player uses the exact Poison/Venom varp, others use hitsplat timing. */
	Set<StatusEffect> activeStatusEffects(Actor actor)
	{
		int currentTick = client.getTickCount();
		EnumSet<StatusEffect> active = EnumSet.noneOf(StatusEffect.class);
		Map<StatusEffect, Integer> ticks = statusEffectTicks.getOrDefault(actor, Collections.emptyMap());

		if (actor == client.getLocalPlayer())
		{
			// Exact signal for the local player - preferred over the hitsplat heuristic below.
			int poison = client.getVarpValue(VarPlayerID.POISON);
			if (poison >= VENOM_THRESHOLD)
			{
				active.add(StatusEffect.VENOM);
			}
			else if (poison > 0)
			{
				active.add(StatusEffect.POISON);
			}

			addIfActive(active, StatusEffect.BLEED, ticks.get(StatusEffect.BLEED), currentTick);
		}
		else if (actor instanceof NPC || actor instanceof Player)
		{
			addIfActive(active, StatusEffect.VENOM, ticks.get(StatusEffect.VENOM), currentTick);
			addIfActive(active, StatusEffect.POISON, ticks.get(StatusEffect.POISON), currentTick);
		}
		else
		{
			return active;
		}

		addIfActive(active, StatusEffect.BURN, ticks.get(StatusEffect.BURN), currentTick);
		addIfActive(active, StatusEffect.DISEASE, ticks.get(StatusEffect.DISEASE), currentTick);
		addIfActive(active, StatusEffect.CORRUPTION, ticks.get(StatusEffect.CORRUPTION), currentTick);
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

	/** Highest-priority active effect for the bar tint - StatusEffect's declaration order is the priority order. */
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
	 * Status effect colors are fixed, sampled directly from the actual hitsplat sprites - not
	 * configurable. Target and Player profiles keep separate values rather than sharing one.
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

	/** Bar fill color for actor's current status effect, or null if none/disabled. Color profile follows actor type, not "is local player". */
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
				// Local-player-only - see activeStatusEffects.
				return SELF_BLEED_COLOR;
			case DISEASE:
				return isPlayer ? SELF_DISEASE_COLOR : TARGET_DISEASE_COLOR;
			case CORRUPTION:
				return isPlayer ? SELF_CORRUPTION_COLOR : TARGET_CORRUPTION_COLOR;
			default:
				return null;
		}
	}

	/** Live [current, max] HP for actor; local player reads the Hitpoints skill directly, not the combat-only ratio/scale. */
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

	/** Catches a prayer switching on between ticks, so a flick's off-half can't be all onGameTick ever samples. */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getValue() != 0 && PRAYER_VARBITS.contains(event.getVarbitId()))
		{
			lastPrayerActiveTick = client.getTickCount();
		}
	}

	/**
	 * Whether the Prayer bar should treat prayer as on - a prayer seen active within the last
	 * PRAYER_FLICK_GRACE_TICKS, so flicking holds the bar up. Long arithmetic so the
	 * never-seen sentinel and a tick counter reset both read as "not active".
	 */
	boolean isPrayerActive()
	{
		long elapsed = (long) client.getTickCount() - lastPrayerActiveTick;
		return elapsed >= 0 && elapsed <= PRAYER_FLICK_GRACE_TICKS;
	}

	/** Raw "is any prayer on right now" sample; isPrayerActive() is what drives the bar. */
	private boolean isAnyPrayerActive()
	{
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				return true;
			}
		}
		return false;
	}

	/** Advances the aggression tolerance window, porting core's real two-safe-tile mechanic rather than an "is a monster nearby" heuristic. */
	private void updateAggressionArea(int currentTick)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		WorldPoint location = localPlayer.getWorldLocation();
		if (aggressionSafeCenters[1] == null
			|| Arrays.stream(aggressionSafeCenters).noneMatch(
				center -> center != null && center.distanceTo2D(location) <= AGGRESSION_SAFE_RADIUS))
		{
			aggressionSafeCenters[0] = aggressionSafeCenters[1];
			aggressionSafeCenters[1] = location;
			aggressionEndTick = currentTick + AGGRESSION_TICKS;
		}
	}

	/** Whether npc would attack the local player if still aggressive (type + the OSRS 2x-combat-level rule). Doesn't check the tolerance window. */
	private boolean wouldBeAggressive(Player localPlayer, NPC npc)
	{
		int npcLevel = npc.getCombatLevel();
		return npcLevel > 0
			&& localPlayer.getCombatLevel() <= 2 * npcLevel
			&& AggressiveNpcTable.isAggressive(npc.getId());
	}

	/** Whether npc is currently aggressive: would attack, and the tolerance window hasn't expired near its own location. */
	boolean isNpcAggressive(NPC npc)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || !wouldBeAggressive(localPlayer, npc))
		{
			return false;
		}
		if (client.getTickCount() < aggressionEndTick)
		{
			return true;
		}

		WorldPoint npcLocation = npc.getWorldLocation();
		return Arrays.stream(aggressionSafeCenters)
			.noneMatch(center -> center != null && npcLocation.distanceTo2D(center) <= AGGRESSION_SAFE_RADIUS);
	}

	/** True while the local player is any Ironman variant - preferred over the deprecated Client.getAccountType(). */
	private boolean isIronman()
	{
		return client.getVarbitValue(VarbitID.IRONMAN) > 0;
	}

	/** Whether npc's bar should grey out because someone else damaged it (Ironman-only, gated by the config toggle). */
	boolean isLootTainted(NPC npc)
	{
		return config.greyOutOtherPlayerDamage() && isIronman() && otherPlayerDamaged.contains(npc)
			&& !LOOTLESS_NPC_IDS.contains(npc.getId())
			&& !isCommunalLootEncounter(npc);
	}

	/** Whether npc belongs to a confirmed Ironman group-loot exemption (CoX/ToB/ToA by location, or COMMUNAL_LOOT_NPC_IDS/NAMES). */
	private boolean isCommunalLootEncounter(NPC npc)
	{
		if (COMMUNAL_LOOT_NPC_IDS.contains(npc.getId()))
		{
			return true;
		}
		if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
		{
			return true;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			int region = localPlayer.getWorldLocation().getRegionID();
			if (TOB_REGION_IDS.contains(region) || TOA_REGION_IDS.contains(region))
			{
				return true;
			}
		}

		// normalizeNpcName, not removeTags+toLowerCase: NPC names commonly carry U+00A0 non-breaking
		// spaces, which the latter leaves intact - so "Blood<nbsp>Moon" silently missed this set and
		// greyed the boss out anyway. Same root cause b5c04b9 fixed for HIDDEN_MECHANIC_NPC_NAMES.
		String normalized = normalizeNpcName(npc.getName());
		return normalized != null && COMMUNAL_LOOT_NAMES.contains(normalized);
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
	 * Whether npc is eligible for a bar/name at all, independent of current tracked state - also what
	 * the overlay's "Always Show NPC Bar/Name" pass filters on, so tracking and names stay consistent.
	 * Combat level 0 excludes non-attackable NPCs (bankers, fishing spots, pets), gated behind
	 * onlyShowCombatNpcNames(). Cheap ID/level checks run first - the name checks below are the
	 * expensive part and this runs for every NPC in the scene every frame.
	 */
	boolean isTrackedNpc(NPC npc)
	{
		if (config.onlyShowCombatNpcNames() && npc.getCombatLevel() <= 0)
		{
			return false;
		}

		int npcId = npc.getId();
		if (HIDDEN_MECHANIC_NPC_IDS.contains(npcId))
		{
			return false;
		}

		String name = npc.getName();
		String normalizedName = normalizeNpcName(name);
		return (normalizedName == null || !HIDDEN_MECHANIC_NPC_NAMES.contains(normalizedName))
			&& matchesFilter(name);
	}

	/** Pure blacklist: empty filter shows all, any matching entry hides that NPC. Patterns are comma-separated, case-insensitive, '*' wildcards. */
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
