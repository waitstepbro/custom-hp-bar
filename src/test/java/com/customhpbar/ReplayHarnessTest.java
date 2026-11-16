package com.customhpbar;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ReplayHarnessTest
{
	private static final Path SMOKE = Paths.get("src/test/resources/com/customhpbar/replay-smoke");

	private static Map<String, int[]> run() throws Exception
	{
		Map<String, int[]> frames = new LinkedHashMap<>();
		ReplayHarness.play(SMOKE, new Properties(), Integer.MIN_VALUE, Integer.MAX_VALUE, 1, (name, img) ->
			frames.put(name, img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())));
		return frames;
	}

	@Test
	public void replayIsDeterministicAndDrawsTheFight() throws Exception
	{
		Map<String, int[]> first = run();
		Map<String, int[]> second = run();
		assertEquals(16, first.size());
		for (String name : first.keySet())
		{
			assertArrayEquals(name, first.get(name), second.get(name));
		}

		// Mid-fight, the hellhound's bar and the self bars must have drawn something besides the scene.
		int[] mid = first.get("tick-0000103.png");
		int background = mid[0];
		long drawn = java.util.Arrays.stream(mid).filter(p -> p != background).count();
		assertTrue("expected bars drawn, got " + drawn + " non-background pixels", drawn > 1000);
	}
}
