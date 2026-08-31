package com.customhpbar;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Renderable;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.SkullIcon;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
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
import java.util.Objects;
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

	/** A damage trail with no fresh observation this recently snaps to current HP instead of animating - see damageTrailFraction(). */
	private static final long TRAIL_STALE_MS = 2000;

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

	/** The trailing parenthetical the boss HP HUD appends to some encounters' names - the HUD's own annotation, not part of the NPC's name. */
	private static final Pattern HUD_NAME_SUFFIX = Pattern.compile("\\s*\\([^()]*\\)$");

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
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING,
		// Wardens' obelisk - the phase 1 target, and what the HUD bar shows for that phase
		NpcID.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, NpcID.TOA_WARDENS_P1_OBELISK_NPC,
		NpcID.TOA_WARDENS_P2_OBELISK_NPC
	));

	/**
	 * The Gemstone Crab and the remains it leaves - a public group encounter with participation-based
	 * rewards, so it is grey-out exempt, and percent-only in isPercentOnlyNpc().
	 */
	private static final Set<Integer> GEMSTONE_CRAB_NPC_IDS = new HashSet<>(Arrays.asList(
		NpcID.GEMSTONE_CRAB, NpcID.GEMSTONE_CRAB_REMAINS
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

	/** ToA room region -> that path's level varbit; the Wardens/Nexus/Tomb rooms are absent because no path level applies there. */
	private static final Map<Integer, Integer> TOA_PATH_LEVEL_VARBITS = new HashMap<>();

	static
	{
		TOA_PATH_LEVEL_VARBITS.put(14674, VarbitID.TOA_CLIENT_HET_LEVEL);       // Het
		TOA_PATH_LEVEL_VARBITS.put(14676, VarbitID.TOA_CLIENT_HET_LEVEL);       // Akkha
		TOA_PATH_LEVEL_VARBITS.put(15186, VarbitID.TOA_CLIENT_APMEKEN_LEVEL);   // Apmeken
		TOA_PATH_LEVEL_VARBITS.put(15188, VarbitID.TOA_CLIENT_APMEKEN_LEVEL);   // Ba-Ba
		TOA_PATH_LEVEL_VARBITS.put(14162, VarbitID.TOA_CLIENT_SCABARAS_LEVEL);  // Scabaras
		TOA_PATH_LEVEL_VARBITS.put(14164, VarbitID.TOA_CLIENT_SCABARAS_LEVEL);  // Kephri
		TOA_PATH_LEVEL_VARBITS.put(15698, VarbitID.TOA_CLIENT_CRONDIS_LEVEL);   // Crondis
		TOA_PATH_LEVEL_VARBITS.put(15700, VarbitID.TOA_CLIENT_CRONDIS_LEVEL);   // Zebak
	}

	/**
	 * ToA NPCs that take no raid scaling at all and return their base row untouched.
	 *
	 * The Apmeken wave-room baboons are wiki-stated - HP "static regardless of raid level and party
	 * size", the room being a fixed 8-wave combat challenge rather than a scaled one - and every one
	 * of them measured its base row exactly at raid 300 and again at raid 230 (2026-08-21 logs, see
	 * CLAUDE.md). "Their health varies" on the wiki refers to the per-variant spread the table
	 * already carries (Brawler 25/30, Thrower 30/35, Mage 20/25), not to scaling.
	 *
	 * The Scarabs, the Crocodile and Ba-Ba's Baboon are measured, not sourced: nothing on the wiki
	 * says they scale, and logToaDeathTally() found each sitting on its base row at two different
	 * raid levels while the formula predicted roughly double. The Egg is still assumption alone - no
	 * kill has been logged for it.
	 *
	 * The Obelisk (11751) sat here on that same assumption and is now measured out of it: a 5-man raid
	 * at raid level 305 read 2310 off the boss HUD, exactly the scaled figure, where static would have
	 * been 260 (2026-08-27, see CLAUDE.md). It scales like anything else.
	 *
	 * Not here, and deliberately not in npc_hp.csv either: the scenery NPCs (Boulder 11783/11737,
	 * Rubble 11784, Jug 11735) were measured at 25/150/12/5 and left rowless, so they resolve to -1
	 * and show a percentage. The Wardens' phase-2 exposed core (11755/11758) is rowless for the same
	 * reason, by choice rather than measurement - not a pool worth tracking as a number - and is out of
	 * TOA_BOSS_NPC_IDS too, so the boss HUD cannot lend it one either.
	 *
	 * A row is what turns an NPC into a number, so dropping the row is how you opt one out entirely.
	 * See CLAUDE.md.
	 *
	 * NOT here, though it reads like it belongs: Baboon Thrall (11718) scales normally - measured 3
	 * at raid 230 and 4 at raid 300 off a base of 2, exactly what toaScaledMaxHp() gives.
	 *
	 * Only ids npc_hp.csv actually carries a row for matter here; the other Obelisk/Egg variants
	 * resolve to -1 with or without this set.
	 */
	private static final Set<Integer> TOA_STATIC_HP_NPC_IDS = new HashSet<>(Arrays.asList(
		11709, 11712,   // Baboon Brawler
		11710, 11713,   // Baboon Thrower
		11711, 11714,   // Baboon Mage
		11715,          // Baboon Shaman
		11716,          // Volatile Baboon
		11717,          // Cursed Baboon
		11697,          // Scarab - Scabaras puzzle room
		11723,          // Scarab - Kephri's room
		11727,          // Agile Scarab - Kephri's room, measured 30 on the nose
		11705,          // Crocodile - Crondis
		11781,          // Baboon - Ba-Ba's room, not the wave room
		11728, 11729    // Egg - Kephri
	));

	/**
	 * Het's Seal, the Path of Het challenge-room objective, by party size (index 0 = solo). The wiki
	 * publishes these as a table rather than a formula because it has no clean one - the steps run
	 * 106, 96, 96, 95, 96, 96, 96 - and states outright that "raid level or selected invocations have
	 * no effect on the HP of Het's Seal. Its HP scales differently to other NPCs found within the
	 * raid." Confirmed solo live at raid 230 / path 1: 119 on the nose, from the boss HUD and from a
	 * damage tally both. Absent from npc_hp.csv entirely, hence the table here.
	 */
	private static final int[] HET_SEAL_HP_BY_PARTY_SIZE = {119, 225, 321, 417, 512, 608, 704, 800};
	private static final Set<Integer> HET_SEAL_NPC_IDS = new HashSet<>(Arrays.asList(11706, 11707));

	/** ToA party slot varbits - a nonzero slot is an occupied one, same set core's own LootTrackerPlugin counts. */
	private static final int[] TOA_PARTY_SLOT_VARBITS = {
		VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
		VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7
	};

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

	@Inject
	private KeyManager keyManager;

	/**
	 * Runtime-only show/hide state for "Toggle Names"/"Toggle HP Bars" - flipped by the hotkey
	 * listeners below, read by CustomHpBarOverlay's draw calls. Not config-backed (a hotkey is
	 * meant to be an instant, temporary override, not a persisted setting) and deliberately not
	 * reset in startUp()/shutDown() - default true (visible) matches every other bar/name toggle's
	 * "on by default" convention, and there'd be nothing to reset to anyway since nothing persists
	 * this across a client restart. Volatile since AWT's key-event thread writes these, not the
	 * client thread that reads them in CustomHpBarOverlay.render().
	 */
	@Getter
	private volatile boolean namesVisible = true;
	@Getter
	private volatile boolean hpBarsVisible = true;
	@Getter
	private volatile boolean weaknessIconsVisible = true;

	private final HotkeyListener toggleNamesHotkeyListener = new HotkeyListener(() -> config.toggleNamesHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			namesVisible = !namesVisible;
		}
	};

	private final HotkeyListener toggleHpBarsHotkeyListener = new HotkeyListener(() -> config.toggleHpBarsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			hpBarsVisible = !hpBarsVisible;
		}
	};

	private final HotkeyListener toggleWeaknessIconsHotkeyListener = new HotkeyListener(() -> config.toggleWeaknessIconsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			weaknessIconsVisible = !weaknessIconsVisible;
		}
	};

	/** Cached showForSelf - volatile since onConfigChanged isn't guaranteed on the client thread. */
	private volatile boolean suppressSelfOverheads;

	/**
	 * Whether anyone but the local player is in the scene, sampled once per tick - the guard on
	 * DAMAGE_OTHER meaning what greyOutOtherPlayerDamage assumes it means. Volatile and
	 * tick-granular for the same reason overheadEligiblePlayers is: hitsplats arrive off the tick.
	 */
	private volatile boolean otherPlayersInScene;

	/**
	 * Other players currently eligible for the same overhead icon/hitsplat/chat-text replacement
	 * self gets - tracked (a bar is showing) or "Always Show Player Name" eligible (a name is
	 * showing) - recomputed once per tick in updateOverheadEligiblePlayers(), not per frame, since
	 * the render callback needs an answer before the overlay's own per-frame draw decisions run.
	 * Volatile, swapped as a whole immutable snapshot: the render callback may read this off the
	 * client thread relative to the tick handler that writes it - see suppressSelfOverheads above.
	 */
	private volatile Set<Player> overheadEligiblePlayers = Collections.emptySet();

	/** Suppresses the client's native overhead UI (health bar, prayer icon, hitsplats, chat text) for the local player and overheadEligiblePlayers. */
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			if (!ui)
			{
				return true;
			}
			if (renderable == client.getLocalPlayer())
			{
				return !suppressSelfOverheads;
			}
			return !overheadEligiblePlayers.contains(renderable);
		}
	};

	/**
	 * Overhead hitsplats per player, redrawn since renderCallback suppresses the native ones - self
	 * plus overheadEligiblePlayers. Copies, not the client's own Hitsplat objects, which it pools and
	 * reuses - see CLAUDE.md.
	 */
	@Getter
	private final Map<Player, List<OverheadHitsplat>> overheadHitsplats = new ConcurrentHashMap<>();

	/** Actors whose bars are active; value = tick of last valid health-ratio read. */
	@Getter
	private final Map<Actor, Integer> trackedActors = new ConcurrentHashMap<>();

	/** Most-recent [current, max] HP per actor, used while a bar persists past the last live read. */
	@Getter
	private final Map<Actor, int[]> lastKnownHp = new ConcurrentHashMap<>();

	/** Damage-trail animation state per actor, created lazily by damageTrailFraction() only while the bar's own trail toggle is on. */
	private final Map<Actor, TrailState> damageTrails = new ConcurrentHashMap<>();

	/** Damage seen by onHitsplatApplied but not yet by any trail state - seeds the trail for a bar's first observed frame. See damageTrailFraction(). */
	private final Map<Actor, Integer> pendingTrailDamage = new ConcurrentHashMap<>();

	/** NPCs mid death-fade, keyed to the wall-clock ms their death was observed - see beginDeathFade(). */
	@Getter
	private final Map<Actor, Long> deathFades = new ConcurrentHashMap<>();

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

	/** Same, for the other-player blacklist - kept separate so one filter's edits don't invalidate the other's cache. */
	private String cachedPlayerFilterString = "";
	private List<Pattern> cachedPlayerPatterns = new ArrayList<>();

	/** isTrackedNpc() result per NPC, cached for one game tick rather than recomputed every frame - see isTrackedNpcCached(). CLAUDE.md. */
	private int trackedNpcCacheTick = Integer.MIN_VALUE;
	private final Map<NPC, Boolean> trackedNpcCache = new ConcurrentHashMap<>();
	private int trackedNpcCacheHits;
	private int trackedNpcCacheMisses;
	private int trackedNpcCacheLastLogTick;

	/** Debug-only: ToA NPC ids already logged for the current room, so logToaScaling() reports each once per room. TODO bug 1. */
	private final Set<Integer> toaLoggedNpcIds = new HashSet<>();
	private int toaLoggedRegion = -1;

	/** Debug-only: last boss-HUD reading already logged, so logToaBossHud() reports each boss and phase once. TODO bug 1. */
	private int loggedHudMaxHp = -1;
	private String loggedHudName;

	/** Debug-only: damage dealt to each live ToA NPC as {total, lastHit, hits}, dumped on death by logToaDeathTally(). TODO bug 1. */
	private final Map<NPC, int[]> toaDamageTally = new ConcurrentHashMap<>();
	private final Set<NPC> toaTallyLogged = ConcurrentHashMap.newKeySet();

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

	/** Wall-clock ms the last GameTick was observed - powers tickProgress(), the Prayer bar's sweeping tick timer. */
	private long lastTickTimeMs = System.currentTimeMillis();

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

	/**
	 * nativeHudBossName with HUD_NAME_SUFFIX removed, or null when there was nothing to remove.
	 * Only ever consulted after the exact comparison fails, and only the HUD side is stripped:
	 * stripping the actor's side too would let "Great Olm (Left claw)" answer to Olm's own HUD
	 * figure. A field rather than a per-call strip because nativeHudHp() runs per actor per frame.
	 */
	private String nativeHudBossBaseName;
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
		suppressSelfOverheads = config.showForSelf();
		overlayManager.add(overlay);
		renderCallbackManager.register(renderCallback);
		keyManager.registerKeyListener(toggleNamesHotkeyListener);
		keyManager.registerKeyListener(toggleHpBarsHotkeyListener);
		keyManager.registerKeyListener(toggleWeaknessIconsHotkeyListener);
		clientThread.invokeLater(this::syncNativeBarOverrides);
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(renderCallback);
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(toggleNamesHotkeyListener);
		keyManager.unregisterKeyListener(toggleHpBarsHotkeyListener);
		keyManager.unregisterKeyListener(toggleWeaknessIconsHotkeyListener);
		trackedActors.clear();
		lastKnownHp.clear();
		damageTrails.clear();
		pendingTrailDamage.clear();
		deathFades.clear();
		preciseNpcHp.clear();
		otherPlayerDamaged.clear();
		statusEffectTicks.clear();
		overheadHitsplats.clear();
		overheadEligiblePlayers = Collections.emptySet();
		otherPlayersInScene = false;
		pendingClickActor = null;
		aggressionEndTick = 0;
		Arrays.fill(aggressionSafeCenters, null);
		doomDelveLevel = 1;
		setNativeHudBossName(null);
		prayerActive = false;
		lastTickTimeMs = System.currentTimeMillis();
		bleedEndVarcCounting = false;
		bleedEndedTick = Integer.MIN_VALUE;
		trackedNpcCache.clear();
		trackedNpcCacheTick = Integer.MIN_VALUE;
		trackedNpcCacheHits = 0;
		trackedNpcCacheMisses = 0;
		trackedNpcCacheLastLogTick = 0;
		toaLoggedNpcIds.clear();
		toaLoggedRegion = -1;
		loggedHudMaxHp = -1;
		loggedHudName = null;
		toaDamageTally.clear();
		toaTallyLogged.clear();
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

		if ("showForSelf".equals(event.getKey()))
		{
			suppressSelfOverheads = config.showForSelf();
		}
	}

	/**
	 * Scene reloads (walking far enough to shift the loaded area, not just world-hops) can rebuild the
	 * client's health-bar render cache without re-consulting our sprite overrides, letting the native bar
	 * flash through until something else touches it again. Re-assert on every LOADING -> LOGGED_IN
	 * transition, not just config changes.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::syncNativeBarOverrides);
		}
	}

	/** Recomputes native-sprite overrides from hideNativeBar + showPrayerBar together, rather than each toggle its own set. */
	private void syncNativeBarOverrides()
	{
		// Removal still spans ALL, not HEALTH_ONLY: whatever the last sync applied has to come back
		// off, including the mechanic bars an older build of this method blanked.
		removeSpriteOverride(NativeHealthBarSprites.ALL);
		if (config.hideNativeBar())
		{
			applySpriteOverride(NativeHealthBarSprites.HEALTH_ONLY);
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
		// overheadEligiblePlayers is tick-granular (see its own doc), so a player who only just
		// became eligible this exact tick may miss their very first hitsplat - accepted, matches
		// the same "risk is asymmetric, not worth chasing exactly" tradeoff used elsewhere here.
		if (actor == client.getLocalPlayer() || (actor instanceof Player && overheadEligiblePlayers.contains(actor)))
		{
			overheadHitsplats.computeIfAbsent((Player) actor, k -> new CopyOnWriteArrayList<>())
				.add(new OverheadHitsplat(hitsplat.getHitsplatType(), hitsplat.getAmount(),
					hitsplat.getDisappearsOnGameCycle()));
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
			tallyToaDamage((NPC) actor, hitsplat);
		}

		// The only record of a hit landed before the bar's first drawn frame - without it a kill
		// from full HP has no earlier fraction to trail from, which is the exact case issue #36
		// reports as janky. Consumed once, by whichever frame draws this actor next.
		if (hitsplat.getAmount() > 0 && anyDamageTrailEnabled())
		{
			pendingTrailDamage.merge(actor, hitsplat.getAmount(), Integer::sum);
		}

		// A landed hitsplat is stronger proof of a valid combat target than isAttackableNpc() -
		// covers no-attack-option NPCs like ToB's Supporting Pillars. See CLAUDE.md.
		boolean trackable = actor instanceof NPC ? isTrackedNpc((NPC) actor) : isTrackedType(actor);
		if (trackable)
		{
			track(actor, client.getTickCount());
		}

		// A hit you dealt to someone else - Hitsplat.isMine(), which includes BLOCK_ME (a 0-damage
		// hit you landed) - keeps your own bar's tracked clock refreshed even while you're not
		// taking damage yourself. Without this, isInCombat(localPlayer) (native health ratio, taking
		// damage only) is the sole thing keeping self tracked, so the bar could expire mid-fight
		// just from attacking something that isn't hitting back. See CLAUDE.md.
		Player localPlayer = client.getLocalPlayer();
		if (actor != localPlayer && hitsplat.isMine() && isTrackedType(localPlayer))
		{
			track(localPlayer, client.getTickCount());
		}

		trackStatusEffect(actor, hitsplat.getHitsplatType());

		// otherPlayersInScene is the whole point: the client types an NPC's own mechanic damage
		// (a Doom larva exploding, and anything else shaped like it) as DAMAGE_OTHER, exactly as it
		// types another player's hit, so the splat alone cannot tell them apart. Nobody else being
		// in the scene can - it makes "another player did this" impossible rather than unlikely.
		// The chat-message path stays untouched; it's the game's own verdict, not an inference.
		if (actor instanceof NPC && otherPlayersInScene
			&& OTHER_PLAYER_DAMAGE_HITSPLATS.contains(hitsplat.getHitsplatType()))
		{
			otherPlayerDamaged.add((NPC) actor);
		}

		// Hide the bar the instant the killing blow lands, rather than waiting out
		// persistTicks()/the death animation - see isConfirmedDead().
		if (isConfirmedDead(actor))
		{
			logToaDeathTally(actor);
			beginDeathFade(actor);
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

		// Latched first, before anything else in this handler - tickProgress() measures elapsed
		// wall-clock time since this exact moment, so it must be set at the true start of the tick.
		lastTickTimeMs = System.currentTimeMillis();

		// The only prayer sample there is, exactly as core PrayerPlugin.onGameTick does it.
		prayerActive = isAnyPrayerActive();

		trackRunEnergyChange();

		// The overlay's render-time check against getDisappearsOnGameCycle() is what actually
		// controls when a hitsplat stops drawing; this just bounds each list's size between prunes.
		int currentCycle = client.getGameCycle();
		for (List<OverheadHitsplat> hitsplats : overheadHitsplats.values())
		{
			hitsplats.removeIf(h -> currentCycle >= h.getDisappearsOnGameCycle());
		}
		// Bounds the map itself - a player who wanders off (no longer eligible) with no hitsplats
		// left to redraw doesn't need an entry kept around; computeIfAbsent recreates it on demand.
		overheadHitsplats.values().removeIf(List::isEmpty);

		// Both animation maps are keyed by Actor, so they need a bound of their own for anything
		// that stops being drawn without ever despawning. The overlay checks elapsed time itself
		// rather than trusting this to have run - this only stops the maps growing.
		long nowMs = System.currentTimeMillis();
		int fadeMs = config.npcDeathFadeDuration();
		deathFades.values().removeIf(start -> nowMs - start >= fadeMs || nowMs < start);
		damageTrails.values().removeIf(trail -> nowMs - trail.lastSeenMs > TRAIL_STALE_MS || nowMs < trail.lastSeenMs);

		updateAggressionArea(currentTick);

		// onScriptPostFired only fires while the native HUD is actively updating, so this clears
		// a stale boss name once its widget is hidden/absent - otherwise it would linger forever.
		if (nativeHudBossName != null)
		{
			Widget hudWidget = client.getWidget(InterfaceID.HpbarHud.HP);
			if (hudWidget == null || hudWidget.isHidden())
			{
				setNativeHudBossName(null);
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
			// Death first: isTrackedType() already returns false for a corpse, so testing it first
			// swallowed every death before beginDeathFade() could see one. Same eviction either
			// way - only which branch claims it changes.
			if (isConfirmedDead(actor))
			{
				beginDeathFade(actor);
				evict(actor);
				return;
			}

			// A config toggle may have turned this actor's bar off since it was tracked - evict
			// immediately rather than waiting out the persist timer.
			if (!isTrackedType(actor))
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

		// Last, so this tick's tracking additions/evictions above are reflected immediately rather
		// than lagging a further tick behind.
		updateOverheadEligiblePlayers();

		logToaScaling();
	}

	/**
	 * Recomputes overheadEligiblePlayers: tracked other players (a bar is showing) union "Always
	 * Show Player Name" eligible other players (a name is showing) union "Always Show Player HP
	 * Bar" eligible other players (a bar is showing) union any other player currently showing a
	 * skull or overhead (prayer) icon - the same conditions CustomHpBarOverlay's own render()
	 * passes use to decide whether a player gets anything drawn at all (see its "Always Show
	 * Player Bar"/"Always Show Player Name" loop, and its iconOnly branch specifically for the
	 * last case). Tick-granular, not per-frame - see the field's own doc for why.
	 *
	 * The skull/icon union exists so a player with neither a bar nor a name showing (not tracked,
	 * neither "Always Show" toggle on) still gets their native skull/prayer icon suppressed - and
	 * therefore needs CustomHpBarOverlay to actually redraw a replacement, or it would just
	 * vanish instead of showing at its own default position. Requires scanning every player
	 * unconditionally now, not just when an always-show toggle is on, since a skulled/praying
	 * player can appear regardless of those toggles.
	 */
	private void updateOverheadEligiblePlayers()
	{
		Player localPlayer = client.getLocalPlayer();
		boolean anyOtherPlayer = false;
		Set<Player> eligible = new HashSet<>();
		for (Actor actor : trackedActors.keySet())
		{
			if (actor instanceof Player && actor != localPlayer)
			{
				eligible.add((Player) actor);
			}
		}

		boolean alwaysShow = (config.showPlayerName() && config.alwaysShowPlayerName())
			|| (config.showForPlayers() && config.alwaysShowPlayerBar());
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player == null || player == localPlayer)
			{
				continue;
			}

			// Set before the blacklist gate below: a blacklisted player still damages things, so
			// hiding their bar must not also make their hits read as the NPC's own mechanic.
			anyOtherPlayer = true;

			// Player Blacklist excludes them here as well as from trackedActors: without this a filtered
			// player's native overhead icon would still be suppressed, with nothing redrawing it.
			if (!isTrackedPlayer(player))
			{
				continue;
			}
			if (alwaysShow && player.getName() != null && !player.getName().isEmpty())
			{
				eligible.add(player);
			}
			else if (player.getSkullIcon() != SkullIcon.NONE || player.getOverheadIcon() != null)
			{
				eligible.add(player);
			}
		}

		overheadEligiblePlayers = eligible.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(eligible);
		otherPlayersInScene = anyOtherPlayer;
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
	 * True once an actor (NPC or player, self included) is confirmed dead (0 HP) - its bar should
	 * vanish immediately, not after persistTicks(), and not after the death animation. Ratio 0
	 * unambiguously means dead in OSRS's ratio/scale scheme, but the animation keeps reporting it
	 * for several more ticks before the actor actually despawns - persist duration is for actors
	 * that are still alive but out of combat, not for this. Originally NPC-only; widened to cover
	 * players too - see CLAUDE.md.
	 */
	static boolean isConfirmedDead(Actor actor)
	{
		return actor.getHealthRatio() == 0;
	}

	/**
	 * Starts an NPC's death fade, if the feature is on and its bar was actually on screen. Called
	 * immediately before each of the two death evict() calls, never instead of one - the fade is a
	 * separate render pass over already-evicted NPCs (see CustomHpBarOverlay.render()), so every
	 * gate issue #22's instant-hide fix installed stays exactly as it was.
	 *
	 * The "was showing" test matters: without it a kill by someone else across the room would make
	 * a bar appear purely to fade it out again.
	 */
	private void beginDeathFade(Actor actor)
	{
		if (!(actor instanceof NPC) || !config.fadeNpcBarOnDeath() || config.npcDeathFadeDuration() <= 0)
		{
			return;
		}

		// The "Always Show NPC Bar" half mirrors that pass's own conditions exactly - without
		// isTrackedNpcCached() a blacklisted or non-combat NPC would qualify here despite never
		// having had a bar to fade.
		NPC npc = (NPC) actor;
		if (trackedActors.containsKey(npc)
			|| (config.alwaysShowNpcBar() && isTrackedNpcCached(npc) && isAttackableNpc(npc)))
		{
			deathFades.putIfAbsent(npc, System.currentTimeMillis());
		}
	}

	/**
	 * The fraction a bar's damage trail should draw to this frame, always >= the actor's own
	 * current fraction. Frame-driven rather than event-driven: the overlay has already resolved
	 * one authoritative fraction per actor (boss HUD, precise NPC HP, live ratio, or last known),
	 * so comparing this frame's against the previous one covers every HP source at once.
	 *
	 * A drop starts a trail from wherever the previous frame left off; a rise (healing) snaps, as
	 * does a first sighting or one that went TRAIL_STALE_MS without an observation - a bar that
	 * was off screen, hotkey-hidden or stack-capped for a while must not animate down the whole
	 * gap it missed. The one exception is pendingTrailDamage: on a first sighting with a known max
	 * HP, the hitsplat that has landed but never been drawn seeds the trail's start, which is what
	 * gives a one-shot kill a trail at all.
	 *
	 * Only called while the drawing profile's own trail toggle is on, so state is never allocated
	 * for a bar that isn't using it. maxHp <= 0 means an unresolved max (percent-only NPCs, other
	 * players) - the frame-to-frame half still works there, only the seed is unavailable.
	 */
	double damageTrailFraction(Actor actor, double fraction, int maxHp)
	{
		long now = System.currentTimeMillis();
		Integer pending = pendingTrailDamage.remove(actor);
		TrailState state = damageTrails.get(actor);

		if (state == null || now - state.lastSeenMs > TRAIL_STALE_MS || now < state.lastSeenMs)
		{
			state = new TrailState();
			state.reset(fraction, now);
			damageTrails.put(actor, state);

			if (pending != null && maxHp > 0)
			{
				state.start(Math.min(1.0, fraction + pending / (double) maxHp), fraction, now,
					config.damageTrailHold());
			}
			return state.trailAt(now, config.damageTrailDrain());
		}

		double trail = state.trailAt(now, config.damageTrailDrain());
		if (fraction < state.lastFraction)
		{
			state.start(Math.max(trail, state.lastFraction), fraction, now, config.damageTrailHold());
		}
		else if (fraction > state.lastFraction && fraction >= trail)
		{
			// Healing snaps: a trail below the new fill would never be visible anyway, and one
			// above it would read as damage that never happened.
			state.reset(fraction, now);
		}

		state.lastFraction = fraction;
		state.lastSeenMs = now;
		return Math.max(fraction, state.trailAt(now, config.damageTrailDrain()));
	}

	/** Whether any bar profile has its damage trail on - gates the hitsplat seed so it costs nothing with the feature off. */
	private boolean anyDamageTrailEnabled()
	{
		return config.targetDamageTrail() || config.playerDamageTrail() || config.otherPlayerDamageTrail();
	}

	/** Drops trail/fade state that no bar can still be drawing - despawn is the hard end for both. */
	private void clearAnimations(Actor actor)
	{
		damageTrails.remove(actor);
		pendingTrailDamage.remove(actor);
		deathFades.remove(actor);
	}

	/** One damage trail mid-animation: hold at trailStart, then drain to trailTarget. See damageTrailFraction(). */
	private static final class TrailState
	{
		private double lastFraction;
		private double trailStart;
		private double trailTarget;
		private long drainStartMs;
		private long lastSeenMs;

		private void reset(double fraction, long now)
		{
			lastFraction = fraction;
			trailStart = fraction;
			trailTarget = fraction;
			drainStartMs = now;
			lastSeenMs = now;
		}

		private void start(double from, double to, long now, int holdMs)
		{
			trailStart = from;
			trailTarget = to;
			drainStartMs = now + holdMs;
		}

		private double trailAt(long now, int drainMs)
		{
			if (now <= drainStartMs)
			{
				return trailStart;
			}
			if (drainMs <= 0)
			{
				return trailTarget;
			}

			double progress = Math.min(1.0, (now - drainStartMs) / (double) drainMs);
			return trailStart + (trailTarget - trailStart) * progress;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		// Second chance at the tally: on the killing hitsplat getHealthRatio() has usually already
		// read 0, but not guaranteed - a dead NPC despawning is the backstop. TODO bug 1.
		if (isConfirmedDead(event.getNpc()))
		{
			logToaDeathTally(event.getNpc());
		}
		toaDamageTally.remove(event.getNpc());
		toaTallyLogged.remove(event.getNpc());
		clearAnimations(event.getNpc());
		evict(event.getNpc());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		clearAnimations(event.getPlayer());
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
			setNativeHudBossName(null);
			return;
		}

		Widget nameWidget = client.getWidget(InterfaceID.HpbarHud.CREATURE_NAME);
		String rawName = nameWidget != null ? nameWidget.getText() : null;
		String name = rawName != null ? Text.removeTags(rawName) : null;
		if (name == null || name.isEmpty())
		{
			setNativeHudBossName(null);
			return;
		}

		setNativeHudBossName(name);
		nativeHudCurrentHp = client.getVarbitValue(VarbitID.HPBAR_HUD_HP);
		nativeHudMaxHp = maxHp;
	}

	/** The one write point for the HUD boss name, so its stripped form can't drift out of step with it. */
	private void setNativeHudBossName(String name)
	{
		nativeHudBossName = name;
		String base = name == null ? null : HUD_NAME_SUFFIX.matcher(name).replaceAll("").trim();
		nativeHudBossBaseName = base == null || base.isEmpty() || base.equals(name) ? null : base;
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
		if (actorName == null)
		{
			return null;
		}

		String plainName = Text.removeTags(actorName);
		if (!nativeHudBossName.equalsIgnoreCase(plainName)
			&& (nativeHudBossBaseName == null || !nativeHudBossBaseName.equalsIgnoreCase(plainName)))
		{
			return null;
		}

		return new int[]{nativeHudCurrentHp, nativeHudMaxHp};
	}

	/**
	 * NPCs whose HP must always read as a percentage, never a number - the native HUD carries a real
	 * current/max for them, but the encounter only ever shows a percentage in game, so resolveMaxHp()
	 * returns -1 for these and the HUD figures survive only as the fraction behind that percentage.
	 */
	boolean isPercentOnlyNpc(NPC npc)
	{
		return GEMSTONE_CRAB_NPC_IDS.contains(npc.getId());
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

	/** Single chokepoint for an NPC's max HP - Doom/Vasa special-cased first, then ToA's own raid scaling (see CLAUDE.md). */
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
		if (HET_SEAL_NPC_IDS.contains(npcId))
		{
			// Straight table lookup - no raid level, no path level, nothing to scale. Clamped
			// because the varbits can only describe an eight-slot party anyway.
			int partySize = Math.min(toaPartySize(), HET_SEAL_HP_BY_PARTY_SIZE.length);
			return HET_SEAL_HP_BY_PARTY_SIZE[partySize - 1];
		}

		int baseHp = NpcMaxHpTable.getMaxHp(npcId);
		if (isInsideToa() && !TOA_STATIC_HP_NPC_IDS.contains(npcId))
		{
			// The party term has never been measured and the wiki only claims it for bosses, so a
			// minion number in a team would be a guess - percent instead. Bosses keep their number:
			// the HUD carries the server's own figure for them.
			if (toaPartySize() > 1 && !TOA_BOSS_NPC_IDS.contains(npcId))
			{
				return -1;
			}
			return baseHp > 0 ? toaScaledMaxHp(baseHp) : -1;
		}
		return baseHp;
	}

	/**
	 * npc_hp.csv's ToA rows are base (raid level 0, path 0, solo) HP - ToA scales that by raid level,
	 * path level and party size. Integer division and rounding mirror the game's own, see CLAUDE.md.
	 */
	private int toaScaledMaxHp(int baseHp)
	{
		int hp = baseHp;
		hp += hp * (4 * client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL) / 10) / 100;

		int pathLevel = toaPathLevel();
		if (pathLevel > 0)
		{
			// Level 1 is +8%, each level after +5%.
			hp += hp * (3 + 5 * pathLevel) / 100;
		}

		int partySize = toaPartySize();
		if (partySize >= 2)
		{
			// 2nd and 3rd member add 90% of base each, 4th and beyond 60% each.
			int partyFactor = 9 * Math.min(partySize - 1, 2) + 6 * Math.max(partySize - 3, 0);
			hp += hp * partyFactor / 10;
		}

		if (hp > 100)
		{
			int roundTo = hp > 300 ? 10 : 5;
			hp = (hp + roundTo / 2) / roundTo * roundTo;
		}
		return hp;
	}

	/**
	 * Debug-only (TODO bug 1): dumps every scaling input and output for each ToA NPC once per room, so a
	 * live raid can be compared against real max HP. getHealthScale() is included because it may already
	 * carry the true scaled max, which would make the formula unnecessary for the small-HP minions.
	 */
	private void logToaScaling()
	{
		if (!log.isDebugEnabled() || !isInsideToa())
		{
			return;
		}

		int region = localPlayerRegion();
		if (region != toaLoggedRegion)
		{
			toaLoggedRegion = region;
			toaLoggedNpcIds.clear();
		}

		int raidLevel = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
		int pathLevel = toaPathLevel();
		int partySize = toaPartySize();

		logToaBossHud(region, raidLevel, pathLevel, partySize);

		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null || !toaLoggedNpcIds.add(npc.getId()))
			{
				continue;
			}

			int baseHp = NpcMaxHpTable.getMaxHp(npc.getId());
			log.debug("ToA scaling: region={} id={} name={} baseRow={} raidLevel={} pathLevel={} partySize={}"
					+ " scaledMax={} healthRatio={} healthScale={}",
				region, npc.getId(), npc.getName(), baseHp, raidLevel, pathLevel, partySize,
				resolveNpcMaxHp(npc.getId()), npc.getHealthRatio(), npc.getHealthScale());
		}
	}

	/**
	 * Debug-only (TODO bug 1): the boss HP HUD's own numbers, which are the server's, not ours.
	 * VarbitID.HPBAR_HUD_HP / HPBAR_HUD_BASEHP drive the bar core's OpponentInfoPlugin rewrites, and
	 * its comment names the content: "used in CoX, ToA, Gauntlet, quest bosses ... not ToB". So
	 * BASEHP is the true scaled max for whatever the bar is showing - exact, and free of the two
	 * things that make logToaDeathTally() useless on a boss: Akkha restarting the tally at every
	 * elemental phase, and Tumeken's Warden carrying one across a phase change.
	 *
	 * Logged once per (name, max) pair, so each boss and each phase reports once rather than every
	 * tick. Also prints what the local player is fighting and what our own formula predicts for it,
	 * which is the actual comparison; the HUD names its target but doesn't give an NPC id, so
	 * attribution is by interaction and by the name the HUD itself draws.
	 */
	private void logToaBossHud(int region, int raidLevel, int pathLevel, int partySize)
	{
		int hudMaxHp = client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP);
		if (hudMaxHp <= 0)
		{
			return;
		}

		Widget nameWidget = client.getWidget(InterfaceID.HpbarHud.CREATURE_NAME);
		String hudName = nameWidget == null ? null : nameWidget.getText();
		if (hudMaxHp == loggedHudMaxHp && Objects.equals(hudName, loggedHudName))
		{
			return;
		}
		loggedHudMaxHp = hudMaxHp;
		loggedHudName = hudName;

		Player localPlayer = client.getLocalPlayer();
		Actor target = localPlayer == null ? null : localPlayer.getInteracting();
		NPC targetNpc = target instanceof NPC ? (NPC) target : null;

		log.debug("ToA HUD: region={} hudName={} hudHp={}/{} raidLevel={} pathLevel={} partySize={}"
				+ " fighting={} id={} baseRow={} predictedMax={}",
			region, hudName, client.getVarbitValue(VarbitID.HPBAR_HUD_HP), hudMaxHp,
			raidLevel, pathLevel, partySize,
			targetNpc == null ? null : targetNpc.getName(), targetNpc == null ? -1 : targetNpc.getId(),
			targetNpc == null ? -1 : NpcMaxHpTable.getMaxHp(targetNpc.getId()),
			targetNpc == null ? -1 : resolveNpcMaxHp(targetNpc.getId()));
	}

	/**
	 * Debug-only (TODO bug 1): accumulates damage dealt to a ToA NPC so logToaDeathTally() can
	 * recover its true max HP from the total, independent of npc_hp.csv and of the scaling formula.
	 */
	private void tallyToaDamage(NPC npc, Hitsplat hitsplat)
	{
		if (!log.isDebugEnabled() || !isInsideToa() || toaTallyLogged.contains(npc)
			|| !DAMAGE_HITSPLATS.contains(hitsplat.getHitsplatType()))
		{
			return;
		}

		int[] tally = toaDamageTally.computeIfAbsent(npc, k -> new int[3]);
		tally[0] += hitsplat.getAmount();
		tally[1] = hitsplat.getAmount();
		tally[2]++;
	}

	/**
	 * Debug-only (TODO bug 1): on the killing blow, reports total damage taken against what the
	 * table and the scaling formula predicted. The last hit can overkill, so the true max HP is
	 * within (total - lastHit, total] - a few kills of the same NPC narrow that to one number.
	 */
	private void logToaDeathTally(Actor actor)
	{
		if (!log.isDebugEnabled() || !(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		int[] tally = toaDamageTally.remove(npc);
		if (tally == null)
		{
			return;
		}
		toaTallyLogged.add(npc);

		log.debug("ToA death: region={} id={} name={} baseRow={} predictedMax={} damageTotal={}"
				+ " lastHit={} hits={} trueMaxRange=({}..{}]",
			localPlayerRegion(), npc.getId(), npc.getName(), NpcMaxHpTable.getMaxHp(npc.getId()),
			resolveNpcMaxHp(npc.getId()),
			tally[0], tally[1], tally[2], tally[0] - tally[1], tally[0]);
	}

	/** Path level (0-6) for the room the player is in, or 0 in the rooms no path level applies to. */
	private int toaPathLevel()
	{
		Integer varbit = TOA_PATH_LEVEL_VARBITS.get(localPlayerRegion());
		return varbit == null ? 0 : client.getVarbitValue(varbit);
	}

	/** Players in the raid, counted from the occupied party slots; never below 1. */
	private int toaPartySize()
	{
		int size = 0;
		for (int varbit : TOA_PARTY_SLOT_VARBITS)
		{
			if (client.getVarbitValue(varbit) != 0)
			{
				size++;
			}
		}
		return Math.max(1, size);
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

	/**
	 * Progress through the current game tick, in radians [0, PI) - powers the Prayer bar's
	 * sweeping tick timer (see CustomHpBarOverlay.drawPrayerTickTimer()). Deliberately matches
	 * core's own `PrayerPlugin.getTickProgress()` exactly (verified against the decompiled
	 * client-1.12.35-sources.jar, package net.runelite.client.plugins.prayer) rather than a
	 * simpler [0, 1) fraction: elapsed time since the last GameTick is taken modulo the nominal
	 * 600ms tick length (not clamped - stays a wrapping sawtooth instead of sticking at the
	 * endpoint if a frame renders late), then scaled to [0, PI) so callers can drive the same
	 * `-cos(t)` easing curve core's PrayerBarOverlay/PrayerFlickOverlay use for their own flick
	 * indicators - the sweep should feel identical to theirs, not just exist independently.
	 */
	double tickProgress()
	{
		double elapsedMs = System.currentTimeMillis() - lastTickTimeMs;
		double fraction = (elapsedMs % MS_PER_TICK) / MS_PER_TICK;
		return fraction * Math.PI;
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

	/** The NPC's elemental weakness, or null if it has none. Static per ID - no live client source exists, see CLAUDE.md. */
	NpcWeaknessTable.Weakness npcWeakness(NPC npc)
	{
		return NpcWeaknessTable.getWeakness(npc.getId());
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

	/** Whether npc belongs to a confirmed Ironman group-loot exemption (CoX/ToB/ToA by location, the Gemstone Crab, or COMMUNAL_LOOT_NPC_IDS/NAMES). */
	private boolean isCommunalLootEncounter(NPC npc)
	{
		if (COMMUNAL_LOOT_NPC_IDS.contains(npc.getId()) || GEMSTONE_CRAB_NPC_IDS.contains(npc.getId()))
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
			// isConfirmedDead excluded here too - a corpse mid-death-animation still has an Attack
			// option (hasAttackOption doesn't know it just died), so without this the onGameTick
			// discovery loops below re-track it the moment isConfirmedDead's evict() removes it,
			// since getInteracting() == localPlayer is still true for several more ticks. See CLAUDE.md.
			NPC npc = (NPC) actor;
			return isTrackedNpc(npc) && isAttackableNpc(npc) && !isConfirmedDead(npc);
		}
		if (!(actor instanceof Player))
		{
			return false;
		}
		// isConfirmedDead excluded for the same reason as the NPC branch above - a dying player's
		// getInteracting() reference doesn't clear until despawn either, so without this the
		// discovery loops below would re-track them the moment evict() removes them.
		return (actor == client.getLocalPlayer() ? config.showForSelf() : config.showForPlayers())
			&& isTrackedPlayer((Player) actor)
			&& !isConfirmedDead(actor);
	}

	/** Whether npc is eligible for a bar or a name at all - isAttackableNpc() is the stricter bar-only gate on top of this. Cheap checks first. */
	boolean isTrackedNpc(NPC npc)
	{
		// Widening-only: level 0 but attackable still counts as combat (CoX scaled trash), so nothing
		// that passed before is excluded. CLAUDE.md.
		if (config.onlyShowCombatNpcNames() && npc.getCombatLevel() <= 0 && !isAttackableNpc(npc))
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

	/**
	 * Pure blacklist for other players, mirroring matchesFilter() for NPCs. Text.standardize() rather
	 * than toLowerCase(): every multi-word RSN carries U+00A0 non-breaking spaces, so a user-typed
	 * "Zezima Two" would never match the raw name. See CLAUDE.md.
	 */
	private boolean matchesPlayerFilter(String playerName)
	{
		String filterStr = config.playerFilter().trim();
		if (filterStr.isEmpty() || playerName == null)
		{
			return true;
		}

		if (!filterStr.equals(cachedPlayerFilterString))
		{
			cachedPlayerFilterString = filterStr;
			cachedPlayerPatterns = compilePatterns(filterStr);
		}

		String standardized = Text.standardize(playerName);
		for (Pattern pattern : cachedPlayerPatterns)
		{
			if (pattern.matcher(standardized).matches())
			{
				return false;
			}
		}
		return true;
	}

	/** Whether player is eligible for a bar, a name, or a redrawn overhead icon at all. The local player is never filtered. */
	boolean isTrackedPlayer(Player player)
	{
		return player == client.getLocalPlayer() || matchesPlayerFilter(player.getName());
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
