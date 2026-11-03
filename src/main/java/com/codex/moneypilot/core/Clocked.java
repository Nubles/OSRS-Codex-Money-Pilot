package com.codex.moneypilot.core;

/** A finished run of a method: when, for how long it was played, and what the account gained. */
public final class Clocked
{
	/** A run shorter than this is not kept; it says too little about the rate. */
	public static final long MIN_MS = 5 * 60_000L;

	String m;
	long t;
	long ms;
	long gp;

	Clocked()
	{
	}

	public Clocked(String method, long startedAt, long playedMs, long profit)
	{
		this.m = method;
		this.t = startedAt;
		this.ms = playedMs;
		this.gp = profit;
	}

	public String getMethod()
	{
		return m;
	}

	public long getStartedAt()
	{
		return t;
	}

	public long getPlayedMs()
	{
		return ms;
	}

	public long getProfit()
	{
		return gp;
	}

	public boolean isValid()
	{
		return m != null && t > 0 && ms >= MIN_MS && ms <= 30L * 86_400_000L;
	}
}
