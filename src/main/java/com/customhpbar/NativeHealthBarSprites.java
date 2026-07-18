package com.customhpbar;

import net.runelite.api.gameval.SpriteID;

/**
 * Catalog of every known health/shield/armour/charge/prayer/poison bar sprite ID
 * (front + back), mirroring the set the core "Interface Styles" plugin overrides for
 * its HD health bar reskin. Used to swap the native bars for a transparent sprite.
 */
final class NativeHealthBarSprites
{
	private static final int[] HEALTH = {
		SpriteID.StandardHealth30.FRONT, SpriteID.StandardHealth30.BACK,
		SpriteID.StandardHealth40.FRONT, SpriteID.StandardHealth40.BACK,
		SpriteID.StandardHealth50.FRONT, SpriteID.StandardHealth50.BACK,
		SpriteID.StandardHealth60.FRONT, SpriteID.StandardHealth60.BACK,
		SpriteID.StandardHealth70.FRONT, SpriteID.StandardHealth70.BACK,
		SpriteID.StandardHealth80.FRONT, SpriteID.StandardHealth80.BACK,
		SpriteID.StandardHealth90.FRONT, SpriteID.StandardHealth90.BACK,
		SpriteID.StandardHealth100.FRONT, SpriteID.StandardHealth100.BACK,
		SpriteID.StandardHealth120.FRONT, SpriteID.StandardHealth120.BACK,
		SpriteID.StandardHealth140.FRONT, SpriteID.StandardHealth140.BACK,
		SpriteID.StandardHealth160.FRONT, SpriteID.StandardHealth160.BACK,
	};

	private static final int[] SHIELD = {
		SpriteID.StandardShield30.FRONT, SpriteID.StandardShield30.BACK,
		SpriteID.StandardShield40.FRONT, SpriteID.StandardShield40.BACK,
		SpriteID.StandardShield50.FRONT, SpriteID.StandardShield50.BACK,
		SpriteID.StandardShield60.FRONT, SpriteID.StandardShield60.BACK,
		SpriteID.StandardShield70.FRONT, SpriteID.StandardShield70.BACK,
		SpriteID.StandardShield80.FRONT, SpriteID.StandardShield80.BACK,
		SpriteID.StandardShield90.FRONT, SpriteID.StandardShield90.BACK,
		SpriteID.StandardShield100.FRONT, SpriteID.StandardShield100.BACK,
		SpriteID.StandardShield120.FRONT, SpriteID.StandardShield120.BACK,
		SpriteID.StandardShield140.FRONT, SpriteID.StandardShield140.BACK,
		SpriteID.StandardShield160.FRONT, SpriteID.StandardShield160.BACK,
	};

	private static final int[] ARMOUR = {
		SpriteID.StandardArmour30.FRONT, SpriteID.StandardArmour30.BACK,
		SpriteID.StandardArmour40.FRONT, SpriteID.StandardArmour40.BACK,
		SpriteID.StandardArmour50.FRONT, SpriteID.StandardArmour50.BACK,
		SpriteID.StandardArmour60.FRONT, SpriteID.StandardArmour60.BACK,
		SpriteID.StandardArmour70.FRONT, SpriteID.StandardArmour70.BACK,
		SpriteID.StandardArmour80.FRONT, SpriteID.StandardArmour80.BACK,
		SpriteID.StandardArmour90.FRONT, SpriteID.StandardArmour90.BACK,
		SpriteID.StandardArmour100.FRONT, SpriteID.StandardArmour100.BACK,
		SpriteID.StandardArmour120.FRONT, SpriteID.StandardArmour120.BACK,
		SpriteID.StandardArmour140.FRONT, SpriteID.StandardArmour140.BACK,
		SpriteID.StandardArmour160.FRONT, SpriteID.StandardArmour160.BACK,
	};

	private static final int[] CHARGE = {
		SpriteID.StandardCharge30.FRONT, SpriteID.StandardCharge30.BACK,
		SpriteID.StandardCharge40.FRONT, SpriteID.StandardCharge40.BACK,
		SpriteID.StandardCharge50.FRONT, SpriteID.StandardCharge50.BACK,
		SpriteID.StandardCharge60.FRONT, SpriteID.StandardCharge60.BACK,
		SpriteID.StandardCharge70.FRONT, SpriteID.StandardCharge70.BACK,
		SpriteID.StandardCharge80.FRONT, SpriteID.StandardCharge80.BACK,
		SpriteID.StandardCharge90.FRONT, SpriteID.StandardCharge90.BACK,
		SpriteID.StandardCharge100.FRONT, SpriteID.StandardCharge100.BACK,
		SpriteID.StandardCharge120.FRONT, SpriteID.StandardCharge120.BACK,
		SpriteID.StandardCharge140.FRONT, SpriteID.StandardCharge140.BACK,
		SpriteID.StandardCharge160.FRONT, SpriteID.StandardCharge160.BACK,
	};

	/**
	 * SpriteID's "Prayer" category is purple - confirmed by cross-referencing the core
	 * "Interface Styles" plugin's HD healthbar color mapping (StandardShield -> cyan,
	 * StandardPrayer -> purple). But the actual native overhead bar shown for the player's own
	 * Prayer points is cyan (confirmed live, via screenshot) - i.e. it's StandardShield, not
	 * StandardPrayer, despite the name. Rather than bet on a single guess again, PRAYER includes
	 * both categories: StandardShield (the one actually confirmed live) and StandardPrayer (the
	 * name-matched one, which may still be used in some other circumstance we haven't seen).
	 * Extra sprite-override entries are free (just a couple more Map.put calls), so there's no
	 * real cost to hedging here.
	 */
	static final int[] PRAYER = concat(SHIELD, new int[] {
		SpriteID.StandardPrayer30.FRONT, SpriteID.StandardPrayer30.BACK,
		SpriteID.StandardPrayer40.FRONT, SpriteID.StandardPrayer40.BACK,
		SpriteID.StandardPrayer50.FRONT, SpriteID.StandardPrayer50.BACK,
		SpriteID.StandardPrayer60.FRONT, SpriteID.StandardPrayer60.BACK,
		SpriteID.StandardPrayer70.FRONT, SpriteID.StandardPrayer70.BACK,
		SpriteID.StandardPrayer80.FRONT, SpriteID.StandardPrayer80.BACK,
		SpriteID.StandardPrayer90.FRONT, SpriteID.StandardPrayer90.BACK,
		SpriteID.StandardPrayer100.FRONT, SpriteID.StandardPrayer100.BACK,
		SpriteID.StandardPrayer120.FRONT, SpriteID.StandardPrayer120.BACK,
		SpriteID.StandardPrayer140.FRONT, SpriteID.StandardPrayer140.BACK,
		SpriteID.StandardPrayer160.FRONT, SpriteID.StandardPrayer160.BACK,
	});

	private static final int[] POISON = {
		SpriteID.StandardPoison30.FRONT, SpriteID.StandardPoison30.BACK,
		SpriteID.StandardPoison40.FRONT, SpriteID.StandardPoison40.BACK,
		SpriteID.StandardPoison50.FRONT, SpriteID.StandardPoison50.BACK,
		SpriteID.StandardPoison60.FRONT, SpriteID.StandardPoison60.BACK,
		SpriteID.StandardPoison70.FRONT, SpriteID.StandardPoison70.BACK,
		SpriteID.StandardPoison80.FRONT, SpriteID.StandardPoison80.BACK,
		SpriteID.StandardPoison90.FRONT, SpriteID.StandardPoison90.BACK,
		SpriteID.StandardPoison100.FRONT, SpriteID.StandardPoison100.BACK,
		SpriteID.StandardPoison120.FRONT, SpriteID.StandardPoison120.BACK,
		SpriteID.StandardPoison140.FRONT, SpriteID.StandardPoison140.BACK,
		SpriteID.StandardPoison160.FRONT, SpriteID.StandardPoison160.BACK,
	};

	private static final int[] HEADBAR = {
		SpriteID.HeadbarShootingStar50.FRONT, SpriteID.HeadbarShootingStar50.BACK,

		SpriteID.HeadbarShield100.COX_GREEN, SpriteID.HeadbarShield100.COX_BLUE,
		SpriteID.HeadbarOlmtimer100.YELLOW, SpriteID.HeadbarOlmtimer100.RED,

		SpriteID.HeadbarBlood120.FRONT, SpriteID.HeadbarBlood120.BACK,
		SpriteID.HeadbarIce120.FRONT, SpriteID.HeadbarIce120.BACK,
		SpriteID.HeadbarHeat120.FRONT, SpriteID.HeadbarHeat120.BACK,

		SpriteID.HeadbarBlood90.FRONT, SpriteID.HeadbarBlood90.BACK,
		SpriteID.HeadbarIce90.FRONT, SpriteID.HeadbarIce90.BACK,
		SpriteID.HeadbarHeat90.FRONT, SpriteID.HeadbarHeat90.BACK,

		SpriteID.HeadbarBlood30.FRONT, SpriteID.HeadbarBlood30.BACK,
	};

	/**
	 * Every category combined - built from the category arrays above, not re-listed by hand.
	 * SHIELD isn't listed again here since PRAYER already includes it (see PRAYER's doc comment).
	 */
	static final int[] ALL = concat(HEALTH, ARMOUR, CHARGE, PRAYER, POISON, HEADBAR);

	private static int[] concat(int[]... arrays)
	{
		int total = 0;
		for (int[] array : arrays)
		{
			total += array.length;
		}

		int[] result = new int[total];
		int pos = 0;
		for (int[] array : arrays)
		{
			System.arraycopy(array, 0, result, pos, array.length);
			pos += array.length;
		}
		return result;
	}

	private NativeHealthBarSprites()
	{
	}
}
