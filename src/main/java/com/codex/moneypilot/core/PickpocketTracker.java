package com.codex.moneypilot.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Turns what the game says and shows into the pickpocket log, without touching anything: the chat
 * lines for a pick that worked, one that failed and a stun; the NPC chosen to pickpocket; and the pack,
 * a tick at a time. Nothing here works out a success chance; it counts.
 *
 * <p>A pick is filed under its target as soon as the target is known: at once for a Master Farmer, by
 * name, and for the rest when the first coin pouch arrives from that NPC, since each kind of NPC gives
 * its own pouch. Picks before that wait, so a fail on a vyre before the first pouch still counts. Picks
 * of NPCs that are never known (men, H.A.M. members) are simply not logged.
 *
 * <p>Time counts from one pick to the next while the gap is under {@link #IDLE_TICKS}, so a bank trip
 * counts but an evening away does not. Ticks only pass while logged in.
 */
public final class PickpocketTracker
{
	/** A longer gap between picks ends a sitting; the gap itself is not counted. */
	public static final int IDLE_TICKS = 500;
	/** The pack changing this many ticks after a pick that worked is that pick's loot. */
	static final int LOOT_TICKS = 1;
	/** Names waiting for their target, at most; more is someone pickpocketing everyone in a town. */
	private static final int MAX_PENDING = 24;
	private static final String UNKNOWN = "";

	private final PickpocketLog log;
	private long tick;
	/** The NPC last chosen to pickpocket, and the NPC of the last pick. */
	private String aim;
	private String lastName;
	private long lastPick = -1;
	private long lastSuccess = -1;
	private final Map<String, Pickpockets.Target> learned = new HashMap<>();
	private final Map<String, PickpocketLog.Counts> pending = new HashMap<>();

	/** The pack as last seen, and its changes not yet read (read a tick late, once the chat has caught up). */
	private Map<Integer, Long> pack;
	private final Map<Integer, Long> unread = new HashMap<>();
	private long unreadAt = -1;
	private boolean unreadBanking;

	/** The sitting under way: its target, what it has added up to, and when its last pick was. */
	private Pickpockets.Target sitting;
	private PickpocketLog.Counts live = new PickpocketLog.Counts();
	private long sittingPick = -1;
	private boolean changed;

	public PickpocketTracker(PickpocketLog log)
	{
		this.log = log;
	}

	public PickpocketLog getLog()
	{
		return log;
	}

	/** A game tick passed while logged in. */
	public void tick(IntPredicate edible, long now)
	{
		readPack(edible, now);
		tick++;
	}

	/** The player chose Pickpocket on an NPC. */
	public void aim(String npc)
	{
		aim = npc == null ? null : npc.trim();
	}

	/** A chat line from the game. Returns true when it was about pickpocketing. */
	public boolean chat(String message, long now)
	{
		Pickpockets.Said said = Pickpockets.read(message);
		switch (said)
		{
			case SUCCESS:
			case FAIL:
			{
				String name = aim == null || aim.isEmpty() ? UNKNOWN : aim;
				PickpocketLog.Counts c = bucket(name);
				if (lastPick >= 0 && tick - lastPick <= IDLE_TICKS)
				{
					c.ticks += tick - lastPick;
				}
				lastPick = tick;
				lastName = name;
				if (said == Pickpockets.Said.SUCCESS)
				{
					c.successes++;
					lastSuccess = tick;
				}
				else
				{
					c.fails++;
				}
				file(now);
				return true;
			}
			case STUNNED:
				if (lastName != null && lastPick >= 0 && tick - lastPick <= 2)
				{
					bucket(lastName).stuns++;
					file(now);
				}
				return true;
			default:
				return false;
		}
	}

	/**
	 * The pack (with the seed box, when the game shows it) as it now stands. {@code banking} is true while
	 * the bank or a deposit box is open, when what leaves the pack is not eaten.
	 */
	public void pack(Map<Integer, Long> items, boolean banking)
	{
		Map<Integer, Long> now = items == null ? new HashMap<>() : new HashMap<>(items);
		if (pack != null)
		{
			Set<Integer> ids = new HashSet<>(now.keySet());
			ids.addAll(pack.keySet());
			for (int id : ids)
			{
				long change = now.getOrDefault(id, 0L) - pack.getOrDefault(id, 0L);
				if (change != 0)
				{
					unread.merge(id, change, Long::sum);
				}
			}
			unread.values().removeIf(v -> v == 0);
			if (unreadAt < 0)
			{
				unreadAt = tick;
			}
			unreadBanking |= banking;
		}
		pack = now;
	}

	private void readPack(IntPredicate edible, long now)
	{
		if (unreadAt < 0)
		{
			return;
		}
		Map<Integer, Long> diff = new HashMap<>(unread);
		boolean banking = unreadBanking;
		long at = unreadAt;
		unread.clear();
		unreadAt = -1;
		unreadBanking = false;
		if (diff.isEmpty() || banking)
		{
			return;
		}

		// Coin pouches opened: the coins that came with them are that target's.
		boolean opened = false;
		for (Pickpockets.Target t : Pickpockets.Target.values())
		{
			long change = t.hasPouch() ? diff.getOrDefault(t.getPouch(), 0L) : 0;
			if (change < 0)
			{
				opened = true;
				PickpocketLog.Counts c = new PickpocketLog.Counts();
				c.opened = (int) Math.min(Integer.MAX_VALUE, -change);
				long coins = diff.getOrDefault(Items.COINS, 0L);
				if (coins > 0)
				{
					c.loot.put(Items.COINS, coins);
					diff.remove(Items.COINS);
				}
				log.add(t, now, c);
				if (t == sitting)
				{
					live.addAll(c);
				}
				changed = true;
			}
		}

		// Food and drink: one of an item gone, outside the bank, while picking.
		int gone = 0;
		Map<Integer, Long> ate = new HashMap<>();
		for (Map.Entry<Integer, Long> e : diff.entrySet())
		{
			if (e.getValue() < 0)
			{
				gone++;
				if (e.getValue() == -1 && edible != null && edible.test(e.getKey()))
				{
					ate.put(e.getKey(), 1L);
				}
			}
		}
		boolean picking = lastName != null && lastPick >= 0 && tick - lastPick <= IDLE_TICKS;
		if (!ate.isEmpty() && gone <= 2 && picking && !opened)
		{
			PickpocketLog.Counts c = bucket(lastName);
			for (Map.Entry<Integer, Long> e : ate.entrySet())
			{
				c.food++;
				c.eaten.merge(e.getKey(), e.getValue(), Long::sum);
			}
		}

		// Loot: what came in just after a pick that worked. A tick that also ate is left alone, since what
		// is left of a drink (an empty jug) is not loot.
		if (lastSuccess >= 0 && lastName != null && at - lastSuccess <= LOOT_TICKS && at >= lastSuccess && ate.isEmpty()
			&& !opened)
		{
			PickpocketLog.Counts c = bucket(lastName);
			for (Map.Entry<Integer, Long> e : diff.entrySet())
			{
				if (e.getValue() <= 0)
				{
					continue;
				}
				Pickpockets.Target pouch = Pickpockets.Target.ofPouch(e.getKey());
				if (pouch != null)
				{
					// The pouch tells which kind of NPC this is; its coins count when it is opened.
					learned.put(lastName, pouch);
					continue;
				}
				c.loot.merge(e.getKey(), e.getValue(), Long::sum);
			}
		}
		file(now);
	}

	private PickpocketLog.Counts bucket(String name)
	{
		PickpocketLog.Counts c = pending.get(name);
		if (c == null)
		{
			if (pending.size() >= MAX_PENDING)
			{
				pending.clear();
			}
			c = new PickpocketLog.Counts();
			pending.put(name, c);
		}
		return c;
	}

	/** Files what waits under a name whose target is now known. */
	private void file(long now)
	{
		Iterator<Map.Entry<String, PickpocketLog.Counts>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<String, PickpocketLog.Counts> e = it.next();
			Pickpockets.Target t = target(e.getKey());
			if (t == null || e.getValue().isEmpty())
			{
				continue;
			}
			PickpocketLog.Counts c = e.getValue();
			log.add(t, now, c);
			if (t != sitting || sittingPick < 0 || lastPick - sittingPick > IDLE_TICKS)
			{
				sitting = t;
				live = new PickpocketLog.Counts();
			}
			live.addAll(c);
			sittingPick = lastPick;
			it.remove();
			changed = true;
		}
	}

	private Pickpockets.Target target(String name)
	{
		Pickpockets.Target t = Pickpockets.Target.ofName(name);
		return t != null ? t : learned.get(name);
	}

	/** True once since the log last changed, so it is saved. */
	public boolean takeChanged()
	{
		boolean c = changed;
		changed = false;
		return c;
	}

	/** The target of the sitting under way, or null when none has been picked lately. */
	public Pickpockets.Target getSitting()
	{
		return sitting != null && sittingPick >= 0 && tick - sittingPick <= IDLE_TICKS ? sitting : null;
	}

	/** What the sitting under way has added up to. */
	public PickpocketLog.Counts getLive()
	{
		return live;
	}

	/** Ticks since the last pick, or -1 before the first. */
	public long sinceLastPick()
	{
		return lastPick < 0 ? -1 : tick - lastPick;
	}

	/** Coin pouches of the sitting's kind in the pack now. */
	public long pouches()
	{
		Pickpockets.Target t = getSitting();
		return t == null || !t.hasPouch() || pack == null ? 0 : pack.getOrDefault(t.getPouch(), 0L);
	}

	/** Forgets the sitting and what waits, on logging out or changing account. */
	public void reset()
	{
		aim = null;
		lastName = null;
		lastPick = -1;
		lastSuccess = -1;
		learned.clear();
		pending.clear();
		pack = null;
		unread.clear();
		unreadAt = -1;
		unreadBanking = false;
		sitting = null;
		live = new PickpocketLog.Counts();
		sittingPick = -1;
	}

	/** Item ids the tracker needs by name. */
	static final class Items
	{
		static final int COINS = net.runelite.api.gameval.ItemID.COINS;

		private Items()
		{
		}
	}
}
