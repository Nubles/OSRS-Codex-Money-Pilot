package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

public class GoalPlanTest
{
	private static final long MIN = 60_000L;
	private static final long TWO_HOURS = 2 * 3_600_000L;

	private static Method active(String id, long perHour)
	{
		return new Method(id, id, id, Method.Category.COMBAT, perHour, null);
	}

	private static Method run(String id, long perRun, long seconds, long minutes, String group)
	{
		return new Method(id, id, id, Method.Category.COLLECTING, perRun * 3600 / seconds, null).recurring(perRun, seconds, minutes, group);
	}

	private static Rate yours(long perHour)
	{
		return new Rate(perHour, Rate.Source.YOURS, 3_600_000L, 3);
	}

	@Test
	public void aMethodAloneTakesWhatItsRateSays()
	{
		// 4.2m at 1.1m an hour is 3.82 hours: 2h on day one (2.2m), then 110 minutes for the last 2.0m.
		GoalPlan.Mix m = GoalPlan.mix(4_200_000, active("vorkath", 1_100_000), yours(1_100_000), Collections.emptyList(), TWO_HOURS);
		assertTrue(m.isReached());
		assertEquals(2, m.getDays());
		assertEquals(230 * MIN, m.getPlayMs());
		assertEquals(230 * MIN, m.getActiveMs());
		assertTrue(m.getProfit() >= 4_200_000);
		assertTrue(m.getRuns().isEmpty());
	}

	@Test
	public void birdHousesAlongsideCutTheTime()
	{
		// Bird houses as the guide gives them: 52,254 a run of 84 seconds, every 50 minutes.
		Method houses = run("houses", 52_254, 84, 50, null);
		GoalPlan.Mix m = GoalPlan.mix(4_200_000, active("vorkath", 1_100_000), yours(1_100_000), Collections.singletonList(houses), TWO_HOURS);
		// Day one: 2 runs in 120 minutes (2,253,175). Day two: one at login and one each 50 minutes; the last
		// 1,946,825 is made by minute 102 with 3 runs done.
		assertEquals(2, m.getDays());
		assertEquals(222 * MIN, m.getPlayMs());
		assertEquals(1, m.getRuns().size());
		assertEquals(5, m.getRuns().get(0).getCount());
		assertEquals(5 * 52_254, m.getRunsProfit());
		assertEquals(222 * MIN - 5 * 84_000L, m.getActiveMs());
		assertTrue(m.getProfit() >= 4_200_000);
		// One minute less would not do.
		GoalPlan.Mix alone = GoalPlan.mix(4_200_000, active("vorkath", 1_100_000), yours(1_100_000), Collections.emptyList(), TWO_HOURS);
		assertTrue(m.getPlayMs() < alone.getPlayMs());
	}

	@Test
	public void aRunIsLeftOutWhenTheMainMethodPaysMoreForItsMinutes()
	{
		// 10k for a minute is 600k an hour, less than the 3m an hour main method.
		Method slow = run("slow", 10_000, 60, 30, null);
		GoalPlan.Mix m = GoalPlan.mix(9_000_000, active("raid", 3_000_000), yours(3_000_000), Collections.singletonList(slow), TWO_HOURS);
		assertTrue(m.getRuns().isEmpty());
		assertEquals(180 * MIN, m.getPlayMs());
	}

	@Test
	public void aDailyRunCountsOnceADay()
	{
		// Miscellania as the guide gives it: 21,976 a day. 100k takes five days of it alone.
		Method misc = run("misc", 21_976, 30, 1440, null);
		GoalPlan.Mix m = GoalPlan.mix(100_000, null, null, Collections.singletonList(misc), TWO_HOURS);
		assertTrue(m.isReached());
		assertEquals(5, m.getDays());
		assertEquals(5, m.getRuns().get(0).getCount());
		// Runs alone count only the time they take.
		assertEquals(5 * 30_000L, m.getPlayMs());
		assertEquals(0, m.getActiveMs());
	}

	@Test
	public void runsAloneUseTheirTimers()
	{
		// Herb runs every 90 minutes in a 2 hour day: one on day one, two (login and one more) after.
		Method herbs = run("herbs", 178_960, 360, 90, "herbs");
		GoalPlan.Mix m = GoalPlan.mix(178_960 * 5, null, null, Collections.singletonList(herbs), TWO_HOURS);
		assertEquals(3, m.getDays());
		assertEquals(5, m.getRuns().get(0).getCount());
	}

	@Test
	public void longerNeedsNeverTakeLess()
	{
		Method houses = run("houses", 52_254, 84, 50, null);
		Method herbs = run("herbs", 178_960, 360, 90, "herbs");
		Method misc = run("misc", 21_976, 30, 1440, null);
		List<Method> runs = Arrays.asList(houses, herbs, misc);
		long last = 0;
		int lastDays = 0;
		for (long need = 100_000; need < 60_000_000; need += 977_777)
		{
			GoalPlan.Mix m = GoalPlan.mix(need, active("m", 1_234_567), yours(1_234_567), runs, TWO_HOURS);
			assertTrue(need + " reached", m.isReached());
			assertTrue(need + " covered", m.getProfit() >= need);
			assertTrue(need + " monotonic", m.getPlayMs() >= last);
			assertTrue(need + " days", m.getDays() >= lastDays);
			// Never more than a day's window a day.
			assertTrue(m.getPlayMs() <= m.getDays() * TWO_HOURS);
			last = m.getPlayMs();
			lastDays = m.getDays();
		}
	}

	@Test
	public void tooFarIsSaidSo()
	{
		GoalPlan.Mix m = GoalPlan.mix(2_000_000_000L, active("tiny", 1_000), yours(1_000), Collections.emptyList(), TWO_HOURS);
		assertFalse(m.isReached());
		assertEquals(GoalPlan.MAX_DAYS, m.getDays());
	}

	@Test
	public void planFromTheAccount()
	{
		Account a = Accounts.all(90).level(Skill.HUNTER, 89).level(Skill.FARMING, 99)
			.done(Quest.DRAGON_SLAYER_II, Quest.BONE_VOYAGE, Quest.GHOSTS_AHOY, Quest.MY_ARMS_BIG_ADVENTURE,
				Quest.FAIRYTALE_I__GROWING_PAINS, Quest.THRONE_OF_MISCELLANIA).build();
		Rates rates = Rates.of(Collections.singletonList(new Clocked("vorkath", 1_800_000_000_000L - 3_600_000L, 3_600_000L, 1_100_000)),
			Collections.emptyList(), 1_800_000_000_000L);
		Planner p = Planner.plan(Methods.all(), a, rates, Planner.Filter.all());
		List<Method> runs = GoalPlan.runsFor(a, Methods.recurring());
		// One herb run only, the best paying a run; bird houses and Miscellania too.
		assertEquals(3, runs.size());
		int herbs = 0;
		for (Method m : runs)
		{
			herbs += "herbs".equals(m.getGroup()) ? 1 : 0;
		}
		assertEquals(1, herbs);
		assertTrue(runs.contains(Methods.recurring().get(0)));
		GoalPlan plan = GoalPlan.of(4_200_000, p.getOpen(), runs, p.getNear(), Collections.emptyList(), 2);
		assertNotNull(plan.getBest());
		assertEquals(p.getOpen().get(0).getMethod(), plan.getBest().getMethod());
		assertNotNull(plan.getAlone());
		assertTrue(plan.getBest().getPlayMs() <= plan.getAlone().getPlayMs());
		assertEquals(GoalPlan.OTHERS, plan.getOthers().size());
		assertNotNull(plan.getRunsOnly());
		assertNull(plan.getRunsOnly().getMethod());
	}

	@Test
	public void nothingToMakeNothingToPlan()
	{
		GoalPlan plan = GoalPlan.of(0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 2);
		assertNull(plan.getBest());
		assertNull(plan.getRunsOnly());
		assertTrue(plan.getOpeners().isEmpty());
	}

	@Test
	public void runsNeedWhatTheGuideSays()
	{
		Account short1 = Accounts.all(99).level(Skill.HUNTER, 88).done(Quest.BONE_VOYAGE).build();
		assertTrue(GoalPlan.runsFor(short1, Methods.recurring()).isEmpty());
		Account noQuest = Accounts.all(99).build();
		assertTrue(GoalPlan.runsFor(noQuest, Methods.recurring()).isEmpty());
		Account free = Accounts.all(99).f2p().done(Quest.BONE_VOYAGE, Quest.THRONE_OF_MISCELLANIA).build();
		assertTrue(GoalPlan.runsFor(free, Methods.recurring()).isEmpty());
		Account houses = Accounts.all(99).done(Quest.BONE_VOYAGE).build();
		assertEquals(Collections.singletonList(Methods.recurring().get(0)), GoalPlan.runsFor(houses, Methods.recurring()));
	}

	@Test
	public void aPinnedQuestThatOpensABetterMethodIsShown()
	{
		// At 80 with 69 Attack the Chambers are a stretch, so the best open pays less than Vorkath would.
		Account a = Accounts.all(80).level(Skill.ATTACK, 69).done(Quest.PRIEST_IN_PERIL).build();
		Planner p = Planner.plan(Methods.all(), a, Rates.none(), Planner.Filter.all());
		Pinned.Goal ds2 = Pinned.navigator(new com.google.gson.Gson(),
			"{\"goals\":[{\"key\":\"quest:Dragon Slayer II\",\"type\":\"quest\",\"title\":\"Dragon Slayer II\",\"complete\":false,"
				+ "\"hoursLeft\":12.5,\"skills\":{},\"quests\":[\"Dragon Slayer II\"]}]}").get(0);
		GoalPlan plan = GoalPlan.of(20_000_000, p.getOpen(), Collections.emptyList(), p.getNear(), Collections.singletonList(ds2), 2);
		assertEquals(1, plan.getOpeners().size());
		GoalPlan.Opener o = plan.getOpeners().get(0);
		// Dragon Slayer II opens Vorkath and rune dragons; Vorkath pays more.
		assertEquals("vorkath", o.getMethod().getId());
		assertTrue(o.getMix().getPlayMs() < plan.getBest().getPlayMs());
		// A goal that covers only part of what a method needs opens nothing.
		Pinned.Goal partial = Pinned.navigator(new com.google.gson.Gson(),
			"{\"goals\":[{\"key\":\"skill:Slayer 90\",\"title\":\"Slayer 90\",\"skills\":{\"Slayer\":90},\"quests\":[]}]}").get(0);
		Fit hydra = Fit.of(Methods.byId("hydra"), a);
		assertFalse(partial.opens(hydra));
		// Nor does a goal that opens a method the account is far below the guide's levels for.
		Account low = Accounts.all(60).done(Quest.PRIEST_IN_PERIL).build();
		Planner lp = Planner.plan(Methods.all(), low, Rates.none(), Planner.Filter.all());
		GoalPlan lowPlan = GoalPlan.of(20_000_000, lp.getOpen(), Collections.emptyList(), lp.getNear(), Collections.singletonList(ds2), 2);
		for (GoalPlan.Opener op : lowPlan.getOpeners())
		{
			assertFalse(op.getMethod().getId().equals("vorkath"));
		}
	}
}
