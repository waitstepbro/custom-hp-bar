package com.customhpbar;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Renderable;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarClientID;
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
@Slf4j
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

	/** Hitsplats meaning another player hit the NPC - powers greyOutOtherPlayerDamage. Includes BLOCK_OTHER (a 0-damage hit). */
	private static final Set<Integer> OTHER_PLAYER_DAMAGE_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DAMAGE_OTHER, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_OTHER_ORANGE,
		HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_OTHER_POISE,
		HitsplatID.BLOCK_OTHER
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

	/** NPC IDs with no drop table of their own - never greyed out by greyOutOtherPlayerDamage(), since there's no loot to taint. */
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

	/** ToA's five boss encounters' combat-form NPC IDs - the only ToA NPCs allowed to read the native boss HP HUD, see nativeHudHp(). */
	private static final Set<Integer> TOA_BOSS_NPC_IDS = new HashSet<>(Arrays.asList(
		// Akkha
		NpcID.AKKHA_SPAWN, NpcID.AKKHA_MELEE, NpcID.AKKHA_RANGE, NpcID.AKKHA_MAGE,
		NpcID.AKKHA_ENRAGE_SPAWN, NpcID.AKKHA_ENRAGE_INITIAL, NpcID.AKKHA_ENRAGE, NpcID.AKKHA_ENRAGE_DUMMY,
		NpcID.AKKHA_HEADBAR_NPC,
		// Zebak
		NpcID.TOA_ZEBAK, NpcID.TOA_ZEBAK_ENRAGED, NpcID.TOA_ZEBAK_DEAD,
		// Kephri
		NpcID.TOA_KEPHRI_BOSS_SHIELDED, NpcID.TOA_KEPHRI_BOSS_WEAK, NpcID.TOA_KEPHRI_BOSS_ENRAGE, NpcID.TOA_KEPHRI_BOSS_DEAD,
		// Ba-Ba
		NpcID.TOA_BABA, NpcID.TOA_BABA_COFFIN, NpcID.TOA_BABA_DIGGING,
		// Wardens - both forms, all three phases
		NpcID.TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE1_INACTIVE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE1, NpcID.TOA_WARDEN_TUMEKEN_PHASE1,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING
	));

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

	/** Invariant middle fragments of the game's two Ironman loot warnings - wording varies at the edges. Lowercase, matching Text.standardize(). */
	private static final String[] NO_LOOT_MESSAGES = {
		"not receive kill-credit",
		"don't get loot if other players helped you"
	};

	/** Matches the "Delve level: N duration: ..." end-of-fight message (deep delves report the real level in group 2). */
	private static final Pattern DOOM_DELVE_MESSAGE = Pattern.compile(
		"^Delve level: (\\d+)(?:\\+ \\((\\d+)\\))? duration:");

	/** Menu action every attackable NPC carries; its absence is what marks a talk-only NPC. */
	private static final String ATTACK_ACTION = "Attack";

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

	/** isTrackedNpc() result per NPC, cached for one game tick rather than recomputed every frame - see isTrackedNpcCached(). CLAUDE.md. */
	private int trackedNpcCacheTick = Integer.MIN_VALUE;
	private final Map<NPC, Boolean> trackedNpcCache = new ConcurrentHashMap<>();
	private int trackedNpcCacheHits;
	private int trackedNpcCacheMisses;
	private int trackedNpcCacheLastLogTick;

	/** Actor + whether the last actor-targeted menu click was "Attack" - see isGenuineAttackTarget(). */
	private Actor pendingClickActor;
	private boolean pendingClickIsAttack;

	/** Tick the aggression tolerance window expires; aggressionSafeCenters holds the two anchor positions it's relative to. */
	private int aggressionEndTick;
	private final WorldPoint[] aggressionSafeCenters = new WorldPoint[2];

	/** Current Doom of Mokhaiotl delve level, indexes DOOM_DELVE_HP - advanced via onChatMessage/DOOM_DELVE_MESSAGE. */
	private int doomDelveLevel = 1;

	/** Latched once per game tick, exactly as core PrayerPlugin latches prayersActive - never sampled mid-tick. */
	private boolean prayerActive;

	/** Run energy last seen by trackRunEnergyChange(), for detecting the next decrease - see isRunEnergyBarTimedOut(). -1 = never sampled. */
	private int lastRunEnergyValue = -1;

	/** Wall-clock ms run energy last actively decreased (not tick-based - server ticks aren't continuous across instances). See CLAUDE.md. */
	private long lastRunEnergyDrainMs = Long.MIN_VALUE;

	/** Whether a BLEED_END_VARCS entry was still counting down last time it was read - see isSelfBleeding(). */
	private boolean bleedEndVarcCounting;

	/** Tick a varc-timed bleed was last seen to end; bleed hitsplats at or before it belong to that finished bleed. */
	private int bleedEndedTick = Integer.MIN_VALUE;

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
		prayerActive = false;
		bleedEndVarcCounting = false;
		bleedEndedTick = Integer.MIN_VALUE;
		trackedNpcCache.clear();
		trackedNpcCacheTick = Integer.MIN_VALUE;
		trackedNpcCacheHits = 0;
		trackedNpcCacheMisses = 0;
		trackedNpcCacheLastLogTick = 0;
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

		// Before cacheHp() below, so updatePreciseHp()'s clamp is the last word on this tick - see
		// CLAUDE.md ("precise NPC HP oscillates"). Applying the delta after the clamp double-counts
		// the hitsplat whenever getHealthRatio() has already caught up with it.
		if (actor instanceof NPC)
		{
			applyHitsplatDamage((NPC) actor, hitsplat);
		}

		// A landed hitsplat is stronger proof of a valid combat target than isAttackableNpc() -
		// covers no-attack-option NPCs like ToB's Supporting Pillars. See CLAUDE.md.
		boolean trackable = actor instanceof NPC ? isTrackedNpc((NPC) actor) : isTrackedType(actor);
		if (trackable)
		{
			track(actor, client.getTickCount());
		}

		trackStatusEffect(actor, hitsplat.getHitsplatType());

		if (actor instanceof NPC && OTHER_PLAYER_DAMAGE_HITSPLATS.contains(hitsplat.getHitsplatType()))
		{
			otherPlayerDamaged.add((NPC) actor);
		}

		// Hide the bar the instant the killing blow lands, rather than waiting out
		// persistTicks()/the death animation - see isConfirmedDeadNpc().
		if (isConfirmedDeadNpc(actor))
		{
			evict(actor);
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
			track(target, client.getTickCount());
		}

		if (isTrackedType(source))
		{
			track(source, client.getTickCount());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int currentTick = client.getTickCount();

		// The only prayer sample there is, exactly as core PrayerPlugin.onGameTick does it.
		prayerActive = isAnyPrayerActive();

		trackRunEnergyChange();

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
				track(interacting, currentTick);
			}
			if (isTrackedType(localPlayer) && isInCombat(localPlayer) && !trackedActors.containsKey(localPlayer))
			{
				track(localPlayer, currentTick);
			}

			// The other direction: an NPC already attacking the player (aggro, or resuming combat
			// after login/teleport) never fires InteractingChanged for the player's own side.
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npc.getInteracting() == localPlayer
						&& isTrackedType(npc) && !trackedActors.containsKey(npc))
				{
					track(npc, currentTick);
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

			// Safety net for onHitsplatApplied's same check - covers ratio reaching 0 without a
			// hitsplat landing that tick (e.g. a DOT tick already folded into the ratio read).
			if (isConfirmedDeadNpc(actor))
			{
				evict(actor);
				return;
			}

			if (isInCombat(actor))
			{
				track(actor, currentTick);
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

	/** (Re-)marks actor as tracked as of tick and caches its current HP - the pair every discovery/resync path performs together. */
	private void track(Actor actor, int tick)
	{
		trackedActors.put(actor, tick);
		cacheHp(actor);
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

	/**
	 * True once an NPC is confirmed dead (0 HP) - its bar should vanish immediately, not after
	 * persistTicks(), and not after the death animation. Ratio 0 unambiguously means dead in
	 * OSRS's ratio/scale scheme, but the animation keeps reporting it for several more ticks
	 * before the NPC actually despawns - persist duration is for actors that are still alive
	 * but out of combat, not for this. NPC-only: see CLAUDE.md.
	 */
	static boolean isConfirmedDeadNpc(Actor actor)
	{
		return actor instanceof NPC && actor.getHealthRatio() == 0;
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
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		// Checked for both types: only one of the two wordings has had its type confirmed in-game.
		markLootTaintedFromMessage(event.getMessage());

		if (type != ChatMessageType.GAMEMESSAGE)
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

	/** The game's Ironman no-loot chat warning - an extra source into otherPlayerDamaged, not a replacement for the hitsplat heuristic. */
	private void markLootTaintedFromMessage(String message)
	{
		if (message == null || !isNoLootMessage(Text.standardize(message)))
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		Actor target = localPlayer == null ? null : localPlayer.getInteracting();
		if (target instanceof NPC)
		{
			otherPlayerDamaged.add((NPC) target);
		}
	}

	private static boolean isNoLootMessage(String standardized)
	{
		for (String fragment : NO_LOOT_MESSAGES)
		{
			if (standardized.contains(fragment))
			{
				return true;
			}
		}
		return false;
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
		String rawName = nameWidget != null ? nameWidget.getText() : null;
		String name = rawName != null ? Text.removeTags(rawName) : null;
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

		// The HUD follows whatever's currently being fought, not just the boss, so a targeted minion
		// can briefly leak a real number onto its bar - restrict this path to real bosses in ToA.
		if (actor instanceof NPC && isInsideToa() && !TOA_BOSS_NPC_IDS.contains(((NPC) actor).getId()))
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
			int[] previous = lastKnownHp.put(actor, hp);
			if (actor instanceof NPC)
			{
				// An identical ratio/scale is the same server reading re-read, not a new one.
				boolean freshRead = previous == null || previous[0] != hp[0] || previous[1] != hp[1];
				updatePreciseHp((NPC) actor, hp[0], hp[1], freshRead);
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

	/** Local player's region ID, translated through WorldPoint.fromLocalInstance() - raw getWorldLocation() breaks inside raids. See CLAUDE.md. */
	private int localPlayerRegion()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return -1;
		}
		LocalPoint localPoint = localPlayer.getLocalLocation();
		WorldPoint worldPoint = localPoint == null ? null : WorldPoint.fromLocalInstance(client, localPoint);
		return worldPoint == null ? -1 : worldPoint.getRegionID();
	}

	/** Whether the local player is currently inside Tombs of Amascut - see TOA_REGION_IDS. */
	private boolean isInsideToa()
	{
		return TOA_REGION_IDS.contains(localPlayerRegion());
	}

	/** Establishes/clamps the precise HP baseline from a fresh ratio/scale read into core's exact [minHealth, maxHealth] bound. */
	private void updatePreciseHp(NPC npc, int ratio, int scale, boolean freshRead)
	{
		int maxHp = resolveNpcMaxHp(npc.getId());
		if (maxHp <= 0)
		{
			preciseNpcHp.remove(npc);
			return;
		}

		Integer current = preciseNpcHp.get(npc);
		// A repeat of the last reading carries no new information, so it must not overwrite
		// hitsplat deltas accumulated since - those are strictly fresher than it is.
		if (current != null && !freshRead)
		{
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

		if (scale <= 1)
		{
			// Ratio is always 1 while alive at this scale - it bounds nothing, so only seed a
			// baseline if there isn't one yet rather than pretending the ratio constrains anything.
			if (current == null)
			{
				preciseNpcHp.put(npc, maxHp / 2);
			}
			return;
		}

		// Inverse of the server's ratio = 1 + (scale - 1) * hp / maxHp (see OpponentInfoOverlay) -
		// bounds the true HP to an exact interval, clamped into rather than snapped to. See CLAUDE.md.
		int minHealth = ratio == 1 ? 1 : (maxHp * (ratio - 1) + scale - 2) / (scale - 1);
		int maxHealth = Math.min(maxHp, (maxHp * ratio - 1) / (scale - 1));

		if (current == null)
		{
			preciseNpcHp.put(npc, (minHealth + maxHealth) / 2);
		}
		else if (current < minHealth)
		{
			preciseNpcHp.put(npc, minHealth);
		}
		else if (current > maxHealth)
		{
			preciseNpcHp.put(npc, maxHealth);
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

	/** Ticks a Poison/Venom/Disease tint stays active, matching PoisonPlugin's cadence. Not used for Burn/Bleed - they tick far faster. */
	private static final int STATUS_EFFECT_TICKS = 31;

	/** Ticks a Burn hitsplat's tint/icon stays active - short, since Burn applies instantly and fades fast. */
	private static final int BURN_STATUS_TICKS = 8;

	/** Same idea for Bleed, tuned a notch shorter than Burn by in-game testing - 8 still outlasted the real bleed slightly. */
	private static final int BLEED_STATUS_TICKS = 6;

	/** VarPlayerID.POISON value at and above which the player is envenomed rather than poisoned - matches PoisonPlugin's own VENOM_THRESHOLD. */
	private static final int VENOM_THRESHOLD = 1_000_000;

	/** Game cycle each of the player's bleed debuffs ends on - the only signal that reacts to a haemostatic dressing, see isSelfBleeding(). */
	private static final int[] BLEED_END_VARCS = {VarClientID.BUFF_BLEED_END, VarClientID.BUFF_ZEBAK_BLEED_END};

	enum StatusEffect
	{
		VENOM, POISON, BURN, BLEED, DISEASE, CORRUPTION
	}

	/** Every status effect currently active for actor; local player uses the exact Poison/Venom/Disease varps, others use hitsplat timing. */
	Set<StatusEffect> activeStatusEffects(Actor actor)
	{
		int currentTick = client.getTickCount();
		EnumSet<StatusEffect> active = EnumSet.noneOf(StatusEffect.class);
		Map<StatusEffect, Integer> ticks = statusEffectTicks.getOrDefault(actor, Collections.emptyMap());

		if (actor == client.getLocalPlayer())
		{
			// Exact signals for the local player - preferred over the hitsplat heuristic below.
			// Every cure writes these vars, so any of them ending shows up on the same tick.
			int poison = client.getVarpValue(VarPlayerID.POISON);
			if (poison >= VENOM_THRESHOLD)
			{
				active.add(StatusEffect.VENOM);
			}
			else if (poison > 0)
			{
				active.add(StatusEffect.POISON);
			}

			if (client.getVarpValue(VarPlayerID.DISEASE) > 0)
			{
				active.add(StatusEffect.DISEASE);
			}

			if (isSelfBleeding(ticks, currentTick))
			{
				active.add(StatusEffect.BLEED);
			}
		}
		else if (actor instanceof NPC || actor instanceof Player)
		{
			addIfActive(active, StatusEffect.VENOM, ticks.get(StatusEffect.VENOM), currentTick);
			addIfActive(active, StatusEffect.POISON, ticks.get(StatusEffect.POISON), currentTick);
			addIfActive(active, StatusEffect.DISEASE, ticks.get(StatusEffect.DISEASE), currentTick);
		}
		else
		{
			return active;
		}

		addIfActive(active, StatusEffect.BURN, ticks.get(StatusEffect.BURN), currentTick);
		addIfActive(active, StatusEffect.CORRUPTION, ticks.get(StatusEffect.CORRUPTION), currentTick);
		return active;
	}

	/** Whether the local player is bleeding - prefers the bleed-end varc, falls back to the hitsplat window for bleeds with no varc. See CLAUDE.md. */
	private boolean isSelfBleeding(Map<StatusEffect, Integer> ticks, int currentTick)
	{
		int cycle = client.getGameCycle();
		for (int varc : BLEED_END_VARCS)
		{
			if (client.getVarcIntValue(varc) > cycle)
			{
				bleedEndVarcCounting = true;
				return true;
			}
		}

		// Countdown gone since the last read - the bleed it was timing was cured or ran out.
		if (bleedEndVarcCounting)
		{
			bleedEndVarcCounting = false;
			bleedEndedTick = currentTick;
		}

		Integer lastBleedTick = ticks.get(StatusEffect.BLEED);
		return lastBleedTick != null
			&& lastBleedTick > bleedEndedTick
			&& withinStatusWindow(lastBleedTick, currentTick, BLEED_STATUS_TICKS);
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

	/** Reads the once-per-tick latch, mirroring core PrayerPlugin's prayersActive - so the bar can only change on a tick boundary. */
	boolean isPrayerActive()
	{
		return prayerActive;
	}

	/** Special attack energy as a 0-100 percentage - SA_ENERGY counts in tenths of a percent, same read as core's StatusBarsOverlay. */
	int specialAttackEnergy()
	{
		return client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
	}

	/**
	 * Run energy as a 0-100 percentage. Client.getEnergy() is documented as "1/100th of a percent",
	 * so this divides by 100 - the exact read core's StatusBarsOverlay uses for its own run energy bar.
	 */
	int runEnergy()
	{
		return client.getEnergy() / 100;
	}

	/** Whether a Stamina potion's drain-reduction buff is active - same varbit core's StatusBarsOverlay swaps its run bar color on. */
	boolean isStaminaActive()
	{
		return client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
	}

	/** Latches lastRunEnergyValue/lastRunEnergyDrainMs whenever run energy actually decreases - sampled once per tick from onGameTick. */
	private void trackRunEnergyChange()
	{
		int current = runEnergy();
		if (lastRunEnergyValue >= 0 && current < lastRunEnergyValue)
		{
			lastRunEnergyDrainMs = System.currentTimeMillis();
		}
		lastRunEnergyValue = current;
	}

	/** Whether the run bar's timeout has elapsed since energy was last actually draining (decreases only, not regen). See CLAUDE.md. */
	boolean isRunEnergyBarTimedOut()
	{
		int timeoutSeconds = config.runEnergyBarTimeout();
		if (timeoutSeconds <= 0)
		{
			return false;
		}
		if (lastRunEnergyDrainMs == Long.MIN_VALUE)
		{
			// Never actually run this session - nothing to justify showing the bar yet.
			return true;
		}
		long elapsedMs = System.currentTimeMillis() - lastRunEnergyDrainMs;
		return elapsedMs >= timeoutSeconds * 1000L;
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

	/** Whether another player's damage has cost npc its exclusive loot (Ironman-only). Callers apply their own grey-out toggle. */
	boolean isLootTainted(NPC npc)
	{
		return isIronman() && otherPlayerDamaged.contains(npc)
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

		int region = localPlayerRegion();
		if (TOB_REGION_IDS.contains(region) || TOA_REGION_IDS.contains(region))
		{
			return true;
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
			// Tracking exists to drive bars, so it takes the stricter attackable test - talking to a
			// guard makes it interact with you, which would otherwise track it via onGameTick.
			// isConfirmedDeadNpc excluded here too - a corpse mid-death-animation still has an Attack
			// option (hasAttackOption doesn't know it just died), so without this the onGameTick
			// discovery loops below re-track it the moment isConfirmedDeadNpc's evict() removes it,
			// since getInteracting() == localPlayer is still true for several more ticks. See CLAUDE.md.
			NPC npc = (NPC) actor;
			return isTrackedNpc(npc) && isAttackableNpc(npc) && !isConfirmedDeadNpc(npc);
		}
		if (!(actor instanceof Player))
		{
			return false;
		}
		return actor == client.getLocalPlayer() ? config.showForSelf() : config.showForPlayers();
	}

	/** Whether npc is eligible for a bar or a name at all - isAttackableNpc() is the stricter bar-only gate on top of this. Cheap checks first. */
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

	/** Same as isTrackedNpc(), memoized per game tick for the overlay's per-frame "Always Show" pass - see trackedNpcCache. */
	boolean isTrackedNpcCached(NPC npc)
	{
		int tick = client.getTickCount();
		if (tick != trackedNpcCacheTick)
		{
			logTrackedNpcCacheStats(tick);
			trackedNpcCache.clear();
			trackedNpcCacheTick = tick;
		}

		Boolean cached = trackedNpcCache.get(npc);
		if (cached != null)
		{
			trackedNpcCacheHits++;
			return cached;
		}

		trackedNpcCacheMisses++;
		boolean result = isTrackedNpc(npc);
		trackedNpcCache.put(npc, result);
		return result;
	}

	/** Debug-only: reports the cache's hit rate roughly every 30s so the always-show pass's per-frame savings can be checked live. */
	private void logTrackedNpcCacheStats(int tick)
	{
		if (!log.isDebugEnabled() || tick - trackedNpcCacheLastLogTick < 50)
		{
			return;
		}
		trackedNpcCacheLastLogTick = tick;

		int total = trackedNpcCacheHits + trackedNpcCacheMisses;
		int hitRate = total == 0 ? 0 : Math.round(100f * trackedNpcCacheHits / total);
		log.debug("trackedNpcCache: {} hits, {} misses ({}% reuse) since last report",
			trackedNpcCacheHits, trackedNpcCacheMisses, hitRate);
		trackedNpcCacheHits = 0;
		trackedNpcCacheMisses = 0;
	}

	/** Whether npc can have an HP bar - a live health ratio overrides the Attack-option test outright. Combat level deliberately not required. */
	boolean isAttackableNpc(NPC npc)
	{
		return npc.getHealthRatio() != -1 || hasAttackOption(npc);
	}

	/** Whether npc offers an Attack option - same signal core's NpcUtil/IdleNotifier/MenuEntrySwapper use. Unknown composition keeps the bar. */
	private static boolean hasAttackOption(NPC npc)
	{
		NPCComposition composition = npc.getTransformedComposition();
		return composition == null
			|| Arrays.stream(composition.getActions()).anyMatch(ATTACK_ACTION::equalsIgnoreCase);
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
