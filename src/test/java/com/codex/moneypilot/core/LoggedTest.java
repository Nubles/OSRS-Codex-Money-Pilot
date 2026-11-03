package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

public class LoggedTest
{
	private static final long NOW = 1_800_000_000_000L;
	private final Gson gson = new Gson();

	@Test
	public void readsWorthAndLoot()
	{
		String json = "[{\"startedAt\":" + (NOW - 7_200_000) + ",\"playedMs\":3600000,\"activity\":\"Vorkath\","
			+ "\"worthStart\":100000000,\"worthNow\":103000000},"
			+ "{\"startedAt\":" + (NOW - 20_000_000) + ",\"playedMs\":1800000,\"activity\":\"Zulrah\","
			+ "\"loot\":{\"12934\":{\"id\":12934,\"name\":\"Zulrah's scales\",\"quantity\":1000,\"price\":150}},"
			+ "\"otherLoot\":50000,\"supplies\":20000}]";
		List<Logged> l = Logged.read(gson, json, NOW);
		assertEquals(2, l.size());
		assertTrue(l.get(0).isByWorth());
		assertEquals(3_000_000, l.get(0).getProfit());
		assertFalse(l.get(1).isByWorth());
		assertEquals(150_000 + 50_000 - 20_000, l.get(1).getProfit());
	}

	@Test
	public void leavesOutWhatCannotBeTrue()
	{
		String json = "[{\"startedAt\":" + (NOW - 600_000) + ",\"playedMs\":3600000,\"activity\":\"Vorkath\"},"
			+ "{\"startedAt\":" + (NOW - 7_200_000) + ",\"playedMs\":60000,\"activity\":\"Vorkath\"},"
			+ "{\"startedAt\":" + (NOW - 7_200_000) + ",\"playedMs\":3600000},"
			+ "\"junk\", 5, {\"startedAt\":\"soon\"},"
			+ "{\"startedAt\":" + (NOW - 7_200_000) + ",\"playedMs\":3600000,\"activity\":\"Kraken\",\"worthStart\":-5,\"worthNow\":10}]";
		List<Logged> l = Logged.read(gson, json, NOW);
		assertEquals(1, l.size());
		assertEquals("Kraken", l.get(0).getActivity());
		assertFalse(l.get(0).isByWorth());
	}

	@Test
	public void brokenJournalIsEmpty()
	{
		assertTrue(Logged.read(gson, "{not json", NOW).isEmpty());
		assertTrue(Logged.read(gson, "{}", NOW).isEmpty());
		assertTrue(Logged.read(gson, null, NOW).isEmpty());
	}
}
