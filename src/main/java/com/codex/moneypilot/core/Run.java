package com.codex.moneypilot.core;

/**
 * A run of one method being clocked: what the account held as it began and how long it has been
 * played. Time only passes while logged in, a game tick at a time, so a break does not lower the
 * rate. Plain fields so it saves to the profile and survives a restart of the client.
 */
public final class Run
{
	public static final long TICK_MS = 600;

	String method;
	long startedAt;
	long ticks;
	String start;

	Run()
	{
	}

	public Run(String method, long startedAt, Holdings start)
	{
		this.method = method;
		this.startedAt = startedAt;
		this.start = start.encode();
	}

	public String getMethod()
	{
		return method;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getPlayedMs()
	{
		return ticks * TICK_MS;
	}

	/** A game tick passed while logged in. */
	public void tick()
	{
		ticks++;
	}

	public Holdings getStart()
	{
		return Holdings.decode(start);
	}

	/** True when a saved run is whole enough to carry on with. */
	public boolean isValid()
	{
		return method != null && Methods.byId(method) != null && startedAt > 0 && ticks >= 0;
	}

	/** The run as finished, with what the account gained over it. */
	public Clocked finish(long profit)
	{
		return new Clocked(method, startedAt, getPlayedMs(), profit);
	}
}
