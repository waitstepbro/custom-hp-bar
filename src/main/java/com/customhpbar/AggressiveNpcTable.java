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
 * NPC IDs whose monster is aggressive, from aggressive_npcs.csv - the type, not per-location tolerance - plus the
 * subset in level_exempt_npcs.csv that attacks regardless of the player's combat level.
 */
@Slf4j
class AggressiveNpcTable
{
	private static final Set<Integer> IDS = load("aggressive_npcs.csv");
	private static final Set<Integer> LEVEL_EXEMPT_IDS = load("level_exempt_npcs.csv");

	private static Set<Integer> load(String file)
	{
		Set<Integer> ids = new HashSet<>();
		try (InputStream in = AggressiveNpcTable.class.getResourceAsStream(file))
		{
			if (in == null)
			{
				log.warn("{} not found on classpath; aggressive-NPC coloring will be incomplete", file);
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
						log.debug("Skipping malformed {} line: {}", file, line);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load {}; aggressive-NPC coloring will be incomplete", file, e);
		}
		return ids;
	}

	/** Whether the given NPC ID is a known-aggressive monster type. */
	static boolean isAggressive(int npcId)
	{
		return IDS.contains(npcId);
	}

	/** Whether the given NPC ID ignores the 2x-combat-level rule wherever it is. */
	static boolean ignoresLevel(int npcId)
	{
		return LEVEL_EXEMPT_IDS.contains(npcId);
	}
}
