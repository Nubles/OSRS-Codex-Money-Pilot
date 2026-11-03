package com.codex.moneypilot.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each method's best known profit an hour: your own clocked runs when they add up to enough play,
 * else Session Chronicle's sessions spent on it, else the wiki's guide. Only the last
 * {@link #DAYS} days count, so the figure follows prices and your gear as they change.
 */
public final class Rates
{
	public static final int DAYS = 60;
	/** Play time needed before your own figure replaces the wiki's. */
	public static final long ENOUGH_MS = 15 * 60_000L;

	private final Map<String, Rate> own = new HashMap<>();
	private final Map<String, Rate> sessions = new HashMap<>();

	private Rates()
	{
	}

	public static Rates of(Collection<Clocked> runs, Collection<Logged> journal, long now)
	{
		Rates r = new Rates();
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

	public boolean hasAny()
	{
		return !own.isEmpty() || !sessions.isEmpty();
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
