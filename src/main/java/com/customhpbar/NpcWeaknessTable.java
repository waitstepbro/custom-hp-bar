package com.customhpbar;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps NPC IDs to their elemental weakness, from npc_weakness.csv - keyed by ID since the same name
 * can carry different weaknesses across variants. Sourced from the OSRS Wiki's own infobox_monster
 * bucket, the same query its Elemental weakness page renders from. See CLAUDE.md.
 */
@Slf4j
class NpcWeaknessTable
{
	/** The four standard-spellbook elements. Only these exist in game - see CLAUDE.md. */
	enum Element
	{
		AIR, WATER, EARTH, FIRE
	}

	@Value
	static class Weakness
	{
		Element element;
		/** Extra magic damage and accuracy percent, 5-200. Not a fraction of anything - see CLAUDE.md. */
		int percent;
	}

	private static final Map<Integer, Weakness> TABLE = load();

	private static Map<Integer, Weakness> load()
	{
		Map<Integer, Weakness> table = new HashMap<>();
		try (InputStream in = NpcWeaknessTable.class.getResourceAsStream("npc_weakness.csv"))
		{
			if (in == null)
			{
				log.warn("npc_weakness.csv not found on classpath; NPC weakness lookups will be unavailable");
				return table;
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					String[] parts = line.split(",");
					if (parts.length != 3)
					{
						continue;
					}
					try
					{
						int id = Integer.parseInt(parts[0].trim());
						Element element = Element.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
						table.put(id, new Weakness(element, Integer.parseInt(parts[2].trim())));
					}
					catch (IllegalArgumentException e)
					{
						log.debug("Skipping malformed npc_weakness.csv line: {}", line);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load npc_weakness.csv; NPC weakness lookups will be unavailable", e);
		}
		return table;
	}

	/** The known elemental weakness for an NPC ID, or null if it has none. Most NPCs have none. */
	static Weakness getWeakness(int npcId)
	{
		return TABLE.get(npcId);
	}
}
