package com.codex.moneypilot.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How fast the account can make what a goal is short: the best paying method it can do, at your own
 * clocked rate where there is one, with the timegated runs (bird houses, herb runs, Miscellania) fitted
 * in alongside, and how many days that is at the hours a day you play.
 *
 * <p>The model is kept plain so it can be told in a sentence. Each day you play for the same number of
 * hours. A daily run (Miscellania) is done once a day. A run on a shorter timer is done each time it
 * comes round while you play, and once more at the start of every day after the first, for what grew
 * while you were away (nothing is planted before the plan begins). The time a run takes comes out of
 * the time on the main method, and a run is only fitted in when it pays more for its minutes than the
 * main method would.
 */
public final class GoalPlan
{
	public static final long DAY_MS = 86_400_000L;
	/** Past this many days of play the plan says "a very long way" instead of a date. */
	public static final int MAX_DAYS = 3_650;
	/** Methods compared with the best one. */
	static final int OTHERS = 2;

	/** One timegated run in a plan: how many times it is done and what it brings in. */
	public static final class Runs
	{
		private final Method method;
		private final int count;

		Runs(Method method, int count)
		{
			this.method = method;
			this.count = count;
		}

		public Method getMethod()
		{
			return method;
		}

		public int getCount()
		{
			return count;
		}

		public long getProfit()
		{
			return count * method.getPerRun();
		}

		public long getMs()
		{
			return count * method.getRunMs();
		}
	}

	/** One way to make the money: a main method (or none) with runs alongside. */
	public static final class Mix
	{
		private final Method method;
		private final Rate rate;
		private final long playMs;
		private final long activeMs;
		private final List<Runs> runs;
		private final int days;
		private final boolean reached;

		Mix(Method method, Rate rate, long playMs, long activeMs, List<Runs> runs, int days, boolean reached)
		{
			this.method = method;
			this.rate = rate;
			this.playMs = playMs;
			this.activeMs = activeMs;
			this.runs = Collections.unmodifiableList(runs);
			this.days = days;
			this.reached = reached;
		}

		/** The main method, or null when the plan is the timegated runs alone. */
		public Method getMethod()
		{
			return method;
		}

		/** The main method's rate and where it comes from; null with no main method. */
		public Rate getRate()
		{
			return rate;
		}

		/** Time played in all, runs included; for runs alone, only the time the runs take. */
		public long getPlayMs()
		{
			return playMs;
		}

		/** Time on the main method. */
		public long getActiveMs()
		{
			return activeMs;
		}

		public long getActiveProfit()
		{
			return rate == null ? 0 : Math.round(rate.getPerHour() * (activeMs / 3_600_000.0));
		}

		/** The timegated runs done, best paying first; only those done at least once. */
		public List<Runs> getRuns()
		{
			return runs;
		}

		public long getRunsProfit()
		{
			long sum = 0;
			for (Runs r : runs)
			{
				sum += r.getProfit();
			}
			return sum;
		}

		public long getProfit()
		{
			return getActiveProfit() + getRunsProfit();
		}

		/** Days of play it takes, counting the day it is reached; 1 when it is done today. */
		public int getDays()
		{
			return days;
		}

		/** False when it would take more than {@link #MAX_DAYS} days. */
		public boolean isReached()
		{
			return reached;
		}
	}

	/** A pinned Navigator goal that would open a better paying method, and the plan once it is open. */
	public static final class Opener
	{
		private final Pinned.Goal goal;
		private final Method method;
		private final Mix mix;

		Opener(Pinned.Goal goal, Method method, Mix mix)
		{
			this.goal = goal;
			this.method = method;
			this.mix = mix;
		}

		public Pinned.Goal getGoal()
		{
			return goal;
		}

		public Method getMethod()
		{
			return method;
		}

		public Mix getMix()
		{
			return mix;
		}
	}

	private final long need;
	private final long windowMs;
	private final Mix best;
	private final Mix alone;
	private final List<Mix> others;
	private final Mix runsOnly;
	private final List<Method> runs;
	private final List<Opener> openers;

	private GoalPlan(long need, long windowMs, Mix best, Mix alone, List<Mix> others, Mix runsOnly, List<Method> runs,
		List<Opener> openers)
	{
		this.need = need;
		this.windowMs = windowMs;
		this.best = best;
		this.alone = alone;
		this.others = Collections.unmodifiableList(others);
		this.runsOnly = runsOnly;
		this.runs = Collections.unmodifiableList(runs);
		this.openers = Collections.unmodifiableList(openers);
	}

	/**
	 * @param need coins still to make
	 * @param open the methods the account can do now with the rate each is ranked by (the planner's open list)
	 * @param runs the timegated runs the account can do (see {@link #runsFor})
	 * @param near locked methods, for what a pinned goal would open; may be empty
	 * @param pinned Navigator's pinned goals; may be empty
	 * @param hoursPerDay hours played a day
	 */
	public static GoalPlan of(long need, List<Planner.Pick> open, List<Method> runs, List<Planner.Pick> near,
		List<Pinned.Goal> pinned, double hoursPerDay)
	{
		long window = Math.max(15 * 60_000L, Math.min(DAY_MS, Math.round(hoursPerDay * 3_600_000L)));
		List<Planner.Pick> active = new ArrayList<>();
		for (Planner.Pick p : open)
		{
			if (p.getRate().getPerHour() > 0 && !p.getMethod().isRecurring())
			{
				active.add(p);
			}
		}
		active.sort((a, b) -> Long.compare(b.getRate().getPerHour(), a.getRate().getPerHour()));
		Mix best = null;
		Mix alone = null;
		List<Mix> others = new ArrayList<>();
		if (need > 0 && !active.isEmpty())
		{
			Planner.Pick top = active.get(0);
			best = mix(need, top.getMethod(), top.getRate(), runs, window);
			alone = runs.isEmpty() ? null : mix(need, top.getMethod(), top.getRate(), Collections.emptyList(), window);
			for (Planner.Pick p : active.subList(1, Math.min(active.size(), OTHERS + 1)))
			{
				others.add(mix(need, p.getMethod(), p.getRate(), runs, window));
			}
		}
		Mix runsOnly = need > 0 && !runs.isEmpty() ? mix(need, null, null, runs, window) : null;
		List<Opener> openers = new ArrayList<>();
		if (need > 0)
		{
			long bar = active.isEmpty() ? 0 : active.get(0).getRate().getPerHour();
			for (Pinned.Goal g : pinned)
			{
				Planner.Pick opened = null;
				for (Planner.Pick p : near)
				{
					// Only a method the account would be near enough the guide's levels for, once open, to earn its rate.
					if (p.getRate().getPerHour() > bar && !p.getMethod().isRecurring() && g.opens(p.getFit())
						&& p.getFit().getLevelsShort() <= Fit.STRETCH_LEVELS
						&& (opened == null || p.getRate().getPerHour() > opened.getRate().getPerHour()))
					{
						opened = p;
					}
				}
				if (opened != null)
				{
					openers.add(new Opener(g, opened.getMethod(), mix(need, opened.getMethod(), opened.getRate(), runs, window)));
				}
			}
		}
		return new GoalPlan(need, window, best, alone, others, runsOnly, runs, openers);
	}

	/**
	 * The timegated runs an account can do now: open by their needs, members only on a members account,
	 * and of a group sharing patches, only the one paying most a run.
	 */
	public static List<Method> runsFor(Account account, List<Method> recurring)
	{
		Map<String, Method> byGroup = new HashMap<>();
		List<Method> out = new ArrayList<>();
		for (Method m : recurring)
		{
			if (!m.isRecurring() || (m.isMembers() && !account.isMembers()) || (m.isTrade() && account.isIronman())
				|| !Fit.of(m, account).isOpen())
			{
				continue;
			}
			if (m.getGroup() == null)
			{
				out.add(m);
				continue;
			}
			Method had = byGroup.get(m.getGroup());
			if (had == null || m.getPerRun() > had.getPerRun())
			{
				if (had != null)
				{
					out.remove(had);
				}
				byGroup.put(m.getGroup(), m);
				out.add(m);
			}
		}
		out.sort((a, b) -> Long.compare(b.getWikiPerHour(), a.getWikiPerHour()));
		return out;
	}

	/**
	 * Plays day after day until the profit covers what is needed.
	 *
	 * @param method the main method, or null for the runs alone
	 */
	static Mix mix(long need, Method method, Rate rate, List<Method> runs, long window)
	{
		double perMs = rate == null ? 0 : Math.max(0, rate.getPerHour()) / 3_600_000.0;
		List<Method> fitted = new ArrayList<>();
		for (Method m : runs)
		{
			// A run is only worth its minutes when they pay more than the same minutes on the main method.
			if (m.getRunMs() > 0 && m.getPerRun() > perMs * m.getRunMs())
			{
				fitted.add(m);
			}
		}
		fitted.sort((a, b) -> Double.compare(b.getPerRun() / (double) b.getRunMs(), a.getPerRun() / (double) a.getRunMs()));
		int[] total = new int[fitted.size()];
		long made = 0;
		long played = 0;
		long active = 0;
		for (int d = 0; d < MAX_DAYS; d++)
		{
			Day full = day(d, window, fitted, perMs, method == null);
			if (made + full.profit >= need)
			{
				// Today is the day: the fewest minutes of it that make the rest.
				long lo = 0;
				long hi = window / 60_000L;
				while (lo < hi)
				{
					long mid = (lo + hi) / 2;
					if (made + day(d, mid * 60_000L, fitted, perMs, method == null).profit >= need)
					{
						hi = mid;
					}
					else
					{
						lo = mid + 1;
					}
				}
				Day last = day(d, Math.max(1, lo) * 60_000L, fitted, perMs, method == null);
				for (int i = 0; i < total.length; i++)
				{
					total[i] += last.counts[i];
				}
				return new Mix(method, rate, played + last.played, active + last.active, runs(fitted, total), d + 1, true);
			}
			if (full.profit <= 0)
			{
				break;
			}
			made += full.profit;
			played += full.played;
			active += full.active;
			for (int i = 0; i < total.length; i++)
			{
				total[i] += full.counts[i];
			}
		}
		return new Mix(method, rate, played, active, runs(fitted, total), MAX_DAYS, false);
	}

	private static List<Runs> runs(List<Method> fitted, int[] counts)
	{
		List<Runs> out = new ArrayList<>();
		for (int i = 0; i < counts.length; i++)
		{
			if (counts[i] > 0)
			{
				out.add(new Runs(fitted.get(i), counts[i]));
			}
		}
		out.sort((a, b) -> Long.compare(b.getProfit(), a.getProfit()));
		return out;
	}

	/** One day of play. */
	private static final class Day
	{
		final int[] counts;
		long profit;
		long played;
		long active;

		Day(int n)
		{
			counts = new int[n];
		}
	}

	/**
	 * What a day of play makes in a window of play.
	 *
	 * @param d the day, 0 for the first
	 * @param runsOnly true when there is no main method: the time played is then only the runs' time
	 */
	private static Day day(int d, long window, List<Method> fitted, double perMs, boolean runsOnly)
	{
		Day day = new Day(fitted.size());
		long left = window;
		long runMs = 0;
		long profit = 0;
		for (int i = 0; i < fitted.size(); i++)
		{
			Method m = fitted.get(i);
			long n;
			if (m.getEveryMs() >= DAY_MS)
			{
				n = 1;
			}
			else
			{
				n = window / m.getEveryMs() + (d > 0 ? 1 : 0);
			}
			// The runs share the window; the best paying for their minutes go first.
			n = Math.min(n, left / m.getRunMs());
			day.counts[i] = (int) n;
			left -= n * m.getRunMs();
			runMs += n * m.getRunMs();
			profit += n * m.getPerRun();
		}
		day.active = runsOnly ? 0 : window - runMs;
		day.played = runsOnly ? runMs : window;
		day.profit = profit + Math.round(perMs * day.active);
		return day;
	}

	/** Coins still to make. */
	public long getNeed()
	{
		return need;
	}

	/** Play a day the plan assumes. */
	public long getWindowMs()
	{
		return windowMs;
	}

	/** The fastest way: the best paying method with the runs alongside; null when nothing pays. */
	public Mix getBest()
	{
		return best;
	}

	/** The same method without the runs, to show what they save; null when there are no runs to fit. */
	public Mix getAlone()
	{
		return alone;
	}

	/** The next best methods, each with the runs alongside. */
	public List<Mix> getOthers()
	{
		return others;
	}

	/** The timegated runs alone, with no main method; null when there are none. */
	public Mix getRunsOnly()
	{
		return runsOnly;
	}

	/** The timegated runs the account can do. */
	public List<Method> getRuns()
	{
		return runs;
	}

	/** Pinned Navigator goals that would open a better paying method. */
	public List<Opener> getOpeners()
	{
		return openers;
	}
}
