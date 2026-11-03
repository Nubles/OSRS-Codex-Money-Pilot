package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

public class PlannerTest
{
	private static final long NOW = 1_800_000_000_000L;

	@Test
	public void freshFreeAccountSeesOnlyFreeMethods()
	{
		Planner p = Planner.plan(Methods.all(), Accounts.fresh().f2p().build(), Rates.none(), Planner.Filter.all());
		assertFalse(p.getOpen().isEmpty());
		for (Planner.Pick pick : p.getOpen())
		{
			assertFalse(pick.getMethod().getName(), pick.getMethod().isMembers());
		}
		for (Planner.Pick pick : p.getNear())
		{
			assertFalse(pick.getMethod().getName(), pick.getMethod().isMembers());
		}
		assertTrue(p.getHidden() > 0);
	}

	@Test
	public void ironmenDoNotSeeTradingMethods()
	{
		Planner p = Planner.plan(Methods.all(), Accounts.all(99).iron().build(), Rates.none(), Planner.Filter.all());
		for (Planner.Pick pick : p.getOpen())
		{
			assertFalse(pick.getMethod().getName(), pick.getMethod().isTrade());
		}
	}

	static Accounts diaries(Accounts a)
	{
		return a.varbit(net.runelite.api.gameval.VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, 1)
			.varbit(net.runelite.api.gameval.VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, 1);
	}

	@Test
	public void openIsBestPayingFirst()
	{
		Planner p = Planner.plan(Methods.all(), diaries(Accounts.all(99).done(Quest.values())).build(), Rates.none(),
			Planner.Filter.all());
		assertTrue(p.getNear().toString(), p.getNear().isEmpty());
		long last = Long.MAX_VALUE;
		for (Planner.Pick pick : p.getOpen())
		{
			assertTrue(pick.getRate().getPerHour() <= last);
			last = pick.getRate().getPerHour();
		}
	}

	@Test
	public void yourRateMovesAMethodUp()
	{
		Account a = Accounts.all(99).done(Quest.values()).build();
		Rates r = Rates.of(Collections.singletonList(new Clocked("barrows", NOW - 3_600_000, 3_600_000, 50_000_000)),
			Collections.emptyList(), NOW);
		Planner p = Planner.plan(Methods.all(), a, r, Planner.Filter.all());
		assertEquals("barrows", p.getOpen().get(0).getMethod().getId());
		assertEquals(Rate.Source.YOURS, p.getOpen().get(0).getRate().getSource());
	}

	@Test
	public void nearestLockedFirst()
	{
		Account a = Accounts.all(70).level(Skill.RUNECRAFT, 76).done(Quest.SINS_OF_THE_FATHER).build();
		Planner p = Planner.plan(Methods.all(), a, Rates.none(), Planner.Filter.all());
		assertEquals(p.getNear().get(0).getMethod().getName() + " " + p.getNear().get(0).getFit().getUnmet(),
			"blood-runes", p.getNear().get(0).getMethod().getId());
		long best = p.getOpen().get(0).getRate().getPerHour();
		for (Planner.Pick up : p.getUpgrades())
		{
			assertTrue(up.getRate().getPerHour() > best);
		}
	}

	@Test
	public void filtersLeaveMethodsOut()
	{
		Account a = Accounts.all(99).done(Quest.values()).build();
		Planner p = Planner.plan(Methods.all(), a, Rates.none(), new Planner.Filter(EnumSet.of(Method.Category.COMBAT), false));
		for (Planner.Pick pick : p.getOpen())
		{
			assertEquals(Method.Category.COMBAT, pick.getMethod().getCategory());
			assertFalse(pick.getMethod().isWilderness());
		}
	}

	@Test
	public void freshMembersAccountIsNotToldToRaid()
	{
		Planner p = Planner.plan(Methods.all(), Accounts.fresh().build(), Rates.none(), Planner.Filter.all());
		for (Planner.Pick pick : p.getOpen())
		{
			assertTrue(pick.getMethod().getName(), pick.getFit().getShortOf().size() <= 2);
		}
		assertFalse(p.getOpen().get(0).getMethod().getId().equals("chambers-of-xeric"));
		assertTrue(p.getUpgrades().stream().anyMatch(x -> x.getMethod().getId().equals("chambers-of-xeric")));
	}
}
