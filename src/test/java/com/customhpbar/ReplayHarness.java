package com.customhpbar;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
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
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;

/**
 * Plays a ReplayRecorderPlugin recording into the real plugin and overlay against a fake client, and writes
 * one PNG per game tick. Deterministic: the plugin's clock and the game cycle are simulated.
 *
 * <p>Usage: ReplayHarness &lt;recording dir&gt; &lt;out dir&gt; [--config file.properties] [--from tick] [--to tick]
 * [--frames n]. Config keys are CustomHpBarConfig method names; unset keys use the plugin defaults.
 */
public class ReplayHarness
{
	static final int W = 800;
	static final int H = 600;
	private static final int TICK_MS = 600;
	private static final int CYCLES_PER_TICK = 30;
	// Fallback camera for recordings without one: pitch in 14-bit units (16384 per circle), distance from player.
	private static final int CAMERA_PITCH = 2400;
	private static final int CAMERA_DISTANCE = 1400;

	private final Map<String, Act> actors = new LinkedHashMap<>();
	private final Map<Integer, Projectile> projectiles = new HashMap<>();
	private final Set<String> unknownCalls = new TreeSet<>();

	private JsonObject g = new JsonObject();
	private int[][][] chunks;
	int tick;
	private int cycle;
	private long clockMs = 1_000_000L;

	final Client client;
	final WorldView worldView;
	final CustomHpBarPlugin plugin;
	final CustomHpBarOverlay overlay;
	private final ClientThread clientThread;
	private final Method drainClientThread;

	/** One recorded actor: the proxy the plugin holds, and its latest recorded state. */
	private final class Act
	{
		final boolean npc;
		final Actor proxy;
		JsonObject s;

		Act(JsonObject s)
		{
			this.npc = "n".equals(s.get("k").getAsString());
			this.s = s;
			this.proxy = (Actor) proxy(npc ? NPC.class : Player.class, this::call);
		}

		Object call(Object self, Method m, Object[] a)
		{
			switch (m.getName())
			{
				case "getName":
					return str(s, "name");
				case "getCombatLevel":
					return s.get("lvl").getAsInt();
				case "getId":
					return npc ? s.get("id").getAsInt() : s.get("i").getAsInt();
				case "getIndex":
					return s.get("i").getAsInt();
				case "getWorldLocation":
					return worldPoint();
				case "getLocalLocation":
					return new LocalPoint(s.get("lx").getAsInt(), s.get("ly").getAsInt(), worldView);
				case "getWorldArea":
					return new WorldArea(worldPoint(), size(), size());
				case "getWorldView":
					return worldView;
				case "getLogicalHeight":
					return s.get("h").getAsInt();
				case "getHealthRatio":
					return s.get("hr").getAsInt();
				case "getHealthScale":
					return s.get("hs").getAsInt();
				case "getAnimation":
					return s.get("anim").getAsInt();
				case "getOverheadText":
					return str(s, "txt");
				case "getOverheadCycle":
					return s.get("txtCycle").getAsInt();
				case "getInteracting":
					return resolve(s.get("tgt"));
				case "getSpotAnims":
					return spotAnims();
				case "hasSpotAnim":
					return spotIds().contains((Integer) a[0]);
				case "getTransformedComposition":
				case "getComposition":
					return s.has("cid") ? proxy(NPCComposition.class, this::composition) : null;
				case "getOverheadArchiveIds":
					return ints(s.getAsJsonArray("ohA"));
				case "getOverheadSpriteIds":
				{
					int[] v = ints(s.getAsJsonArray("ohS"));
					short[] out = new short[v.length];
					for (int i = 0; i < v.length; i++)
					{
						out[i] = (short) v[i];
					}
					return out;
				}
				case "getSkullIcon":
					return s.get("skull").getAsInt();
				case "getOverheadIcon":
					return s.get("head").isJsonNull() ? null : HeadIcon.valueOf(s.get("head").getAsString());
				case "isFriend":
					return s.get("friend").getAsBoolean();
				case "isClanMember":
					return s.get("clan").getAsBoolean();
				case "getCanvasTextLocation":
					return Perspective.getCanvasTextLocation(client, (Graphics2D) a[0],
						(LocalPoint) call(self, method("getLocalLocation"), null), (String) a[1], (Integer) a[2]);
				case "toString":
					return s.get("k").getAsString() + s.get("i") + " " + str(s, "name");
				default:
					return unknown(npc ? "NPC" : "Player", m);
			}
		}

		Object composition(Object self, Method m, Object[] a)
		{
			switch (m.getName())
			{
				case "getId":
					return s.get("cid").getAsInt();
				case "getName":
					return str(s, "name");
				case "getCombatLevel":
					return s.get("lvl").getAsInt();
				case "getSize":
					return size();
				case "isFollower":
					return s.get("follower").getAsBoolean();
				case "isInteractible":
					return s.get("interactible").getAsBoolean();
				case "getActions":
				{
					JsonArray arr = s.getAsJsonArray("actions");
					String[] out = new String[arr.size()];
					for (int i = 0; i < out.length; i++)
					{
						out[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
					}
					return out;
				}
				default:
					return unknown("NPCComposition", m);
			}
		}

		WorldPoint worldPoint()
		{
			return new WorldPoint(s.get("wx").getAsInt(), s.get("wy").getAsInt(), s.get("pl").getAsInt());
		}

		int size()
		{
			return s.get("size").getAsInt();
		}

		Set<Integer> spotIds()
		{
			Set<Integer> ids = new HashSet<>();
			s.getAsJsonArray("spots").forEach(e -> ids.add(e.getAsInt()));
			return ids;
		}

		Object spotAnims()
		{
			List<ActorSpotAnim> list = new ArrayList<>();
			for (int id : spotIds())
			{
				list.add((ActorSpotAnim) proxy(ActorSpotAnim.class, (p, m, a) ->
					"getId".equals(m.getName()) ? id : unknown("ActorSpotAnim", m)));
			}
			return proxy(IterableHashTable.class, (p, m, a) ->
				"iterator".equals(m.getName()) ? list.iterator() : unknown("IterableHashTable", m));
		}
	}

	ReplayHarness(Properties overrides) throws ReflectiveOperationException
	{
		client = (Client) proxy(Client.class, this::clientCall);
		worldView = (WorldView) proxy(WorldView.class, this::worldViewCall);

		clientThread = new ClientThread();
		set(clientThread, "client", client);
		drainClientThread = ClientThread.class.getDeclaredMethod("invoke");
		drainClientThread.setAccessible(true);

		SpriteManager sprites = allocate(SpriteManager.class);
		Cache<Long, BufferedImage> cache = CacheBuilder.newBuilder().build();
		set(sprites, "client", client);
		set(sprites, "clientThread", clientThread);
		set(sprites, "cache", cache);
		spriteCache = cache;

		CustomHpBarConfig config = (CustomHpBarConfig) proxy(CustomHpBarConfig.class, (p, m, a) ->
		{
			String v = overrides.getProperty(m.getName());
			return v != null ? convert(v, m.getReturnType()) : invokeDefault(p, m, a);
		});

		plugin = new CustomHpBarPlugin();
		plugin.clock = () -> clockMs;
		overlay = new CustomHpBarOverlay(plugin, config, client, sprites,
			(ItemStatChangesService) proxy(ItemStatChangesService.class, (p, m, a) -> null),
			allocate(PartyService.class));
		set(plugin, "client", client);
		set(plugin, "clientThread", clientThread);
		set(plugin, "overlay", overlay);
		set(plugin, "config", config);
		set(plugin, "hitAttribution", new HitAttribution(client));
	}

	private final Cache<Long, BufferedImage> spriteCache;

	void loadSprites(Path dir) throws IOException
	{
		if (!Files.isDirectory(dir))
		{
			return;
		}
		try (Stream<Path> files = Files.list(dir))
		{
			for (Path f : (Iterable<Path>) files::iterator)
			{
				String[] parts = f.getFileName().toString().replace(".png", "").split("_");
				long key = ((long) Integer.parseInt(parts[0]) << 32) | (Integer.parseInt(parts[1]) & 0xFFFFFFFFL);
				spriteCache.put(key, ImageIO.read(f.toFile()));
			}
		}
	}

	// ---- client fakes ----

	private Object clientCall(Object self, Method m, Object[] a)
	{
		switch (m.getName())
		{
			case "getLocalPlayer":
				return actorFor("p" + g.get("local").getAsInt());
			case "getTickCount":
				return tick;
			case "getGameCycle":
				return cycle;
			case "getGameState":
				return GameState.LOGGED_IN;
			case "isClientThread":
				return true;
			case "getTopLevelWorldView":
			case "getWorldView":
				return worldView;
			case "getVarbitValue":
				return varIn("varbits", (Integer) a[0]);
			case "getVarpValue":
				return varIn("varps", (Integer) a[0]);
			case "getVarcIntValue":
				return varIn("varcs", (Integer) a[0]);
			case "getRealSkillLevel":
			case "getBoostedSkillLevel":
			{
				JsonElement e = g.has("skills") ? g.getAsJsonObject("skills").get(((Skill) a[0]).name()) : null;
				if (e == null)
				{
					return 1;
				}
				String[] rb = e.getAsString().split("/");
				return Integer.parseInt(rb["getRealSkillLevel".equals(m.getName()) ? 0 : 1]);
			}
			case "getEnergy":
				return intOr(g, "energy", 10000);
			case "isPrayerActive":
				return (longOr(g, "prayers") & (1L << ((Prayer) a[0]).ordinal())) != 0;
			case "getScale":
				return intOr(g, "scale", 512);
			case "getHintArrowNpc":
				return resolve(g.get("hintNpc"));
			case "getWidget":
				return hudWidget(a.length == 1 ? (Integer) a[0] : -1);
			case "getSpriteOverrides":
				return spriteOverrides;
			case "isMenuOpen":
			case "isGpu":
				return false;
			case "getMenu":
				return proxy(Menu.class, (p, mm, aa) ->
					"getMenuEntries".equals(mm.getName()) ? new MenuEntry[0] : unknown("Menu", mm));
			case "getViewportWidth":
			case "getViewportHeight":
			case "getViewportXOffset":
			case "getViewportYOffset":
			case "getCameraPitch":
			case "getCameraYaw":
			case "getCameraX":
			case "getCameraY":
			case "getCameraZ":
				return camera(m.getName());
			case "getCanvasWidth":
				return camera("getViewportXOffset") + camera("getViewportWidth");
			case "getCanvasHeight":
				return camera("getViewportYOffset") + camera("getViewportHeight");
			case "resetHealthBarCaches":
				return null;
			default:
				return unknown("Client", m);
		}
	}

	private final Map<Integer, Object> spriteOverrides = new HashMap<>();

	/** The recorded camera and viewport, or a fixed view behind the local player for hand-written recordings. */
	private int camera(String getter)
	{
		String key = getter.replace("getCamera", "").replace("getViewport", "v").toLowerCase();
		key = key.equals("vwidth") ? "vw" : key.equals("vheight") ? "vh" : key.equals("vxoffset") ? "vx"
			: key.equals("vyoffset") ? "vy" : key;
		if (g.has("cam"))
		{
			return g.getAsJsonObject("cam").get(key).getAsInt();
		}
		switch (key)
		{
			case "vw":
				return W;
			case "vh":
				return H;
			case "pitch":
				return CAMERA_PITCH;
			case "x":
				return localPlayerLocal()[0];
			case "y":
				return localPlayerLocal()[1] - (int) (CAMERA_DISTANCE * Math.cos(pitchRadians()));
			case "z":
				return -(int) (CAMERA_DISTANCE * Math.sin(pitchRadians()));
			default:
				return 0;
		}
	}

	private Object hudWidget(int id)
	{
		if (id == InterfaceID.HpbarHud.CREATURE_NAME && g.has("hudName") && !g.get("hudName").isJsonNull())
		{
			String text = g.get("hudName").getAsString();
			return proxy(Widget.class, (p, m, a) -> "getText".equals(m.getName()) ? text
				: "isHidden".equals(m.getName()) ? false : unknown("Widget", m));
		}
		if (id == InterfaceID.HpbarHud.HP && g.has("hudShown") && g.get("hudShown").getAsBoolean())
		{
			return proxy(Widget.class, (p, m, a) -> "isHidden".equals(m.getName()) ? false : unknown("Widget", m));
		}
		return null;
	}

	private Object worldViewCall(Object self, Method m, Object[] a)
	{
		switch (m.getName())
		{
			case "npcs":
				return actorSet(true);
			case "players":
				return actorSet(false);
			case "worldEntities":
				return proxy(IndexedObjectSet.class, (p, mm, aa) -> "iterator".equals(mm.getName())
					? new ArrayList<>().iterator() : null);
			case "getId":
				return WorldView.TOPLEVEL;
			case "getPlane":
				return intOr(g, "plane", 0);
			case "getBaseX":
				return intOr(g, "baseX", 0);
			case "getBaseY":
				return intOr(g, "baseY", 0);
			case "isTopLevel":
				return true;
			case "isInstance":
				return g.has("instance") && g.get("instance").getAsBoolean();
			case "getInstanceTemplateChunks":
				return chunks;
			case "getSizeX":
			case "getSizeY":
				return Perspective.SCENE_SIZE;
			case "getTileHeights":
				return TILE_HEIGHTS;
			case "getTileSettings":
				return TILE_SETTINGS;
			case "getTileHeight":
				return 0;
			default:
				return unknown("WorldView", m);
		}
	}

	private static final int[][][] TILE_HEIGHTS = new int[4][Perspective.SCENE_SIZE + 1][Perspective.SCENE_SIZE + 1];
	private static final byte[][][] TILE_SETTINGS = new byte[4][Perspective.SCENE_SIZE][Perspective.SCENE_SIZE];

	private Object actorSet(boolean npcs)
	{
		List<Actor> list = new ArrayList<>();
		for (Act act : actors.values())
		{
			if (act.npc == npcs)
			{
				list.add(act.proxy);
			}
		}
		return proxy(IndexedObjectSet.class, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "iterator":
					return list.iterator();
				case "byIndex":
					return actorFor((npcs ? "n" : "p") + a[0]);
				default:
					return invokeDefault(p, m, a);
			}
		});
	}

	private int[] localPlayerLocal()
	{
		Act me = actors.get("p" + intOr(g, "local", -1));
		return me == null ? new int[]{0, 0} : new int[]{me.s.get("lx").getAsInt(), me.s.get("ly").getAsInt()};
	}

	private static double pitchRadians()
	{
		return CAMERA_PITCH * Math.PI * 2 / 16384;
	}

	// ---- recording playback ----

	private Actor actorFor(String key)
	{
		Act act = actors.get(key);
		return act == null ? null : act.proxy;
	}

	private Actor resolve(JsonElement ref)
	{
		if (ref == null || ref.isJsonNull())
		{
			return null;
		}
		JsonObject r = ref.getAsJsonObject();
		return actorFor(r.get("k").getAsString() + r.get("i").getAsInt());
	}

	private static String key(JsonObject o)
	{
		return o.get("k").getAsString() + o.get("i").getAsInt();
	}

	/** Updates or creates an actor; a new proxy only when nothing holds that key, so identity survives. */
	private Act upsert(JsonObject s)
	{
		Act act = actors.get(key(s));
		if (act == null)
		{
			act = new Act(s);
			actors.put(key(s), act);
		}
		act.s = s;
		return act;
	}

	/** Applies one line of the recording. Returns true for a tick line, after which a frame should be drawn. */
	boolean apply(JsonObject o)
	{
		String type = o.get("t").getAsString();
		tick = o.get("tick").getAsInt();
		if (!"tick".equals(type))
		{
			cycle = o.get("cycle").getAsInt();
		}
		switch (type)
		{
			case "tick":
				applyTick(o);
				return true;
			case "hit":
			{
				Act act = actors.get(key(o.getAsJsonObject("a")));
				if (act == null)
				{
					return false;
				}
				act.s.add("hr", o.get("hr"));
				act.s.add("hs", o.get("hs"));
				int hitType = o.get("type").getAsInt();
				int amount = o.get("amount").getAsInt();
				int disappears = o.get("disappears").getAsInt();
				Hitsplat hs = (Hitsplat) proxy(Hitsplat.class, (p, m, a) ->
				{
					switch (m.getName())
					{
						case "getHitsplatType":
							return hitType;
						case "getAmount":
							return amount;
						case "getDisappearsOnGameCycle":
							return disappears;
						default:
							return invokeDefault(p, m, a);
					}
				});
				HitsplatApplied e = new HitsplatApplied();
				e.setActor(act.proxy);
				e.setHitsplat(hs);
				plugin.onHitsplatApplied(e);
				break;
			}
			case "npcSpawn":
				plugin.onNpcSpawned(new NpcSpawned((NPC) upsert(o).proxy));
				break;
			case "npcDespawn":
			{
				Act act = actors.get(key(o.getAsJsonObject("a")));
				if (act != null)
				{
					act.s.add("hr", o.get("hr"));
					plugin.onNpcDespawned(new NpcDespawned((NPC) act.proxy));
					actors.remove(key(o.getAsJsonObject("a")));
				}
				break;
			}
			case "playerDespawn":
			{
				Act act = actors.remove(key(o.getAsJsonObject("a")));
				if (act != null)
				{
					plugin.onPlayerDespawned(new PlayerDespawned((Player) act.proxy));
				}
				break;
			}
			case "npcChanged":
			{
				int oldId = o.get("oldId").getAsInt();
				NPCComposition old = (NPCComposition) proxy(NPCComposition.class, (p, m, a) ->
					"getId".equals(m.getName()) ? oldId : unknown("NPCComposition(old)", m));
				plugin.onNpcChanged(new NpcChanged((NPC) upsert(o).proxy, old));
				break;
			}
			case "anim":
			{
				Act act = actors.get(key(o.getAsJsonObject("a")));
				if (act != null)
				{
					act.s.add("anim", o.get("anim"));
					AnimationChanged e = new AnimationChanged();
					e.setActor(act.proxy);
					plugin.onAnimationChanged(e);
				}
				break;
			}
			case "proj":
			{
				Projectile p = projectiles.computeIfAbsent(o.get("key").getAsInt(), k -> projectile(o));
				ProjectileMoved e = new ProjectileMoved();
				e.setProjectile(p);
				plugin.onProjectileMoved(e);
				break;
			}
			case "chat":
				plugin.onChatMessage(new ChatMessage(null, ChatMessageType.valueOf(o.get("type").getAsString()),
					str(o, "name"), str(o, "msg"), null, 0));
				break;
			case "script":
				g.add("hudName", o.get("hudName"));
				plugin.onScriptPostFired(new ScriptPostFired(o.get("id").getAsInt()));
				break;
			case "gameState":
			{
				GameStateChanged e = new GameStateChanged();
				e.setGameState(GameState.valueOf(o.get("state").getAsString()));
				plugin.onGameStateChanged(e);
				break;
			}
			default:
				break;
		}
		drain();
		return false;
	}

	private Projectile projectile(JsonObject o)
	{
		WorldPoint src = o.has("sx") ? new WorldPoint(o.get("sx").getAsInt(), o.get("sy").getAsInt(),
			o.get("sp").getAsInt()) : null;
		JsonElement srcRef = o.get("src");
		JsonElement dstRef = o.get("dst");
		int id = o.get("id").getAsInt();
		int start = o.get("start").getAsInt();
		return (Projectile) proxy(Projectile.class, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getId":
					return id;
				case "getStartCycle":
					return start;
				case "getSourcePoint":
					return src;
				case "getSourceActor":
					return resolve(srcRef);
				case "getTargetActor":
					return resolve(dstRef);
				default:
					return unknown("Projectile", m);
			}
		});
	}

	private void applyTick(JsonObject o)
	{
		cycle = o.get("cycle").getAsInt();
		clockMs += TICK_MS;
		JsonObject prev = g;
		g = o;
		if (o.has("chunks"))
		{
			chunks = new com.google.gson.Gson().fromJson(o.get("chunks"), int[][][].class);
		}
		else if (!o.get("instance").getAsBoolean())
		{
			chunks = null;
		}
		if (!o.has("hudName") && prev.has("hudName"))
		{
			o.add("hudName", prev.get("hudName"));
		}
		Set<String> present = new HashSet<>();
		for (JsonElement e : o.getAsJsonArray("actors"))
		{
			present.add(key(upsert(e.getAsJsonObject()).s));
		}
		actors.keySet().retainAll(present);
		plugin.onGameTick(new GameTick());
		drain();
	}

	private void drain()
	{
		try
		{
			drainClientThread.invoke(clientThread);
		}
		catch (ReflectiveOperationException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	/** Draws the overlay over a plain scene marking each actor's footprint, at an offset into the tick. */
	BufferedImage render(int frame, int frames)
	{
		long tickClock = clockMs;
		int tickCycle = cycle;
		clockMs = tickClock + (long) TICK_MS * frame / frames;
		cycle = tickCycle + CYCLES_PER_TICK * frame / frames;
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gfx = img.createGraphics();
		gfx.setColor(new Color(40, 44, 40));
		gfx.fillRect(0, 0, width, height);
		gfx.setColor(new Color(70, 76, 70));
		for (Act act : actors.values())
		{
			LocalPoint lp = new LocalPoint(act.s.get("lx").getAsInt(), act.s.get("ly").getAsInt(), worldView);
			int half = act.size() * Perspective.LOCAL_TILE_SIZE / 2;
			Polygon poly = new Polygon();
			for (int[] c : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}})
			{
				net.runelite.api.Point p = Perspective.localToCanvas(client,
					lp.getX() + c[0] * half, lp.getY() + c[1] * half, 0);
				if (p != null)
				{
					poly.addPoint(p.getX(), p.getY());
				}
			}
			gfx.drawPolygon(poly);
		}
		overlay.render(gfx);
		gfx.dispose();
		clockMs = tickClock;
		cycle = tickCycle;
		return img;
	}

	// ---- reflection helpers ----

	interface Handler
	{
		Object call(Object proxy, Method m, Object[] args) throws Throwable;
	}

	static Object proxy(Class<?> iface, Handler h)
	{
		InvocationHandler ih = (p, m, a) ->
		{
			if (m.getDeclaringClass() == Object.class)
			{
				switch (m.getName())
				{
					case "equals":
						return p == a[0];
					case "hashCode":
						return System.identityHashCode(p);
					default:
						break;
				}
			}
			return h.call(p, m, a == null ? new Object[0] : a);
		};
		return Proxy.newProxyInstance(ReplayHarness.class.getClassLoader(), new Class<?>[]{iface}, ih);
	}

	static Object invokeDefault(Object proxy, Method m, Object[] a) throws Throwable
	{
		if (!m.isDefault())
		{
			return zero(m.getReturnType());
		}
		Class<?> decl = m.getDeclaringClass();
		return MethodHandles.privateLookupIn(decl, MethodHandles.lookup())
			.unreflectSpecial(m, decl).bindTo(proxy).invokeWithArguments(a);
	}

	private Object unknown(String type, Method m)
	{
		if (unknownCalls.add(type + "." + m.getName()))
		{
			System.err.println("replay: unfaked call " + type + "." + m.getName() + " -> default");
		}
		return zero(m.getReturnType());
	}

	private static Object zero(Class<?> t)
	{
		if (t == boolean.class)
		{
			return false;
		}
		if (t == int.class || t == short.class || t == byte.class || t == char.class)
		{
			return t == int.class ? (Object) 0 : t == short.class ? (Object) (short) 0
				: t == byte.class ? (Object) (byte) 0 : (Object) (char) 0;
		}
		if (t == long.class)
		{
			return 0L;
		}
		if (t == float.class)
		{
			return 0f;
		}
		if (t == double.class)
		{
			return 0d;
		}
		return null;
	}

	private static Object convert(String v, Class<?> t)
	{
		v = v.trim();
		if (t == boolean.class)
		{
			return Boolean.parseBoolean(v);
		}
		if (t == int.class)
		{
			return Integer.parseInt(v);
		}
		if (t == double.class)
		{
			return Double.parseDouble(v);
		}
		if (t == String.class)
		{
			return v;
		}
		if (t == Color.class)
		{
			long argb = Long.parseLong(v.replace("#", ""), 16);
			return new Color((int) argb, v.replace("#", "").length() > 6);
		}
		if (t.isEnum())
		{
			for (Object c : t.getEnumConstants())
			{
				if (((Enum<?>) c).name().equalsIgnoreCase(v))
				{
					return c;
				}
			}
		}
		throw new IllegalArgumentException("Can't convert '" + v + "' to " + t.getSimpleName());
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocate(Class<T> c) throws ReflectiveOperationException
	{
		Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
		Field f = unsafeClass.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return (T) unsafeClass.getMethod("allocateInstance", Class.class).invoke(f.get(null), c);
	}

	private static void set(Object target, String name, Object value) throws ReflectiveOperationException
	{
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass())
		{
			try
			{
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				f.set(target, value);
				return;
			}
			catch (NoSuchFieldException ignored)
			{
				// keep walking up
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static Method method(String name)
	{
		for (Method m : Actor.class.getMethods())
		{
			if (m.getName().equals(name))
			{
				return m;
			}
		}
		throw new IllegalArgumentException(name);
	}

	private int varIn(String group, int id)
	{
		JsonObject vars = g.has(group) ? g.getAsJsonObject(group) : null;
		JsonElement e = vars == null ? null : vars.get(String.valueOf(id));
		return e == null ? 0 : e.getAsInt();
	}

	private static String str(JsonObject o, String k)
	{
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static int intOr(JsonObject o, String k, int def)
	{
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? def : e.getAsInt();
	}

	private static long longOr(JsonObject o, String k)
	{
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsLong();
	}

	private static int[] ints(JsonArray arr)
	{
		if (arr == null)
		{
			return null;
		}
		int[] out = new int[arr.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = arr.get(i).getAsInt();
		}
		return out;
	}

	// ---- entry point ----

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.err.println("usage: ReplayHarness <recording dir> <out dir> [--config f] [--from t] [--to t] [--frames n]");
			System.exit(2);
		}
		Path recording = Paths.get(args[0]);
		File out = new File(args[1]);
		Properties overrides = new Properties();
		int from = Integer.MIN_VALUE;
		int to = Integer.MAX_VALUE;
		int frames = 1;
		for (int i = 2; i + 1 < args.length; i += 2)
		{
			switch (args[i])
			{
				case "--config":
					try (Reader r = Files.newBufferedReader(Paths.get(args[i + 1]), StandardCharsets.UTF_8))
					{
						overrides.load(r);
					}
					break;
				case "--from":
					from = Integer.parseInt(args[i + 1]);
					break;
				case "--to":
					to = Integer.parseInt(args[i + 1]);
					break;
				case "--frames":
					frames = Integer.parseInt(args[i + 1]);
					break;
				default:
					throw new IllegalArgumentException(args[i]);
			}
		}
		if (!out.isDirectory() && !out.mkdirs())
		{
			throw new IOException("Can't create " + out);
		}

		int[] written = {0};
		play(recording, overrides, from, to, frames, (name, img) ->
		{
			ImageIO.write(img, "png", new File(out, name));
			written[0]++;
		});
		System.out.println("replay: wrote " + written[0] + " frames to " + out);
	}

	interface FrameSink
	{
		void accept(String name, BufferedImage frame) throws IOException;
	}

	/** Replays a whole recording, handing each rendered frame in [from, to] to sink. */
	static void play(Path recording, Properties overrides, int from, int to, int frames, FrameSink sink)
		throws IOException, ReflectiveOperationException
	{
		ReplayHarness h = new ReplayHarness(overrides);
		h.loadSprites(recording.resolve("sprites"));
		try (BufferedReader r = Files.newBufferedReader(recording.resolve("recording.jsonl"), StandardCharsets.UTF_8))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || !h.apply(new JsonParser().parse(line).getAsJsonObject()))
				{
					continue;
				}
				if (h.tick < from || h.tick > to)
				{
					continue;
				}
				for (int f = 0; f < frames; f++)
				{
					String name = frames == 1 ? String.format("tick-%07d.png", h.tick)
						: String.format("tick-%07d-%d.png", h.tick, f);
					sink.accept(name, h.render(f, frames));
				}
			}
		}
	}
}
