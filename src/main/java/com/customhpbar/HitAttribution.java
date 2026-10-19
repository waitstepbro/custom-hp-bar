package com.customhpbar;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Decides whether an other-typed hitsplat on an NPC came from another player. The splat can't say - NPC-dealt
 * damage is DAMAGE_OTHER/BLOCK_OTHER too - so each hit is judged at end of tick, on positive evidence only.
 * See CLAUDE.md ("Loot-taint grey-out").
 */
@Slf4j
class HitAttribution
{
	enum Evidence
	{
		ATTACK,
		PROJECTILE,
		CANNON,
		THRALL,
		SPLASH_GRAPHIC,
		SPLASH_NEARBY
	}

	@Value
	static class Verdict
	{
		NPC npc;
		int hitsplatType;
		int amount;
		/** Evidence found, with a short who/what note each - empty means the hit is not a player's. */
		Map<Evidence, String> evidence;

		boolean isPlayerHit()
		{
			return !evidence.isEmpty();
		}
	}

	@Value
	private static class PendingHit
	{
		NPC npc;
		int hitsplatType;
		int amount;
	}

	@Value
	private static class RecentProjectile
	{
		int tick;
		String shooter;
		boolean cannonball;
	}

	/** How long after a player's attack its hit can still land - covers the longest projectile flights. */
	private static final int ATTACK_MEMORY_TICKS = 6;
	private static final int PROJECTILE_MEMORY_TICKS = 4;
	/** Barrage, burst and chinchompas hit a 3x3 around their target; one more tile for large NPCs. */
	private static final int SPLASH_RADIUS = 2;
	private static final int SHOOTER_RADIUS = 1;
	private static final int OWN_FOLLOWER_SPAWN_RADIUS = 3;
	/** VarPlayerID.DROPCANNON while a cannon is set up, as core's CannonPlugin reads it. */
	private static final int CANNON_PLACED = 4;

	private static final Set<Integer> CANNONBALL_PROJECTILES = new HashSet<>(Arrays.asList(
		SpotanimID.CANNONBALL_TRAVEL, SpotanimID.CANNONBALL_TRAVEL_GRANITE, SpotanimID.CANNONBALL_TRAVEL_GRANITE_LEAGUE
	));

	private final Client client;

	/** Per other player, the last tick they animated while targeting each NPC. */
	private final Map<Player, Map<NPC, Integer>> recentAttacks = new ConcurrentHashMap<>();
	private final Map<NPC, List<RecentProjectile>> recentProjectiles = new ConcurrentHashMap<>();
	/** Projectile -> start cycle, so a pooled object reused for a new shot still counts as new. */
	private final Map<Projectile, Integer> seenProjectiles = Collections.synchronizedMap(new WeakHashMap<>());
	/** Our own thralls and pets - they fight whatever we fight, so they are never evidence of another player. */
	private final Set<NPC> ownFollowers = ConcurrentHashMap.newKeySet();
	private final Queue<PendingHit> pendingHits = new ConcurrentLinkedQueue<>();
	private final Set<Actor> hitThisTick = ConcurrentHashMap.newKeySet();

	@Inject
	HitAttribution(Client client)
	{
		this.client = client;
	}

	void reset()
	{
		recentAttacks.clear();
		recentProjectiles.clear();
		seenProjectiles.clear();
		ownFollowers.clear();
		pendingHits.clear();
		hitThisTick.clear();
	}

	/** Queues an other-typed hit on an NPC for judgeTick(), and notes every hit for the splash check. */
	void onHitsplatApplied(Actor actor, Hitsplat hitsplat)
	{
		hitThisTick.add(actor);
		if (actor instanceof NPC
			&& CustomHpBarPlugin.OTHER_PLAYER_DAMAGE_HITSPLATS.contains(hitsplat.getHitsplatType()))
		{
			pendingHits.add(new PendingHit((NPC) actor, hitsplat.getHitsplatType(), hitsplat.getAmount()));
		}
	}

	void onAnimationChanged(Actor actor)
	{
		if (actor instanceof Player && actor != client.getLocalPlayer() && actor.getAnimation() != -1
			&& actor.getInteracting() instanceof NPC)
		{
			recordAttack((Player) actor, (NPC) actor.getInteracting(), client.getTickCount());
		}
	}

	void onProjectileMoved(Projectile projectile)
	{
		Integer startCycle = seenProjectiles.put(projectile, projectile.getStartCycle());
		if ((startCycle != null && startCycle == projectile.getStartCycle())
			|| !(projectile.getTargetActor() instanceof NPC))
		{
			return;
		}

		boolean cannonball = CANNONBALL_PROJECTILES.contains(projectile.getId());
		if (cannonball && isFromOwnCannon(projectile.getSourcePoint()))
		{
			return;
		}
		String shooter = cannonball ? String.valueOf(projectile.getSourcePoint()) : otherPlayerShooter(projectile);
		if (shooter != null)
		{
			recentProjectiles.computeIfAbsent((NPC) projectile.getTargetActor(), k -> new ArrayList<>())
				.add(new RecentProjectile(client.getTickCount(), shooter, cannonball));
		}
	}

	/** A thrall or pet appearing right beside us is ours - they spawn next to their owner. */
	void onNpcSpawned(NPC npc)
	{
		Player me = client.getLocalPlayer();
		if (me != null && isFollowerLike(npc)
			&& npc.getWorldLocation().distanceTo(me.getWorldLocation()) <= OWN_FOLLOWER_SPAWN_RADIUS)
		{
			addOwnFollower(npc);
		}
	}

	void onNpcDespawned(NPC npc)
	{
		ownFollowers.remove(npc);
		recentProjectiles.remove(npc);
		for (Map<NPC, Integer> attacks : recentAttacks.values())
		{
			attacks.remove(npc);
		}
	}

	void onPlayerDespawned(Player player)
	{
		recentAttacks.remove(player);
	}

	/** Judges every hit queued this tick. Call once per tick from onGameTick, when all its evidence is in. */
	List<Verdict> judgeTick()
	{
		List<Verdict> verdicts = new ArrayList<>();
		Player me = client.getLocalPlayer();
		if (me != null)
		{
			int tick = client.getTickCount();
			WorldView view = client.getTopLevelWorldView();
			sampleTick(view, me, tick);
			for (PendingHit hit : pendingHits)
			{
				verdicts.add(new Verdict(hit.getNpc(), hit.getHitsplatType(), hit.getAmount(),
					evidenceFor(hit.getNpc(), view, me, tick)));
			}
			prune(tick);
		}
		pendingHits.clear();
		hitThisTick.clear();
		return verdicts;
	}

	/** Backs up onAnimationChanged, whose interacting can lag the animation, and spots followers already out. */
	private void sampleTick(WorldView view, Player me, int tick)
	{
		for (Player player : view.players())
		{
			if (player != null && player != me && player.getAnimation() != -1 && player.getInteracting() instanceof NPC)
			{
				recordAttack(player, (NPC) player.getInteracting(), tick);
			}
		}
		for (NPC npc : view.npcs())
		{
			if (npc != null && npc.getInteracting() == me && isFollowerLike(npc) && !ownFollowers.contains(npc))
			{
				addOwnFollower(npc);
			}
		}
	}

	private Map<Evidence, String> evidenceFor(NPC victim, WorldView view, Player me, int tick)
	{
		Map<Evidence, String> evidence = new EnumMap<>(Evidence.class);
		for (Map.Entry<Player, Map<NPC, Integer>> attacks : recentAttacks.entrySet())
		{
			Integer attackTick = attacks.getValue().get(victim);
			if (attackTick != null && tick - attackTick <= ATTACK_MEMORY_TICKS)
			{
				evidence.put(Evidence.ATTACK, attacks.getKey().getName() + " -" + (tick - attackTick));
			}
		}

		for (RecentProjectile projectile : recentProjectiles.getOrDefault(victim, Collections.emptyList()))
		{
			if (tick - projectile.getTick() <= PROJECTILE_MEMORY_TICKS)
			{
				Evidence kind = projectile.isCannonball() ? Evidence.CANNON : Evidence.PROJECTILE;
				evidence.put(kind, projectile.getShooter());
			}
		}

		// Our own thrall fights our target, so a thrall on our target says nothing about anyone else.
		if (victim != me.getInteracting())
		{
			for (NPC other : view.npcs())
			{
				if (other != null && other != victim && other.getInteracting() == victim && isFollowerLike(other)
					&& !ownFollowers.contains(other))
				{
					evidence.put(Evidence.THRALL, describe(other));
				}
			}
		}

		Set<Integer> victimGraphics = spotAnims(victim);
		boolean fightingNpc = isFightingNpc(victim, view);
		for (Map.Entry<Player, Map<NPC, Integer>> attacks : recentAttacks.entrySet())
		{
			for (Map.Entry<NPC, Integer> attack : attacks.getValue().entrySet())
			{
				NPC target = attack.getKey();
				if (target == victim || tick - attack.getValue() > ATTACK_MEMORY_TICKS
					|| distance(victim.getWorldArea(), target.getWorldArea()) > SPLASH_RADIUS)
				{
					continue;
				}
				String note = attacks.getKey().getName() + " on " + describe(target);
				if (!Collections.disjoint(victimGraphics, spotAnims(target)))
				{
					evidence.put(Evidence.SPLASH_GRAPHIC, note);
				}
				else if (hitThisTick.contains(target) && !fightingNpc)
				{
					evidence.put(Evidence.SPLASH_NEARBY, note);
				}
			}
		}
		return evidence;
	}

	private void prune(int tick)
	{
		for (Map<NPC, Integer> attacks : recentAttacks.values())
		{
			attacks.values().removeIf(attackTick -> tick - attackTick > ATTACK_MEMORY_TICKS);
		}
		recentAttacks.values().removeIf(Map::isEmpty);
		for (List<RecentProjectile> projectiles : recentProjectiles.values())
		{
			projectiles.removeIf(projectile -> tick - projectile.getTick() > PROJECTILE_MEMORY_TICKS);
		}
		recentProjectiles.values().removeIf(List::isEmpty);
	}

	private void recordAttack(Player player, NPC target, int tick)
	{
		recentAttacks.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(target, tick);
	}

	private void addOwnFollower(NPC npc)
	{
		ownFollowers.add(npc);
		log.debug("Own follower: {}", describe(npc));
	}

	/** Whether a non-follower NPC is fighting npc, in either direction. */
	private static boolean isFightingNpc(NPC npc, WorldView view)
	{
		Actor target = npc.getInteracting();
		if (target instanceof NPC && !isFollowerLike((NPC) target))
		{
			return true;
		}
		for (NPC other : view.npcs())
		{
			if (other != null && other != npc && other.getInteracting() == npc && !isFollowerLike(other))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The other player who fired projectile: its source actor, else whoever stands on its source tile. NPCs
	 * count as candidates too, so a monster beside a player never has its projectile pinned on them.
	 */
	private String otherPlayerShooter(Projectile projectile)
	{
		Player me = client.getLocalPlayer();
		Actor source = projectile.getSourceActor();
		if (source != null)
		{
			return source instanceof Player && source != me ? source.getName() : null;
		}
		WorldPoint from = projectile.getSourcePoint();
		if (from == null)
		{
			return null;
		}

		Actor nearest = null;
		int nearestDist = Integer.MAX_VALUE;
		WorldView view = client.getTopLevelWorldView();
		for (NPC npc : view.npcs())
		{
			int dist = npc == null ? Integer.MAX_VALUE : distance(npc.getWorldArea(), from);
			if (dist < nearestDist)
			{
				nearest = npc;
				nearestDist = dist;
			}
		}
		for (Player player : view.players())
		{
			int dist = player == null ? Integer.MAX_VALUE : player.getWorldLocation().distanceTo(from);
			if (dist < nearestDist)
			{
				nearest = player;
				nearestDist = dist;
			}
		}
		return nearest instanceof Player && nearest != me && nearestDist <= SHOOTER_RADIUS ? nearest.getName() : null;
	}

	private boolean isFromOwnCannon(WorldPoint from)
	{
		if (from == null || client.getVarpValue(VarPlayerID.DROPCANNON) != CANNON_PLACED)
		{
			return false;
		}
		WorldPoint cannon = WorldPoint.fromCoord(client.getVarpValue(VarPlayerID.OWNEDMCANNON));
		return distance(new WorldArea(cannon.getX(), cannon.getY(), 3, 3, cannon.getPlane()), from) <= 1;
	}

	/** Thralls and pets: followers, or NPCs the game won't let anyone interact with at all. */
	private static boolean isFollowerLike(NPC npc)
	{
		NPCComposition composition = npc.getTransformedComposition();
		return composition != null && (composition.isFollower() || !composition.isInteractible());
	}

	private static Set<Integer> spotAnims(Actor actor)
	{
		Set<Integer> ids = new HashSet<>();
		if (actor.getSpotAnims() != null)
		{
			for (ActorSpotAnim spot : actor.getSpotAnims())
			{
				ids.add(spot.getId());
			}
		}
		return ids;
	}

	private static int distance(WorldArea area, WorldArea other)
	{
		return area == null || other == null ? Integer.MAX_VALUE : area.distanceTo(other);
	}

	private static int distance(WorldArea area, WorldPoint point)
	{
		return area == null || point == null ? Integer.MAX_VALUE : area.distanceTo(point);
	}

	private static String describe(NPC npc)
	{
		return npc.getName() + "#" + npc.getId() + "@" + npc.getIndex();
	}
}
