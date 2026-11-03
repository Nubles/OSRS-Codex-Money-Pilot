package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class HoldingsTest
{
	private static Map<Integer, Long> map(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void placesAddUp()
	{
		Holdings h = Holdings.of(map(995, 100, 565, 10), map(995, 50), null);
		assertEquals(150, h.quantity(995));
		assertEquals(10, h.quantity(565));
	}

	@Test
	public void gainIsValuedAtOnePrice()
	{
		// 1,000 pure essence became 1,000 blood runes and 2,000 coins were spent on staminas.
		Holdings before = Holdings.of(map(7936, 1000, 995, 10_000));
		Holdings after = Holdings.of(map(565, 1000, 995, 8_000));
		Map<Integer, Long> price = map(7936, 2, 565, 330, 995, 1);
		long gained = after.gainedSince(before, id -> price.getOrDefault(id, 0L));
		assertEquals(1000 * 330 - 1000 * 2 - 2000, gained);
	}

	@Test
	public void untradeablesCountForNothing()
	{
		Holdings before = Holdings.EMPTY;
		Holdings after = Holdings.of(map(1, 5));
		assertEquals(0, after.gainedSince(before, id -> -1));
	}

	@Test
	public void encodesAndReadsBack()
	{
		Holdings h = Holdings.of(map(995, 12_345_678, 565, 3, 4151, 1));
		Holdings back = Holdings.decode(h.encode());
		assertEquals(h.getItems(), back.getItems());
		assertEquals("565:3,995:12345678,4151:1", h.encode());
	}

	@Test
	public void damagedTextKeepsWhatItCan()
	{
		Holdings h = Holdings.decode("995:10,x:1,,565:-4,4151:2,:9,12");
		assertEquals(10, h.quantity(995));
		assertEquals(2, h.quantity(4151));
		assertEquals(2, h.getItems().size());
		assertTrue(Holdings.decode(null).isEmpty());
	}
}
