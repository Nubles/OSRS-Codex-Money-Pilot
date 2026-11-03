package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class FitTest
{
	@Test
	public void levelShortLocks()
	{
		Account a = Accounts.all(70).level(Skill.RUNECRAFT, 74).done(Quest.SINS_OF_THE_FATHER).build();
		Fit f = Fit.of(Methods.byId("blood-runes"), a);
		assertEquals(Fit.Status.LOCKED, f.getStatus());
		assertEquals(1, f.getUnmet().size());
		assertEquals(3, f.getGap());
	}

	@Test
	public void questMissingWeighsMoreThanALevel()
	{
		Account a = Accounts.all(80).build();
		Fit f = Fit.of(Methods.byId("blood-runes"), a);
		assertEquals(Fit.Status.LOCKED, f.getStatus());
		assertEquals(Fit.UNLOCK_WEIGHT, f.getGap());
	}

	@Test
	public void allMetIsReadyAndSuggestionsMakeItAble()
	{
		Account ready = Accounts.all(80).done(Quest.SINS_OF_THE_FATHER).build();
		assertEquals(Fit.Status.READY, Fit.of(Methods.byId("blood-runes"), ready).getStatus());
		Account low = Accounts.all(80).level(Skill.FIREMAKING, 50).done(Quest.SINS_OF_THE_FATHER).build();
		Fit f = Fit.of(Methods.byId("blood-runes"), low);
		assertEquals(Fit.Status.ABLE, f.getStatus());
		assertEquals(Need.skill(Skill.FIREMAKING, 62), f.getShortOf().get(0));
	}

	@Test
	public void startedQuestCountsForRegicide()
	{
		Account a = Accounts.all(90).started(Quest.REGICIDE).build();
		assertTrue(Fit.of(Methods.byId("zulrah"), a).isOpen());
		Account none = Accounts.all(90).build();
		assertEquals(Fit.Status.LOCKED, Fit.of(Methods.byId("zulrah"), none).getStatus());
	}

	@Test
	public void trollStrongholdUnderwayCannotBeTold()
	{
		Account a = Accounts.all(90).started(Quest.TROLL_STRONGHOLD).build();
		Fit f = Fit.of(Methods.byId("graardor"), a);
		assertEquals(Fit.Status.CHECK, f.getStatus());
		assertTrue(f.isOpen());
		Account done = Accounts.all(90).done(Quest.TROLL_STRONGHOLD).build();
		assertEquals(Fit.Status.READY, Fit.of(Methods.byId("graardor"), done).getStatus());
	}

	@Test
	public void diaryIsReadFromItsVarbit()
	{
		Account without = Accounts.all(90).build();
		assertEquals(Fit.Status.LOCKED, Fit.of(Methods.byId("rogues-castle"), without).getStatus());
		Account with = Accounts.all(90).varbit(VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, 1)
			.varbit(VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, 1).build();
		assertEquals(Fit.Status.READY, Fit.of(Methods.byId("rogues-castle"), with).getStatus());
	}

	@Test
	public void notesAreNeverCounted()
	{
		Account a = Accounts.all(99).build();
		// The hydra needs a slayer task, which is told, not checked.
		assertEquals(Fit.Status.READY, Fit.of(Methods.byId("hydra"), a).getStatus());
	}

	@Test
	public void totalLevelGate()
	{
		Account low = Accounts.all(20).f2p().total(400).build();
		Fit f = Fit.of(Methods.byId("wine-of-zamorak-f2p"), low);
		assertEquals(Fit.Status.LOCKED, f.getStatus());
		assertEquals(13 + 100, f.getGap());
	}

	@Test
	public void farShortOfTheGuideIsAStretch()
	{
		// Chambers of Xeric has no gates, but a fresh account is nowhere near the guide's 90 Attack.
		Fit raid = Fit.of(Methods.byId("chambers-of-xeric"), Accounts.fresh().build());
		assertEquals(Fit.Status.STRETCH, raid.getStatus());
		assertTrue(!raid.isOpen());
		assertEquals(89 + 73, raid.getGap());
		// A few levels short is still open.
		Fit near = Fit.of(Methods.byId("chambers-of-xeric"), Accounts.all(80).level(Skill.ATTACK, 85).build());
		assertEquals(Fit.Status.ABLE, near.getStatus());
	}
}
