package com.codex.moneypilot.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Turns what the game shows into the Port Roberts stall log, without touching anything. Choosing
 * "Steal-from" on a market stall starts stealing, and the wiki says the game then "will automatically
 * thieve after clicking a stall until another action is taken"; so while no other menu option has been
 * chosen since, each gain of Thieving experience is a steal. The gain itself names the stall, since each
 * gives its own experience; when it matches none (a boost), the stall last chosen is used. What lands in
 * the pack on a steal's tick is its loot, so selling to the market traders or banking never counts.
 *
 * <p>A Market Guard's arrest counts as caught; coins that leave the pack after an arrest and before the
 * next steal, up to the guard's highest bribe, are what getting out of the jail cost. Time counts from one
 * steal to the next while the gap is under {@link #IDLE_TICKS}, so a trip to sell, to bank or out of the
 * jail counts but an evening away does not.
 *
 * <p>A sitting is filed under the rotation whose primary is the best stall stolen from in it. The first
 * {@link #SETTLE_STEALS} steals wait until a whole round has shown which that is.
 */
public final class StallTracker
{
	/** A longer gap between steals ends a sitting; the gap itself is not counted. */
	public static final int IDLE_TICKS = 500;
	/** Experience this soon after choosing Steal-from is the first steal. */
	static final int AIM_TICKS = 30;
	/** While stealing goes on by itself, the next steal comes this soon after the last. */
	static final int ON_TICKS = 3 * Stalls.STEAL_TICKS;
	/** The pack changing this close to a steal is its loot. */
	static final int LOOT_TICKS = 1;
	/** Steals held back at the start of a sitting until the rotation shows: one whole round of 4 and 2. */
	static final int SETTLE_STEALS = 6;
	/** After an arrest, coins paid out this soon are the bribe. */
	static final int JAIL_TICKS = 300;
	/** A gain this many times a steal's experience is not a steal (a lamp, a book). */
	private static final int MOST = 3;

	private final StallLog log;
	private long tick;
	private Stalls.Stall aimed;
	private long aimedAt = -1;
	private long lastSteal = -1;
	private long xp = -1;
	private final Deque<Long> steals = new ArrayDeque<>();
	private long caughtAt = -1;
	private long bribed;

	private Map<Integer, Long> pack;
	private final Deque<Change> changes = new ArrayDeque<>();

	/** The sitting: the best stall stolen from, the steals so far, and what waits to be filed. */
	private Stalls.Stall best;
	private int sittingSteals;
	private StallLog.Counts pending = new StallLog.Counts();
	private StallLog.Counts live = new StallLog.Counts();
	private Stalls.Stall lastStall;
	private boolean changed;

	private static final class Change
	{
		final long at;
		final Map<Integer, Long> diff;
		final boolean banking;

		Change(long at, Map<Integer, Long> diff, boolean banking)
		{
			this.at = at;
			this.diff = diff;
			this.banking = banking;
		}
	}

	public StallTracker(StallLog log)
	{
		this.log = log;
	}

	public StallLog getLog()
	{
		return log;
	}

	/** The player chose an option on a game object. */
	public void chose(String option, int objectId)
	{
		Stalls.Stall stall = Stalls.Stall.of(objectId);
		if (stall != null && option != null && Stalls.STEAL.equalsIgnoreCase(option.trim()))
		{
			aimed = stall;
			aimedAt = tick;
		}
		else
		{
			other();
		}
	}

	/** The player chose anything else, which stops the stealing. */
	public void other()
	{
		aimed = null;
		aimedAt = -1;
	}

	/** The account's Thieving experience, as the game last gave it. */
	public void thievingXp(int total, long now)
	{
		if (xp < 0 || total < xp)
		{
			xp = total;
			return;
		}
		long gain = total - xp;
		xp = total;
		if (gain <= 0 || aimed == null || !stealing())
		{
			return;
		}
		Stalls.Stall stall = Stalls.Stall.ofXp(gain);
		if (stall == null)
		{
			if (gain > (long) MOST * (aimed.getXpTenths() + 9) / 10)
			{
				return;
			}
			stall = aimed;
		}
		StallLog.Counts c = new StallLog.Counts();
		c.steals = 1;
		c.xp = gain;
		if (!sitting())
		{
			settle(now);
			best = null;
			sittingSteals = 0;
			live = new StallLog.Counts();
		}
		else
		{
			c.ticks = tick - lastSteal;
		}
		lastSteal = tick;
		lastStall = stall;
		caughtAt = -1;
		sittingSteals++;
		if (best == null || stall.getLevel() > best.getLevel())
		{
			best = stall;
		}
		steals.addLast(tick);
		while (steals.size() > 8)
		{
			steals.removeFirst();
		}
		file(now, c);
		if (sittingSteals == SETTLE_STEALS)
		{
			settle(now);
		}
	}

	/** A Market Guard was seen or heard making an arrest. Only counted while stealing. */
	public void arrested(long now)
	{
		if (!sitting() || caughtAt >= 0)
		{
			return;
		}
		caughtAt = tick;
		bribed = 0;
		other();
		StallLog.Counts c = new StallLog.Counts();
		c.caught = 1;
		file(now, c);
	}

	/** The pack as it now stands; {@code banking} is true while a bank or deposit box is open. */
	public void pack(Map<Integer, Long> items, boolean banking)
	{
		Map<Integer, Long> now = copy(items);
		if (pack != null)
		{
			Map<Integer, Long> diff = diff(pack, now);
			if (!diff.isEmpty())
			{
				changes.addLast(new Change(tick, diff, banking));
				while (changes.size() > 64)
				{
					changes.removeFirst();
				}
			}
		}
		pack = now;
	}

	/** A game tick passed while logged in. */
	public void tick(long now)
	{
		read(now);
		if (sittingSteals > 0 && !sitting())
		{
			settle(now);
		}
		tick++;
	}

	private void read(long now)
	{
		Iterator<Change> it = changes.iterator();
		while (it.hasNext())
		{
			Change ch = it.next();
			if (tick - ch.at <= LOOT_TICKS)
			{
				// The steal that goes with it may not have come in yet.
				break;
			}
			it.remove();
			if (ch.banking)
			{
				continue;
			}
			if (nearSteal(ch.at))
			{
				StallLog.Counts c = new StallLog.Counts();
				ch.diff.forEach((id, q) ->
				{
					if (q > 0)
					{
						c.loot.merge(id, q, Long::sum);
					}
				});
				file(now, c);
			}
			else if (caughtAt >= 0 && ch.at - caughtAt <= JAIL_TICKS)
			{
				long coins = -ch.diff.getOrDefault(ItemID.COINS, 0L);
				long take = Math.min(coins, Stalls.BRIBE_MAX - bribed);
				if (take > 0)
				{
					bribed += take;
					StallLog.Counts c = new StallLog.Counts();
					c.paid.put(ItemID.COINS, take);
					file(now, c);
				}
			}
		}
	}

	private boolean nearSteal(long at)
	{
		for (long s : steals)
		{
			if (Math.abs(at - s) <= LOOT_TICKS)
			{
				return true;
			}
		}
		return false;
	}

	private void file(long now, StallLog.Counts c)
	{
		if (c.isEmpty())
		{
			return;
		}
		live.addAll(c);
		if (sittingSteals < SETTLE_STEALS && sittingSteals > 0 && sitting())
		{
			pending.addAll(c);
			return;
		}
		Stalls.Rotation r = best == null ? null : Stalls.Rotation.of(best);
		if (r != null)
		{
			log.add(now, r, c);
			changed = true;
		}
	}

	/** Files what waited at the start of the sitting under the rotation it showed. */
	private void settle(long now)
	{
		if (pending.isEmpty())
		{
			return;
		}
		Stalls.Rotation r = best == null ? null : Stalls.Rotation.of(best);
		if (r != null)
		{
			log.add(now, r, pending);
			changed = true;
		}
		pending = new StallLog.Counts();
		if (sittingSteals < SETTLE_STEALS)
		{
			// Settled early (the sitting ended): what follows is a new sitting.
			sittingSteals = SETTLE_STEALS;
		}
	}

	/** Files what waits; on saving, logging out or turning the log off. */
	public void flush(long now)
	{
		settle(now);
	}

	private boolean stealing()
	{
		return (aimedAt >= 0 && tick - aimedAt <= AIM_TICKS) || (lastSteal >= 0 && tick - lastSteal <= ON_TICKS);
	}

	private boolean sitting()
	{
		return lastSteal >= 0 && tick - lastSteal <= IDLE_TICKS;
	}

	private static Map<Integer, Long> copy(Map<Integer, Long> items)
	{
		Map<Integer, Long> out = new HashMap<>();
		if (items != null)
		{
			items.forEach((id, q) ->
			{
				if (id != null && q != null && q > 0)
				{
					out.put(id, q);
				}
			});
		}
		return out;
	}

	private static Map<Integer, Long> diff(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		Map<Integer, Long> out = new HashMap<>();
		for (int id : ids)
		{
			long change = after.getOrDefault(id, 0L) - before.getOrDefault(id, 0L);
			if (change != 0)
			{
				out.put(id, change);
			}
		}
		return out;
	}

	/** True once since the log last changed, so it is saved. */
	public boolean takeChanged()
	{
		boolean c = changed;
		changed = false;
		return c;
	}

	/** True while a sitting is under way: a steal in the last {@link #IDLE_TICKS}. */
	public boolean isStealing()
	{
		return sitting();
	}

	/** What the sitting under way has added up to. */
	public StallLog.Counts getLive()
	{
		return live;
	}

	/** The rotation the sitting is filed under so far, or null before a steal or below the silver stall. */
	public Stalls.Rotation getRotation()
	{
		return best == null ? null : Stalls.Rotation.of(best);
	}

	/** The best stall stolen from this sitting, or null. */
	public Stalls.Stall getBest()
	{
		return best;
	}

	/** The stall of the last steal, or null. */
	public Stalls.Stall getLastStall()
	{
		return lastStall;
	}

	/** Ticks since the last steal, or -1 before the first. */
	public long sinceLastSteal()
	{
		return lastSteal < 0 ? -1 : tick - lastSteal;
	}

	/** Forgets the sitting and what waits, on logging out or changing account; file first with {@link #flush}. */
	public void reset()
	{
		aimed = null;
		aimedAt = -1;
		lastSteal = -1;
		xp = -1;
		steals.clear();
		caughtAt = -1;
		bribed = 0;
		pack = null;
		changes.clear();
		best = null;
		sittingSteals = 0;
		pending = new StallLog.Counts();
		live = new StallLog.Counts();
		lastStall = null;
	}
}
