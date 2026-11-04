package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Account;
import com.codex.moneypilot.core.Coach;
import com.codex.moneypilot.core.GoalPlan;
import com.codex.moneypilot.core.Target;
import com.codex.moneypilot.core.Logged;
import com.codex.moneypilot.core.Method;
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

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now)
	{
		this(loggedIn, account, plan, rates, runs, journal, shown, live, bankSeen, chronicle, now, Goals.NONE);
	}

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now, Goals goals)
	{
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
