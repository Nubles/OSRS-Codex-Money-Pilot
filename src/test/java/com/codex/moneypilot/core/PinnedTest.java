package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * Pins what Money Pilot reads from the other Codex plugins. The JSON here is as Codex Gear Roadmap
 * (Savings.Goal, saved as codexgearroadmap.goal) and Codex Navigator (GoalBoard, saved as
 * codexnavigator.goalBoard) write it; if either changes shape, these break first.
 */
public class PinnedTest
{
	private final Gson gson = new Gson();

	@Test
	public void gearRoadmapGoalAsItIsSaved()
	{
		Pinned.Gear g = Pinned.gear(gson, "{\"name\":\"Twisted bow\",\"ids\":[20997]}");
		assertEquals("Twisted bow", g.getName());
		assertEquals(Arrays.asList(20997), g.getIds());
		Pinned.Gear set = Pinned.gear(gson, "{\"name\":\"Masori (f)\",\"ids\":[27235,27238,27241,27238]}");
		assertEquals(3, set.getIds().size());
	}

	@Test
	public void gearRoadmapGoalThatCannotBeReadIsNoGoal()
	{
		assertNull(Pinned.gear(gson, null));
		assertNull(Pinned.gear(gson, ""));
		assertNull(Pinned.gear(gson, "not json {"));
		assertNull(Pinned.gear(gson, "[1,2]"));
		assertNull(Pinned.gear(gson, "{\"name\":\"\",\"ids\":[20997]}"));
		assertNull(Pinned.gear(gson, "{\"name\":\"Bow\",\"ids\":[]}"));
		assertNull(Pinned.gear(gson, "{\"name\":\"Bow\",\"ids\":\"20997\"}"));
		assertNull(Pinned.gear(gson, "{\"name\":7,\"ids\":[20997]}"));
		// Ids that are not ids are left out; the rest stand.
		assertEquals(Arrays.asList(20997), Pinned.gear(gson, "{\"name\":\"Bow\",\"ids\":[-1,\"x\",1.5,20997,null]}").getIds());
	}

	@Test
	public void navigatorBoardAsItIsSaved()
	{
		String board = "{\"version\":1,\"focus\":\"diary:Ardougne Hard\",\"goals\":["
			+ "{\"key\":\"quest:Dragon Slayer II\",\"type\":\"quest\",\"title\":\"Dragon Slayer II\",\"complete\":false,\"progress\":0.42,"
			+ "\"next\":\"Start Legends' Quest\",\"hoursLeft\":11.5,\"skills\":{\"Magic\":75,\"Smithing\":70},"
			+ "\"quests\":[\"Legends' Quest\",\"Dragon Slayer II\"]},"
			+ "{\"key\":\"quest:Monkey Madness I\",\"type\":\"quest\",\"title\":\"Monkey Madness I\",\"complete\":true,\"progress\":1.0,"
			+ "\"hoursLeft\":0.0,\"skills\":{},\"quests\":[]},"
			+ "{\"key\":\"diary:Ardougne Hard\",\"type\":\"diary\",\"title\":\"Ardougne Hard\",\"complete\":false,\"progress\":0.8,"
			+ "\"hoursLeft\":2.0,\"skills\":{\"Thieving\":72},\"quests\":[]}]}";
		List<Pinned.Goal> goals = Pinned.navigator(gson, board);
		// The finished goal is left out; the focus leads.
		assertEquals(2, goals.size());
		assertEquals("Ardougne Hard", goals.get(0).getTitle());
		Pinned.Goal ds2 = goals.get(1);
		assertEquals("quest:Dragon Slayer II", ds2.getKey());
		assertEquals(11.5, ds2.getHoursLeft(), 1e-9);

		Account a = Accounts.all(80).done(Quest.PRIEST_IN_PERIL).build();
		assertTrue(ds2.opens(Fit.of(Methods.byId("vorkath"), a)));
		assertTrue(ds2.opens(Fit.of(Methods.byId("rune-dragons"), a)));
		// Hydra needs Slayer 95, which the goal does not train.
		assertFalse(ds2.opens(Fit.of(Methods.byId("hydra"), a)));
		// An open method is not "opened".
		assertFalse(ds2.opens(Fit.of(Methods.byId("barrows"), a)));
		// A level goal opens a method gated on that level alone.
		Pinned.Goal slayer = Pinned.navigator(gson,
			"{\"goals\":[{\"key\":\"skill:Slayer 87\",\"title\":\"Slayer 87\",\"skills\":{\"Slayer\":87},\"quests\":[]}]}").get(0);
		assertTrue(slayer.opens(Fit.of(Methods.byId("kraken"), Accounts.all(80).level(Skill.SLAYER, 80).build())));
	}

	@Test
	public void navigatorBoardThatCannotBeReadIsNoGoals()
	{
		assertTrue(Pinned.navigator(gson, null).isEmpty());
		assertTrue(Pinned.navigator(gson, "{").isEmpty());
		assertTrue(Pinned.navigator(gson, "{\"goals\":{}}").isEmpty());
		assertTrue(Pinned.navigator(gson, "{\"goals\":[1,null,{\"title\":\"no key\"},{\"key\":\"k\"}]}").isEmpty());
		// Bad skills and quests inside a good goal are left out, the goal kept.
		List<Pinned.Goal> g = Pinned.navigator(gson,
			"{\"goals\":[{\"key\":\"k\",\"title\":\"T\",\"hoursLeft\":\"x\",\"skills\":{\"Nope\":5,\"Agility\":\"60\",\"Magic\":120},\"quests\":[1,null]}]}");
		assertEquals(1, g.size());
		assertEquals(0, g.get(0).getHoursLeft(), 0);
	}
}
