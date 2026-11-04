package com.codex.moneypilot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;

/**
 * What you would lose if you died right now, by the wiki's Items Kept on Death rules: "they keep the three
 * most valuable items they are carrying", "If a player is skulled the number of protected items is zero",
 * and "The Protect Item prayer always protects an additional item". Kept items are chosen "in order of
 * value", which here is RuneLite's own item price, so an item with no price is neither kept first nor
 * counted as lost. It is a figure to read, never a warning: Codex Guardian keeps watch for player killers.
 *
 * <p>A stack gives up its items one at a time, so three coins of a large stack are three kept items; the
 * wiki page does not say so outright, so the figure says "about".
 */
public final class DeathRisk
{
	/** Items kept by an account that is not skulled. */
	public static final int KEPT = 3;

	private final long lost;
	private final int kept;
	private final long keptValue;

	private DeathRisk(long lost, int kept, long keptValue)
	{
		this.lost = lost;
		this.kept = kept;
		this.keptValue = keptValue;
	}

	/**
	 * How many items the account keeps. Ultimate ironmen "are also unable to benefit from Protect Item
	 * despite being able to activate it".
	 */
	public static int kept(boolean skulled, boolean protectItem, boolean ultimate)
	{
		int n = skulled ? 0 : KEPT;
		if (protectItem && !ultimate)
		{
			n++;
		}
		return n;
	}

	/**
	 * The risk of carrying {@code carried} (pack and worn), keeping {@code keep} items, plus {@code bag}, the
	 * looting bag's contents, which are never kept.
	 */
	public static DeathRisk of(Map<Integer, Long> carried, Map<Integer, Long> bag, int keep, IntToLongFunction price)
	{
		List<long[]> priced = new ArrayList<>();
		if (carried != null)
		{
			for (Map.Entry<Integer, Long> e : carried.entrySet())
			{
				long each = Math.max(0, price.applyAsLong(e.getKey()));
				if (e.getValue() != null && e.getValue() > 0 && each > 0)
				{
					priced.add(new long[]{each, e.getValue()});
				}
			}
		}
		priced.sort((a, b) -> Long.compare(b[0], a[0]));
		long lost = 0;
		long keptValue = 0;
		int left = Math.max(0, keep);
		for (long[] p : priced)
		{
			long keepHere = Math.min(left, p[1]);
			left -= keepHere;
			keptValue = add(keptValue, keepHere, p[0]);
			lost = add(lost, p[1] - keepHere, p[0]);
		}
		if (bag != null)
		{
			for (Map.Entry<Integer, Long> e : bag.entrySet())
			{
				long each = Math.max(0, price.applyAsLong(e.getKey()));
				if (e.getValue() != null && e.getValue() > 0)
				{
					lost = add(lost, e.getValue(), each);
				}
			}
		}
		return new DeathRisk(lost, Math.max(0, keep), keptValue);
	}

	private static long add(long total, long qty, long each)
	{
		if (qty <= 0 || each <= 0)
		{
			return total;
		}
		if (qty > (Logged.MAX_WORTH - total) / each)
		{
			return Logged.MAX_WORTH;
		}
		return total + qty * each;
	}

	/** The value you would lose. */
	public long getLost()
	{
		return lost;
	}

	/** How many items you would keep. */
	public int getKept()
	{
		return kept;
	}

	/** The value of what you would keep. */
	public long getKeptValue()
	{
		return keptValue;
	}
}
