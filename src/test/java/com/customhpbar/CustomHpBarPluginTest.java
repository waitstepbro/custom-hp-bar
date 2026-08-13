package com.customhpbar;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CustomHpBarPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CustomHpBarPlugin.class);

		// --developer-mode unlocks the core Developer Tools plugin (widget inspector etc.) -
		// needed while investigating TODO item 2. Appended rather than replacing args so
		// whatever's passed to :runPlugin still comes through.
		String[] devArgs = new String[args.length + 1];
		System.arraycopy(args, 0, devArgs, 0, args.length);
		devArgs[args.length] = "--developer-mode";

		RuneLite.main(devArgs);
	}
}
