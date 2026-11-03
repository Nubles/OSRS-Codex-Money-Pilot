package com.codex.moneypilot.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One of Codex Session Chronicle's filed sessions, as far as Money Pilot needs it. Read from the
 * RuneLite profile (group "codexsessionchronicle", key "journal") and never written: when it began,
 * how long it was played, what it was mainly spent on ("Vorkath", "Runecraft"), and what it made.
 * What it made is the net worth Codex Ledger saw it gain when Ledger was running, or else the loot
 * Chronicle counted less the supplies used.
 */
public final class Logged
{
	/** More than any account is worth; a figure past it is a damaged save. */
	static final long MAX_WORTH = 10_000_000_000_000L;
	/** A session must be at least this long for its rate to count, as Chronicle's own records do. */
	public static final long MIN_PLAYED_MS = 15 * 60_000L;

	private final long startedAt;
	private final long playedMs;
	private final String activity;
	private final long profit;
	private final boolean byWorth;

	public Logged(long startedAt, long playedMs, String activity, long profit, boolean byWorth)
	{
		this.startedAt = startedAt;
		this.playedMs = playedMs;
		this.activity = activity;
		this.profit = profit;
		this.byWorth = byWorth;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getPlayedMs()
	{
		return playedMs;
	}

	public String getActivity()
	{
		return activity;
	}

	public long getProfit()
	{
		return profit;
	}

	/** True when the profit is Ledger's net worth change, which counts everything. */
	public boolean isByWorth()
	{
		return byWorth;
	}

	/**
	 * Chronicle's saved journal; empty when there is none or it cannot be read. A session that cannot
	 * be read, is too short, or has no activity is left out on its own.
	 */
	public static List<Logged> read(Gson gson, String json, long now)
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		JsonElement root;
		try
		{
			root = gson.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException e)
		{
			return Collections.emptyList();
		}
		if (root == null || !root.isJsonArray())
		{
			return Collections.emptyList();
		}
		List<Logged> out = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray())
		{
			try
			{
				Logged l = e.isJsonObject() ? one(e.getAsJsonObject(), now) : null;
				if (l != null)
				{
					out.add(l);
				}
			}
			catch (RuntimeException ex)
			{
				// This session only.
			}
		}
		return out;
	}

	private static Logged one(JsonObject o, long now)
	{
		long startedAt = whole(o, "startedAt");
		long playedMs = whole(o, "playedMs");
		String activity = o.has("activity") && o.get("activity").isJsonPrimitive() ? o.get("activity").getAsString().trim() : "";
		if (activity.isEmpty() || startedAt <= 0 || startedAt > now || playedMs < MIN_PLAYED_MS
			|| playedMs > now - startedAt + 60_000L)
		{
			return null;
		}
		Long start = worth(o, "worthStart");
		Long end = worth(o, "worthNow");
		if (start != null && end != null)
		{
			return new Logged(startedAt, playedMs, activity, end - start, true);
		}
		long loot = Math.max(0, whole(o, "otherLoot"));
		JsonElement drops = o.get("loot");
		if (drops != null && drops.isJsonObject())
		{
			for (java.util.Map.Entry<String, JsonElement> d : drops.getAsJsonObject().entrySet())
			{
				if (d.getValue().isJsonObject())
				{
					JsonObject drop = d.getValue().getAsJsonObject();
					long qty = Math.max(0, whole(drop, "quantity"));
					long price = Math.max(0, whole(drop, "price"));
					if (qty > 0 && price > 0 && qty <= MAX_WORTH / price)
					{
						loot += qty * price;
					}
				}
			}
		}
		long supplies = Math.max(0, whole(o, "supplies"));
		return new Logged(startedAt, playedMs, activity, Math.min(loot, MAX_WORTH) - Math.min(supplies, MAX_WORTH), false);
	}

	private static Long worth(JsonObject o, String key)
	{
		if (!o.has(key) || o.get(key).isJsonNull())
		{
			return null;
		}
		long v = whole(o, key);
		return v < 0 || v > MAX_WORTH ? null : v;
	}

	private static long whole(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		try
		{
			return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
		}
		catch (RuntimeException ex)
		{
			return 0;
		}
	}
}
