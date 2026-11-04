package com.codex.moneypilot.core;

import net.runelite.api.gameval.ObjectID;

/**
 * The few game facts the Rogues' Castle chest log leans on, each from the wiki's "Chest (Rogues' Castle)"
 * page (read 4 November 2026) and RuneLite's gameval ids. Loot is never looked up from a table: the log
 * values what actually lands in your pack.
 */
public final class RoguesChests
{
	/** The money making method this log's rate feeds. */
	public static final String METHOD = "rogues-castle";

	/** The chest, closed and ready, and the same chest standing open until it restocks (gameval ObjectID). */
	public static final int CHEST = ObjectID.WILDERNESS_ROGUE_CHEST;
	public static final int CHEST_OPEN = ObjectID.WILDERNESS_ROGUE_CHEST_OPEN;

	/** "They require 84 Thieving to open." */
	public static final int LEVEL = 84;
	/** "the player is granted 701.7 Thieving experience", in tenths. Shown for reference; the log counts your own. */
	public static final int XP_TENTHS = 7017;
	/** "Three chests, each resetting in 34 game ticks (20.4 seconds)". */
	public static final int CHESTS = 3;
	public static final int RESTOCK_TICKS = 34;
	/** "the open option will cause the player to be hit for severe damage, anywhere from 16 to 43." */
	public static final int TRAP_MIN = 16;
	public static final int TRAP_MAX = 43;

	/** The menu options on the chest: "Open, Search for traps". */
	public static final String SEARCH = "Search for traps";
	public static final String OPEN = "Open";

	private RoguesChests()
	{
	}

	public static boolean isChest(int objectId)
	{
		return objectId == CHEST || objectId == CHEST_OPEN;
	}

	/** True when a hit on the player is the size the chest's trap deals. */
	public static boolean isTrapHit(int damage)
	{
		return damage >= TRAP_MIN && damage <= TRAP_MAX;
	}
}
