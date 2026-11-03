package com.codex.moneypilot.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the method list, the account and the known rates into the two lists the panel shows: what
 * you can do now, best paying first, and what is nearest to opening up.
 */
public final class Planner
{
	/** What the player has chosen to see. */
	public static final class Filter
	{
		public final Set<Method.Category> categories;
		public final boolean wilderness;

		public Filter(Set<Method.Category> categories, boolean wilderness)
		{
			this.categories = categories.isEmpty() ? EnumSet.noneOf(Method.Category.class) : EnumSet.copyOf(categories);
			this.wilderness = wilderness;
		}

		public static Filter all()
		{
			return new Filter(EnumSet.allOf(Method.Category.class), true);
		}
	}

	/** A method with its fit and the rate it is ranked by. */
	public static final class Pick
	{
		private final Fit fit;
		private final Rate rate;

		Pick(Fit fit, Rate rate)
		{
			this.fit = fit;
			this.rate = rate;
		}

		public Fit getFit()
		{
			return fit;
		}

		public Method getMethod()
		{
			return fit.getMethod();
		}

		public Rate getRate()
		{
			return rate;
		}
	}

	private final List<Pick> open;
	private final List<Pick> near;
	private final int hidden;

	private Planner(List<Pick> open, List<Pick> near, int hidden)
	{
		this.open = Collections.unmodifiableList(open);
		this.near = Collections.unmodifiableList(near);
		this.hidden = hidden;
	}

	public static Planner plan(List<Method> methods, Account account, Rates rates, Filter filter)
	{
		List<Pick> open = new ArrayList<>();
		List<Pick> near = new ArrayList<>();
		int hidden = 0;
		for (Method m : methods)
		{
			// What the account cannot do at all is left out, not shown as locked.
			if ((m.isMembers() && !account.isMembers()) || (m.isTrade() && account.isIronman()))
			{
				hidden++;
				continue;
			}
			if (!filter.categories.contains(m.getCategory()) || (m.isWilderness() && !filter.wilderness))
			{
				hidden++;
				continue;
			}
			Fit fit = Fit.of(m, account);
			Pick pick = new Pick(fit, rates.best(m));
			(fit.isOpen() ? open : near).add(pick);
		}
		open.sort((a, b) ->
		{
			int c = Long.compare(b.rate.getPerHour(), a.rate.getPerHour());
			return c != 0 ? c : a.getMethod().getName().compareTo(b.getMethod().getName());
		});
		near.sort((a, b) ->
		{
			int c = Integer.compare(a.fit.getGap(), b.fit.getGap());
			if (c != 0)
			{
				return c;
			}
			c = Long.compare(b.rate.getPerHour(), a.rate.getPerHour());
			return c != 0 ? c : a.getMethod().getName().compareTo(b.getMethod().getName());
		});
		return new Planner(open, near, hidden);
	}

	/** Methods you can do now, best paying an hour first. */
	public List<Pick> getOpen()
	{
		return open;
	}

	/**
	 * Locked methods, nearest first; among those as near, the better paying first. Only the ones that
	 * pay more than the best open method are worth working toward, but all are kept here.
	 */
	public List<Pick> getNear()
	{
		return near;
	}

	/** Locked methods that would pay more an hour than the best you can do now, nearest first. */
	public List<Pick> getUpgrades()
	{
		long best = open.isEmpty() ? 0 : open.get(0).rate.getPerHour();
		List<Pick> out = new ArrayList<>();
		for (Pick p : near)
		{
			if (p.rate.getPerHour() > best)
			{
				out.add(p);
			}
		}
		return out;
	}

	/** Methods left out: members-only on a free account, trading for an ironman, or filtered away. */
	public int getHidden()
	{
		return hidden;
	}
}
