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
 * Set of NPC IDs whose monster is aggressive, sourced from the OSRS Wiki (each monster page's
 * infobox "aggressive = Yes/No" parameter, matched to that variant's NPC id), bundled as
 * aggressive_npcs.csv (one id per line).
 *
 * Keyed by ID, like NpcMaxHpTable: aggression is per combat-variant, and the same name can have
 * both aggressive and non-aggressive variants. Used only to decide *whether a monster type is
 * aggressive at all* - the per-encounter "will it actually attack me" decision still applies the
 * level formula (see CustomHpBarPlugin.isNpcAggressive) on top of this.
 *
 * Known limitation, matching the wiki's own single per-monster flag: aggression is really
 * location-dependent (a monster aggressive in one area may be tolerant in another), which this
 * flat set can't express. This was an accepted trade-off ("ignore aggression by area for now").
 * To refresh after a game update: re-run the scrape described in this file's history/CLAUDE.md.
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
