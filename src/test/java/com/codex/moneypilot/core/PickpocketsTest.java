package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The facts the pickpocket log leans on, as checked on the wiki and in RuneLite (see the pull request). */
public class PickpocketsTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@Test
	public void pouchCapFollowsTheArdougneDiary()
	{
		// Coin pouch: 28, then "56, 84, and 140 after completing the Medium, Hard, and Elite diaries".
		assertEquals(28, Pickpockets.pouchCap(false, false, false));
		assertEquals(56, Pickpockets.pouchCap(true, false, false));
		assertEquals(84, Pickpockets.pouchCap(true, true, false));
		assertEquals(140, Pickpockets.pouchCap(true, true, true));
	}

	@Test
	public void pouchesSayWhichNpc()
	{
		assertEquals(22531, ItemID.PICKPOCKET_COIN_POUCH_KNIGHT);
		assertEquals(22535, ItemID.PICKPOCKET_COIN_POUCH_PALADIN);
		assertEquals(22538, ItemID.PICKPOCKET_COIN_POUCH_ELF);
		assertEquals(24703, ItemID.PICKPOCKET_COIN_POUCH_VYRE);
		assertEquals(Pickpockets.Target.KNIGHTS, Pickpockets.Target.ofPouch(22531));
		assertEquals(Pickpockets.Target.PALADINS, Pickpockets.Target.ofPouch(22535));
		assertEquals(Pickpockets.Target.ELVES, Pickpockets.Target.ofPouch(22538));
		assertEquals(Pickpockets.Target.VYRES, Pickpockets.Target.ofPouch(24703));
		assertNull(Pickpockets.Target.ofPouch(ItemID.PICKPOCKET_COIN_POUCH_HERO));
		assertEquals(Pickpockets.Target.MASTER_FARMERS, Pickpockets.Target.ofName("Master Farmer"));
		assertNull(Pickpockets.Target.ofName("Knight of Ardougne"));
	}

	@Test
	public void readsTheGamesLines()
	{
		assertEquals(Pickpockets.Said.SUCCESS, Pickpockets.read("You pick the knight's pocket."));
		assertEquals(Pickpockets.Said.FAIL, Pickpockets.read("You fail to pick the knight's pocket."));
		assertEquals(Pickpockets.Said.STUNNED, Pickpockets.read("You've been stunned!"));
		assertEquals(Pickpockets.Said.NONE, Pickpockets.read("You attempt to pick the knight's pocket."));
		assertEquals(Pickpockets.Said.NONE, Pickpockets.read("You pick some flax."));
		assertEquals(Pickpockets.Said.NONE, Pickpockets.read(null));
	}

	@Test
	public void everyTargetFeedsAMethod()
	{
		for (Pickpockets.Target t : Pickpockets.Target.values())
		{
			Method m = Methods.byId(t.getMethod());
			assertNotNull(t.name(), m);
			assertEquals(Skill.THIEVING, m.getSkill());
		}
	}

	/** The pickpocketing guides and NPC pages, read 4 November 2026. */
	@Test
	public void methodsMatchTheWiki()
	{
		assertNeed("ardougne-knights", Need.skill(Skill.THIEVING, 55));
		assertNeed("paladins", Need.skill(Skill.THIEVING, 70));
		assertNeed("master-farmers", Need.skill(Skill.THIEVING, 38));
		assertNeed("vyres", Need.skill(Skill.THIEVING, 82));
		assertNeed("vyres", Need.quest(Quest.SINS_OF_THE_FATHER));
		assertNeed("elves", Need.skill(Skill.THIEVING, 85));
		assertNeed("elves", Need.quest(Quest.SONG_OF_THE_ELVES));
		assertEquals(1, Methods.byId("ardougne-knights").getNeeds().size());
		assertEquals(1, Methods.byId("paladins").getNeeds().size());
		assertEquals(1, Methods.byId("master-farmers").getNeeds().size());
		assertEquals(265_455, Methods.byId("ardougne-knights").getWikiPerHour());
		assertEquals(551_649, Methods.byId("paladins").getWikiPerHour());
		assertEquals(865_529, Methods.byId("master-farmers").getWikiPerHour());
		assertEquals("https://oldschool.runescape.wiki/w/Money_making_guide/Pickpocketing_Ardougne_knights",
			Methods.byId("ardougne-knights").getUrl());
	}

	private static void assertNeed(String id, Need need)
	{
		assertTrue(id + " should need " + need, Methods.byId(id).getNeeds().contains(need));
	}

	@Test
	public void clockedRunsBeatPicksBeatSessions()
	{
		Method elves = Methods.byId("elves");
		Map<String, Rate> picks = new HashMap<>();
		picks.put("elves", new Rate(2_500_000, Rate.Source.PICKPOCKETS, HOUR, 500));
		Rates withSessions = Rates.of(Collections.emptyList(),
			Collections.singletonList(new Logged(NOW - 3 * HOUR, HOUR, "Thieving", 1_000_000, true)), picks, NOW);
		assertEquals(Rate.Source.PICKPOCKETS, withSessions.best(elves).getSource());
		assertEquals(2_500_000, withSessions.best(elves).getPerHour());

		Rates withRuns = Rates.of(Collections.singletonList(new Clocked("elves", NOW - HOUR, HOUR, 3_000_000)),
			Collections.emptyList(), picks, NOW);
		assertEquals(Rate.Source.YOURS, withRuns.best(elves).getSource());
		assertEquals(2_500_000, withRuns.pickpockets(elves).getPerHour());

		// Too little picking does not rank.
		picks.put("elves", new Rate(9_000_000, Rate.Source.PICKPOCKETS, 60_000L, 20));
		assertEquals(Rate.Source.WIKI, Rates.of(Collections.emptyList(), Collections.emptyList(), picks, NOW).best(elves).getSource());
	}

	@Test
	public void oldDaysAndDamagedSavesAreDropped()
	{
		Gson gson = new Gson();
		long today = PickpocketLog.day(NOW);
		String json = "[{\"t\":\"ELVES\",\"d\":" + today + ",\"s\":10,\"f\":2,\"st\":2,\"po\":0,\"fo\":1,\"tk\":3000},"
			+ "{\"t\":\"ELVES\",\"d\":" + (today - 61) + ",\"s\":99,\"f\":0,\"st\":0,\"po\":0,\"fo\":0,\"tk\":100},"
			+ "{\"t\":\"GOBLINS\",\"d\":" + today + ",\"s\":1},"
			+ "{\"t\":\"VYRES\",\"d\":" + today + ",\"s\":-4}]";
		PickpocketLog log = PickpocketLog.decode(gson, json);
		Map<Pickpockets.Target, PickpocketLog.Tally> t = log.tallies(NOW);
		assertEquals(Collections.singleton(Pickpockets.Target.ELVES), t.keySet());
		assertEquals(12, t.get(Pickpockets.Target.ELVES).getAttempts());
		// Seen from 61 days ago the old day still counts; pruned, it is gone from the save.
		long then = NOW - 61 * 86_400_000L;
		assertEquals(99, log.tallies(then).get(Pickpockets.Target.ELVES).getSuccesses());
		log.prune(NOW);
		assertTrue(PickpocketLog.decode(gson, log.encode(gson)).tallies(then).isEmpty());
		assertTrue(PickpocketLog.decode(gson, "not json").isEmpty());
		assertTrue(PickpocketLog.decode(gson, null).isEmpty());
		log.forget(Pickpockets.Target.ELVES);
		assertTrue(log.isEmpty());
		assertTrue(log.tallies(NOW).isEmpty());
	}
}
