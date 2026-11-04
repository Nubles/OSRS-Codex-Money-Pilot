package com.codex.moneypilot.core;

/** A profit an hour for a method and where it came from. */
public final class Rate
{
	public enum Source
	{
		/** Runs clocked with Money Pilot. */
		YOURS("Yours"),
		/** Money Pilot's pickpocket log, kept as you pick. */
		PICKPOCKETS("Your picks"),
		/** Money Pilot's Rogues' Castle chest log, kept as you loot. */
		CHESTS("Your chests"),
		/** Session Chronicle's sessions spent on the method. */
		SESSIONS("Your sessions"),
		/** The wiki's money making guide. */
		WIKI("Wiki");

		private final String title;

		Source(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}
	}

	private final long perHour;
	private final Source source;
	private final long playedMs;
	private final int count;

	public Rate(long perHour, Source source, long playedMs, int count)
	{
		this.perHour = perHour;
		this.source = source;
		this.playedMs = playedMs;
		this.count = count;
	}

	public long getPerHour()
	{
		return perHour;
	}

	public Source getSource()
	{
		return source;
	}

	/** Play time behind the figure; 0 for the wiki's. */
	public long getPlayedMs()
	{
		return playedMs;
	}

	/** Runs or sessions behind the figure; 0 for the wiki's. */
	public int getCount()
	{
		return count;
	}

	public boolean isOwn()
	{
		return source != Source.WIKI;
	}

	static long perHour(long profit, long playedMs)
	{
		return playedMs <= 0 ? 0 : Math.round(profit * 3_600_000.0 / playedMs);
	}
}
