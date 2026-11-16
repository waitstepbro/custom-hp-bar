package com.customhpbar;

import com.google.common.cache.Cache;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PostHealthBarConfig;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Developer-only: writes everything Custom HP Bar reads, tick by tick, so ReplayHarness can play a fight back
 * headless. Enable it in the plugin list to start a recording, disable it to finish.
 */
@PluginDescriptor(
	name = "Custom HP Bar Recorder",
	description = "Developer tool: records fights for the Custom HP Bar replay harness",
	enabledByDefault = false
)
public class ReplayRecorderPlugin extends Plugin
{
	private static final Logger LOG = LoggerFactory.getLogger(ReplayRecorderPlugin.class);

	// Every var the plugin reads; a new one in the plugin must be added here or it replays as 0.
	static final int[] VARBITS = {
		VarbitID.HPBAR_HUD_BASEHP, VarbitID.HPBAR_HUD_HP, VarbitID.INSIDE_WILDERNESS, VarbitID.IRONMAN,
		VarbitID.MM2_PROGRESS, VarbitID.RAIDS_CHALLENGE_MODE, VarbitID.RAIDS_CLIENT_INDUNGEON,
		VarbitID.STAMINA_ACTIVE, VarbitID.TOA_CLIENT_APMEKEN_LEVEL, VarbitID.TOA_CLIENT_CRONDIS_LEVEL,
		VarbitID.TOA_CLIENT_HET_LEVEL, VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2,
		VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6,
		VarbitID.TOA_CLIENT_P7, VarbitID.TOA_CLIENT_RAID_LEVEL, VarbitID.TOA_CLIENT_SCABARAS_LEVEL,
		VarbitID.YAMA_TRANSITION_PHASE
	};
	static final int[] VARPS = {
		VarPlayerID.DISEASE, VarPlayerID.DROPCANNON, VarPlayerID.OWNEDMCANNON, VarPlayerID.POISON, VarPlayerID.SA_ENERGY
	};
	static final int[] VARCS = {VarClientID.BUFF_BLEED_END, VarClientID.BUFF_ZEBAK_BLEED_END};

	@Inject
	private Client client;

	@Inject
	private SpriteManager spriteManager;

	private Path dir;
	private BufferedWriter out;
	private int[][][] lastChunks;

	@Override
	protected void startUp() throws IOException
	{
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		dir = RuneLite.RUNELITE_DIR.toPath().resolve("custom-hp-bar-recordings").resolve(stamp);
		Files.createDirectories(dir);
		out = Files.newBufferedWriter(dir.resolve("recording.jsonl"), StandardCharsets.UTF_8);
		lastChunks = null;
		LOG.info("Custom HP Bar recording to {}", dir);
	}

	@Override
	protected void shutDown() throws IOException
	{
		BufferedWriter w = out;
		out = null;
		if (w != null)
		{
			w.close();
		}
		dumpSprites();
		LOG.info("Custom HP Bar recording saved to {}", dir);
	}

	/** Copies every sprite the client has loaded this session, so the replay can draw icons and hitsplats. */
	@SuppressWarnings("unchecked")
	private void dumpSprites()
	{
		try
		{
			Field f = SpriteManager.class.getDeclaredField("cache");
			f.setAccessible(true);
			Map<Long, BufferedImage> sprites = ((Cache<Long, BufferedImage>) f.get(spriteManager)).asMap();
			Path spriteDir = Files.createDirectories(dir.resolve("sprites"));
			for (Map.Entry<Long, BufferedImage> e : sprites.entrySet())
			{
				long key = e.getKey();
				String name = (key >>> 32) + "_" + (int) key + ".png";
				ImageIO.write(e.getValue(), "png", spriteDir.resolve(name).toFile());
			}
		}
		catch (ReflectiveOperationException | IOException ex)
		{
			LOG.warn("Sprite dump failed", ex);
		}
	}

	private void write(String type, JsonObject o)
	{
		if (out == null)
		{
			return;
		}
		o.addProperty("t", type);
		o.addProperty("tick", client.getTickCount());
		o.addProperty("cycle", client.getGameCycle());
		try
		{
			out.write(o.toString());
			out.newLine();
		}
		catch (IOException ex)
		{
			LOG.warn("Recording write failed", ex);
		}
	}

	static JsonObject ref(Actor a)
	{
		if (a == null)
		{
			return null;
		}
		JsonObject r = new JsonObject();
		r.addProperty("k", a instanceof NPC ? "n" : "p");
		r.addProperty("i", a instanceof NPC ? ((NPC) a).getIndex() : ((Player) a).getId());
		return r;
	}

	private static void addRef(JsonObject o, String key, Actor a)
	{
		JsonObject r = ref(a);
		o.add(key, r == null ? JsonNull.INSTANCE : r);
	}

	private JsonObject actor(Actor a)
	{
		JsonObject o = ref(a);
		o.addProperty("name", a.getName());
		o.addProperty("lvl", a.getCombatLevel());
		WorldPoint wp = a.getWorldLocation();
		o.addProperty("wx", wp.getX());
		o.addProperty("wy", wp.getY());
		o.addProperty("pl", wp.getPlane());
		LocalPoint lp = a.getLocalLocation();
		o.addProperty("lx", lp.getX());
		o.addProperty("ly", lp.getY());
		o.addProperty("h", a.getLogicalHeight());
		o.addProperty("hr", a.getHealthRatio());
		o.addProperty("hs", a.getHealthScale());
		o.addProperty("anim", a.getAnimation());
		o.addProperty("txt", a.getOverheadText());
		o.addProperty("txtCycle", a.getOverheadCycle());
		o.addProperty("size", a.getWorldArea().getWidth());
		addRef(o, "tgt", a.getInteracting());
		JsonArray spots = new JsonArray();
		if (a.getSpotAnims() != null)
		{
			for (ActorSpotAnim s : a.getSpotAnims())
			{
				spots.add(s.getId());
			}
		}
		o.add("spots", spots);
		if (a instanceof NPC)
		{
			NPC npc = (NPC) a;
			o.addProperty("id", npc.getId());
			NPCComposition c = npc.getTransformedComposition();
			if (c != null)
			{
				o.addProperty("cid", c.getId());
				o.addProperty("follower", c.isFollower());
				o.addProperty("interactible", c.isInteractible());
				JsonArray actions = new JsonArray();
				for (String s : c.getActions())
				{
					actions.add(s);
				}
				o.add("actions", actions);
			}
			JsonArray archives = new JsonArray();
			JsonArray sprites = new JsonArray();
			if (npc.getOverheadArchiveIds() != null)
			{
				for (int i = 0; i < npc.getOverheadArchiveIds().length; i++)
				{
					archives.add(npc.getOverheadArchiveIds()[i]);
					sprites.add(npc.getOverheadSpriteIds()[i]);
				}
			}
			o.add("ohA", archives);
			o.add("ohS", sprites);
		}
		else
		{
			Player p = (Player) a;
			o.addProperty("skull", p.getSkullIcon());
			HeadIcon head = p.getOverheadIcon();
			o.addProperty("head", head == null ? null : head.name());
			o.addProperty("friend", p.isFriend());
			o.addProperty("clan", p.isClanMember());
		}
		return o;
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		WorldView wv = client.getTopLevelWorldView();
		Player me = client.getLocalPlayer();
		if (wv == null || me == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("local", me.getId());
		o.addProperty("plane", wv.getPlane());
		o.addProperty("baseX", wv.getBaseX());
		o.addProperty("baseY", wv.getBaseY());
		o.addProperty("instance", wv.isInstance());
		int[][][] chunks = wv.isInstance() ? wv.getInstanceTemplateChunks() : null;
		if (chunks != null && !java.util.Arrays.deepEquals(chunks, lastChunks))
		{
			o.add("chunks", new com.google.gson.Gson().toJsonTree(chunks));
		}
		lastChunks = chunks;
		o.add("varbits", vars(VARBITS, client::getVarbitValue));
		o.add("varps", vars(VARPS, client::getVarpValue));
		o.add("varcs", vars(VARCS, client::getVarcIntValue));
		JsonObject skills = new JsonObject();
		for (Skill s : new Skill[]{Skill.HITPOINTS, Skill.PRAYER})
		{
			skills.addProperty(s.name(), client.getRealSkillLevel(s) + "/" + client.getBoostedSkillLevel(s));
		}
		o.add("skills", skills);
		o.addProperty("energy", client.getEnergy());
		long prayers = 0;
		for (Prayer p : Prayer.values())
		{
			if (client.isPrayerActive(p))
			{
				prayers |= 1L << p.ordinal();
			}
		}
		o.addProperty("prayers", prayers);
		o.addProperty("scale", client.getScale());
		JsonObject cam = new JsonObject();
		cam.addProperty("x", client.getCameraX());
		cam.addProperty("y", client.getCameraY());
		cam.addProperty("z", client.getCameraZ());
		cam.addProperty("pitch", client.getCameraPitch());
		cam.addProperty("yaw", client.getCameraYaw());
		cam.addProperty("vw", client.getViewportWidth());
		cam.addProperty("vh", client.getViewportHeight());
		cam.addProperty("vx", client.getViewportXOffset());
		cam.addProperty("vy", client.getViewportYOffset());
		o.add("cam", cam);
		addRef(o, "hintNpc", client.getHintArrowNpc());
		Widget hudName = client.getWidget(InterfaceID.HpbarHud.CREATURE_NAME);
		o.addProperty("hudName", hudName == null ? null : hudName.getText());
		Widget hudHp = client.getWidget(InterfaceID.HpbarHud.HP);
		o.addProperty("hudShown", hudHp != null && !hudHp.isHidden());
		JsonArray actors = new JsonArray();
		for (NPC npc : wv.npcs())
		{
			actors.add(actor(npc));
		}
		for (Player p : wv.players())
		{
			actors.add(actor(p));
		}
		o.add("actors", actors);
		write("tick", o);
	}

	private static JsonObject vars(int[] ids, java.util.function.IntUnaryOperator read)
	{
		JsonObject o = new JsonObject();
		for (int id : ids)
		{
			o.addProperty(String.valueOf(id), read.applyAsInt(id));
		}
		return o;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		JsonObject o = new JsonObject();
		addRef(o, "a", e.getActor());
		o.addProperty("type", e.getHitsplat().getHitsplatType());
		o.addProperty("amount", e.getHitsplat().getAmount());
		o.addProperty("disappears", e.getHitsplat().getDisappearsOnGameCycle());
		o.addProperty("hr", e.getActor().getHealthRatio());
		o.addProperty("hs", e.getActor().getHealthScale());
		write("hit", o);
	}

	@Subscribe
	public void onPostHealthBarConfig(PostHealthBarConfig e)
	{
		JsonObject o = new JsonObject();
		o.addProperty("sprite", e.getHealthBarConfig().getHealthBarFrontSpriteId());
		write("hbc", o);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		JsonObject o = actor(e.getNpc());
		write("npcSpawn", o);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		JsonObject o = new JsonObject();
		addRef(o, "a", e.getNpc());
		o.addProperty("hr", e.getNpc().getHealthRatio());
		write("npcDespawn", o);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned e)
	{
		JsonObject o = new JsonObject();
		addRef(o, "a", e.getPlayer());
		write("playerDespawn", o);
	}

	@Subscribe
	public void onNpcChanged(NpcChanged e)
	{
		JsonObject o = actor(e.getNpc());
		o.addProperty("oldId", e.getOld() == null ? -1 : e.getOld().getId());
		write("npcChanged", o);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (e.getActor() == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		addRef(o, "a", e.getActor());
		o.addProperty("anim", e.getActor().getAnimation());
		write("anim", o);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved e)
	{
		Projectile p = e.getProjectile();
		JsonObject o = new JsonObject();
		o.addProperty("key", System.identityHashCode(p));
		o.addProperty("id", p.getId());
		o.addProperty("start", p.getStartCycle());
		WorldPoint src = p.getSourcePoint();
		if (src != null)
		{
			o.addProperty("sx", src.getX());
			o.addProperty("sy", src.getY());
			o.addProperty("sp", src.getPlane());
		}
		addRef(o, "src", p.getSourceActor());
		addRef(o, "dst", p.getTargetActor());
		write("proj", o);
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		JsonObject o = new JsonObject();
		o.addProperty("type", e.getType().name());
		o.addProperty("name", e.getName());
		o.addProperty("msg", e.getMessage());
		write("chat", o);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		JsonObject o = new JsonObject();
		o.addProperty("id", e.getScriptId());
		Widget hudName = client.getWidget(InterfaceID.HpbarHud.CREATURE_NAME);
		o.addProperty("hudName", hudName == null ? null : hudName.getText());
		write("script", o);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		JsonObject o = new JsonObject();
		o.addProperty("state", e.getGameState().name());
		write("gameState", o);
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			lastChunks = null;
		}
	}
}
