package com.codex.moneypilot.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;

/**
 * The account's own Rogues' Castle chest looting, a day at a time: chests opened, Thieving experience,
 * chests opened without searching (and hit by the trap), deaths, the time spent at it, what came out of the
 * chests, the supplies used and what was gone after a death, all as items so they are valued at today's
 * prices. Only the last {@link Rates#DAYS} days are kept. Saved to the RuneLite profile, so each account
 * has its own.
 */
public final class ChestLog
{
	private static final long DAY_MS = 86_400_000L;

	/** One day. Short field names keep the saved form small. */
	static final class Day
	{
		/** Day since 1970, UTC. */
		long d;
		/** Chests opened, chests opened without searching, deaths. */
		int o;
		int tr;
		int de;
		/** Game ticks spent at it, Thieving experience gained. */
		long tk;
		long xp;
		/** Item id to quantity: loot, supplies used (what came back, such as a vial, is negative), lost on deaths. */
		Map<Integer, Long> loot;
		Map<Integer, Long> used;
		Map<Integer, Long> lost;

		Day()
		{
		}

		Day(long day)
		{
			d = day;
		}

		boolean isValid()
		{
			return d > 0 && o >= 0 && tr >= 0 && de >= 0 && tk >= 0 && xp >= 0;
		}
	}

	/** What a stretch of looting added up to. */
	public static final class Counts
	{
		int opens;
		int trapped;
		int deaths;
		long ticks;
		long xp;
		final Map<Integer, Long> loot = new HashMap<>();
		final Map<Integer, Long> used = new HashMap<>();
		final Map<Integer, Long> lost = new HashMap<>();

		boolean isEmpty()
		{
			return opens == 0 && trapped == 0 && deaths == 0 && ticks == 0 && xp == 0 && loot.isEmpty() && used.isEmpty()
				&& lost.isEmpty();
		}

		void addAll(Counts o)
		{
			opens += o.opens;
			trapped += o.trapped;
			deaths += o.deaths;
			ticks += o.ticks;
			xp += o.xp;
			o.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			o.used.forEach((id, q) -> used.merge(id, q, Long::sum));
			o.lost.forEach((id, q) -> lost.merge(id, q, Long::sum));
		}

		void add(Day d)
		{
			opens += d.o;
			trapped += d.tr;
			deaths += d.de;
			ticks += d.tk;
			xp += d.xp;
			if (d.loot != null)
			{
				d.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			}
			if (d.used != null)
			{
				d.used.forEach((id, q) -> used.merge(id, q, Long::sum));
			}
			if (d.lost != null)
			{
				d.lost.forEach((id, q) -> lost.merge(id, q, Long::sum));
			}
		}

		public int getOpens()
		{
			return opens;
		}

		public int getTrapped()
		{
			return trapped;
		}

		public int getDeaths()
		{
			return deaths;
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
			return value(loot, price);
		}

		/** Supplies used, less what they leave behind; never below nothing. */
		public long usedValue(IntToLongFunction price)
		{
			return Math.max(0, value(used, price));
		}

		public long lostValue(IntToLongFunction price)
		{
			return Math.max(0, value(lost, price));
		}

		/** Loot less supplies and what was gone after deaths. */
		public long profit(IntToLongFunction price)
		{
			return lootValue(price) - usedValue(price) - lostValue(price);
		}

		public long perHour(IntToLongFunction price)
		{
			return Rate.perHour(profit(price), getPlayedMs());
		}

		public long xpPerHour()
		{
			return Rate.perHour(xp, getPlayedMs());
		}

		public long opensPerHour()
		{
			return Rate.perHour(opens, getPlayedMs());
		}

		/** The loot's worth per chest opened; 0 with none. */
		public long perOpen(IntToLongFunction price)
		{
			return opens == 0 ? 0 : lootValue(price) / opens;
		}

		/** Signed: a negative quantity is something that came back and is taken off. */
		static long value(Map<Integer, Long> items, IntToLongFunction price)
		{
			long total = 0;
			for (Map.Entry<Integer, Long> e : items.entrySet())
			{
				long each = Math.max(0, price.applyAsLong(e.getKey()));
				long q = e.getValue();
				if (each > 0 && q != 0 && Math.abs(q) <= Logged.MAX_WORTH / each)
				{
					total += q * each;
				}
			}
			return Math.max(-Logged.MAX_WORTH, Math.min(total, Logged.MAX_WORTH));
		}
	}

	private final List<Day> days = new ArrayList<>();

	public static long day(long millis)
	{
		return Math.floorDiv(millis, DAY_MS);
	}

	private Day today(long now)
	{
		long d = day(now);
		for (Day x : days)
		{
			if (x.d == d)
			{
				return x;
			}
		}
		Day x = new Day(d);
		days.add(x);
		return x;
	}

	public void add(long now, Counts c)
	{
		if (c == null || c.isEmpty())
		{
			return;
		}
		Day d = today(now);
		d.o += c.opens;
		d.tr += c.trapped;
		d.de += c.deaths;
		d.tk += c.ticks;
		d.xp += c.xp;
		d.loot = merge(d.loot, c.loot);
		d.used = merge(d.used, c.used);
		d.lost = merge(d.lost, c.lost);
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

	public boolean isEmpty()
	{
		return days.isEmpty();
	}

	/** The last {@link Rates#DAYS} days added up; null with no chest opened or lost. */
	public Counts tally(long now)
	{
		long today = day(now);
		long from = today - Rates.DAYS;
		Counts c = new Counts();
		for (Day d : days)
		{
			if (d.isValid() && d.d >= from && d.d <= today)
			{
				c.add(d);
			}
		}
		return c.opens == 0 && c.deaths == 0 && c.trapped == 0 ? null : c;
	}

	/** The method's rate an hour for ranking, once there is {@link Rates#ENOUGH_MS} of looting. */
	public Map<String, Rate> rates(long now, IntToLongFunction price)
	{
		Map<String, Rate> out = new HashMap<>();
		Counts c = tally(now);
		if (c != null && c.getPlayedMs() >= Rates.ENOUGH_MS)
		{
			out.put(RoguesChests.METHOD, new Rate(c.perHour(price), Rate.Source.CHESTS, c.getPlayedMs(), c.opens));
		}
		return out;
	}

	public String encode(Gson gson)
	{
		return gson.toJson(days);
	}

	/** Reads the saved log; an entry that cannot be read is left out, a damaged log starts again. */
	public static ChestLog decode(Gson gson, String json)
	{
		ChestLog log = new ChestLog();
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
