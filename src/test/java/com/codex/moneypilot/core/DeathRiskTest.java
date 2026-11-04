package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** What a death now would cost, by the wiki's Items Kept on Death rules. */
public class DeathRiskTest
{
	private static final int WHIP = 1;
	private static final int BOOTS = 2;
	private static final int GLOVES = 3;
	private static final int CAPE = 4;
	private static final int RUNE = 5;
	private static final int UNTRADEABLE = 6;
	private static final Map<Integer, Long> PRICES = new HashMap<>();

	static
	{
		PRICES.put(WHIP, 1_500_000L);
		PRICES.put(BOOTS, 800_000L);
		PRICES.put(GLOVES, 200_000L);
		PRICES.put(CAPE, 50_000L);
		PRICES.put(RUNE, 100L);
	}

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	private static Map<Integer, Long> carried()
	{
		Map<Integer, Long> m = new HashMap<>();
		m.put(WHIP, 1L);
		m.put(BOOTS, 1L);
		m.put(GLOVES, 1L);
		m.put(CAPE, 1L);
		m.put(RUNE, 1_000L);
		m.put(UNTRADEABLE, 1L);
		return m;
	}

	@Test
	public void keepsThreeUnskulledNoneSkulledAndOneMoreWithProtectItem()
	{
		// "they keep the three most valuable items", "If a player is skulled the number of protected items is zero",
		// "The Protect Item prayer always protects an additional item, no matter the skull status".
		assertEquals(3, DeathRisk.kept(false, false, false));
		assertEquals(4, DeathRisk.kept(false, true, false));
		assertEquals(0, DeathRisk.kept(true, false, false));
		assertEquals(1, DeathRisk.kept(true, true, false));
		// Ultimate ironmen "are also unable to benefit from Protect Item despite being able to activate it".
		assertEquals(3, DeathRisk.kept(false, true, true));
	}

	@Test
	public void theMostValuableItemsAreKept()
	{
		DeathRisk r = DeathRisk.of(carried(), null, 3, DeathRiskTest::price);
		assertEquals(2_500_000, r.getKeptValue());
		assertEquals(50_000 + 100_000, r.getLost());
		DeathRisk prot = DeathRisk.of(carried(), null, 4, DeathRiskTest::price);
		assertEquals(100_000, prot.getLost());
		DeathRisk skulled = DeathRisk.of(carried(), null, 0, DeathRiskTest::price);
		assertEquals(2_650_000, skulled.getLost());
	}

	@Test
	public void aStackGivesUpItsItemsOneAtATime()
	{
		Map<Integer, Long> m = new HashMap<>();
		m.put(RUNE, 1_000L);
		m.put(CAPE, 1L);
		DeathRisk r = DeathRisk.of(m, null, 3, DeathRiskTest::price);
		// The cape and two runes are kept.
		assertEquals(998 * 100, r.getLost());
	}

	@Test
	public void theLootingBagIsNeverKept()
	{
		Map<Integer, Long> bag = Collections.singletonMap(WHIP, 1L);
		DeathRisk r = DeathRisk.of(Collections.singletonMap(CAPE, 1L), bag, 3, DeathRiskTest::price);
		assertEquals(1_500_000, r.getLost());
		assertEquals(50_000, r.getKeptValue());
	}

	@Test
	public void nothingCarriedRisksNothing()
	{
		assertEquals(0, DeathRisk.of(null, null, 3, DeathRiskTest::price).getLost());
		assertEquals(0, DeathRisk.of(Collections.singletonMap(UNTRADEABLE, 1L), null, 0, DeathRiskTest::price).getLost());
	}
}
