package com.customhpbar;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps NPC IDs to their maximum HP, sourced from the OSRS Wiki's bucket API
 * (bucket('infobox_monster').select('id','name','hitpoints')...), bundled as npc_hp.csv.
 *
 * Keyed by ID rather than name: the same name can map to several different HP values across
 * combat-level/variant IDs (e.g. different "Goblin" levels, reanimated/enraged versions), so a
 * name-keyed table is structurally incapable of being correct for every variant of a monster
 * with more than one. Percentage display is the fallback for any NPC ID not in this table.
 */
@Slf4j
class NpcMaxHpTable
{
	private static final Map<Integer, Integer> TABLE = load();

	private static Map<Integer, Integer> load()
	{
		Map<Integer, Integer> table = new HashMap<>();
		try (InputStream in = NpcMaxHpTable.class.getResourceAsStream("npc_hp.csv"))
		{
			if (in == null)
			{
				log.warn("npc_hp.csv not found on classpath; NPC max HP lookups will be unavailable");
				return table;
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					int comma = line.indexOf(',');
					if (comma < 0)
					{
						continue;
					}
					try
					{
						int id = Integer.parseInt(line.substring(0, comma).trim());
						int hp = Integer.parseInt(line.substring(comma + 1).trim());
						table.put(id, hp);
					}
					catch (NumberFormatException e)
					{
						log.debug("Skipping malformed npc_hp.csv line: {}", line);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load npc_hp.csv; NPC max HP lookups will be unavailable", e);
		}
		return table;
	}

	/** Returns the known max HP for an NPC ID, or -1 if the ID is not in the table. */
	static int getMaxHp(int npcId)
	{
		Integer hp = TABLE.get(npcId);
		return hp != null ? hp : -1;
	}
}
