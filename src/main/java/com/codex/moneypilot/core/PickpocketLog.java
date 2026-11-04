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
 * The account's own pickpocketing, a day and a target at a time: picks tried, picks that worked, stuns,
 * coin pouches opened, food eaten, the time spent at it and what it brought in, as items. Items are
 * kept rather than coins so they are valued at today's prices, as clocked runs are. Only the last
 * {@link Rates#DAYS} days are kept. Saved to the RuneLite profile, so each account has its own.
 */
public final class PickpocketLog
{
	private static final long DAY_MS = 86_400_000L;

	/** One target on one day. Short field names keep the saved form small. */
	public static final class Day
	{
		/** Target. */
		String t;
		/** Day since 1970, UTC. */
		long d;
		/** Picks that worked, picks that failed, stuns, pouches opened, food and drink used. */
		int s;
		int f;
		int st;
		int po;
		int fo;
		/** Game ticks spent at it. */
		long tk;
		/** Item id to quantity gained, and food used. */
		Map<Integer, Long> loot;
		Map<Integer, Long> food;

		Day()
		{
		}

		Day(Pickpockets.Target target, long day)
		{
			t = target.name();
			d = day;
		}

		Pickpockets.Target target()
		{
			return Pickpockets.Target.of(t);
		}

		boolean isValid()
		{
			return target() != null && d > 0 && s >= 0 && f >= 0 && st >= 0 && po >= 0 && fo >= 0 && tk >= 0;
		}
	}

	/** What the log adds up to for one target. */
	public static final class Tally
	{
		private final Pickpockets.Target target;
		private int successes;
		private int fails;
		private int stuns;
		private int opened;
		private int food;
		private long ticks;
		private final Map<Integer, Long> loot = new HashMap<>();
		private final Map<Integer, Long> eaten = new HashMap<>();

		Tally(Pickpockets.Target target)
		{
			this.target = target;
		}

		void add(Day d)
		{
			successes += d.s;
			fails += d.f;
			stuns += d.st;
			opened += d.po;
			food += d.fo;
			ticks += d.tk;
			if (d.loot != null)
			{
				d.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			}
			if (d.food != null)
			{
				d.food.forEach((id, q) -> eaten.merge(id, q, Long::sum));
			}
		}

		public Pickpockets.Target getTarget()
		{
			return target;
		}

		public int getAttempts()
		{
			return successes + fails;
		}

		public int getSuccesses()
		{
			return successes;
		}

		public int getFails()
		{
			return fails;
		}

		public int getStuns()
		{
			return stuns;
		}

		public int getOpened()
		{
			return opened;
		}

		public int getFood()
		{
			return food;
		}

		public long getPlayedMs()
		{
			return ticks * Run.TICK_MS;
		}

		public Map<Integer, Long> getLoot()
		{
			return Collections.unmodifiableMap(loot);
		}

		/** Your own success rate, 0 to 1; 0 with no picks. */
		public double successRate()
		{
			int a = getAttempts();
			return a == 0 ? 0 : successes / (double) a;
		}

		/** What the loot is worth at {@code price}, which gives 1 for a coin. */
		public long lootValue(IntToLongFunction price)
		{
			return value(loot, price);
		}

		public long foodValue(IntToLongFunction price)
		{
			return value(eaten, price);
		}

		/** Loot less the food used. */
		public long profit(IntToLongFunction price)
		{
			return lootValue(price) - foodValue(price);
		}

		public long perHour(IntToLongFunction price)
		{
			return Rate.perHour(profit(price), getPlayedMs());
		}

		/** Food and drink used an hour; 0 with too little time to say. */
		public double foodPerHour()
		{
			long ms = getPlayedMs();
			return ms < 60_000L ? 0 : food * 3_600_000.0 / ms;
		}

		/** Picks an hour, the guides' measure. */
		public long picksPerHour()
		{
			return Rate.perHour(successes, getPlayedMs());
		}

		private static long value(Map<Integer, Long> items, IntToLongFunction price)
		{
			long total = 0;
			for (Map.Entry<Integer, Long> e : items.entrySet())
			{
				long each = Math.max(0, price.applyAsLong(e.getKey()));
				if (each > 0 && e.getValue() > 0 && e.getValue() <= Logged.MAX_WORTH / each)
				{
					total += e.getValue() * each;
				}
			}
			return Math.min(total, Logged.MAX_WORTH);
		}
	}

	private final List<Day> days = new ArrayList<>();

	public PickpocketLog()
	{
	}

	public static long day(long millis)
	{
		return Math.floorDiv(millis, DAY_MS);
	}

	/** The day entry for a target, made when there is none. */
	Day today(Pickpockets.Target target, long now)
	{
		long d = day(now);
		for (Day x : days)
		{
			if (x.d == d && target.name().equals(x.t))
			{
				return x;
			}
		}
		Day x = new Day(target, d);
		days.add(x);
		return x;
	}

	/** Adds what a sitting picked up to today's entry for its target. */
	public void add(Pickpockets.Target target, long now, Counts c)
	{
		if (target == null || c == null || c.isEmpty())
		{
			return;
		}
		Day d = today(target, now);
		d.s += c.successes;
		d.f += c.fails;
		d.st += c.stuns;
		d.po += c.opened;
		d.fo += c.food;
		d.tk += c.ticks;
		if (!c.loot.isEmpty())
		{
			if (d.loot == null)
			{
				d.loot = new HashMap<>();
			}
			c.loot.forEach((id, q) -> d.loot.merge(id, q, Long::sum));
		}
		if (!c.eaten.isEmpty())
		{
			if (d.food == null)
			{
				d.food = new HashMap<>();
			}
			c.eaten.forEach((id, q) -> d.food.merge(id, q, Long::sum));
		}
	}

	/** Drops days older than the window. */
	public void prune(long now)
	{
		long from = day(now) - Rates.DAYS;
		days.removeIf(d -> d == null || !d.isValid() || d.d < from);
	}

	public void forget(Pickpockets.Target target)
	{
		days.removeIf(d -> target.name().equals(d.t));
	}

	public boolean isEmpty()
	{
		return days.isEmpty();
	}

	/** Each target's tally over the last {@link Rates#DAYS} days, for the targets with any picks. */
	public Map<Pickpockets.Target, Tally> tallies(long now)
	{
		long today = day(now);
		long from = today - Rates.DAYS;
		Map<Pickpockets.Target, Tally> out = new EnumMap<>(Pickpockets.Target.class);
		for (Day d : days)
		{
			if (d.isValid() && d.d >= from && d.d <= today)
			{
				out.computeIfAbsent(d.target(), Tally::new).add(d);
			}
		}
		out.values().removeIf(t -> t.getAttempts() == 0 && t.getOpened() == 0);
		return out;
	}

	/**
	 * Each logged method's rate an hour, for ranking: only targets with {@link Rates#ENOUGH_MS} of picking,
	 * as for clocked runs.
	 */
	public Map<String, Rate> rates(long now, IntToLongFunction price)
	{
		Map<String, Rate> out = new HashMap<>();
		for (Tally t : tallies(now).values())
		{
			if (t.getPlayedMs() >= Rates.ENOUGH_MS)
			{
				out.put(t.getTarget().getMethod(), new Rate(t.perHour(price), Rate.Source.PICKPOCKETS, t.getPlayedMs(),
					t.getSuccesses()));
			}
		}
		return out;
	}

	public String encode(Gson gson)
	{
		return gson.toJson(days);
	}

	/** Reads the saved log; an entry that cannot be read is left out, a damaged log starts again. */
	public static PickpocketLog decode(Gson gson, String json)
	{
		PickpocketLog log = new PickpocketLog();
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

	/** What a stretch of pickpocketing added up to, before it is filed under a target. */
	public static final class Counts
	{
		int successes;
		int fails;
		int stuns;
		int opened;
		int food;
		long ticks;
		final Map<Integer, Long> loot = new HashMap<>();
		final Map<Integer, Long> eaten = new HashMap<>();

		boolean isEmpty()
		{
			return successes == 0 && fails == 0 && stuns == 0 && opened == 0 && food == 0 && ticks == 0 && loot.isEmpty()
				&& eaten.isEmpty();
		}

		void addAll(Counts o)
		{
			successes += o.successes;
			fails += o.fails;
			stuns += o.stuns;
			opened += o.opened;
			food += o.food;
			ticks += o.ticks;
			o.loot.forEach((id, q) -> loot.merge(id, q, Long::sum));
			o.eaten.forEach((id, q) -> eaten.merge(id, q, Long::sum));
		}

		public int getSuccesses()
		{
			return successes;
		}

		public int getAttempts()
		{
			return successes + fails;
		}

		public int getStuns()
		{
			return stuns;
		}

		public long getPlayedMs()
		{
			return ticks * Run.TICK_MS;
		}

		public long profit(IntToLongFunction price)
		{
			return Tally.value(loot, price) - Tally.value(eaten, price);
		}
	}
}
