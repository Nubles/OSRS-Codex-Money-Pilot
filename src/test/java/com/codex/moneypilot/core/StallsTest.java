package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** The Port Roberts facts, each checked against the wiki's Port Roberts page and stall pages (4 November 2026). */
public class StallsTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@Test
	public void stallsMatchTheWiki()
	{
		// Object ID, level and experience from each stall's infobox.
		int[][] wiki = {
			{58100, 2, 50}, {58101, 20, 240}, {58102, 35, 385}, {58103, 42, 495}, {58104, 50, 800},
			{58105, 65, 1100}, {58106, 75, 1295}, {58107, 82, 1910}, {58108, 87, 2230}};
		Stalls.Stall[] all = Stalls.Stall.values();
		assertEquals(9, all.length);
		for (int i = 0; i < all.length; i++)
		{
			assertEquals(all[i].name(), wiki[i][0], all[i].getObjectId());
			assertEquals(all[i].name(), wiki[i][1], all[i].getLevel());
			assertEquals(all[i].name(), wiki[i][2], all[i].getXpTenths());
			assertSame(all[i], Stalls.Stall.of(wiki[i][0]));
		}
		assertNull(Stalls.Stall.of(58109));
		assertEquals(1_200, Stalls.STEALS_AN_HOUR);
	}

	@Test
	public void eachStealNamesItsStall()
	{
		assertSame(Stalls.Stall.CANNONBALL, Stalls.Stall.ofXp(223));
		assertSame(Stalls.Stall.ORE, Stalls.Stall.ofXp(191));
		// 129.5 and 38.5 show as either whole number.
		assertSame(Stalls.Stall.GEM, Stalls.Stall.ofXp(129));
		assertSame(Stalls.Stall.GEM, Stalls.Stall.ofXp(130));
		assertSame(Stalls.Stall.FUR, Stalls.Stall.ofXp(38));
		assertSame(Stalls.Stall.FUR, Stalls.Stall.ofXp(39));
		assertSame(Stalls.Stall.FISH, Stalls.Stall.ofXp(50));
		assertSame(Stalls.Stall.SPICE, Stalls.Stall.ofXp(110));
		assertNull(Stalls.Stall.ofXp(300));
		assertNull(Stalls.Stall.ofXp(0));
	}

	/** The table on the Port Roberts page: primary four times, secondary twice. */
	@Test
	public void rotationsMatchTheWikiTable()
	{
		assertRotation(Stalls.Rotation.SILVER, Stalls.Stall.SILVER, Stalls.Stall.FISH, 83_800);
		assertRotation(Stalls.Rotation.SPICE, Stalls.Stall.SPICE, Stalls.Stall.SILVER, 120_000);
		assertRotation(Stalls.Rotation.GEM, Stalls.Stall.GEM, Stalls.Stall.SPICE, 147_600);
		assertRotation(Stalls.Rotation.ORE, Stalls.Stall.ORE, Stalls.Stall.GEM, 204_600);
		assertRotation(Stalls.Rotation.CANNONBALL, Stalls.Stall.CANNONBALL, Stalls.Stall.ORE, 254_800);
		for (Stalls.Rotation r : Stalls.Rotation.values())
		{
			// The table's figure is the stalls' own experience at 200 rounds of six steals an hour.
			long round = 4L * r.getPrimary().getXpTenths() + 2L * r.getSecondary().getXpTenths();
			assertEquals(r.name(), r.getWikiXpPerHour(), round * (Stalls.STEALS_AN_HOUR / 6) / 10);
		}
		assertNull(Stalls.Rotation.of(Stalls.Stall.FISH));
	}

	private static void assertRotation(Stalls.Rotation r, Stalls.Stall primary, Stalls.Stall secondary, int xp)
	{
		assertSame(primary, r.getPrimary());
		assertSame(secondary, r.getSecondary());
		assertEquals(xp, r.getWikiXpPerHour());
		assertSame(r, Stalls.Rotation.of(primary));
		assertSame(r, Stalls.Rotation.ofMethod(r.getMethod()));
	}

	@Test
	public void eachRotationIsAMethodBesidePickpocketing()
	{
		for (Stalls.Rotation r : Stalls.Rotation.values())
		{
			Method m = Methods.byId(r.getMethod());
			assertNotNull(r.name(), m);
			assertEquals(Skill.THIEVING, m.getSkill());
			assertEquals(Method.Category.SKILLING, m.getCategory());
			assertTrue(m.isEstimate());
			assertEquals("https://oldschool.runescape.wiki/w/Port_Roberts", m.getUrl());
			assertEquals(2, m.getNeeds().size());
			assertTrue(m.getNeeds().contains(Need.skill(Skill.THIEVING, r.getPrimary().getLevel())));
			// "Level 50 Sailing is required to dock at and Charter to Port Roberts."
			assertTrue(m.getNeeds().contains(Need.skill(Skill.SAILING, 50)));
			assertFalse(m.isWilderness());
		}
		assertEquals("Stealing from Port Roberts stalls (cannonball and ore)", Methods.byId("port-roberts-cannonballs").getName());
		// 200 rounds of the stall pages' average theft: 4 x 102.16 + 2 x 235.35, 4 x 235.35 + 2 x 205.01, 4 x 205.01 + 2 x 42.
		assertEquals(175_868, Methods.byId("port-roberts-cannonballs").getWikiPerHour());
		assertEquals(270_284, Methods.byId("port-roberts-ore").getWikiPerHour());
		assertEquals(180_808, Methods.byId("port-roberts-gems").getWikiPerHour());
		// The wiki's own: "approximately 97k Thieving experience and 100k gp per hour".
		assertEquals(100_000, Methods.byId("port-roberts-spice").getWikiPerHour());
		assertEquals(80_889, Methods.byId("port-roberts-silver").getWikiPerHour());
	}

	@Test
	public void theGatesAreThievingAndSailing()
	{
		Method cannon = Methods.byId("port-roberts-cannonballs");
		assertEquals(Fit.Status.LOCKED, Fit.of(cannon, account(86, 60)).getStatus());
		assertEquals(Fit.Status.LOCKED, Fit.of(cannon, account(90, 49)).getStatus());
		assertTrue(Fit.of(cannon, account(87, 50)).isOpen());
		assertTrue(Fit.of(Methods.byId("port-roberts-silver"), account(50, 50)).isOpen());
	}

	private static Account account(int thieving, int sailing)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			levels.put(s, 1);
		}
		levels.put(Skill.THIEVING, thieving);
		levels.put(Skill.SAILING, sailing);
		return new Account(levels, new EnumMap<Quest, QuestState>(Quest.class), new HashMap<>(), 3, 30, true, false, true);
	}

	@Test
	public void guardsAndTheirArrest()
	{
		assertTrue(Stalls.isMarketGuard(14881));
		assertTrue(Stalls.isMarketGuard(14882));
		assertTrue(Stalls.isMarketGuard(14883));
		assertFalse(Stalls.isMarketGuard(14884));
		assertTrue(Stalls.isArrest("Hey! What do you think you're doing?! You're under arrest!"));
		assertTrue(Stalls.isArrest("Market Guard|Hey! What do you think you're doing?! You're under arrest!"));
		assertFalse(Stalls.isArrest("You steal a steel cannonball."));
		assertFalse(Stalls.isArrest(null));
	}

	@Test
	public void stealsRankBelowRunsAndAboveSessions()
	{
		Method ore = Methods.byId("port-roberts-ore");
		Map<String, Rate> steals = new HashMap<>();
		steals.put("port-roberts-ore", new Rate(240_000, Rate.Source.STALLS, HOUR, 1_100));
		Rates withSessions = Rates.of(Collections.emptyList(),
			Collections.singletonList(new Logged(NOW - 3 * HOUR, HOUR, "Thieving", 1_000_000, true)), null, null, steals, NOW);
		assertEquals(Rate.Source.STALLS, withSessions.best(ore).getSource());
		assertEquals(240_000, withSessions.stalls(ore).getPerHour());
		Rates withRuns = Rates.of(Collections.singletonList(new Clocked("port-roberts-ore", NOW - HOUR, HOUR, 300_000)),
			Collections.emptyList(), null, null, steals, NOW);
		assertEquals(Rate.Source.YOURS, withRuns.best(ore).getSource());
		steals.put("port-roberts-ore", new Rate(900_000, Rate.Source.STALLS, 60_000L, 20));
		assertEquals(Rate.Source.WIKI, Rates.of(Collections.emptyList(), Collections.emptyList(), null, null, steals, NOW)
			.best(ore).getSource());
	}

	@Test
	public void oldDaysAndDamagedSavesAreDropped()
	{
		Gson gson = new Gson();
		long today = StallLog.day(NOW);
		String json = "[{\"d\":" + today + ",\"m\":\"port-roberts-ore\",\"s\":120,\"c\":1,\"tk\":700,\"xp\":20000,"
			+ "\"loot\":{\"440\":40}},"
			+ "{\"d\":" + (today - 61) + ",\"m\":\"port-roberts-ore\",\"s\":99,\"tk\":100},"
			+ "{\"d\":" + today + ",\"m\":\"port-roberts-fruit\",\"s\":1},"
			+ "{\"d\":" + today + ",\"m\":\"port-roberts-gems\",\"s\":-4}]";
		StallLog log = StallLog.decode(gson, json);
		Map<Stalls.Rotation, StallLog.Counts> t = log.tallies(NOW);
		assertEquals(Collections.singleton(Stalls.Rotation.ORE), t.keySet());
		assertEquals(120, t.get(Stalls.Rotation.ORE).getSteals());
		assertEquals(40L, (long) t.get(Stalls.Rotation.ORE).getLoot().get(440));
		log.prune(NOW);
		long then = NOW - 61 * 86_400_000L;
		assertTrue(StallLog.decode(gson, log.encode(gson)).tallies(then).isEmpty());
		assertTrue(StallLog.decode(gson, "not json").isEmpty());
		assertTrue(StallLog.decode(gson, null).isEmpty());
		log.forget(Stalls.Rotation.GEM);
		assertFalse(log.isEmpty());
		log.forget(Stalls.Rotation.ORE);
		assertTrue(log.isEmpty());
	}
}
