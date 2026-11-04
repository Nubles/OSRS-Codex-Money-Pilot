package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CoachTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final long H = 3_600_000L;
	private static final Method VORKATH = Methods.byId("vorkath");

	/** Two kept runs: 1.2m an hour and 0.9m an hour, 1.05m an hour together. */
	private static final List<Clocked> RUNS = Arrays.asList(
		new Clocked("vorkath", NOW - 2 * H, 30 * 60_000L, 600_000),
		new Clocked("vorkath", NOW - 26 * H, 30 * 60_000L, 450_000),
		new Clocked("zulrah", NOW - 30 * H, 60 * 60_000L, 5_000_000));

	private static Coach coach(long playedMs, long profit)
	{
		Rate own = Rates.of(RUNS, Collections.emptyList(), NOW).own(VORKATH);
		return Coach.of(VORKATH, playedMs, profit, RUNS, own);
	}

	@Test
	public void tooEarlyToSay()
	{
		Coach c = coach(4 * 60_000L, 900_000);
		assertEquals(Coach.Verdict.SETTLING, c.getVerdict());
		assertEquals("Pace after 5m", c.line());
	}

	@Test
	public void weighedAgainstYourOwnRuns()
	{
		assertEquals(Long.valueOf(1_200_000), coach(H, 0).getBest());
		assertEquals(Long.valueOf(1_050_000), coach(H, 0).getUsual());
		assertEquals(3_439_802, coach(H, 0).getWiki());

		Coach best = coach(H, 1_300_000);
		assertEquals(Coach.Verdict.BEST, best.getVerdict());
		assertEquals("Your best run yet, past 1.2m/hr", best.line());
		assertEquals("Best yet, was 1.2m", best.shortLine());

		Coach ahead = coach(H, 1_180_000);
		assertEquals(Coach.Verdict.AHEAD, ahead.getVerdict());
		assertEquals("12% ahead of your usual 1.05m", ahead.line());
		assertEquals("+12% on your 1.05m", ahead.shortLine());

		Coach usual = coach(H, 1_000_000);
		assertEquals(Coach.Verdict.USUAL, usual.getVerdict());

		Coach behind = coach(30 * 60_000L, 400_000);
		assertEquals(800_000, behind.getNow());
		assertEquals(Coach.Verdict.BEHIND, behind.getVerdict());
		assertEquals("24% behind your usual 1.05m", behind.line());
		assertEquals("-24% on your 1.05m", behind.shortLine());
	}

	@Test
	public void withNoRunsOfYourOwnTheWikiIsTheYardstick()
	{
		Coach c = Coach.of(VORKATH, H, 1_720_000, Collections.emptyList(), null);
		assertNull(c.getBest());
		assertNull(c.getUsual());
		assertEquals(Coach.Verdict.WIKI, c.getVerdict());
		assertEquals("50% of the wiki's 3.44m", c.line());
	}

	@Test
	public void aLossIsBehind()
	{
		Coach c = coach(H, -200_000);
		assertEquals(Coach.Verdict.BEHIND, c.getVerdict());
		assertTrue(c.line().contains("behind"));
	}

	@Test
	public void replayOfARunTickByTick()
	{
		// A run replayed a game tick at a time: loot banked in lumps every 6 minutes at 1.3m an hour.
		Run run = new Run("vorkath", NOW, Holdings.of(Collections.emptyMap()));
		Coach.Verdict seen = null;
		int changes = 0;
		long changedAt = 0;
		long lastBanked = 0;
		Rate own = Rates.of(RUNS, Collections.emptyList(), NOW).own(VORKATH);
		for (int tick = 1; tick <= 6000; tick++)
		{
			run.tick();
			long banked = (run.getPlayedMs() / (6 * 60_000L)) * 130_000L;
			if (banked != lastBanked)
			{
				lastBanked = banked;
				changedAt = run.getPlayedMs();
			}
			Coach c = Coach.of(VORKATH, run.getPlayedMs(), banked, changedAt, RUNS, own, seen);
			if (c.getVerdict() != seen)
			{
				changes++;
				seen = c.getVerdict();
			}
			if (run.getPlayedMs() < Coach.SETTLE_MS)
			{
				assertEquals(Coach.Verdict.SETTLING, c.getVerdict());
			}
		}
		// It settles, then ends as the best run; the verdict holds between lumps instead of flickering.
		assertEquals(Coach.Verdict.BEST, seen);
		assertTrue("changes " + changes, changes <= 3);
		// Without the memory of the last verdict the same run flickers.
		int flicker = 0;
		Coach.Verdict last = null;
		Run again = new Run("vorkath", NOW, Holdings.of(Collections.emptyMap()));
		for (int tick = 1; tick <= 6000; tick++)
		{
			again.tick();
			Coach.Verdict v = Coach.of(VORKATH, again.getPlayedMs(), (again.getPlayedMs() / (6 * 60_000L)) * 130_000L, RUNS, own).getVerdict();
			flicker += v != last ? 1 : 0;
			last = v;
		}
		assertTrue(flicker > changes);
		// Loot that stops coming is not counted as the old pace for ever.
		Coach stalled = Coach.of(VORKATH, 60 * 60_000L, 1_100_000, 30 * 60_000L, RUNS, own, Coach.Verdict.BEST);
		assertEquals(1_100_000, stalled.getNow());
		assertEquals(Coach.Verdict.USUAL, stalled.getVerdict());
		// Eight minutes since the last lump is still between lumps: 1.1m in 52 minutes.
		Coach fresh = Coach.of(VORKATH, 60 * 60_000L, 1_100_000, 52 * 60_000L, RUNS, own, null);
		assertEquals(1_269_231, fresh.getNow());
		assertEquals(Coach.Verdict.BEST, fresh.getVerdict());
	}
}
