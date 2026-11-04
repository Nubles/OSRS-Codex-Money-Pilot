package com.codex.moneypilot.core;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

/**
 * The Port Roberts market stalls and the few game facts the stall log leans on, each from the wiki's
 * "Port Roberts" page and its stall pages (read 4 November 2026), with ids from RuneLite's gameval.
 * Loot is never looked up from a table: the log values what actually lands in your pack.
 *
 * <p>The wiki's way to work the market is a rotation: steal from a primary stall four times and a
 * secondary stall twice while the guards look elsewhere. Each rotation the wiki lists from 50 Thieving
 * up is a money making method here, and a sitting at the stalls is filed under the rotation whose
 * primary is the best stall you stole from in it.
 */
public final class Stalls
{
	/** "players can only steal from a stall every 5 ticks instead of every 2". */
	public static final int STEAL_TICKS = 5;
	/** Steals an hour at one every {@link #STEAL_TICKS} ticks of 0.6 seconds. */
	public static final int STEALS_AN_HOUR = 6_000 / STEAL_TICKS;
	/** "Level 50 Sailing is required to dock at and Charter to Port Roberts." */
	public static final int SAILING = 50;
	/** The stalls' only option: "Steal-from". */
	public static final String STEAL = "Steal-from";
	/** What a Market Guard says when he catches you (the wiki's transcript of the Market Guard). */
	public static final String ARREST = "Hey! What do you think you're doing?! You're under arrest!";
	/** "In the jail, players can picklock the gate to leave, or bribe the guard who will ask for up to 500 coins." */
	public static final int BRIBE_MAX = 500;

	public enum Stall
	{
		// Levels and experience from each stall's page; object ids from gameval ObjectID (PORT_ROBERTS_MARKET_STALL_*),
		// which match the ids on those pages.
		VEG("Veg stall", ObjectID.PORT_ROBERTS_MARKET_STALL_VEG, 2, 50),
		SILK("Silk stall", ObjectID.PORT_ROBERTS_MARKET_STALL_SILK, 20, 240),
		FUR("Fur stall", ObjectID.PORT_ROBERTS_MARKET_STALL_FUR, 35, 385),
		FISH("Fish stall", ObjectID.PORT_ROBERTS_MARKET_STALL_FISH, 42, 495),
		SILVER("Silver stall", ObjectID.PORT_ROBERTS_MARKET_STALL_SILVER, 50, 800),
		SPICE("Spice stall", ObjectID.PORT_ROBERTS_MARKET_STALL_SPICE, 65, 1100),
		GEM("Gem stall", ObjectID.PORT_ROBERTS_MARKET_STALL_GEMS, 75, 1295),
		ORE("Ore stall", ObjectID.PORT_ROBERTS_MARKET_STALL_ORE, 82, 1910),
		CANNONBALL("Cannonball stall", ObjectID.PORT_ROBERTS_MARKET_STALL_CBALL, 87, 2230);

		private final String title;
		private final int objectId;
		private final int level;
		private final int xpTenths;

		Stall(String title, int objectId, int level, int xpTenths)
		{
			this.title = title;
			this.objectId = objectId;
			this.level = level;
			this.xpTenths = xpTenths;
		}

		public String getTitle()
		{
			return title;
		}

		public int getObjectId()
		{
			return objectId;
		}

		/** The Thieving level the stall needs. */
		public int getLevel()
		{
			return level;
		}

		/** Thieving experience a steal gives, in tenths (38.5 is 385). */
		public int getXpTenths()
		{
			return xpTenths;
		}

		/** True when a gain in the game's whole-number experience is one steal here: 38.5 shows as 38 or 39. */
		boolean gives(long gain)
		{
			return gain == xpTenths / 10 || gain == (xpTenths + 9) / 10;
		}

		/** The stall standing as this object, or null. */
		public static Stall of(int objectId)
		{
			for (Stall s : values())
			{
				if (s.objectId == objectId)
				{
					return s;
				}
			}
			return null;
		}

		/** The one stall a steal of this much experience comes from, or null when none or more than one could. */
		static Stall ofXp(long gain)
		{
			Stall found = null;
			for (Stall s : values())
			{
				if (s.gives(gain))
				{
					if (found != null)
					{
						return null;
					}
					found = s;
				}
			}
			return found;
		}
	}

	/** The wiki's rotations from 50 Thieving: the primary stall four times, the secondary twice. */
	public enum Rotation
	{
		// "Thieving level | primary stall | secondary stall | XP per hour" from the Port Roberts page's table.
		SILVER("port-roberts-silver", "Silver and fish stalls", Stall.SILVER, Stall.FISH, 83_800),
		SPICE("port-roberts-spice", "Spice and silver stalls", Stall.SPICE, Stall.SILVER, 120_000),
		GEM("port-roberts-gems", "Gem and spice stalls", Stall.GEM, Stall.SPICE, 147_600),
		ORE("port-roberts-ore", "Ore and gem stalls", Stall.ORE, Stall.GEM, 204_600),
		CANNONBALL("port-roberts-cannonballs", "Cannonball and ore stalls", Stall.CANNONBALL, Stall.ORE, 254_800);

		private final String method;
		private final String title;
		private final Stall primary;
		private final Stall secondary;
		private final int wikiXpPerHour;

		Rotation(String method, String title, Stall primary, Stall secondary, int wikiXpPerHour)
		{
			this.method = method;
			this.title = title;
			this.primary = primary;
			this.secondary = secondary;
			this.wikiXpPerHour = wikiXpPerHour;
		}

		/** The id of the money making method this rotation's rate feeds. */
		public String getMethod()
		{
			return method;
		}

		public String getTitle()
		{
			return title;
		}

		public Stall getPrimary()
		{
			return primary;
		}

		public Stall getSecondary()
		{
			return secondary;
		}

		/** The wiki's most experience an hour for the rotation, with no ticks lost. */
		public int getWikiXpPerHour()
		{
			return wikiXpPerHour;
		}

		/** The rotation whose primary stall this is, or null for a stall below the silver stall. */
		public static Rotation of(Stall primary)
		{
			for (Rotation r : values())
			{
				if (r.primary == primary)
				{
					return r;
				}
			}
			return null;
		}

		/** The rotation a method id names, or null. */
		public static Rotation ofMethod(String method)
		{
			for (Rotation r : values())
			{
				if (r.method.equals(method))
				{
					return r;
				}
			}
			return null;
		}
	}

	private Stalls()
	{
	}

	/** True for the three Market Guards that patrol the stalls (gameval NpcID PORT_ROBERTS_MARKET_GUARD*). */
	public static boolean isMarketGuard(int npcId)
	{
		return npcId == NpcID.PORT_ROBERTS_MARKET_GUARD || npcId == NpcID.PORT_ROBERTS_MARKET_GUARD_2
			|| npcId == NpcID.PORT_ROBERTS_MARKET_GUARD_3;
	}

	/** True when a line the game shows is a Market Guard's arrest. */
	public static boolean isArrest(String text)
	{
		return text != null && text.contains(ARREST);
	}
}
