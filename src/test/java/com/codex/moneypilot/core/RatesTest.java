package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RatesTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@Test
	public void wikiUntilYouHaveEnough()
	{
		Method vork = Methods.byId("vorkath");
		Rates none = Rates.of(Collections.emptyList(), Collections.emptyList(), NOW);
		assertEquals(Rate.Source.WIKI, none.best(vork).getSource());
		assertEquals(vork.getWikiPerHour(), none.best(vork).getPerHour());

		// Ten minutes is not enough to say.
		Rates short_ = Rates.of(Collections.singletonList(new Clocked("vorkath", NOW - HOUR, 10 * 60_000L, 900_000)),
			Collections.emptyList(), NOW);
		assertEquals(Rate.Source.WIKI, short_.best(vork).getSource());
	}

	@Test
	public void yourRunsBeatSessionsBeatWiki()
	{
		Method vork = Methods.byId("vorkath");
		Rates r = Rates.of(
			Arrays.asList(new Clocked("vorkath", NOW - 2 * HOUR, HOUR / 2, 1_000_000), new Clocked("vorkath", NOW - HOUR, HOUR / 2, 2_000_000)),
			Collections.singletonList(new Logged(NOW - 3 * HOUR, HOUR, "Vorkath", 5_000_000, true)),
			NOW);
		assertEquals(Rate.Source.YOURS, r.best(vork).getSource());
		assertEquals(3_000_000, r.best(vork).getPerHour());
		assertEquals(2, r.best(vork).getCount());
		assertEquals(5_000_000, r.sessions(vork).getPerHour());
	}

	@Test
	public void sessionsCountWhenThereAreNoRuns()
	{
		Method zul = Methods.byId("zulrah");
		Rates r = Rates.of(Collections.emptyList(), Arrays.asList(
			new Logged(NOW - 5 * HOUR, 2 * HOUR, "Zulrah", 3_000_000, true),
			new Logged(NOW - 9 * HOUR, HOUR, "Zulrah", 0, false),
			// The same session read twice is one session.
			new Logged(NOW - 9 * HOUR, HOUR, "Zulrah", 0, false)), NOW);
		assertEquals(Rate.Source.SESSIONS, r.best(zul).getSource());
		assertEquals(1_000_000, r.best(zul).getPerHour());
		assertEquals(2, r.best(zul).getCount());
	}

	@Test
	public void oldRunsAreForgotten()
	{
		Method vork = Methods.byId("vorkath");
		Rates r = Rates.of(Collections.singletonList(new Clocked("vorkath", NOW - (Rates.DAYS + 1) * 86_400_000L, HOUR, 9_000_000)),
			Collections.emptyList(), NOW);
		assertNull(r.own(vork));
	}

	@Test
	public void lossesAreKept()
	{
		Method vork = Methods.byId("vorkath");
		Rates r = Rates.of(Collections.singletonList(new Clocked("vorkath", NOW - HOUR, HOUR, -250_000)), Collections.emptyList(), NOW);
		assertEquals(-250_000, r.best(vork).getPerHour());
	}
}
