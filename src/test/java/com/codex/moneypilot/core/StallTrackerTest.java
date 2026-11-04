package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Before;
import org.junit.Test;

/**
 * Plays the game's side of the Port Roberts stalls into the tracker a tick at a time: the menu choices,
 * the Thieving experience a steal gives, the pack, and a guard's arrest, in the orders the client can
 * deliver them. Steals come one every 5 ticks, as the wiki says.
 */
public class StallTrackerTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final int CANNON = ObjectID.PORT_ROBERTS_MARKET_STALL_CBALL;
	private static final int ORE = ObjectID.PORT_ROBERTS_MARKET_STALL_ORE;
	private static final int GEM = ObjectID.PORT_ROBERTS_MARKET_STALL_GEMS;
	private static final int FISH = ObjectID.PORT_ROBERTS_MARKET_STALL_FISH;
	private static final Map<Integer, Long> PRICES = new HashMap<>();

	static
	{
		PRICES.put(ItemID.COINS, 1L);
		PRICES.put(ItemID.BRONZE_CANNONBALL, 30L);
		PRICES.put(ItemID.RUNE_CANNONBALL, 400L);
		PRICES.put(ItemID.IRON_ORE, 100L);
		PRICES.put(ItemID.COAL, 150L);
		PRICES.put(ItemID.UNCUT_SAPPHIRE, 200L);
	}

	private StallLog log;
	private StallTracker t;
	private Map<Integer, Long> pack;
	private int xp = 3_000_000;

	@Before
	public void setUp()
	{
		log = new StallLog();
		t = new StallTracker(log);
		pack = new HashMap<>();
		pack.put(ItemID.COINS, 2_000L);
		t.pack(pack, false);
		t.thievingXp(xp, NOW);
	}

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	private void ticks(int n)
	{
		for (int i = 0; i < n; i++)
		{
			t.tick(NOW);
		}
	}

	/** One steal: experience and the item in the same tick, then the rest of the 5 tick cycle. */
	private void steal(int gain, int item)
	{
		xp += gain;
		t.thievingXp(xp, NOW);
		pack.merge(item, 1L, Long::sum);
		t.pack(pack, false);
		ticks(Stalls.STEAL_TICKS);
	}

	/** The wiki's rotation: the cannonball stall four times, the ore stall twice. */
	private void round()
	{
		t.chose(Stalls.STEAL, CANNON);
		ticks(1);
		for (int i = 0; i < 4; i++)
		{
			steal(223, i == 3 ? ItemID.RUNE_CANNONBALL : ItemID.BRONZE_CANNONBALL);
		}
		t.chose(Stalls.STEAL, ORE);
		ticks(1);
		steal(191, ItemID.IRON_ORE);
		steal(191, ItemID.COAL);
	}

	@Test
	public void aRoundIsFiledUnderTheCannonballRotation()
	{
		round();
		ticks(3);
		Map<Stalls.Rotation, StallLog.Counts> tally = log.tallies(NOW);
		StallLog.Counts c = tally.get(Stalls.Rotation.CANNONBALL);
		assertEquals(1, tally.size());
		assertEquals(6, c.getSteals());
		assertEquals(4 * 223 + 2 * 191, c.getXp());
		assertEquals(3 * 30 + 400 + 100 + 150, c.lootValue(StallTrackerTest::price));
		// From the first steal to the sixth: five gaps of a cycle, and the walk between the stalls.
		assertEquals(5 * Stalls.STEAL_TICKS + 1, c.getPlayedMs() / Run.TICK_MS);
		assertTrue(t.takeChanged());
		assertSame(Stalls.Rotation.CANNONBALL, t.getRotation());
	}

	@Test
	public void theFirstStealsWaitForTheRoundToShowTheRotation()
	{
		// Ore first: on their own the ore steals would be the ore rotation's.
		t.chose(Stalls.STEAL, ORE);
		ticks(1);
		steal(191, ItemID.IRON_ORE);
		steal(191, ItemID.IRON_ORE);
		ticks(2);
		assertTrue(log.isEmpty());
		t.chose(Stalls.STEAL, CANNON);
		ticks(1);
		for (int i = 0; i < 4; i++)
		{
			steal(223, ItemID.BRONZE_CANNONBALL);
		}
		ticks(2);
		Map<Stalls.Rotation, StallLog.Counts> tally = log.tallies(NOW);
		assertEquals(1, tally.size());
		assertEquals(6, tally.get(Stalls.Rotation.CANNONBALL).getSteals());
		assertEquals(200 + 4 * 30, tally.get(Stalls.Rotation.CANNONBALL).lootValue(StallTrackerTest::price));
	}

	@Test
	public void stealingGoesOnByItselfUntilAnotherAction()
	{
		t.chose(Stalls.STEAL, GEM);
		ticks(1);
		for (int i = 0; i < 6; i++)
		{
			steal(i % 2 == 0 ? 129 : 130, ItemID.UNCUT_SAPPHIRE);
		}
		assertEquals(6, log.tallies(NOW).get(Stalls.Rotation.GEM).getSteals());
		// Walking off stops it: experience from anything else is not a steal.
		t.other();
		ticks(1);
		xp += 130;
		t.thievingXp(xp, NOW);
		ticks(3);
		assertEquals(6, log.tallies(NOW).get(Stalls.Rotation.GEM).getSteals());
		// Nor is a chest or a door: only Steal-from on a market stall starts it.
		t.chose("Open", ObjectID.WILDERNESS_ROGUE_CHEST);
		xp += 130;
		t.thievingXp(xp, NOW);
		assertEquals(6, log.tallies(NOW).get(Stalls.Rotation.GEM).getSteals());
	}

	@Test
	public void sellingAndBankingAreNotLoot()
	{
		round();
		ticks(3);
		long loot = log.tallies(NOW).get(Stalls.Rotation.CANNONBALL).lootValue(StallTrackerTest::price);
		// Selling the ore to the trader: the ore goes, coins come, no steal.
		pack.remove(ItemID.IRON_ORE);
		pack.merge(ItemID.COINS, 90L, Long::sum);
		t.pack(pack, false);
		ticks(3);
		// Banking the cannonballs.
		pack.remove(ItemID.BRONZE_CANNONBALL);
		t.pack(pack, true);
		ticks(3);
		assertEquals(loot, log.tallies(NOW).get(Stalls.Rotation.CANNONBALL).lootValue(StallTrackerTest::price));
	}

	@Test
	public void anArrestAndTheBribeCountAgainstTheRate()
	{
		round();
		t.chose(Stalls.STEAL, CANNON);
		ticks(1);
		t.arrested(NOW);
		// The guard's words may come both overhead and in the chat: one arrest.
		t.arrested(NOW);
		ticks(20);
		// Jail experience (the lock) is not a steal.
		xp += 30;
		t.thievingXp(xp, NOW);
		pack.merge(ItemID.COINS, -500L, Long::sum);
		t.pack(pack, false);
		ticks(30);
		// Back at the stalls.
		round();
		ticks(3);
		StallLog.Counts c = log.tallies(NOW).get(Stalls.Rotation.CANNONBALL);
		assertEquals(12, c.getSteals());
		assertEquals(1, c.getCaught());
		assertEquals(500, c.paidValue(StallTrackerTest::price));
		assertEquals(2 * (3 * 30 + 400 + 100 + 150) - 500, c.profit(StallTrackerTest::price));
		// The time in the jail counts.
		assertTrue(c.getPlayedMs() / Run.TICK_MS > 2 * 26 + 50);
	}

	@Test
	public void anArrestWhileNotStealingIsNotCounted()
	{
		t.arrested(NOW);
		ticks(3);
		assertTrue(log.isEmpty());
	}

	@Test
	public void aLongGapEndsTheSittingAndIsNotCounted()
	{
		round();
		ticks(StallTracker.IDLE_TICKS + 10);
		assertFalse(t.isStealing());
		round();
		ticks(3);
		StallLog.Counts c = log.tallies(NOW).get(Stalls.Rotation.CANNONBALL);
		assertEquals(12, c.getSteals());
		assertEquals(2 * (5 * Stalls.STEAL_TICKS + 1), c.getPlayedMs() / Run.TICK_MS);
	}

	@Test
	public void aShortSittingIsFiledWhenItEnds()
	{
		t.chose(Stalls.STEAL, CANNON);
		ticks(1);
		steal(223, ItemID.BRONZE_CANNONBALL);
		steal(223, ItemID.BRONZE_CANNONBALL);
		ticks(2);
		assertTrue(log.isEmpty());
		t.flush(NOW);
		assertEquals(2, log.tallies(NOW).get(Stalls.Rotation.CANNONBALL).getSteals());
	}

	@Test
	public void stallsBelowSilverAreNotFiled()
	{
		t.chose(Stalls.STEAL, FISH);
		ticks(1);
		for (int i = 0; i < 8; i++)
		{
			steal(i % 2 == 0 ? 49 : 50, ItemID.COAL);
		}
		t.flush(NOW);
		assertTrue(log.isEmpty());
		assertNull(t.getRotation());
		assertSame(Stalls.Stall.FISH, t.getLastStall());
		assertEquals(8, t.getLive().getSteals());
	}

	@Test
	public void aBoostedStealIsTheChosenStall()
	{
		t.chose(Stalls.STEAL, ORE);
		ticks(1);
		for (int i = 0; i < 6; i++)
		{
			// A boost the stall pages do not give: the gain names no stall, so the stall chosen is used.
			steal(210, ItemID.IRON_ORE);
		}
		assertEquals(6, log.tallies(NOW).get(Stalls.Rotation.ORE).getSteals());
		// Far more than a steal is not one.
		xp += 5_000;
		t.thievingXp(xp, NOW);
		ticks(3);
		assertEquals(6, log.tallies(NOW).get(Stalls.Rotation.ORE).getSteals());
	}

	@Test
	public void resetForgetsTheSitting()
	{
		round();
		t.reset();
		assertFalse(t.isStealing());
		assertEquals(-1, t.sinceLastSteal());
		assertEquals(0, t.getLive().getSteals());
	}
}
