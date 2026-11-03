package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void money()
	{
		assertEquals("3.44m", Format.gp(3_439_802));
		assertEquals("1.2m", Format.gp(1_200_000));
		assertEquals("640k", Format.gp(640_400));
		assertEquals("9,500", Format.gp(9_500));
		assertEquals("-250k", Format.gp(-250_000));
		assertEquals("+1.5b", Format.signed(1_500_000_000));
		assertEquals("100m", Format.gp(100_000_000));
		assertEquals("0", Format.signed(0));
	}

	@Test
	public void time()
	{
		assertEquals("1h 05m", Format.duration(3_900_000));
		assertEquals("12m", Format.duration(720_000));
		assertEquals("40s", Format.duration(40_000));
		assertEquals("1:05:09", Format.clock(3_909_000));
		assertEquals("5:09", Format.clock(309_000));
	}
}
