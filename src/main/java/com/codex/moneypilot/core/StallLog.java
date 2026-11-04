package com.codex.moneypilot.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;

/**
 * The account's own stall thieving at Port Roberts, a day and a rotation at a time: steals, times a
 * Market Guard caught you, the time spent at it, Thieving experience, what the stalls gave and the coins
 * a bribe to leave the jail cost, all as items so they are valued at today's prices. Only the last
 * {@link Rates#DAYS} days are kept. Saved to the RuneLite profile, so each account has its own.
 */
public final class StallLog
{
	private static final long DAY_MS = 86_400_000L;

	/** One rotation on one day. Short field names keep the saved form small. */
	static final class Day
	{
		/** Day since 1970, UTC. */
		long d;
		/** The method id of the rotation. */
		String m;
		/** Steals, times caught. */
		int s;
		int c;
		/** Game ticks spent at it, Thieving experience gained. */
		long tk;
		long xp;
		/** Item id to quantity: what the stalls gave, and what was paid out (a bribe). */
		Map<Integer, Long> loot;
		Map<Integer, Long> paid;

		Day()
		{
		}

		Day(long day, String method)
		{
			d = day;
			m = method;
		}

		boolean isValid()
		{
			return d > 0 && Stalls.Rotation.ofMethod(m) != null && s >= 0 && c >= 0 && tk >= 0 && xp >= 0;
		}
	}

	/** What a stretch of stealing added up to. */
	public static final class Counts
	{
		int steals;
		int caught;
		long ticks;
		long xp;
		final Map<Integer, Long> loot = new HashMap<>();
		final Map<Integer, Long> paid = new HashMap<>();

		boolean isEmpty()
		{
			return steals == 0 && caught == 0 && ticks == 0 && xp == 0 && loot.isEmpty() && paid.isEmpty();
		}

		void addAll(Counts o)
		{
			steals += o.steals;
			caught += o.caught;
			ticks += o.ticks;
			xp += o.xp;
			o.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			o.paid.forEach((id, q) -> paid.merge(id, q, Long::sum));
		}

		void add(Day d)
		{
			steals += d.s;
			caught += d.c;
			ticks += d.tk;
			xp += d.xp;
			if (d.loot != null)
			{
				d.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			}
			if (d.paid != null)
			{
				d.paid.forEach((id, q) -> paid.merge(id, q, Long::sum));
			}
		}

		public int getSteals()
		{
			return steals;
		}

		public int getCaught()
		{
			return caught;
		}

		public long getXp()
		{
			return xp;
		}

		public long getPlayedMs()
		{
			return ticks * Run.TICK_MS;
		}

		public Map<Integer, Long> getLoot()
		{
			return Collections.unmodifiableMap(loot);
		}

		public long lootValue(IntToLongFunction price)
		{
			return ChestLog.Counts.value(loot, price);
		}

		public long paidValue(IntToLongFunction price)
		{
			return Math.max(0, ChestLog.Counts.value(paid, price));
		}

		/** What the stalls gave less what getting out of the jail cost. */
		public long profit(IntToLongFunction price)
		{
			return lootValue(price) - paidValue(price);
		}

		public long perHour(IntToLongFunction price)
		{
			return Rate.perHour(profit(price), getPlayedMs());
		}

		public long xpPerHour()
		{
			return Rate.perHour(xp, getPlayedMs());
		}

		public long stealsPerHour()
		{
			return Rate.perHour(steals, getPlayedMs());
		}

		/** The loot's worth per steal; 0 with none. */
		public long perSteal(IntToLongFunction price)
		{
			return steals == 0 ? 0 : lootValue(price) / steals;
		}
	}

	private final List<Day> days = new ArrayList<>();

	public static long day(long millis)
	{
		return Math.floorDiv(millis, DAY_MS);
	}

	private Day today(long now, String method)
	{
		long d = day(now);
		for (Day x : days)
		{
			if (x.d == d && method.equals(x.m))
			{
				return x;
			}
		}
		Day x = new Day(d, method);
		days.add(x);
		return x;
	}

	public void add(long now, Stalls.Rotation rotation, Counts c)
	{
		if (rotation == null || c == null || c.isEmpty())
		{
			return;
		}
		Day d = today(now, rotation.getMethod());
		d.s += c.steals;
		d.c += c.caught;
		d.tk += c.ticks;
		d.xp += c.xp;
		d.loot = merge(d.loot, c.loot);
		d.paid = merge(d.paid, c.paid);
	}

	private static Map<Integer, Long> merge(Map<Integer, Long> into, Map<Integer, Long> from)
	{
		if (from.isEmpty())
		{
			return into;
		}
		Map<Integer, Long> out = into == null ? new HashMap<>() : into;
		from.forEach((id, q) -> out.merge(id, q, Long::sum));
		out.values().removeIf(v -> v == 0);
		return out;
	}

	public void prune(long now)
	{
		long from = day(now) - Rates.DAYS;
		days.removeIf(d -> d == null || !d.isValid() || d.d < from);
	}

	public void forget()
	{
		days.clear();
	}

	/** Forgets one rotation's days. */
	public void forget(Stalls.Rotation rotation)
	{
		if (rotation != null)
		{
			days.removeIf(d -> rotation.getMethod().equals(d.m));
		}
	}

	public boolean isEmpty()
	{
		return days.isEmpty();
	}

	/** The last {@link Rates#DAYS} days added up by rotation, best stalls first; rotations with no steal are left out. */
	public Map<Stalls.Rotation, Counts> tallies(long now)
	{
		long today = day(now);
		long from = today - Rates.DAYS;
		Map<Stalls.Rotation, Counts> out = new EnumMap<>(Stalls.Rotation.class);
		for (Day d : days)
		{
			if (d.isValid() && d.d >= from && d.d <= today)
			{
				out.computeIfAbsent(Stalls.Rotation.ofMethod(d.m), k -> new Counts()).add(d);
			}
		}
		out.values().removeIf(c -> c.steals == 0);
		return out;
	}

	/** Each rotation's rate an hour for ranking, once it has {@link Rates#ENOUGH_MS} of stealing. */
	public Map<String, Rate> rates(long now, IntToLongFunction price)
	{
		Map<String, Rate> out = new HashMap<>();
		tallies(now).forEach((rotation, c) ->
		{
			if (c.getPlayedMs() >= Rates.ENOUGH_MS)
			{
				out.put(rotation.getMethod(), new Rate(c.perHour(price), Rate.Source.STALLS, c.getPlayedMs(), c.steals));
			}
		});
		return out;
	}

	public String encode(Gson gson)
	{
		return gson.toJson(days);
	}

	/** Reads the saved log; an entry that cannot be read is left out, a damaged log starts again. */
	public static StallLog decode(Gson gson, String json)
	{
		StallLog log = new StallLog();
		if (json == null || json.trim().isEmpty())
		{
			return log;
		}
		try
		{
			List<Day> read = gson.fromJson(json, new TypeToken<List<Day>>()
			{
			}.getType());
			if (read != null)
			{
				for (Day d : read)
				{
					if (d != null && d.isValid())
					{
						log.days.add(d);
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			// A damaged log is started again rather than breaking the panel.
		}
		return log;
	}
}
