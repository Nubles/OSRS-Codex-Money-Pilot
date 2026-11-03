package com.codex.moneypilot.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToLongFunction;

/**
 * Everything the account holds that a run can change: the bank as last seen, the inventory and what
 * is worn, as item id to quantity (noted items already turned to their unnoted id). A run keeps the
 * holdings it began with and values the difference at today's prices, so a price moving during the
 * run is not counted as money made.
 */
public final class Holdings
{
	public static final Holdings EMPTY = new Holdings(Collections.emptyMap());

	private final Map<Integer, Long> items;

	public Holdings(Map<Integer, Long> items)
	{
		Map<Integer, Long> kept = new HashMap<>();
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			if (e.getKey() != null && e.getValue() != null && e.getValue() > 0)
			{
				kept.put(e.getKey(), e.getValue());
			}
		}
		this.items = Collections.unmodifiableMap(kept);
	}

	/** The holdings of several places added together. */
	@SafeVarargs
	public static Holdings of(Map<Integer, Long>... places)
	{
		Map<Integer, Long> sum = new HashMap<>();
		for (Map<Integer, Long> place : places)
		{
			if (place != null)
			{
				place.forEach((id, qty) ->
				{
					if (id != null && qty != null && qty > 0)
					{
						sum.merge(id, qty, Long::sum);
					}
				});
			}
		}
		return new Holdings(sum);
	}

	public Map<Integer, Long> getItems()
	{
		return items;
	}

	public long quantity(int id)
	{
		Long q = items.get(id);
		return q == null ? 0 : q;
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}

	/**
	 * What changed from {@code before} to these holdings, valued at {@code price}: positive when the
	 * account is worth more. Items with no price (untradeables) count for nothing.
	 */
	public long gainedSince(Holdings before, IntToLongFunction price)
	{
		Set<Integer> ids = new HashSet<>(items.keySet());
		ids.addAll(before.items.keySet());
		long total = 0;
		for (int id : ids)
		{
			long change = quantity(id) - before.quantity(id);
			if (change != 0)
			{
				total += change * Math.max(0, price.applyAsLong(id));
			}
		}
		return total;
	}

	/** A compact form for saving: "id:qty,id:qty". */
	public String encode()
	{
		StringBuilder sb = new StringBuilder();
		items.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue());
		});
		return sb.toString();
	}

	/** Reads {@link #encode()}'s form; a part that cannot be read is left out. */
	public static Holdings decode(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return EMPTY;
		}
		Map<Integer, Long> items = new HashMap<>();
		for (String part : text.split(","))
		{
			int colon = part.indexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(part.substring(0, colon).trim());
				long qty = Long.parseLong(part.substring(colon + 1).trim());
				if (id >= 0 && qty > 0 && qty <= Integer.MAX_VALUE * 4L)
				{
					items.merge(id, qty, Long::sum);
				}
			}
			catch (NumberFormatException e)
			{
				// This part only.
			}
		}
		return new Holdings(items);
	}
}
