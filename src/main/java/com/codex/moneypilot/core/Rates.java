package com.codex.moneypilot.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each method's best known profit an hour: your own clocked runs when they add up to enough play,
 * else the pickpocket, chest or stall log for a method they cover, else Session Chronicle's sessions spent on it, else the wiki's guide. Only the last
 * {@link #DAYS} days count, so the figure follows prices and your gear as they change.
 */
public final class Rates
{
	public static final int DAYS = 60;
	/** Play time needed before your own figure replaces the wiki's. */
	public static final long ENOUGH_MS = 15 * 60_000L;

	private final Map<String, Rate> own = new HashMap<>();
	private final Map<String, Rate> sessions = new HashMap<>();
	private final Map<String, Rate> picks = new HashMap<>();
	private final Map<String, Rate> chests = new HashMap<>();
	private final Map<String, Rate> stalls = new HashMap<>();

	private Rates()
	{
	}

	public static Rates of(Collection<Clocked> runs, Collection<Logged> journal, long now)
	{
		return of(runs, journal, null, now);
	}

	/**
	 * As above, with the pickpocket log's rates by method id ({@link PickpocketLog#rates}): they rank below
	 * runs you clocked yourself and above Session Chronicle's sessions.
	 */
	public static Rates of(Collection<Clocked> runs, Collection<Logged> journal, Map<String, Rate> pickpockets, long now)
	{
		return of(runs, journal, pickpockets, null, now);
	}

	/**
	 * As above, with the Rogues' Castle chest log's rate ({@link ChestLog#rates}), which ranks as the pickpocket
	 * log's do: below runs you clocked yourself and above Session Chronicle's sessions.
	 */
	public static Rates of(Collection<Clocked> runs, Collection<Logged> journal, Map<String, Rate> pickpockets,
		Map<String, Rate> chestLog, long now)
	{
		return of(runs, journal, pickpockets, chestLog, null, now);
	}

	/**
	 * As above, with the Port Roberts stall log's rates by method id ({@link StallLog#rates}), which rank as the
	 * other logs' do: below runs you clocked yourself and above Session Chronicle's sessions.
	 */
	public static Rates of(Collection<Clocked> runs, Collection<Logged> journal, Map<String, Rate> pickpockets,
		Map<String, Rate> chestLog, Map<String, Rate> stallLog, long now)
	{
		Rates r = new Rates();
		keep(pickpockets, r.picks);
		keep(chestLog, r.chests);
		keep(stallLog, r.stalls);
		long from = now - DAYS * 86_400_000L;

		Map<String, long[]> byMethod = new HashMap<>();
		for (Clocked run : runs)
		{
			if (run != null && run.isValid() && run.getStartedAt() >= from && Methods.byId(run.getMethod()) != null)
			{
				long[] sum = byMethod.computeIfAbsent(run.getMethod(), k -> new long[3]);
				sum[0] += run.getProfit();
				sum[1] += run.getPlayedMs();
				sum[2]++;
			}
		}
		byMethod.forEach((id, sum) ->
		{
			if (sum[1] >= ENOUGH_MS)
			{
				r.own.put(id, new Rate(Rate.perHour(sum[0], sum[1]), Rate.Source.YOURS, sum[1], (int) sum[2]));
			}
		});

		Map<String, long[]> bySession = new HashMap<>();
		Set<Long> seen = new HashSet<>();
		for (Logged s : journal)
		{
			if (s == null || s.getStartedAt() < from || s.getStartedAt() > now || !seen.add(s.getStartedAt()))
			{
				continue;
			}
			for (Method m : Methods.all())
			{
				if (m.matches(s.getActivity()))
				{
					long[] sum = bySession.computeIfAbsent(m.getId(), k -> new long[3]);
					sum[0] += s.getProfit();
					sum[1] += s.getPlayedMs();
					sum[2]++;
				}
			}
		}
		bySession.forEach((id, sum) ->
		{
			if (sum[1] >= ENOUGH_MS)
			{
				r.sessions.put(id, new Rate(Rate.perHour(sum[0], sum[1]), Rate.Source.SESSIONS, sum[1], (int) sum[2]));
			}
		});
		return r;
	}

	private static void keep(Map<String, Rate> from, Map<String, Rate> into)
	{
		if (from != null)
		{
			from.forEach((id, rate) ->
			{
				if (id != null && rate != null && Methods.byId(id) != null && rate.getPlayedMs() >= ENOUGH_MS)
				{
					into.put(id, rate);
				}
			});
		}
	}

	public static Rates none()
	{
		return new Rates();
	}

	/** The figure to rank a method by. */
	public Rate best(Method m)
	{
		Rate r = own.get(m.getId());
		if (r == null)
		{
			r = picks.get(m.getId());
		}
		if (r == null)
		{
			r = chests.get(m.getId());
		}
		if (r == null)
		{
			r = stalls.get(m.getId());
		}
		if (r == null)
		{
			r = sessions.get(m.getId());
		}
		return r != null ? r : wiki(m);
	}

	public static Rate wiki(Method m)
	{
		return new Rate(m.getWikiPerHour(), Rate.Source.WIKI, 0, 0);
	}

	/** Your clocked rate for a method, or null. */
	public Rate own(Method m)
	{
		return own.get(m.getId());
	}

	/** Session Chronicle's rate for a method, or null. */
	public Rate sessions(Method m)
	{
		return sessions.get(m.getId());
	}

	/** The pickpocket log's rate for a method, or null. */
	public Rate pickpockets(Method m)
	{
		return picks.get(m.getId());
	}

	/** The Rogues' Castle chest log's rate for a method, or null. */
	public Rate chests(Method m)
	{
		return chests.get(m.getId());
	}

	/** The Port Roberts stall log's rate for a method, or null. */
	public Rate stalls(Method m)
	{
		return stalls.get(m.getId());
	}

	public boolean hasAny()
	{
		return !own.isEmpty() || !picks.isEmpty() || !chests.isEmpty() || !stalls.isEmpty() || !sessions.isEmpty();
	}

	/** Records of one method, newest first. */
	public static List<Clocked> of(List<Clocked> runs, String method)
	{
		List<Clocked> out = new java.util.ArrayList<>();
		for (Clocked r : runs)
		{
			if (r != null && r.isValid() && method.equals(r.getMethod()))
			{
				out.add(r);
			}
		}
		out.sort((a, b) -> Long.compare(b.getStartedAt(), a.getStartedAt()));
		return out;
	}
}
