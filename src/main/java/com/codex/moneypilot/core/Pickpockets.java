package com.codex.moneypilot.core;

import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * The pickpocketing money makers Money Pilot logs, and the few game facts the log leans on. Which NPC
 * was picked is learned from the coin pouch it gives (each kind of NPC gives its own pouch item), so
 * the many named elves and vyres need no list of names; Master Farmers give no pouch and are known by
 * name. Success chances are never worked out from a formula: the log counts your own picks.
 */
public final class Pickpockets
{
	public enum Target
	{
		// Item ids from RuneLite's gameval ItemID (PICKPOCKET_COIN_POUCH_*), matching the wiki's Coin pouch page.
		KNIGHTS("ardougne-knights", "Ardougne knights", ItemID.PICKPOCKET_COIN_POUCH_KNIGHT),
		PALADINS("paladins", "Paladins", ItemID.PICKPOCKET_COIN_POUCH_PALADIN),
		MASTER_FARMERS("master-farmers", "Master Farmers", -1, "Master Farmer", "Martin the Master Gardener"),
		VYRES("vyres", "Vyres", ItemID.PICKPOCKET_COIN_POUCH_VYRE),
		ELVES("elves", "Elves", ItemID.PICKPOCKET_COIN_POUCH_ELF);

		private final String method;
		private final String title;
		private final int pouch;
		private final String[] names;

		Target(String method, String title, int pouch, String... names)
		{
			this.method = method;
			this.title = title;
			this.pouch = pouch;
			this.names = names;
		}

		/** The id of the money making method this target's rate feeds. */
		public String getMethod()
		{
			return method;
		}

		public String getTitle()
		{
			return title;
		}

		/** The coin pouch item this target gives; -1 when it gives none. */
		public int getPouch()
		{
			return pouch;
		}

		public boolean hasPouch()
		{
			return pouch > 0;
		}

		/** The target whose coin pouch this item is, or null. */
		public static Target ofPouch(int itemId)
		{
			for (Target t : values())
			{
				if (t.pouch > 0 && t.pouch == itemId)
				{
					return t;
				}
			}
			return null;
		}

		/** The target an NPC's name always means (Master Farmers, who give no pouch), or null. */
		public static Target ofName(String npc)
		{
			if (npc == null)
			{
				return null;
			}
			for (Target t : values())
			{
				for (String n : t.names)
				{
					if (n.equalsIgnoreCase(npc.trim()))
					{
						return t;
					}
				}
			}
			return null;
		}

		/** Reads a saved name; null when it is not one. */
		public static Target of(String name)
		{
			if (name == null)
			{
				return null;
			}
			try
			{
				return valueOf(name.toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException e)
			{
				return null;
			}
		}
	}

	/**
	 * The game's messages, as RuneLite's own crowdsourcing code reads them (CrowdsourcingThieving):
	 * "You pick ...'s pocket." and "You fail to pick ...'s pocket.".
	 */
	static final Pattern SUCCESS = Pattern.compile("You pick .*'s pocket\\.");
	static final Pattern FAIL = Pattern.compile("You fail to pick .*'s pocket\\.");
	/** Unconfirmed on the wiki; the text Pickpocket Helper (pajlads) matches. */
	static final String STUNNED = "You've been stunned!";

	/** Coin pouches held at once: 28, then 56, 84 and 140 after the medium, hard and elite Ardougne Diary. */
	public static final int BASE_CAP = 28;

	private Pickpockets()
	{
	}

	/** How many coin pouches of a kind the account can hold before it must open them. */
	public static int pouchCap(boolean medium, boolean hard, boolean elite)
	{
		if (elite)
		{
			return 140;
		}
		if (hard)
		{
			return 84;
		}
		if (medium)
		{
			return 56;
		}
		return BASE_CAP;
	}

	/** What a chat line says about pickpocketing. */
	public enum Said
	{
		SUCCESS, FAIL, STUNNED, NONE
	}

	public static Said read(String message)
	{
		if (message == null)
		{
			return Said.NONE;
		}
		String m = message.trim();
		if (STUNNED.equals(m))
		{
			return Said.STUNNED;
		}
		if (FAIL.matcher(m).matches())
		{
			return Said.FAIL;
		}
		if (SUCCESS.matcher(m).matches())
		{
			return Said.SUCCESS;
		}
		return Said.NONE;
	}
}
