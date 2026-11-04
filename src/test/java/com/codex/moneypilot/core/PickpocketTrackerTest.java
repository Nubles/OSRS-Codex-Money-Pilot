package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;

/**
 * Plays the game's side of pickpocketing into the tracker a tick at a time: the menu choice, the chat
 * lines and the pack, in the order the client can deliver them.
 */
public class PickpocketTrackerTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final int SEED = 5295;
	private static final int WINE = 1993;
	private static final int JUG = 1935;
	private static final int SHARK = 385;
	private static final IntPredicate EDIBLE = id -> id == WINE || id == SHARK;
	private static final Map<Integer, Long> PRICES = new HashMap<>();

	static
	{
		PRICES.put(ItemID.COINS, 1L);
		PRICES.put(SEED, 50_000L);
		PRICES.put(WINE, 10L);
		PRICES.put(SHARK, 900L);
	}

	private PickpocketLog log;
	private PickpocketTracker t;
	private Map<Integer, Long> pack;

	@Before
	public void setUp()
	{
		log = new PickpocketLog();
		t = new PickpocketTracker(log);
		pack = new HashMap<>();
		pack.put(WINE, 10L);
		t.pack(pack, false);
	}

	private void tick()
	{
		t.tick(EDIBLE, NOW);
	}

	private void ticks(int n)
	{
		for (int i = 0; i < n; i++)
		{
			tick();
		}
	}

	private void gain(int id, long qty)
	{
		pack.merge(id, qty, Long::sum);
		pack.values().removeIf(v -> v <= 0);
		t.pack(pack, false);
	}

	private PickpocketLog.Tally tally(Pickpockets.Target target)
	{
		return log.tallies(NOW).get(target);
	}

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	@Test
	public void masterFarmersAreKnownByName()
	{
		t.aim("Master Farmer");
		t.chat("You pick the Master Farmer's pocket.", NOW);
		gain(SEED, 1);
		tick();
		ticks(3);
		t.chat("You fail to pick the Master Farmer's pocket.", NOW);
		t.chat("You've been stunned!", NOW);
		ticks(8);
		t.chat("You pick the Master Farmer's pocket.", NOW);
		tick();

		PickpocketLog.Tally f = tally(Pickpockets.Target.MASTER_FARMERS);
		assertEquals(3, f.getAttempts());
		assertEquals(2, f.getSuccesses());
		assertEquals(1, f.getStuns());
		assertEquals(50_000, f.lootValue(PickpocketTrackerTest::price));
		// From the first pick to the last: 4 ticks then 8.
		assertEquals(12 * Run.TICK_MS, f.getPlayedMs());
		assertEquals(Pickpockets.Target.MASTER_FARMERS, t.getSitting());
		assertEquals(0, t.pouches());
	}

	@Test
	public void martinCountsAsAMasterFarmer()
	{
		t.aim("Martin the Master Gardener");
		t.chat("You pick Martin the Master Gardener's pocket.", NOW);
		tick();
		assertEquals(1, tally(Pickpockets.Target.MASTER_FARMERS).getSuccesses());
	}

	@Test
	public void picksWaitForThePouchThatSaysWhatTheyAre()
	{
		// A vyre's name says nothing; the fails before the first pouch wait for it.
		t.aim("Vallessia von Pitt");
		t.chat("You fail to pick Vallessia von Pitt's pocket.", NOW);
		t.chat("You've been stunned!", NOW);
		tick();
		assertTrue(log.tallies(NOW).isEmpty());
		ticks(8);
		// The pouch can arrive before the chat line in the same tick.
		gain(ItemID.PICKPOCKET_COIN_POUCH_VYRE, 1);
		t.chat("You pick Vallessia von Pitt's pocket.", NOW);
		tick();

		PickpocketLog.Tally v = tally(Pickpockets.Target.VYRES);
		assertEquals(2, v.getAttempts());
		assertEquals(1, v.getSuccesses());
		assertEquals(1, v.getStuns());
		// The pouch is not loot until it is opened.
		assertEquals(0, v.lootValue(PickpocketTrackerTest::price));
		assertEquals(1, t.pouches());
		assertEquals(Pickpockets.Target.VYRES, t.getSitting());

		// Later picks of the same vyre go straight in.
		ticks(2);
		t.chat("You pick Vallessia von Pitt's pocket.", NOW);
		gain(ItemID.PICKPOCKET_COIN_POUCH_VYRE, 1);
		tick();
		assertEquals(2, tally(Pickpockets.Target.VYRES).getSuccesses());
		assertEquals(2, t.pouches());
	}

	@Test
	public void openingPouchesCountsTheirCoinsForTheirTarget()
	{
		t.aim("Knight of Ardougne");
		for (int i = 0; i < 5; i++)
		{
			t.chat("You pick the knight's pocket.", NOW);
			gain(ItemID.PICKPOCKET_COIN_POUCH_KNIGHT, 1);
			ticks(2);
		}
		// Rogue equipment: a pouch and its worth in coins together.
		t.chat("You pick the knight's pocket.", NOW);
		gain(ItemID.PICKPOCKET_COIN_POUCH_KNIGHT, 1);
		gain(ItemID.COINS, 50);
		ticks(2);
		gain(ItemID.PICKPOCKET_COIN_POUCH_KNIGHT, -6);
		gain(ItemID.COINS, 300);
		tick();

		PickpocketLog.Tally k = tally(Pickpockets.Target.KNIGHTS);
		assertEquals(6, k.getSuccesses());
		assertEquals(6, k.getOpened());
		assertEquals(350, k.lootValue(PickpocketTrackerTest::price));
		assertEquals(10 * Run.TICK_MS, k.getPlayedMs());
		assertEquals(0, t.pouches());
	}

	@Test
	public void foodEatenWhilePickingIsCountedButNotBanking()
	{
		t.aim("Master Farmer");
		t.chat("You pick the Master Farmer's pocket.", NOW);
		ticks(3);
		// Drinking a jug of wine leaves the jug: food, not loot.
		gain(WINE, -1);
		gain(JUG, 1);
		tick();
		// Banking takes many items out at once.
		pack.put(WINE, 0L);
		pack.remove(JUG);
		t.pack(pack, true);
		tick();
		// A shark eaten.
		gain(SHARK, 2);
		t.pack(pack, true);
		tick();
		gain(SHARK, -1);
		tick();

		PickpocketLog.Tally f = tally(Pickpockets.Target.MASTER_FARMERS);
		assertEquals(2, f.getFood());
		assertEquals(910, f.foodValue(PickpocketTrackerTest::price));
		assertEquals(0, f.lootValue(PickpocketTrackerTest::price));
	}

	@Test
	public void aLongBreakIsNotCounted()
	{
		t.aim("Master Farmer");
		t.chat("You pick the Master Farmer's pocket.", NOW);
		ticks(PickpocketTracker.IDLE_TICKS + 1);
		assertNull(t.getSitting());
		t.chat("You pick the Master Farmer's pocket.", NOW);
		tick();
		assertEquals(0, tally(Pickpockets.Target.MASTER_FARMERS).getPlayedMs());
		// The sitting starts again.
		assertEquals(1, t.getLive().getSuccesses());
	}

	@Test
	public void otherPickpocketsAreNotLogged()
	{
		t.aim("Man");
		t.chat("You pick the man's pocket.", NOW);
		gain(ItemID.COINS, 3);
		tick();
		t.chat("You fail to pick the man's pocket.", NOW);
		tick();
		assertTrue(log.tallies(NOW).isEmpty());
		assertFalse(t.chat("You eat the shark.", NOW));
	}

	@Test
	public void stunWithoutAFailIsNotCounted()
	{
		t.chat("You've been stunned!", NOW);
		tick();
		assertTrue(log.tallies(NOW).isEmpty());
	}

	@Test
	public void rankingRateFollowsTheLog()
	{
		t.aim("Master Farmer");
		// 1,000 ticks (10 minutes) of picks two ticks apart, half a minute short of enough.
		for (int i = 0; i < 1000; i++)
		{
			if (i % 2 == 0)
			{
				t.chat("You pick the Master Farmer's pocket.", NOW);
				if (i % 20 == 0)
				{
					gain(SEED, 1);
				}
			}
			tick();
		}
		assertTrue(log.rates(NOW, PickpocketTrackerTest::price).isEmpty());
		for (int i = 0; i < 600; i++)
		{
			if (i % 2 == 0)
			{
				t.chat("You pick the Master Farmer's pocket.", NOW);
				if (i % 20 == 0)
				{
					gain(SEED, 1);
				}
			}
			tick();
		}
		Rate r = log.rates(NOW, PickpocketTrackerTest::price).get("master-farmers");
		assertEquals(Rate.Source.PICKPOCKETS, r.getSource());
		// 80 seeds at 50k over 1,598 ticks.
		assertEquals(Rate.perHour(80 * 50_000L, 1598 * Run.TICK_MS), r.getPerHour());

		Rates rates = Rates.of(java.util.Collections.emptyList(), java.util.Collections.emptyList(),
			log.rates(NOW, PickpocketTrackerTest::price), NOW);
		assertEquals(Rate.Source.PICKPOCKETS, rates.best(Methods.byId("master-farmers")).getSource());

		// The log saves and reads back the same.
		Gson gson = new Gson();
		PickpocketLog back = PickpocketLog.decode(gson, log.encode(gson));
		assertEquals(r.getPerHour(), back.rates(NOW, PickpocketTrackerTest::price).get("master-farmers").getPerHour());
	}
}
