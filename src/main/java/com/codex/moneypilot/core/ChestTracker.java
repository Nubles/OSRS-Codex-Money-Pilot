package com.codex.moneypilot.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Turns what the game shows into the Rogues' Castle chest log, without touching anything. A chest counts
 * as opened when Thieving experience comes in soon after you chose "Search for traps" on one; the game's
 * chat lines for it are not on the wiki, so they are not relied on. Choosing "Open" instead and taking a
 * hit the trap's size counts as a chest opened without searching. What lands in the pack, or in an open
 * looting bag, around an opening is that chest's loot; food and drink used while looting are supplies;
 * and what was carried before a death and gone after it is lost, so a trip that ended in death counts.
 *
 * <p>Time counts from one chest to the next while the gap is under {@link #IDLE_TICKS}, so a trip to bank
 * or to restock counts but an evening away does not. The chests in view, open or closed, give which are
 * ready and when the next one will be.
 */
public final class ChestTracker
{
	/** A longer gap between chests ends a sitting; the gap itself is not counted. */
	public static final int IDLE_TICKS = 500;
	/** Experience this soon after choosing Search for traps on a chest is that chest opening. */
	static final int SEARCH_TICKS = 30;
	/** A trap-sized hit this soon after choosing Open on a chest is the trap. */
	static final int TRAP_TICKS = 3;
	/** The pack changing this close to an opening is its loot. */
	static final int LOOT_TICKS = 1;
	/** Ticks after a death before what is left is compared with what was carried. */
	static final int DEATH_TICKS = 10;

	private final ChestLog log;
	private long tick;
	private long searchedAt = -1;
	private long openChosenAt = -1;
	private long lastOpen = -1;
	private long xp = -1;
	private final Deque<Long> opens = new ArrayDeque<>();

	private Map<Integer, Long> pack;
	private Map<Integer, Long> worn = new HashMap<>();
	private Map<Integer, Long> bag;
	private final Deque<Change> changes = new ArrayDeque<>();

	private long diedAt = -1;
	private Map<Integer, Long> carriedAtDeath;
	private Map<Integer, Long> bagAtDeath;

	/** Chests in view by where they stand: the tick each was seen open, or -1 for closed and ready. */
	private final Map<Long, Long> chests = new HashMap<>();

	private ChestLog.Counts live = new ChestLog.Counts();
	private boolean changed;

	private static final class Change
	{
		final long at;
		final Map<Integer, Long> diff;
		final boolean banking;
		final boolean bag;

		Change(long at, Map<Integer, Long> diff, boolean banking, boolean bag)
		{
			this.at = at;
			this.diff = diff;
			this.banking = banking;
			this.bag = bag;
		}
	}

	public ChestTracker(ChestLog log)
	{
		this.log = log;
	}

	public ChestLog getLog()
	{
		return log;
	}

	/** The player chose an option on an object. */
	public void chose(String option, int objectId)
	{
		if (!RoguesChests.isChest(objectId) || option == null)
		{
			return;
		}
		if (RoguesChests.SEARCH.equalsIgnoreCase(option.trim()))
		{
			searchedAt = tick;
		}
		else if (RoguesChests.OPEN.equalsIgnoreCase(option.trim()))
		{
			openChosenAt = tick;
		}
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
		if (gain <= 0 || searchedAt < 0 || tick - searchedAt > SEARCH_TICKS)
		{
			return;
		}
		searchedAt = -1;
		ChestLog.Counts c = new ChestLog.Counts();
		c.opens = 1;
		c.xp = gain;
		if (!sitting())
		{
			live = new ChestLog.Counts();
		}
		else
		{
			c.ticks = tick - lastOpen;
		}
		lastOpen = tick;
		opens.addLast(tick);
		while (opens.size() > 8)
		{
			opens.removeFirst();
		}
		file(now, c);
	}

	/** The player was hit for {@code damage}. */
	public void hit(int damage, long now)
	{
		if (openChosenAt >= 0 && tick - openChosenAt <= TRAP_TICKS && RoguesChests.isTrapHit(damage))
		{
			openChosenAt = -1;
			ChestLog.Counts c = new ChestLog.Counts();
			c.trapped = 1;
			file(now, c);
		}
	}

	/** The player died. Only a death while looting is counted. */
	public void died()
	{
		if (!sitting() || diedAt >= 0)
		{
			return;
		}
		diedAt = tick;
		carriedAtDeath = carried();
		bagAtDeath = bag == null ? new HashMap<>() : new HashMap<>(bag);
		// The bag goes with the death; whatever is seen of it next is a fresh start.
		bag = null;
	}

	/** The pack as it now stands; {@code banking} is true while a bank or deposit box is open. */
	public void pack(Map<Integer, Long> items, boolean banking)
	{
		Map<Integer, Long> now = copy(items);
		if (pack != null)
		{
			queue(diff(pack, now), banking, false);
		}
		pack = now;
	}

	/** What is worn; only needed to see what a death took. */
	public void worn(Map<Integer, Long> items)
	{
		worn = copy(items);
	}

	/** The looting bag's contents, whenever the game sends them. The first sighting is only a start. */
	public void bag(Map<Integer, Long> items, boolean banking)
	{
		Map<Integer, Long> now = copy(items);
		if (bag != null)
		{
			queue(diff(bag, now), banking, true);
		}
		bag = now;
	}

	/** A chest came into view or changed: {@code where} is its packed location. */
	public void chest(long where, int objectId)
	{
		if (objectId == RoguesChests.CHEST)
		{
			chests.put(where, -1L);
		}
		else if (objectId == RoguesChests.CHEST_OPEN)
		{
			Long was = chests.get(where);
			chests.put(where, was != null && was >= 0 ? was : tick);
		}
	}

	/** A chest left view; it is only forgotten when what stands there now is no chest. */
	public void chestGone(long where, int objectId)
	{
		Long was = chests.get(where);
		if (was == null)
		{
			return;
		}
		if ((objectId == RoguesChests.CHEST && was < 0) || (objectId == RoguesChests.CHEST_OPEN && was >= 0))
		{
			chests.remove(where);
		}
	}

	/** The scene was loaded again: every chest is seen afresh. */
	public void sceneCleared()
	{
		chests.clear();
	}

	/** A game tick passed while logged in. */
	public void tick(IntPredicate edible, long now)
	{
		read(edible, now);
		if (diedAt >= 0 && tick - diedAt >= DEATH_TICKS)
		{
			settleDeath(now);
		}
		tick++;
	}

	private void read(IntPredicate edible, long now)
	{
		Iterator<Change> it = changes.iterator();
		while (it.hasNext())
		{
			Change ch = it.next();
			if (tick - ch.at <= LOOT_TICKS)
			{
				// The opening that goes with it may not have come in yet.
				break;
			}
			it.remove();
			if (ch.banking || ch.diff.isEmpty() || diedAt >= 0)
			{
				continue;
			}
			if (nearOpen(ch.at))
			{
				ChestLog.Counts c = new ChestLog.Counts();
				for (Map.Entry<Integer, Long> e : ch.diff.entrySet())
				{
					if (e.getValue() > 0)
					{
						c.loot.merge(e.getKey(), e.getValue(), Long::sum);
					}
				}
				file(now, c);
			}
			else if (!ch.bag && sitting())
			{
				used(ch.diff, edible, now);
			}
		}
	}

	/** Food or drink: one of an edible item gone, and little else moving. What it leaves behind is taken off. */
	private void used(Map<Integer, Long> diff, IntPredicate edible, long now)
	{
		int gone = 0;
		boolean ate = false;
		for (Map.Entry<Integer, Long> e : diff.entrySet())
		{
			if (e.getValue() < 0)
			{
				gone++;
				ate |= e.getValue() == -1 && edible != null && edible.test(e.getKey());
			}
		}
		if (!ate || gone > 2)
		{
			return;
		}
		ChestLog.Counts c = new ChestLog.Counts();
		for (Map.Entry<Integer, Long> e : diff.entrySet())
		{
			c.used.merge(e.getKey(), -e.getValue(), Long::sum);
		}
		file(now, c);
	}

	private void settleDeath(long now)
	{
		Map<Integer, Long> after = carried();
		ChestLog.Counts c = new ChestLog.Counts();
		c.deaths = 1;
		for (Map.Entry<Integer, Long> e : carriedAtDeath.entrySet())
		{
			long gone = e.getValue() - after.getOrDefault(e.getKey(), 0L);
			if (gone > 0)
			{
				c.lost.merge(e.getKey(), gone, Long::sum);
			}
		}
		bagAtDeath.forEach((id, q) ->
		{
			if (q > 0)
			{
				c.lost.merge(id, q, Long::sum);
			}
		});
		diedAt = -1;
		carriedAtDeath = null;
		bagAtDeath = null;
		// What moved in the pack around the death is the death, not loot or food.
		changes.clear();
		file(now, c);
	}

	private boolean nearOpen(long at)
	{
		for (long o : opens)
		{
			if (Math.abs(at - o) <= LOOT_TICKS)
			{
				return true;
			}
		}
		return false;
	}

	private void queue(Map<Integer, Long> diff, boolean banking, boolean fromBag)
	{
		if (!diff.isEmpty())
		{
			changes.addLast(new Change(tick, diff, banking, fromBag));
			while (changes.size() > 64)
			{
				changes.removeFirst();
			}
		}
	}

	private void file(long now, ChestLog.Counts c)
	{
		if (c.isEmpty())
		{
			return;
		}
		log.add(now, c);
		live.addAll(c);
		changed = true;
	}

	private boolean sitting()
	{
		return lastOpen >= 0 && tick - lastOpen <= IDLE_TICKS;
	}

	private Map<Integer, Long> carried()
	{
		Map<Integer, Long> out = pack == null ? new HashMap<>() : new HashMap<>(pack);
		worn.forEach((id, q) -> out.merge(id, q, Long::sum));
		return out;
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

	/** True while a sitting is under way: a chest opened in the last {@link #IDLE_TICKS}. */
	public boolean isLooting()
	{
		return sitting();
	}

	/** What the sitting under way has added up to. */
	public ChestLog.Counts getLive()
	{
		return live;
	}

	/** Ticks since the last chest, or -1 before the first. */
	public long sinceLastOpen()
	{
		return lastOpen < 0 ? -1 : tick - lastOpen;
	}

	/** Chests in view. */
	public int chestsSeen()
	{
		return chests.size();
	}

	/** Chests in view standing closed, ready to search. */
	public int chestsReady()
	{
		int n = 0;
		for (long at : chests.values())
		{
			if (at < 0)
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * Ticks until the next open chest restocks, from when it was seen open; 0 when one is ready now or it
	 * is overdue, -1 with no chest in view.
	 */
	public long nextReady()
	{
		if (chests.isEmpty())
		{
			return -1;
		}
		long best = Long.MAX_VALUE;
		for (long at : chests.values())
		{
			long left = at < 0 ? 0 : Math.max(0, at + RoguesChests.RESTOCK_TICKS - tick);
			best = Math.min(best, left);
		}
		return best;
	}

	/** Forgets the sitting and what waits, on logging out or changing account. */
	public void reset()
	{
		searchedAt = -1;
		openChosenAt = -1;
		lastOpen = -1;
		xp = -1;
		opens.clear();
		pack = null;
		worn = new HashMap<>();
		bag = null;
		changes.clear();
		diedAt = -1;
		carriedAtDeath = null;
		bagAtDeath = null;
		chests.clear();
		live = new ChestLog.Counts();
	}
}
