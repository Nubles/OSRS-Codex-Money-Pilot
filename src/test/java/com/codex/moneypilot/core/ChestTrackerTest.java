package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Before;
import org.junit.Test;

/**
 * Plays the game's side of the Rogues' Castle chests into the tracker a tick at a time: the menu choice,
 * the Thieving experience, hits, deaths, the pack and the looting bag, in the orders the client can
 * deliver them.
 */
public class ChestTrackerTest
{
	private static final long NOW = 1_800_000_000_000L;
	private static final int NATURE = ItemID.NATURERUNE;
	private static final int LAW = ItemID.LAWRUNE;
	private static final int SHARK = ItemID.SHARK;
	private static final int RESTORE4 = ItemID._4DOSE2RESTORE;
	private static final int RESTORE3 = ItemID._3DOSE2RESTORE;
	private static final int WHIP = ItemID.ABYSSAL_WHIP;
	private static final IntPredicate EDIBLE = id -> id == SHARK || id == RESTORE4 || id == RESTORE3;
	private static final Map<Integer, Long> PRICES = new HashMap<>();

	static
	{
		PRICES.put(ItemID.COINS, 1L);
		PRICES.put(NATURE, 100L);
		PRICES.put(LAW, 150L);
		PRICES.put(SHARK, 900L);
		PRICES.put(RESTORE4, 4_000L);
		PRICES.put(RESTORE3, 3_000L);
		PRICES.put(WHIP, 1_500_000L);
	}

	private ChestLog log;
	private ChestTracker t;
	private Map<Integer, Long> pack;
	private int xp = 5_000_000;

	@Before
	public void setUp()
	{
		log = new ChestLog();
		t = new ChestTracker(log);
		pack = new HashMap<>();
		pack.put(SHARK, 5L);
		pack.put(RESTORE4, 2L);
		t.pack(pack, false);
		t.worn(Collections.singletonMap(WHIP, 1L));
		t.thievingXp(xp, NOW);
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

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	/** Search a chest, walk a tick, then the experience and the loot come in on the same tick. */
	private void loot(int id, long qty)
	{
		t.chose(RoguesChests.SEARCH, ObjectID.WILDERNESS_ROGUE_CHEST);
		tick();
		xp += 702;
		t.thievingXp(xp, NOW);
		gain(id, qty);
		tick();
	}

	private ChestLog.Counts tally()
	{
		return log.tally(NOW);
	}

	@Test
	public void factsMatchTheWikiAndGameval()
	{
		// "They require 84 Thieving to open." / "granted 701.7 Thieving experience" / "34 game ticks (20.4 seconds)".
		assertEquals(84, RoguesChests.LEVEL);
		assertEquals(7017, RoguesChests.XP_TENTHS);
		assertEquals(34, RoguesChests.RESTOCK_TICKS);
		assertEquals(3, RoguesChests.CHESTS);
		// "Object IDs: 26757 and 26758" on the wiki; gameval WILDERNESS_ROGUE_CHEST(_OPEN).
		assertEquals(26757, RoguesChests.CHEST);
		assertEquals(26758, RoguesChests.CHEST_OPEN);
		assertTrue(RoguesChests.isTrapHit(16));
		assertTrue(RoguesChests.isTrapHit(43));
		assertFalse(RoguesChests.isTrapHit(15));
		assertFalse(RoguesChests.isTrapHit(44));
		Method m = Methods.byId(RoguesChests.METHOD);
		assertNotNull(m);
		assertTrue(m.isWilderness());
	}

	@Test
	public void aSearchedChestIsCountedWithItsLootAndExperience()
	{
		loot(NATURE, 40);
		ticks(3);
		ChestLog.Counts c = tally();
		assertEquals(1, c.getOpens());
		assertEquals(702, c.getXp());
		assertEquals(40L, (long) c.getLoot().get(NATURE));
		assertEquals(4_000, c.lootValue(ChestTrackerTest::price));
		assertTrue(t.isLooting());
	}

	@Test
	public void lootArrivingATickBeforeTheExperienceStillCounts()
	{
		t.chose(RoguesChests.SEARCH, ObjectID.WILDERNESS_ROGUE_CHEST);
		tick();
		gain(LAW, 40);
		tick();
		xp += 701;
		t.thievingXp(xp, NOW);
		ticks(3);
		assertEquals(40L, (long) tally().getLoot().get(LAW));
	}

	@Test
	public void experienceWithoutSearchingAChestIsNotAChest()
	{
		// Pickpocketing a rogue, say: Thieving experience, but no chest was chosen.
		tick();
		xp += 300;
		t.thievingXp(xp, NOW);
		gain(ItemID.COINS, 50);
		ticks(3);
		assertNull(tally());
		assertFalse(t.isLooting());
	}

	@Test
	public void aSearchLongAgoDoesNotClaimLaterExperience()
	{
		t.chose(RoguesChests.SEARCH, ObjectID.WILDERNESS_ROGUE_CHEST);
		ticks(ChestTracker.SEARCH_TICKS + 1);
		xp += 702;
		t.thievingXp(xp, NOW);
		ticks(3);
		assertNull(tally());
	}

	@Test
	public void timeCountsBetweenChestsButNotAcrossAnIdleGap()
	{
		loot(NATURE, 40);
		ticks(5);
		loot(LAW, 40);
		ticks(5);
		loot(NATURE, 40);
		ChestLog.Counts c = tally();
		assertEquals(3, c.getOpens());
		// 7 ticks from one chest's experience to the next, twice.
		assertEquals(14 * Run.TICK_MS, c.getPlayedMs());
		ticks(ChestTracker.IDLE_TICKS + 5);
		loot(LAW, 40);
		assertEquals(14 * Run.TICK_MS, tally().getPlayedMs());
		assertEquals(4, tally().getOpens());
		// The new sitting starts afresh.
		assertEquals(1, t.getLive().getOpens());
	}

	@Test
	public void openingWithoutSearchingAndTakingTheTrapIsCounted()
	{
		loot(NATURE, 40);
		t.chose(RoguesChests.OPEN, ObjectID.WILDERNESS_ROGUE_CHEST);
		tick();
		t.hit(31, NOW);
		ticks(2);
		assertEquals(1, tally().getTrapped());
		// A rogue's small hit after Open is not the trap.
		t.chose(RoguesChests.OPEN, ObjectID.WILDERNESS_ROGUE_CHEST);
		tick();
		t.hit(4, NOW);
		ticks(5);
		t.hit(30, NOW);
		assertEquals(1, tally().getTrapped());
	}

	@Test
	public void foodAndPotionDosesAreSuppliesLessWhatTheyLeave()
	{
		loot(NATURE, 40);
		ticks(3);
		gain(SHARK, -1);
		ticks(3);
		// A dose of super restore: the four-dose potion becomes a three-dose one.
		pack.merge(RESTORE4, -1L, Long::sum);
		pack.merge(RESTORE3, 1L, Long::sum);
		t.pack(pack, false);
		ticks(3);
		assertEquals(900 + 4_000 - 3_000, tally().usedValue(ChestTrackerTest::price));
		assertEquals(4_000 - 1_900, tally().profit(ChestTrackerTest::price));
	}

	@Test
	public void bankingIsNeitherLootNorSupplies()
	{
		loot(NATURE, 40);
		pack.remove(NATURE);
		pack.merge(SHARK, -1L, Long::sum);
		t.pack(pack, true);
		ticks(3);
		ChestLog.Counts c = tally();
		assertEquals(0, c.usedValue(ChestTrackerTest::price));
		assertEquals(4_000, c.lootValue(ChestTrackerTest::price));
	}

	@Test
	public void lootIntoAnOpenLootingBagCounts()
	{
		// "Loot goes automatically into open looting bags": the first sighting of the bag is only a start.
		Map<Integer, Long> bag = new HashMap<>();
		bag.put(LAW, 500L);
		t.bag(bag, false);
		t.chose(RoguesChests.SEARCH, ObjectID.WILDERNESS_ROGUE_CHEST);
		tick();
		xp += 702;
		t.thievingXp(xp, NOW);
		bag.put(NATURE, 40L);
		t.bag(bag, false);
		ticks(3);
		ChestLog.Counts c = tally();
		assertEquals(40L, (long) c.getLoot().get(NATURE));
		assertNull(c.getLoot().get(LAW));
	}

	@Test
	public void aDeathWhileLootingCountsWhatWasGoneAgainstTheProfit()
	{
		Map<Integer, Long> bag = new HashMap<>();
		t.bag(bag, false);
		loot(NATURE, 40);
		ticks(3);
		// Runes moved into the bag by hand, away from any chest: neither loot nor supplies.
		pack.remove(NATURE);
		t.pack(pack, false);
		bag.put(NATURE, 40L);
		t.bag(bag, false);
		ticks(3);
		t.died();
		tick();
		// Respawned with the three most valuable items kept: the whip and two potions; the rest is gone.
		pack.clear();
		pack.put(RESTORE4, 2L);
		t.pack(pack, false);
		ticks(ChestTracker.DEATH_TICKS + 1);
		ChestLog.Counts c = tally();
		assertEquals(1, c.getDeaths());
		// Five sharks from the pack and the forty nature runes in the bag; the worn whip is kept.
		assertEquals(5 * 900 + 40 * 100, c.lostValue(ChestTrackerTest::price));
		assertEquals(4_000 - 8_500, c.profit(ChestTrackerTest::price));
		assertEquals(4_000, c.lootValue(ChestTrackerTest::price));
	}

	@Test
	public void aDeathAwayFromTheChestsIsNotCounted()
	{
		t.died();
		pack.clear();
		t.pack(pack, false);
		ticks(ChestTracker.DEATH_TICKS + 2);
		assertNull(tally());
	}

	@Test
	public void theChestsInViewSayWhichAreReadyAndWhenTheNextRestocks()
	{
		assertEquals(-1, t.nextReady());
		t.chest(1, RoguesChests.CHEST);
		t.chest(2, RoguesChests.CHEST);
		t.chest(3, RoguesChests.CHEST);
		assertEquals(3, t.chestsReady());
		assertEquals(0, t.nextReady());
		// Chest 1 opens: the open chest spawns before or after the closed one goes.
		t.chest(1, RoguesChests.CHEST_OPEN);
		t.chestGone(1, RoguesChests.CHEST);
		t.chest(2, RoguesChests.CHEST_OPEN);
		t.chestGone(2, RoguesChests.CHEST);
		ticks(10);
		t.chestGone(3, RoguesChests.CHEST);
		t.chest(3, RoguesChests.CHEST_OPEN);
		assertEquals(3, t.chestsSeen());
		assertEquals(0, t.chestsReady());
		assertEquals(RoguesChests.RESTOCK_TICKS - 10, t.nextReady());
		ticks(RoguesChests.RESTOCK_TICKS);
		assertEquals(0, t.nextReady());
		t.chest(1, RoguesChests.CHEST);
		t.chestGone(1, RoguesChests.CHEST_OPEN);
		assertEquals(1, t.chestsReady());
		t.sceneCleared();
		assertEquals(0, t.chestsSeen());
	}

	@Test
	public void theLogKeepsItsDaysThroughSaving()
	{
		loot(NATURE, 40);
		ticks(5);
		loot(LAW, 40);
		ticks(3);
		Gson gson = new Gson();
		ChestLog read = ChestLog.decode(gson, log.encode(gson));
		ChestLog.Counts c = read.tally(NOW);
		assertEquals(2, c.getOpens());
		assertEquals(1404, c.getXp());
		assertEquals(10_000, c.lootValue(ChestTrackerTest::price));
		assertTrue(ChestLog.decode(gson, "not json").isEmpty());
		assertTrue(ChestLog.decode(gson, "[{\"d\":-4,\"o\":1}]").isEmpty());
	}

	@Test
	public void enoughLootingRanksTheMethodByYourOwnRate()
	{
		// Forty chests, 30 ticks apart: 1,170 ticks, under a quarter of an hour.
		for (int i = 0; i < 40; i++)
		{
			loot(NATURE, 40);
			ticks(28);
		}
		assertTrue(log.rates(NOW, ChestTrackerTest::price).isEmpty());
		for (int i = 0; i < 20; i++)
		{
			loot(NATURE, 40);
			ticks(28);
		}
		Map<String, Rate> rates = log.rates(NOW, ChestTrackerTest::price);
		Rate r = rates.get(RoguesChests.METHOD);
		assertNotNull(r);
		assertEquals(Rate.Source.CHESTS, r.getSource());
		assertEquals(60, r.getCount());
		// 59 gaps of 30 ticks; 60 chests of 4,000.
		assertEquals(Rate.perHour(240_000, 59 * 30 * Run.TICK_MS), r.getPerHour());
		Method m = Methods.byId(RoguesChests.METHOD);
		Rates ranked = Rates.of(Collections.emptyList(), Collections.emptyList(), null, rates, NOW);
		assertEquals(Rate.Source.CHESTS, ranked.best(m).getSource());
		assertEquals(r.getPerHour(), ranked.best(m).getPerHour());
	}

	@Test
	public void theRestartingClientDoesNotCountExperienceAsAChest()
	{
		t.reset();
		t.chose(RoguesChests.SEARCH, ObjectID.WILDERNESS_ROGUE_CHEST);
		// After a reset the first experience seen is only where it stands.
		t.thievingXp(xp + 702, NOW);
		ticks(3);
		assertNull(tally());
	}
}
