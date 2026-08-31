package com.customhpbar;

import lombok.Value;

/**
 * One hitsplat, copied at the moment it landed. The client pools its own Hitsplat objects and reuses
 * them for later hits, so a stored reference silently becomes a different splat - see CLAUDE.md.
 */
@Value
class OverheadHitsplat
{
	int type;
	int amount;
	int disappearsOnGameCycle;
}
