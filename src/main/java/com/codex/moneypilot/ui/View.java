package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Account;
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

		public Live(Method method, long playedMs, long profit, boolean loggedIn)
		{
			this.method = method;
			this.playedMs = playedMs;
			this.profit = profit;
			this.loggedIn = loggedIn;
		}

		/** Profit an hour so far; 0 for the first minute, which says too little. */
		public long perHour()
		{
			return playedMs < 60_000L ? 0 : Math.round(profit * 3_600_000.0 / playedMs);
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

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now)
	{
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
