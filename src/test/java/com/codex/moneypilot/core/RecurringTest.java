package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

/** The timegated runs against their wiki pages, read on 4 November 2026. */
public class RecurringTest
{
	private static Method m(String id)
	{
		for (Method m : Methods.recurring())
		{
			if (m.getId().equals(id))
			{
				return m;
			}
		}
		throw new AssertionError(id);
	}

	@Test
	public void figuresMatchTheWiki()
	{
		// Money_making_guide/Bird_house_trapping: "52,254 per instance, after tax", "1.4 minutes", 50 minutes.
		assertRun(m("bird-houses"), 52_254, 84_000, 50, 2_239_436);
		// Money_making_guide/Farming_ranarr_weed: "178,960 per instance", "6 minutes", 90 minutes.
		assertRun(m("herbs-ranarr"), 178_960, 360_000, 90, 1_789_600);
		// Money_making_guide/Farming_snapdragon: "175,174 per instance".
		assertRun(m("herbs-snapdragon"), 175_174, 360_000, 90, 1_751_740);
		// Money_making_guide/Farming_torstol: "146,724 per instance".
		assertRun(m("herbs-torstol"), 146_724, 360_000, 90, 1_467_244);
		// Money_making_guide/Managing_Miscellania: "21,976 per instance", 30 seconds, 1440 minutes.
		assertRun(m("miscellania"), 21_976, 30_000, 1440, 2_637_151);
	}

	private static void assertRun(Method m, long perRun, long runMs, long minutes, long perHour)
	{
		assertTrue(m.isRecurring());
		assertTrue(m.isMembers());
		assertEquals(perRun, m.getPerRun());
		assertEquals(runMs, m.getRunMs());
		assertEquals(minutes * 60_000L, m.getEveryMs());
		assertEquals(perHour, m.getWikiPerHour());
		// The guide's effective rate is its profit per instance over the activity time, within a percent.
		double implied = perRun * 3_600_000.0 / runMs;
		assertTrue(m + " " + implied, Math.abs(implied - perHour) / perHour < 0.01);
		assertTrue(m.getUrl().startsWith("https://oldschool.runescape.wiki/w/Money_making_guide/"));
	}

	@Test
	public void needsMatchTheWiki()
	{
		assertEquals(Arrays.asList(Need.skill(Skill.HUNTER, 89), Need.quest(Quest.BONE_VOYAGE)), m("bird-houses").getNeeds());
		for (String herb : new String[]{"herbs-ranarr", "herbs-snapdragon", "herbs-torstol"})
		{
			assertEquals("herbs", m(herb).getGroup());
			assertTrue(m(herb).getNeeds().containsAll(Arrays.asList(Need.quest(Quest.GHOSTS_AHOY),
				Need.quest(Quest.MY_ARMS_BIG_ADVENTURE), Need.quest(Quest.FAIRYTALE_I__GROWING_PAINS))));
		}
		assertEquals(Need.skill(Skill.FARMING, 32), m("herbs-ranarr").getNeeds().get(0));
		assertEquals(Need.skill(Skill.FARMING, 62), m("herbs-snapdragon").getNeeds().get(0));
		assertEquals(Need.skill(Skill.FARMING, 85), m("herbs-torstol").getNeeds().get(0));
		assertEquals(Arrays.asList(Need.quest(Quest.THRONE_OF_MISCELLANIA)), m("miscellania").getNeeds());
	}

	@Test
	public void runsAreNotRankedOrClocked()
	{
		for (Method r : Methods.recurring())
		{
			assertNull(Methods.byId(r.getId()));
			assertTrue(!Methods.all().contains(r));
		}
	}
}
