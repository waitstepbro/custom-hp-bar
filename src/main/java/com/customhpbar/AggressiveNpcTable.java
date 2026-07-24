package com.customhpbar;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Set of NPC IDs whose monster is aggressive, sourced from the OSRS Wiki's per-monster infobox
 * "aggressive" flag, bundled as aggressive_npcs.csv. Keyed by ID like NpcMaxHpTable, since the
 * same name can have both aggressive and non-aggressive variants. Only answers "is this monster
 * type aggressive at all" - the per-encounter level check lives in
 * CustomHpBarPlugin.isNpcAggressive. Known limitation: real aggression is location-dependent
 * (tolerant in some areas), which this flat set can't express - an accepted trade-off.
 */
@Slf4j
class AggressiveNpcTable
{
	private static final Set<Integer> IDS = load();

	private static Set<Integer> load()
	{
		Set<Integer> ids = new HashSet<>();
		try (InputStream in = AggressiveNpcTable.class.getResourceAsStream("aggressive_npcs.csv"))
		{
			if (in == null)
			{
				log.warn("aggressive_npcs.csv not found on classpath; aggressive-NPC coloring will be unavailable");
				return ids;
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					line = line.trim();
					if (line.isEmpty())
					{
						continue;
					}
					try
					{
						ids.add(Integer.parseInt(line));
					}
					catch (NumberFormatException e)
					{
						log.debug("Skipping malformed aggressive_npcs.csv line: {}", line);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load aggressive_npcs.csv; aggressive-NPC coloring will be unavailable", e);
		}
		return ids;
	}

	/** Whether the given NPC ID is a known-aggressive monster type. */
	static boolean isAggressive(int npcId)
	{
		return IDS.contains(npcId);
	}
}
