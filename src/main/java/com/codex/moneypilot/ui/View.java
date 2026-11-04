package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Account;
import com.codex.moneypilot.core.Coach;
import com.codex.moneypilot.core.GoalPlan;
import com.codex.moneypilot.core.Target;
import com.codex.moneypilot.core.Logged;
import com.codex.moneypilot.core.Method;
import com.codex.moneypilot.core.Pickpockets;
import com.codex.moneypilot.core.Planner;
import com.codex.moneypilot.core.Rates;
import com.codex.moneypilot.core.Clocked;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Everything the panel shows, built on the client thread and handed over whole. */
public final class View
{
	/** The run being clocked, as it stands. */
	public static final class Live
	{
		public final Method method;
		public final long playedMs;
		public final long profit;
		public final boolean loggedIn;
		/** The run coach's verdict, or null when it is turned off. */
		public final Coach coach;

		public Live(Method method, long playedMs, long profit, boolean loggedIn)
		{
			this(method, playedMs, profit, loggedIn, null);
		}

		public Live(Method method, long playedMs, long profit, boolean loggedIn, Coach coach)
		{
			this.method = method;
			this.playedMs = playedMs;
			this.profit = profit;
			this.loggedIn = loggedIn;
			this.coach = coach;
		}

		/** Profit an hour so far; 0 for the first minute, which says too little. */
		public long perHour()
		{
			return playedMs < 60_000L ? 0 : Math.round(profit * 3_600_000.0 / playedMs);
		}
	}

	/** A goal with its plan. */
	public static final class Goal
	{
		public final Target target;
		public final GoalPlan plan;

		public Goal(Target target, GoalPlan plan)
		{
			this.target = target;
			this.plan = plan;
		}
	}

	/** What the Goal tab knows of the other Codex plugins and the settings. */
	public static final class Goals
	{
		public static final Goals NONE = new Goals(Collections.emptyList(), false, 0, 2);

		public final List<Goal> goals;
		/** True when Navigator's goal board was read, pinned goals or not. */
		public final boolean navigator;
		/** Unfinished goals pinned in Navigator. */
		public final int pinned;
		public final double hoursPerDay;

		public Goals(List<Goal> goals, boolean navigator, int pinned, double hoursPerDay)
		{
			this.goals = goals == null ? Collections.emptyList() : goals;
			this.navigator = navigator;
			this.pinned = pinned;
			this.hoursPerDay = hoursPerDay;
		}
	}

	/** One target of the pickpocket log over the last 60 days, valued at today's prices. */
	public static final class Picked
	{
		public final Pickpockets.Target target;
		public final int attempts;
		public final int successes;
		public final int stuns;
		public final int opened;
		public final long playedMs;
		public final long loot;
		public final long food;
		public final double foodPerHour;
		public final long picksPerHour;
		/** True once there is enough picking for the rate to rank the method. */
		public final boolean counts;

		public Picked(Pickpockets.Target target, int attempts, int successes, int stuns, int opened, long playedMs, long loot,
			long food, double foodPerHour, long picksPerHour, boolean counts)
		{
			this.target = target;
			this.attempts = attempts;
			this.successes = successes;
			this.stuns = stuns;
			this.opened = opened;
			this.playedMs = playedMs;
			this.loot = loot;
			this.food = food;
			this.foodPerHour = foodPerHour;
			this.picksPerHour = picksPerHour;
			this.counts = counts;
		}

		public long perHour()
		{
			return playedMs <= 0 ? 0 : Math.round((loot - food) * 3_600_000.0 / playedMs);
		}

		/** Your own success rate, 0 to 1. */
		public double successRate()
		{
			return attempts == 0 ? 0 : successes / (double) attempts;
		}
	}

	/** The pickpocketing sitting under way, for the plaque. */
	public static final class Picking
	{
		public final Pickpockets.Target target;
		public final int attempts;
		public final int successes;
		public final int stuns;
		public final long playedMs;
		public final long profit;
		/** Coin pouches held and how many fit; cap 0 for a target that gives none. */
		public final long pouches;
		public final int cap;
		/** Warn this many pouches before the cap; 0 for never. */
		public final int warnAt;

		public Picking(Pickpockets.Target target, int attempts, int successes, int stuns, long playedMs, long profit, long pouches,
			int cap, int warnAt)
		{
			this.target = target;
			this.attempts = attempts;
			this.successes = successes;
			this.stuns = stuns;
			this.playedMs = playedMs;
			this.profit = profit;
			this.pouches = pouches;
			this.cap = cap;
			this.warnAt = warnAt;
		}

		/** True when the pouches are near enough the cap to open them. */
		public boolean nearCap()
		{
			return cap > 0 && warnAt > 0 && pouches >= cap - warnAt;
		}

		public boolean full()
		{
			return cap > 0 && pouches >= cap;
		}

		public long perHour()
		{
			return playedMs < 60_000L ? 0 : Math.round(profit * 3_600_000.0 / playedMs);
		}

		public double successRate()
		{
			return attempts == 0 ? 0 : successes / (double) attempts;
		}
	}

	public final boolean loggedIn;
	public final Account account;
	public final Planner plan;
	public final Rates rates;
	public final List<Clocked> runs;
	public final List<Logged> journal;
	public final Set<Method.Category> shown;
	public final Live live;
	public final boolean bankSeen;
	public final boolean chronicle;
	public final long now;
	public final Goals goals;
	/** The pickpocket log, best paying first; empty when there is none or it is turned off. */
	public final List<Picked> picked;
	/** False when the pickpocket log is turned off in the settings. */
	public final boolean pickLog;

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now)
	{
		this(loggedIn, account, plan, rates, runs, journal, shown, live, bankSeen, chronicle, now, Goals.NONE);
	}

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now, Goals goals)
	{
		this(loggedIn, account, plan, rates, runs, journal, shown, live, bankSeen, chronicle, now, goals, null, true);
	}

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now, Goals goals, List<Picked> picked,
		boolean pickLog)
	{
		this.picked = picked == null ? Collections.emptyList() : picked;
		this.pickLog = pickLog;
		this.goals = goals == null ? Goals.NONE : goals;
		this.loggedIn = loggedIn;
		this.account = account;
		this.plan = plan;
		this.rates = rates;
		this.runs = runs == null ? Collections.emptyList() : runs;
		this.journal = journal == null ? Collections.emptyList() : journal;
		this.shown = shown.isEmpty() ? EnumSet.noneOf(Method.Category.class) : EnumSet.copyOf(shown);
		this.live = live;
		this.bankSeen = bankSeen;
		this.chronicle = chronicle;
		this.now = now;
	}

	public static View empty()
	{
		return new View(false, Account.UNKNOWN, null, Rates.none(), null, null, EnumSet.allOf(Method.Category.class), null,
			false, false, System.currentTimeMillis());
	}
}
