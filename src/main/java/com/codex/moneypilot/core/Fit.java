package com.codex.moneypilot.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** How a method fits the account: whether it can be done now, and what stands in the way. */
public final class Fit
{
	public enum Status
	{
		/** Every need and every suggestion met. */
		READY,
		/** Every need met; the guide suggests more. */
		ABLE,
		/** A need the game does not show, such as part of a quest, is all that is left to tell. */
		CHECK,
		/**
		 * Every need met, but well short of what the guide suggests: open in the game's eyes, yet the
		 * guide's rate would not be yours. Listed with what is within reach, not ranked with the rest.
		 */
		STRETCH,
		/** A need is not met. */
		LOCKED
	}

	private final Method method;
	private final Status status;
	private final List<Need> unmet;
	private final List<Need> unsure;
	private final List<Need> short_;
	private final int gap;
	private int levelsShort;

	private Fit(Method method, Status status, List<Need> unmet, List<Need> unsure, List<Need> shortOf, int gap)
	{
		this.method = method;
		this.status = status;
		this.unmet = Collections.unmodifiableList(unmet);
		this.unsure = Collections.unmodifiableList(unsure);
		this.short_ = Collections.unmodifiableList(shortOf);
		this.gap = gap;
	}

	/** Weight of a quest or diary against levels when telling how near a locked method is. */
	static final int UNLOCK_WEIGHT = 8;
	/** Levels short of the guide's suggestions, all told, past which a method is a stretch. */
	public static final int STRETCH_LEVELS = 20;

	public static Fit of(Method m, Account a)
	{
		List<Need> unmet = new ArrayList<>();
		List<Need> unsure = new ArrayList<>();
		int gap = 0;
		for (Need n : m.getNeeds())
		{
			switch (a.check(n))
			{
				case UNMET:
					unmet.add(n);
					gap += n.getKind() == Need.Kind.SKILL || n.getKind() == Need.Kind.COMBAT || n.getKind() == Need.Kind.TOTAL
						? a.gap(n) : UNLOCK_WEIGHT;
					break;
				case UNSURE:
					if (n.getKind() != Need.Kind.NOTE)
					{
						unsure.add(n);
					}
					break;
				default:
					break;
			}
		}
		List<Need> shortOf = new ArrayList<>();
		int levelsShort = 0;
		for (Need n : m.getSuggests())
		{
			if (n.getKind() != Need.Kind.NOTE && a.check(n) == Account.Check.UNMET)
			{
				shortOf.add(n);
				if (n.getKind() == Need.Kind.SKILL || n.getKind() == Need.Kind.COMBAT || n.getKind() == Need.Kind.TOTAL)
				{
					levelsShort += a.gap(n);
				}
			}
		}
		Status status;
		if (!unmet.isEmpty())
		{
			status = Status.LOCKED;
		}
		else if (levelsShort > STRETCH_LEVELS)
		{
			status = Status.STRETCH;
			gap = levelsShort;
		}
		else if (!unsure.isEmpty())
		{
			status = Status.CHECK;
		}
		else
		{
			status = shortOf.isEmpty() ? Status.READY : Status.ABLE;
		}
		Fit fit = new Fit(m, status, unmet, unsure, shortOf, gap);
		fit.levelsShort = levelsShort;
		return fit;
	}

	public Method getMethod()
	{
		return method;
	}

	public Status getStatus()
	{
		return status;
	}

	/** True when the method can be done now and the guide's rate is near enough to yours. */
	public boolean isOpen()
	{
		return status != Status.LOCKED && status != Status.STRETCH;
	}

	/** Needs not met, in the guide's order. */
	public List<Need> getUnmet()
	{
		return unmet;
	}

	/** Needs the game does not show. */
	public List<Need> getUnsure()
	{
		return unsure;
	}

	/** Levels short of the guide's suggestions, all told, whatever the status. */
	public int getLevelsShort()
	{
		return levelsShort;
	}

	/** Suggestions not met. */
	public List<Need> getShortOf()
	{
		return short_;
	}

	/**
	 * How far a method is: for a locked one, levels short and {@value #UNLOCK_WEIGHT} for each quest or
	 * diary; for a stretch, levels short of the guide's suggestions.
	 */
	public int getGap()
	{
		return gap;
	}
}
