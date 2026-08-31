package com.customhpbar;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Pet NPC IDs, from pet_npcs.csv - by ID because several pets share a name with an attackable NPC. */
@Slf4j
class PetNpcTable
{
	private static final Set<Integer> IDS = load();

	private static Set<Integer> load()
	{
		Set<Integer> ids = new HashSet<>();
		try (InputStream in = PetNpcTable.class.getResourceAsStream("pet_npcs.csv"))
		{
			if (in == null)
			{
				log.warn("pet_npcs.csv not found on classpath; pet names will always show");
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
						log.debug("Skipping malformed pet_npcs.csv line: {}", line);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load pet_npcs.csv; pet names will always show", e);
		}
		return ids;
	}

	/** Whether the given NPC ID is a known pet, following form or house menagerie form. */
	static boolean isPet(int npcId)
	{
		return IDS.contains(npcId);
	}
}
