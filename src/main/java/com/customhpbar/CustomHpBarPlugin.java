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
import net.runelite.api.Varbits;
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
import java.util.EnumSet;
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
// Chains this plugin's injector as a child of Item Stats', so ItemStatChangesService can be
// @Inject-ed below for the food/prayer restore hover previews (see CustomHpBarOverlay). Item
// Stats is a core, always-loaded plugin, so this is a hard, unconditional dependency.
@PluginDependency(ItemStatPlugin.class)
public class CustomHpBarPlugin extends Plugin
{
	/** OSRS game tick length, for converting the configurable persist duration to ticks. */
	private static final double MS_PER_TICK = 600.0;

	/**
	 * Aggression tolerance window in ticks (1000 = 10 minutes, matching the core NPC
	 * Aggressiveness Timer plugin's AGGRESSIVE_TIME_DURATION). Resets whenever the player moves
	 * away from both remembered AGGRESSION_SAFE_RADIUS tiles - see updateAggressionArea().
	 */
	private static final int AGGRESSION_TICKS = 1000;

	/**
	 * Radius (tiles) around each of the two remembered "safe center" points within which the
	 * tolerance window keeps counting down instead of resetting - matches the core NPC Aggression
	 * Timer plugin's own SAFE_AREA_RADIUS exactly (confirmed by decompiling it): "The game
	 * remembers 2 tiles. When the player goes >10 steps away from both tiles, the oldest one is
	 * moved to under the player and the NPC aggression timer resets." This is a real, per-location
	 * mechanic - tied to the player's own movement pattern, not to whether an aggressive monster
	 * happens to be nearby (see updateAggressionArea()'s doc comment for why an earlier version of
	 * this plugin got that distinction wrong).
	 */
	private static final int AGGRESSION_SAFE_RADIUS = 10;

	/**
	 * Hitsplat types that represent real HP damage, for precise HP tracking. Deliberately
	 * conservative - excludes types whose target resource isn't confirmed to be HP (PRAYER_DRAIN,
	 * SANITY_DRAIN/RESTORE, CYAN_UP/DOWN, DOOM, DISEASE, CORRUPTION). Missing a real damage type
	 * here just costs a recalibration snap on the next ratio update (see updatePreciseHp), so
	 * conservative is the safe direction to be wrong in.
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
	 * The "_OTHER" damage hitsplats specifically mean "dealt by someone other than the local
	 * player," not "landed on someone other than me" - confirmed via core ZalcanoPlugin, which
	 * accumulates its per-player damage counter from only the _ME variants on a shared NPC. Powers
	 * greyOutOtherPlayerDamage (see otherPlayerDamaged). Can't distinguish another player's own
	 * hit from their thrall's - both read as _OTHER - but either still answers the loot-eligibility
	 * question. DoT hitsplats (poison/venom/burn/bleed) have no _ME/_OTHER split, so damage from
	 * those can't be attributed this way.
	 */
	private static final Set<Integer> OTHER_PLAYER_DAMAGE_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DAMAGE_OTHER, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_OTHER_ORANGE,
		HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_OTHER_POISE
	));

	/**
	 * Status-relevant hitsplats that aren't HP damage, kept separate from DAMAGE_HITSPLATS so
	 * applyHitsplatDamage() doesn't treat them as such. DISEASE_BLOCKED is excluded - it means a
	 * disease application was prevented, not applied.
	 */
	private static final Set<Integer> STATUS_ONLY_HITSPLATS = new HashSet<>(Arrays.asList(
		HitsplatID.DISEASE, HitsplatID.CORRUPTION
	));

	/**
	 * NPC IDs for deliberately invisible/non-interactive per-boss mechanic entities - excluded
	 * from tracking entirely (no bar, no name). RuneLite's own PMOON_*_BOSS_INVIS constants
	 * (Moons of Peril) have misleading "Enraged Blood/Blue/Eclipse Moon" doc comments, but the
	 * _INVIS suffix and the OSRS Wiki (no enraged phase documented for any of the three) confirm
	 * this isn't real boss content. The in-game name isn't consistent enough to filter by string
	 * alone ("Enraged Blue Moon" with and without a colon have both been seen), so this keys on
	 * ID instead.
	 *
	 * PMOON_BOSS_WINTER_STORM is the Blue Moon room's "Tornado" hazard (NPC ID 13027, confirmed
	 * via the OSRS Wiki's dedicated "Tornado (Blue Moon)" page, which explicitly documents it as
	 * "a non-interactive NPC" players can't attack) - reported showing a % HP bar the same way
	 * the Enraged Moon ghosts did, so it belongs in the same bucket: a real NPC entity that isn't
	 * legitimate trackable combat content.
	 */
	private static final Set<Integer> HIDDEN_MECHANIC_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.PMOON_BLOOD_BOSS_INVIS, NpcID.PMOON_BLUE_BOSS_INVIS, NpcID.PMOON_ECLIPSE_BOSS_INVIS,
		NpcID.PMOON_BOSS_WINTER_STORM
	));

	/**
	 * Exact display names for the same three "Enraged Blood/Blue/Eclipse Moon" mechanic entities
	 * HIDDEN_MECHANIC_NPC_IDS already excludes by ID - matched here too as defense-in-depth, in
	 * case the entity is ever reached through an ID this table doesn't have (e.g. an in-place
	 * composition change during the boss's death sequence, the same mechanism other bosses use to
	 * swap forms without a real despawn/respawn). Deliberately three exact strings rather than a
	 * generic "contains Enraged" check - Enraged Boar and Enraged barbarian spirit are real,
	 * legitimately-trackable OSRS monsters and must not be caught by this.
	 */
	private static final Set<String> HIDDEN_MECHANIC_NPC_NAMES = new HashSet<>(Arrays.asList(
		"enraged blood moon", "enraged blue moon", "enraged eclipse moon"
	));

	/**
	 * Doom of Mokhaiotl's three combat-form NPC IDs (standard/shielded/burrowed). No gameval
	 * NpcID constants exist for these, so these are the raw IDs from the OSRS Wiki's
	 * infobox_monster data.
	 */
	private static final Set<Integer> DOOM_NPC_IDS = new HashSet<>(Arrays.asList(14707, 14708, 14709));

	/**
	 * Vasa Nistirio/Vasa Crystalline's two combat-form NPC IDs. Unlike Tekton, Vasa doesn't get
	 * separate Challenge Mode IDs - confirmed by fetching the OSRS Wiki's raw infobox template
	 * source directly (oldschool.runescape.wiki/w/Vasa_Nistirio), which declares "hitpoints1 =
	 * 300" / "hitpoints2 = 450" for its Normal/Challenge Mode versions but only a single, shared
	 * "id = 7566,7567" field - not a per-version id1/id2 split the way Tekton's page has. A static
	 * per-ID table entry (npc_hp.csv previously mapped both to 450) therefore can't be correct for
	 * both modes at once; see VASA_NORMAL_HP/VASA_CM_HP and resolveNpcMaxHp() for the live-varbit
	 * override this needs instead, the same pattern DOOM_NPC_IDS already uses.
	 */
	private static final Set<Integer> VASA_NPC_IDS = new HashSet<>(Arrays.asList(7566, 7567));
	private static final int VASA_NORMAL_HP = 300;
	private static final int VASA_CM_HP = 450;

	/**
	 * NPC IDs for encounters where loot is based on the local player's own damage/participation
	 * meeting a threshold, not "who dealt the most/last damage" - so greyOutOtherPlayerDamage
	 * would be a false positive here, since other players damaging the same NPC is the normal,
	 * expected way to fight it, not a sign the kill isn't "yours." Checked in isCommunalLootEncounter().
	 *
	 * Hueycoatl (up to 20 players, loot share and per-player unique rolls both based on the
	 * player's own damage against a per-phase threshold, confirmed via oldschool.runescape.wiki/
	 * w/The_Hueycoatl) and Zalcano (shared per-world, personal points determine your own loot
	 * roll, confirmed via the decompiled core ZalcanoPlugin referenced elsewhere in this file) are
	 * ID-matched here since both have a small, fully-enumerable set of combat-form NPC IDs.
	 * Everything else in the Wiki's confirmed exemption list (see COMMUNAL_LOOT_NAMES/
	 * isCommunalLootEncounter()) is matched by name instead - raid bosses in particular have
	 * dozens of per-phase/per-difficulty NPC ID variants each (e.g. Theatre of Blood's Xarpus
	 * alone has 12 across normal/story/hard mode), making an ID list impractical to keep complete,
	 * whereas the same boss's in-game name stays constant across every phase/difficulty variant.
	 */
	private static final Set<Integer> COMMUNAL_LOOT_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.HUEY_HEAD, NpcID.HUEY_HEAD_RESPAWN_PLACEHOLDER, NpcID.HUEY_HEAD_INVULNERABLE,
		NpcID.HUEY_HEAD_DEFEATED, NpcID.HUEY_HEAD_ENRAGED,
		NpcID.HUEY_TAIL, NpcID.HUEY_TAIL_BROKEN,
		NpcID.HUEY_BODY_PART, NpcID.HUEY_BODY_PART_BROKEN,
		NpcID.ZALCANO, NpcID.ZALCANO_WEAK
	));

	/**
	 * Lowercased boss names for the rest of the OSRS Wiki's Ironman group-loot exemption list
	 * (oldschool.runescape.wiki/w/Ironman_Mode - "Iron accounts receive group-boss rewards when
	 * fighting with others, with the following exceptions to the standard loot rules") not
	 * already covered by COMMUNAL_LOOT_NPC_IDS or the region-based raid checks in
	 * isCommunalLootEncounter(). All names confirmed against their own OSRS Wiki pages.
	 *
	 * - Wilderness multi-combat bosses: Callisto, Venenatis, Vet'ion - deliberately just these
	 *   three, not their solo counterparts (Artio, Spindel, Calvar'ion), which aren't in the
	 *   Wiki's exemption list and have different names, so they're naturally excluded here.
	 * - Moons of Peril: Blood Moon, Blue Moon, Eclipse Moon.
	 * - Nex, The Nightmare (and Phosani's Nightmare), Royal Titans (Branda the Fire Queen, Eldric
	 *   the Ice King), Yama, Tempoross, Wintertodt.
	 */
	private static final Set<String> COMMUNAL_LOOT_NAMES = new HashSet<>(Arrays.asList(
		"callisto", "venenatis", "vet'ion",
		"blood moon", "blue moon", "eclipse moon",
		"nex", "the nightmare", "phosani's nightmare",
		"branda the fire queen", "eldric the ice king",
		"yama", "tempoross", "wintertodt"
	));

	/**
	 * Region IDs for every room of Theatre of Blood, matching CoX's blanket-by-instance approach
	 * (see isCommunalLootEncounter()) rather than a per-boss name list - unlike Tombs of Amascut's
	 * four interchangeable-order bosses, ToB's fixed room sequence means "which region" already
	 * tells you which fight, so a name list would just be a redundant second way of expressing the
	 * same thing. No client-exposed varbit equivalent to Chambers of Xeric's RAIDS_CLIENT_INDUNGEON
	 * was found for ToB, so this uses region IDs instead - sourced from blert-io/plugin's
	 * Location.java (github.com/blert-io/plugin), the companion plugin for blert.io, a widely-used
	 * ToB analytics tool, rather than guessed or decompiled from this environment's client jar.
	 * Includes the lobby and loot room too (harmless even though no damage happens there) for a
	 * genuine "anywhere in this raid" match, mirroring CoX's all-encompassing varbit exactly.
	 */
	private static final Set<Integer> TOB_REGION_IDS = new HashSet<>(Arrays.asList(
		14642, 12869, 12613, 13125, 13122, 13123, 13379, 12612, 12611, 12867
	));

	/**
	 * Region IDs for every room of Tombs of Amascut, same rationale/approach as TOB_REGION_IDS -
	 * sourced from LlemonDuck/tombs-of-amascut's RaidRoom.java (github.com/LlemonDuck/
	 * tombs-of-amascut), a well-established ToA utility plugin. Includes the Nexus/Tomb lobby
	 * rooms and all four puzzle rooms (Crondis/Scabaras/Apmeken/Het) alongside the four boss rooms
	 * (Zebak/Kephri/Ba-Ba/Akkha) and both Wardens phase regions, for the same "anywhere in this
	 * raid" completeness as ToB above.
	 */
	private static final Set<Integer> TOA_REGION_IDS = new HashSet<>(Arrays.asList(
		14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, 14672
	));

	/**
	 * Doom of Mokhaiotl's max HP per delve level (index 0 = level 1) - not linear, levels 6-7 both
	 * sit at 650 before jumping to 675 at level 8. Deep delves (9+) repeat the level-8 fight at a
	 * reduced 625 HP (DOOM_DEEP_DELVE_HP). NpcMaxHpTable can't express this since Doom reuses the
	 * same three IDs at every level - see doomDelveLevel/resolveNpcMaxHp() instead. (npc_hp.csv
	 * previously carried stale 650 rows for all three IDs, dead since resolveNpcMaxHp() intercepts
	 * DOOM_NPC_IDS before ever reaching the static table - removed, same cleanup VASA_NPC_IDS's
	 * comment already describes for its own IDs.)
	 */
	private static final int[] DOOM_DELVE_HP = {525, 550, 575, 600, 625, 650, 650, 675};
	private static final int DOOM_DEEP_DELVE_HP = 625;

	/**
	 * Matches the "Delve level: N duration: ..." end-of-fight message (deep delves read "Delve
	 * level: 8+ (N) duration: ...", group 2 holding the real level). Not verified against a live
	 * screenshot - if the wording differs, delve tracking just stays at its default rather than
	 * tracking incorrectly.
	 */
	private static final Pattern DOOM_DELVE_MESSAGE = Pattern.compile(
		"^Delve level: (\\d+)(?:\\+ \\((\\d+)\\))? duration:");

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

	/**
	 * Cached "replaceOverheadIcon && showForSelf", refreshed on config change rather than read
	 * live in the render callback below (called once per renderable per frame). Volatile since
	 * onConfigChanged isn't guaranteed to run on the client thread.
	 */
	private volatile boolean suppressSelfOverheads;

	/**
	 * Suppresses the client's native overhead UI pass (health bar, prayer icon, hitsplats, chat
	 * text) for the local player only, so this plugin's overlay is the only thing drawn there.
	 * `ui=true` is specifically the overhead-UI draw call, distinct from the model draw call
	 * (`ui=false`, never suppressed) - the same mechanism the Nameplates plugin uses for every actor.
	 */
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			return !(ui && suppressSelfOverheads && renderable == client.getLocalPlayer());
		}
	};

	/**
	 * Hitsplats currently visible on the local player, redrawn by CustomHpBarOverlay since
	 * renderCallback suppresses the native ones (Hitsplat isn't a Renderable, so it can't be kept
	 * while the rest of the overhead pass is suppressed). Evicted in onGameTick once
	 * getDisappearsOnGameCycle() passes, matching native timing exactly.
	 */
	@Getter
	private final List<Hitsplat> selfHitsplats = new CopyOnWriteArrayList<>();

	/**
	 * Actors whose bars are active. Value = tick count of the last valid health-ratio read.
	 * ConcurrentHashMap: game events (game thread) write; overlay render (EDT) reads.
	 */
	@Getter
	private final Map<Actor, Integer> trackedActors = new ConcurrentHashMap<>();

	/**
	 * Most-recently seen [current, max] HP per actor. The overlay falls back to this while the
	 * native bar has faded but the actor is still within its persistDuration window.
	 */
	@Getter
	private final Map<Actor, int[]> lastKnownHp = new ConcurrentHashMap<>();

	/**
	 * Precise current HP per NPC (only for NPCs with a known max HP). getHealthRatio()/
	 * getHealthScale() are coarse buckets, not exact HP, so this is kept in sync between bucket
	 * changes by accumulating hitsplat damage/heal amounts instead - see updatePreciseHp()/
	 * applyHitsplatDamage().
	 */
	@Getter
	private final Map<NPC, Integer> preciseNpcHp = new ConcurrentHashMap<>();

	/**
	 * NPCs damaged by someone other than the local player since they were last evicted (not
	 * stopTracking()'d - see evict()) - powers greyOutOtherPlayerDamage. A plain Set: once true
	 * for an NPC's current lifetime it stays true, since a kill can't become "yours again" after
	 * someone else has hit it.
	 */
	private final Set<NPC> otherPlayerDamaged = ConcurrentHashMap.newKeySet();

	/**
	 * Tick of the most recent hitsplat of each status-effect type, per actor - the only signal
	 * available for NPCs/other players' status effects (no varp is readable for anyone but the
	 * local player). For the local player, Poison/Venom have an exact signal instead
	 * (VarPlayerID.POISON), so those two maps are only actually consulted for NPCs/other players -
	 * see activeStatusEffects() for exactly which actor types consult which map.
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
	 * click was "Attack" - see isGenuineAttackTarget(). Overwritten (not cleared) by each new
	 * click, so a later real Attack click on the same actor un-suppresses it.
	 */
	private Actor pendingClickActor;
	private boolean pendingClickIsAttack;

	/**
	 * The tick the current aggression tolerance window expires - updated once per onGameTick by
	 * updateAggressionArea, read by isNpcAggressive. aggressionSafeCenters holds the (up to) two
	 * most recent player positions the window is currently anchored to - see
	 * updateAggressionArea()'s doc comment for why two, not one.
	 */
	private int aggressionEndTick;
	private final WorldPoint[] aggressionSafeCenters = new WorldPoint[2];

	/**
	 * Which Doom of Mokhaiotl delve level the player is currently fighting - indexes
	 * DOOM_DELVE_HP. Defaults to 1 and advances by parsing the "Delve level: N duration:" message
	 * at the end of each fight (onChatMessage/DOOM_DELVE_MESSAGE); there's no other per-instance
	 * signal, since Doom reuses the same NPC IDs and combat level at every delve level.
	 *
	 * Known limitation: if the plugin starts (or reconnects) mid-delve, before any "duration:"
	 * message has been seen, this stays at 1 until the current fight ends.
	 */
	private int doomDelveLevel = 1;

	/**
	 * Live [currentHp, maxHp] and boss name from the game's own native boss HP HUD
	 * (InterfaceID.HpbarHud, VarbitID.HPBAR_HUD_HP/HPBAR_HUD_BASEHP) - shown at CoX, ToA,
	 * Gauntlet, Moons of Peril, and other supported encounters. This is the exact number the
	 * client is about to display, so it's preferred ahead of every other HP source - see
	 * nativeHudHp(). nativeHudBossName correlates this single-target HUD to whichever tracked
	 * actor it belongs to; null whenever no supported encounter's HUD is populated.
	 */
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
		selfHitsplats.clear();
		aggressionEndTick = 0;
		Arrays.fill(aggressionSafeCenters, null);
		doomDelveLevel = 1;
		nativeHudBossName = null;
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

	/**
	 * Recomputes the full native-sprite-override state from hideNativeBar and showPrayerBar
	 * together, rather than each toggle independently adding/removing its own sprites - since
	 * NativeHealthBarSprites.ALL already includes PRAYER's sprites, touching only one toggle's
	 * own set could leave the other toggle's desired state undone. Clearing everything first and
	 * reapplying what both flags currently want keeps one source of truth for the override map.
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
	 * excludes actors that can never be attacked (bankers, Quetzals, quest NPCs), but can't tell
	 * an Attack click apart from any other menu option on an actor that *can* also be attacked
	 * (e.g. Pickpocket on a Man). pendingClickActor/pendingClickIsAttack overrides the combat-level
	 * signal in that case. Only suppresses a click-driven false positive - has no effect when the
	 * player never clicked this actor at all (e.g. being aggroed and auto-retaliating).
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

		// Also fires with target == null when the interacting reference is cleared (e.g. zone
		// transitions) - only a non-null target means a real interaction started. getInteracting()
		// is also set by non-combat interactions (dialogue, trading, pickpocketing, a transport
		// NPC's "Travel" option), which isGenuineAttackTarget() filters out.
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

			// The other direction: an NPC already attacking the local player (aggro, or resuming
			// a fight after logging back in or teleporting mid-combat) never fires
			// InteractingChanged for the local player's own getInteracting() - only the attacker's
			// side changed, and it may well have already been targeting the player before the
			// teleport/login, so nothing "changes" from this client's perspective at all once the
			// scene loads. Previously only caught once the first hitsplat landed (a whole attack
			// cycle's worth of visible delay - the "HP bars have a slight delay on login/teleport"
			// symptom) or once the player clicked back. Scanning every tick (not every frame) for
			// npc.getInteracting() == localPlayer closes that gap; cheap relative to the "Always
			// Show NPC Bar" overlay pass, which already does a full NPC scan every render frame.
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

	/**
	 * Ends active combat-tracking once the persist-duration window elapses, without clearing
	 * lastKnownHp/preciseNpcHp the way evict() does - "Always Show NPC Bar" reads those caches for
	 * every matching NPC every frame regardless of trackedActors membership, and needs them to
	 * keep reflecting real last-known HP rather than resetting to full. Only despawn or the actor
	 * no longer matching isTrackedType should clear the cache - both still go through evict().
	 */
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
		lastPoisonTick.remove(actor);
		lastVenomTick.remove(actor);
		lastBurnTick.remove(actor);
		lastBleedTick.remove(actor);
		lastDiseaseTick.remove(actor);
		lastCorruptionTick.remove(actor);
		if (actor instanceof NPC)
		{
			preciseNpcHp.remove(actor);
			otherPlayerDamaged.remove(actor);
		}
	}

	/**
	 * True if the actor's native health bar is actively refreshing. Governs whether the
	 * persist-duration eviction clock keeps getting reset. Deliberately just getHealthRatio() !=
	 * -1 for every actor type - an earlier local-player special case using getInteracting() != null
	 * kept resetting the clock long after combat ended, since that reference doesn't clear promptly.
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

	/**
	 * Advances doomDelveLevel from the "Delve level: N duration:" message shown at the end of
	 * each Doom of Mokhaiotl fight - the message reports the level just cleared, so the next
	 * fight is N+1 (deep delves report the real level in group 2 instead of the literal "8").
	 */
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

	/**
	 * Refreshes nativeHudBossName/nativeHudCurrentHp/nativeHudMaxHp from the native boss HP HUD
	 * widget - fires on ScriptID.HP_HUD_UPDATE, the same clientscript that drives the widget's own
	 * text (confirmed via core OpponentInfoPlugin, which reads the same script/varbit pair).
	 */
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

	/**
	 * Returns [currentHp, maxHp] from the native boss HP HUD if it's currently showing data for
	 * this actor (matched by name), or null if the HUD isn't active or shows a different actor.
	 */
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

	/**
	 * Single chokepoint for an NPC's max HP. Doom of Mokhaiotl and Vasa Nistirio/Crystalline are
	 * both special-cased ahead of the static table, since a per-ID table structurally can't
	 * represent Doom's per-delve-level HP or Vasa's per-difficulty HP (see DOOM_NPC_IDS/
	 * VASA_NPC_IDS's doc comments).
	 *
	 * ToA minions deliberately return -1 (unknown) rather than a guessed number: real ToA scales
	 * every enemy's HP by raid level *and* path level (Walk the Path/Pathseeker/etc.) *and* party
	 * size, not raid level alone - showing a number that's missing two of those three factors
	 * would be actively wrong, not just imprecise, so this falls back to the overlay's existing
	 * percentage-only display instead. See CLAUDE.md's "ToA minion HP" section for the verified
	 * formula and sourcing, ready to implement once trusted enough to ship as an exact number.
	 */
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

	/**
	 * Establishes or sanity-checks the precise HP baseline from a fresh ratio/scale reading. Only
	 * overwrites an existing estimate if it has drifted outside the range of true HP that bucket
	 * could represent (e.g. joined a fight in progress, missed a non-hitsplat heal) - otherwise
	 * the hitsplat-accumulated value is left alone since it's finer-grained than the bucket.
	 *
	 * ratio == 0 and ratio == scale are exact floor/ceiling, not fuzzy bucket tolerance - without
	 * this, a dead NPC could show a sliver of HP on its last frames if accumulated damage didn't
	 * land on exactly 0 (e.g. from a slightly-off NpcMaxHpTable entry).
	 */
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

	/**
	 * Ticks a Poison/Venom/Bleed hitsplat's tint/icon stays active, wherever a hitsplat is the
	 * only signal available (see lastPoisonTick). Matches PoisonPlugin's known poison-tick cadence
	 * (18200ms). Not used for Burn (see BURN_STATUS_TICKS) - Burn is a short, instant DoT, nothing
	 * like Poison/Venom's long between-hit cadence.
	 */
	private static final int STATUS_EFFECT_TICKS = 31;

	/** Ticks a Burn hitsplat's tint/icon stays active - short, since Burn applies instantly and fades fast. */
	private static final int BURN_STATUS_TICKS = 8;

	/** VarPlayerID.POISON value at and above which the player is envenomed rather than poisoned - matches PoisonPlugin's own VENOM_THRESHOLD. */
	private static final int VENOM_THRESHOLD = 1_000_000;

	enum StatusEffect
	{
		VENOM, POISON, BURN, BLEED, DISEASE, CORRUPTION
	}

	/**
	 * Every status effect currently active for actor (or empty), independent of whether the
	 * bar-tint/debuff-icon toggles are on - each caller checks its own toggle. An actor can have
	 * more than one at once (e.g. burned while envenomed); currentStatusEffect() picks a single
	 * winner in venom > poison > burn > bleed > disease > corruption priority for the bar tint.
	 *
	 * The local player has an exact Poison/Venom signal (VarPlayerID.POISON) unavailable for
	 * anyone else, so NPCs/other players fall back to the hitsplat heuristic used for Burn/
	 * Disease/Corruption on every actor type. Bleed is local-player-only - it doesn't affect NPCs
	 * in OSRS, and isn't confirmed to land on other players either.
	 */
	Set<StatusEffect> activeStatusEffects(Actor actor)
	{
		int currentTick = client.getTickCount();
		EnumSet<StatusEffect> active = EnumSet.noneOf(StatusEffect.class);

		if (actor == client.getLocalPlayer())
		{
			// Exact signal for the local player, mutually exclusive by construction - prefer it
			// over the hitsplat heuristic used for every other actor type.
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
	 * The single highest-priority effect from activeStatusEffects, for the bar tint (which can
	 * only show one color, unlike the icon row). StatusEffect's declaration order is the priority
	 * order, so values() already iterates it correctly.
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

	/**
	 * Bar fill color for an actor's current status effect, or null if none applies or the
	 * relevant Color By Status Effect toggle is off (the debuff-icon row is gated separately, by
	 * CustomHpBarOverlay's own showStatusIcons()). Color profile is chosen by actor *type* (any
	 * Player vs. NPC) since other players are drawn with the Player Bar style, not "is this
	 * literally the local player."
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

	/**
	 * Returns live [current, max] HP for the actor, or null if unavailable. For the local player
	 * this bypasses getHealthRatio()/getHealthScale() and reads the Hitpoints skill directly -
	 * ratio/scale mirror the native combat bar and don't refresh from non-combat HP changes (e.g.
	 * eating) while that bar isn't actively displaying.
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

	/**
	 * Whether the local player has at least one prayer toggled on - lets CustomHpBarOverlay show
	 * the Prayer bar outside combat too (e.g. praying at a bank). There's no single "any prayer
	 * active" client flag, only a per-prayer check, so this loops Prayer.values() the same way the
	 * core Prayer plugin's own private isAnyPrayerActive() does. Client.isPrayerActive() is
	 * @Deprecated over ambiguity between paired prayers sharing one varbit, which doesn't matter
	 * for this OR-across-all-prayers question.
	 */
	boolean isAnyPrayerActive()
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

	/**
	 * Advances the aggression tolerance window once per tick, by porting the real mechanic from
	 * core's own NPC Aggression Timer plugin (decompiled) rather than the "is any aggressive
	 * monster currently loaded nearby" heuristic an earlier version of this method used. That
	 * heuristic was the actual bug behind TODO item 1 ("aggression timer wrongly colors all
	 * hostile NPCs yellow on expiry, not just nearby ones"): AGGRESSION_LEAVE_GRACE_TICKS's old
	 * 5-tick (3s) gap requirement was trivially satisfied while walking through any area with
	 * back-to-back aggressive-monster spawns (the Wilderness, Slayer caves, etc.), so the window
	 * never reset on arrival at a genuinely new, never-before-seen area - it just kept the stale
	 * expiry from wherever the player was 10 minutes earlier, making brand-new monsters show
	 * tolerant (yellow) immediately.
	 *
	 * The real mechanic (core plugin's own comment): "The game remembers 2 tiles. When the player
	 * goes >10 steps away from both tiles, the oldest one is moved to under the player and the NPC
	 * aggression timer resets." Critically, this reset is driven purely by the player's own
	 * movement pattern relative to those two remembered tiles - it has nothing to do with whether
	 * an aggressive monster happens to be in view, which is what aggressionSafeCenters replicates
	 * here. (This plugin doesn't need the reference implementation's extra teleport-vs-walking
	 * bookkeeping - previousUnknownCenter, the >40-tile special case - since those only matter for
	 * drawing its exact safe-zone overlay; a plain "outside AGGRESSION_SAFE_RADIUS of both
	 * remembered centers" check already produces the same reset decision for a teleport as for a
	 * long walk.)
	 *
	 * aggressionSafeCenters[1] == null only on the very first tick after startup/shutdown, and is
	 * used here to seed both centers with the player's current position immediately - unlike the
	 * reference plugin (which relies on cross-session persisted config plus a big-jump bootstrap),
	 * this plugin is session-only, so waiting for a qualifying jump before the window ever starts
	 * would leave it permanently un-started for a player who logs in and immediately starts
	 * fighting where they spawn.
	 */
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

	/**
	 * Whether npc would attack the local player if still aggressive: a known aggressive monster
	 * type (AggressiveNpcTable) and playerCombatLevel <= 2 * monsterCombatLevel (the OSRS
	 * aggression level rule - monsters combat level 63+ always qualify). Doesn't include the
	 * tolerance window itself - see isNpcAggressive.
	 */
	private boolean wouldBeAggressive(Player localPlayer, NPC npc)
	{
		int npcLevel = npc.getCombatLevel();
		return npcLevel > 0
			&& localPlayer.getCombatLevel() <= 2 * npcLevel
			&& AggressiveNpcTable.isAggressive(npc.getId());
	}

	/**
	 * Whether npc is currently aggressive toward the local player: it would attack
	 * (wouldBeAggressive), and either the tolerance window hasn't expired anywhere yet, or - once
	 * it has - npc itself isn't within AGGRESSION_SAFE_RADIUS of either remembered safe center.
	 * That second check is the part an earlier version of this method was missing: the global
	 * aggressionEndTick only says whether tolerance has been *earned* by now, not *where* - real
	 * OSRS tolerance only applies to monsters near wherever the player actually spent that time
	 * (aggressionSafeCenters), so once the window expires, a monster the player just walked up to
	 * somewhere new should still read as fully aggressive, not tolerant along with everything
	 * else the player happens to be able to see.
	 */
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

	/**
	 * True while the local player is any Ironman variant, via Varbits.ACCOUNT_TYPE directly (0 =
	 * normal, 1+ = some Ironman type - see the varbit's own doc comment for the full mapping).
	 * Preferred over the deprecated Client.getAccountType()/AccountType enum, which is also
	 * missing the "unranked group ironman" variant this varbit's mapping includes.
	 */
	private boolean isIronman()
	{
		return client.getVarbitValue(Varbits.ACCOUNT_TYPE) > 0;
	}

	/**
	 * Whether npc's bar should be greyed out because someone other than the local player has
	 * damaged it. Gated on both the config toggle and isIronman(), so this has no effect on
	 * normal accounts even if the toggle is left on.
	 */
	boolean isLootTainted(NPC npc)
	{
		return config.greyOutOtherPlayerDamage() && isIronman() && otherPlayerDamaged.contains(npc)
			&& !isCommunalLootEncounter(npc);
	}

	/**
	 * Whether npc belongs to one of the OSRS Wiki's confirmed Ironman group-loot exemptions (see
	 * COMMUNAL_LOOT_NPC_IDS/COMMUNAL_LOOT_NAMES). All three raids are blanket-exempted by
	 * location rather than by NPC list or name, since each has far more per-room/per-scale NPC
	 * variants than is practical to enumerate - Chambers of Xeric via
	 * VarbitID.RAIDS_CLIENT_INDUNGEON (the same "am I currently inside" varbit core RuneLite's own
	 * Raids plugin reads), Theatre of Blood/Tombs of Amascut via their region ID sets (see
	 * TOB_REGION_IDS/TOA_REGION_IDS's doc comments for sourcing) since no equivalent varbit was
	 * found for either. Every NPC in any of the three is exempted at once this way, with no
	 * per-boss maintenance required.
	 */
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

		String name = npc.getName();
		return name != null && COMMUNAL_LOOT_NAMES.contains(Text.removeTags(name).toLowerCase(Locale.ROOT));
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
	 * tracked. Used by the overlay's "Always Show NPC Name" path for untracked NPCs.
	 */
	boolean matchesNpcFilter(NPC npc)
	{
		return isTrackedNpc(npc);
	}

	/**
	 * Combat level 0 excludes every non-attackable NPC (bankers, shop owners, fishing spots,
	 * pets) without needing to name/ID them individually - the same signal onInteractingChanged
	 * uses to distinguish real combat from things like a Quetzal's "Travel" option. Gated behind
	 * onlyShowCombatNpcNames() (default on) rather than unconditional, since a hardcoded exclusion
	 * proved more opinionated than wanted.
	 */
	private boolean isTrackedNpc(NPC npc)
	{
		String name = npc.getName();
		return (!config.onlyShowCombatNpcNames() || npc.getCombatLevel() > 0)
			&& !HIDDEN_MECHANIC_NPC_IDS.contains(npc.getId())
			&& (name == null || !HIDDEN_MECHANIC_NPC_NAMES.contains(Text.removeTags(name).toLowerCase(Locale.ROOT)))
			&& matchesFilter(npc.getName());
	}

	/**
	 * Pure blacklist: empty filter shows all, any matching entry hides that NPC - there's no
	 * whitelist mode. Patterns are comma-separated, case-insensitive, support '*' wildcards. A
	 * trailing ':n' is still accepted and stripped for backward compatibility but no longer
	 * changes anything.
	 *
	 * Checked here (gating isTrackedType) rather than only at render time, so a filtered-out NPC
	 * never accumulates hitsplats into preciseNpcHp or clutters trackedActors/lastKnownHp.
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
