package com.codex.moneypilot.core;

/** How money and time read in the panel. */
public final class Format
{
	private Format()
	{
	}

	/** 1.2m, 640k, 950, with a minus sign when it is a loss. */
	public static String gp(long v)
	{
		long a = Math.abs(v);
		String s;
		if (a >= 1_000_000_000L)
		{
			s = trim(a / 1_000_000_000.0) + "b";
		}
		else if (a >= 1_000_000L)
		{
			s = trim(a / 1_000_000.0) + "m";
		}
		else if (a >= 10_000L)
		{
			s = Math.round(a / 1_000.0) + "k";
		}
		else
		{
			s = String.format("%,d", a);
		}
		return v < 0 ? "-" + s : s;
	}

	/** gp with a plus sign when it is a gain. */
	public static String signed(long v)
	{
		return v > 0 ? "+" + gp(v) : gp(v);
	}

	private static String trim(double v)
	{
		String s = v >= 100 ? String.valueOf(Math.round(v)) : String.format("%.2f", v);
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}

	/** 1h 05m, 12m, 40s. */
	public static String duration(long ms)
	{
		long s = Math.max(0, ms / 1000);
		long h = s / 3600;
		long m = (s % 3600) / 60;
		if (h > 0)
		{
			return h + "h " + (m < 10 ? "0" : "") + m + "m";
		}
		if (m > 0)
		{
			return m + "m";
		}
		return s + "s";
	}

	/** A clock face: 1:05:09 or 5:09. */
	public static String clock(long ms)
	{
		long s = Math.max(0, ms / 1000);
		long h = s / 3600;
		long m = (s % 3600) / 60;
		long sec = s % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
	}
}
