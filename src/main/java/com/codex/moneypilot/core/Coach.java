package com.codex.moneypilot.core;

import java.util.List;

/**
 * The run coach: while a run is clocked, its rate so far against your own best run of the method
 * and against the wiki's figure, so you can tell at a glance whether this run is going well. Numbers
 * only; it never says what to click or do.
 */
public final class Coach
{
	/** A rate from less than this much play says too little to weigh against anything. */
	public static final long SETTLE_MS = Clocked.MIN_MS;
	/** Within this share of your own rate counts as your usual pace. */
	static final double USUAL = 0.10;
	/**
	 * How far past a line the rate must go to change a verdict already given, as a share of the rate: loot
	 * reaches the bank in lumps, so the rate saws up and down and would otherwise flicker across a line.
	 */
	static final double STICKY = 0.04;
	/** Past this long with no change in profit, the rate is weighed as it stands now. */
	public static final long STALE_MS = 10 * 60_000L;

	public enum Verdict
	{
		/** Too early to say. */
		SETTLING,
		/** Faster than any run of this method you have kept. */
		BEST,
		/** Faster than your usual rate for the method. */
		AHEAD,
		/** Within a tenth of your usual rate. */
		USUAL,
		/** Slower than your usual rate. */
		BEHIND,
		/** No runs of your own yet: weighed against the wiki alone. */
		WIKI
	}

	private final long now;
	private final Long best;
	private final Long usual;
	private final long wiki;
	private final Verdict verdict;

	private Coach(long now, Long best, Long usual, long wiki, Verdict verdict)
	{
		this.now = now;
		this.best = best;
		this.usual = usual;
		this.wiki = wiki;
		this.verdict = verdict;
	}

	/**
	 * @param method the method being clocked
	 * @param playedMs time played on the run so far
	 * @param profit what the run has made so far
	 * @param runs your kept runs, any method (only those of this method count)
	 * @param own your rate for the method from enough play, or null
	 */
	public static Coach of(Method method, long playedMs, long profit, List<Clocked> runs, Rate own)
	{
		return of(method, playedMs, profit, playedMs, runs, own, null);
	}

	/**
	 * @param changedAtMs the time played when the profit last changed: loot is counted in lumps (a kill's
	 *     drop, a bank trip), so the rate is weighed as it stood when the last lump landed, which is the true
	 *     rate for steady lumps, rather than sinking between them; past {@link #STALE_MS} with nothing new it
	 *     is weighed as it stands now
	 * @param previous the verdict given at the last look, kept unless the rate is clearly past the line; null at the start
	 */
	public static Coach of(Method method, long playedMs, long profit, long changedAtMs, List<Clocked> runs, Rate own,
		Verdict previous)
	{
		long since = Math.max(Math.min(changedAtMs, playedMs), Math.min(playedMs, SETTLE_MS));
		long rate = Rate.perHour(profit, playedMs - since > STALE_MS ? playedMs : since);
		Long best = null;
		for (Clocked r : runs)
		{
			if (r != null && r.isValid() && method.getId().equals(r.getMethod()))
			{
				long h = Rate.perHour(r.getProfit(), r.getPlayedMs());
				if (best == null || h > best)
				{
					best = h;
				}
			}
		}
		Long usual = own != null && own.getSource() == Rate.Source.YOURS ? own.getPerHour() : null;
		Verdict v = verdict(playedMs, rate, best, usual);
		if (previous != null && v != previous && v != Verdict.SETTLING)
		{
			long margin = Math.round(Math.abs(rate) * STICKY);
			if (verdict(playedMs, rate + margin, best, usual) == previous || verdict(playedMs, rate - margin, best, usual) == previous)
			{
				v = previous;
			}
		}
		return new Coach(rate, best, usual, method.getWikiPerHour(), v);
	}

	private static Verdict verdict(long playedMs, long rate, Long best, Long usual)
	{
		if (playedMs < SETTLE_MS)
		{
			return Verdict.SETTLING;
		}
		if (best != null && rate > best)
		{
			return Verdict.BEST;
		}
		if (usual != null && usual > 0)
		{
			double share = (rate - usual) / (double) usual;
			return share > USUAL ? Verdict.AHEAD : share >= -USUAL ? Verdict.USUAL : Verdict.BEHIND;
		}
		return Verdict.WIKI;
	}

	/** The run's profit an hour, as the coach weighs it. */
	public long getNow()
	{
		return now;
	}

	/** Your best kept run of the method, an hour, or null with none. */
	public Long getBest()
	{
		return best;
	}

	/** Your rate for the method from enough play, or null. */
	public Long getUsual()
	{
		return usual;
	}

	public long getWiki()
	{
		return wiki;
	}

	public Verdict getVerdict()
	{
		return verdict;
	}

	/** The run's rate as a share of the wiki's, such as 0.82; 0 when the wiki gives none. */
	public double ofWiki()
	{
		return wiki <= 0 ? 0 : now / (double) wiki;
	}

	/** The coach's line in plain words: "Ahead of your usual 1.1m/hr", "Your best run yet". */
	public String line()
	{
		switch (verdict)
		{
			case SETTLING:
				return "Pace after " + Format.duration(SETTLE_MS);
			case BEST:
				return "Your best run yet, past " + Format.gp(best) + "/hr";
			case AHEAD:
				return pct() + " ahead of your usual " + Format.gp(usual);
			case USUAL:
				return "On your usual " + Format.gp(usual) + "/hr";
			case BEHIND:
				return pct() + " behind your usual " + Format.gp(usual);
			default:
				return wiki <= 0 ? "No wiki figure to weigh it by" : Math.round(ofWiki() * 100) + "% of the wiki's " + Format.gp(wiki);
		}
	}

	/** The line cut short for the plaque on the game screen: "Best yet, was 1.15m", "-24% on your 1.05m". */
	public String shortLine()
	{
		switch (verdict)
		{
			case SETTLING:
				return "Pace after " + Format.duration(SETTLE_MS);
			case BEST:
				return "Best yet, was " + Format.gp(best);
			case AHEAD:
				return "+" + pct() + " on your " + Format.gp(usual);
			case USUAL:
				return "On your usual " + Format.gp(usual);
			case BEHIND:
				return "-" + pct() + " on your " + Format.gp(usual);
			default:
				return wiki <= 0 ? "No wiki figure" : Math.round(ofWiki() * 100) + "% of wiki " + Format.gp(wiki);
		}
	}

	/** How far the run is from your usual rate, as a share of it: "18%". */
	private String pct()
	{
		return Math.round(Math.abs(now - usual) * 100.0 / usual) + "%";
	}
}
