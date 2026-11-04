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
import com.codex.moneypilot.core.Stalls;
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

	/** The Rogues' Castle chest log over the last 60 days, valued at today's prices. */
	public static final class Chests
	{
		public final int opens;
		public final int trapped;
		public final int deaths;
		public final long playedMs;
		public final long xp;
		public final long loot;
		public final long used;
		public final long lost;
		/** True once there is enough looting for the rate to rank the method. */
		public final boolean counts;

		public Chests(int opens, int trapped, int deaths, long playedMs, long xp, long loot, long used, long lost, boolean counts)
		{
			this.opens = opens;
			this.trapped = trapped;
			this.deaths = deaths;
			this.playedMs = playedMs;
			this.xp = xp;
			this.loot = loot;
			this.used = used;
			this.lost = lost;
			this.counts = counts;
		}

		public long profit()
		{
			return loot - used - lost;
		}

		public long perHour()
		{
			return hourly(profit(), playedMs);
		}

		public long xpPerHour()
		{
			return hourly(xp, playedMs);
		}

		public long opensPerHour()
		{
			return hourly(opens, playedMs);
		}

		public long perOpen()
		{
			return opens == 0 ? 0 : loot / opens;
		}

		/** Of the chests you went for, the share you searched first; 1 with none. */
		public double searchedRate()
		{
			int all = opens + trapped;
			return all == 0 ? 1 : opens / (double) all;
		}
	}

	/** The chest sitting under way and the chests in view, for the plaque. */
	public static final class Looting
	{
		public final int opens;
		public final int trapped;
		public final int deaths;
		public final long playedMs;
		public final long profit;
		public final long xp;
		/** Chests in view and how many stand closed; ticks to the next restock, -1 with none in view. */
		public final int seen;
		public final int ready;
		public final long nextTicks;
		/** What a death now would cost and how many items you would keep; risk -1 when it is not worked out. */
		public final long risk;
		public final int kept;

		public Looting(int opens, int trapped, int deaths, long playedMs, long profit, long xp, int seen, int ready, long nextTicks,
			long risk, int kept)
		{
			this.opens = opens;
			this.trapped = trapped;
			this.deaths = deaths;
			this.playedMs = playedMs;
			this.profit = profit;
			this.xp = xp;
			this.seen = seen;
			this.ready = ready;
			this.nextTicks = nextTicks;
			this.risk = risk;
			this.kept = kept;
		}

		public long perHour()
		{
			return playedMs < 60_000L ? 0 : hourly(profit, playedMs);
		}

		public long xpPerHour()
		{
			return playedMs < 60_000L ? 0 : hourly(xp, playedMs);
		}
	}

	/** One rotation of the Port Roberts stall log over the last 60 days, valued at today's prices. */
	public static final class Stolen
	{
		public final Stalls.Rotation rotation;
		public final int steals;
		public final int caught;
		public final long playedMs;
		public final long xp;
		public final long loot;
		public final long paid;
		/** True once there is enough stealing for the rate to rank the method. */
		public final boolean counts;

		public Stolen(Stalls.Rotation rotation, int steals, int caught, long playedMs, long xp, long loot, long paid, boolean counts)
		{
			this.rotation = rotation;
			this.steals = steals;
			this.caught = caught;
			this.playedMs = playedMs;
			this.xp = xp;
			this.loot = loot;
			this.paid = paid;
			this.counts = counts;
		}

		public long profit()
		{
			return loot - paid;
		}

		public long perHour()
		{
			return hourly(profit(), playedMs);
		}

		public long xpPerHour()
		{
			return hourly(xp, playedMs);
		}

		public long stealsPerHour()
		{
			return hourly(steals, playedMs);
		}

		public long perSteal()
		{
			return steals == 0 ? 0 : loot / steals;
		}

		/** Your experience an hour against the wiki's most for the rotation, 0 to 1. */
		public double ofWikiXp()
		{
			int wiki = rotation.getWikiXpPerHour();
			return wiki <= 0 ? 0 : Math.min(1, xpPerHour() / (double) wiki);
		}
	}

	/** The stall sitting under way, for the plaque. */
	public static final class Stealing
	{
		/** The rotation the sitting is filed under, or null below the silver stall. */
		public final Stalls.Rotation rotation;
		/** The stall of the last steal, or null before one. */
		public final Stalls.Stall last;
		public final int steals;
		public final int caught;
		public final long playedMs;
		public final long profit;
		public final long xp;

		public Stealing(Stalls.Rotation rotation, Stalls.Stall last, int steals, int caught, long playedMs, long profit, long xp)
		{
			this.rotation = rotation;
			this.last = last;
			this.steals = steals;
			this.caught = caught;
			this.playedMs = playedMs;
			this.profit = profit;
			this.xp = xp;
		}

		public long perHour()
		{
			return playedMs < 60_000L ? 0 : hourly(profit, playedMs);
		}

		public long xpPerHour()
		{
			return playedMs < 60_000L ? 0 : hourly(xp, playedMs);
		}
	}

	static long hourly(long amount, long ms)
	{
		return ms <= 0 ? 0 : Math.round(amount * 3_600_000.0 / ms);
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
	/** The Rogues' Castle chest log; null when there is none or it is turned off. */
	public final Chests chests;
	/** False when the chest log is turned off in the settings. */
	public final boolean chestLog;
	/** The Port Roberts stall log by rotation, best paying first; empty when there is none or it is turned off. */
	public final List<Stolen> stolen;
	/** False when the stall log is turned off in the settings. */
	public final boolean stallLog;

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
		this(loggedIn, account, plan, rates, runs, journal, shown, live, bankSeen, chronicle, now, goals, picked, pickLog, null, true);
	}

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now, Goals goals, List<Picked> picked,
		boolean pickLog, Chests chests, boolean chestLog)
	{
		this(loggedIn, account, plan, rates, runs, journal, shown, live, bankSeen, chronicle, now, goals, picked, pickLog, chests,
			chestLog, null, true);
	}

	public View(boolean loggedIn, Account account, Planner plan, Rates rates, List<Clocked> runs, List<Logged> journal,
		Set<Method.Category> shown, Live live, boolean bankSeen, boolean chronicle, long now, Goals goals, List<Picked> picked,
		boolean pickLog, Chests chests, boolean chestLog, List<Stolen> stolen, boolean stallLog)
	{
		this.stolen = stolen == null ? Collections.emptyList() : stolen;
		this.stallLog = stallLog;
		this.chests = chests;
		this.chestLog = chestLog;
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
