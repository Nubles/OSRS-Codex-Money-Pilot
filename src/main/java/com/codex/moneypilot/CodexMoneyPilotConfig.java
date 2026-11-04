package com.codex.moneypilot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CodexMoneyPilotConfig.GROUP)
public interface CodexMoneyPilotConfig extends Config
{
	String GROUP = "codexmoneypilot";

	@ConfigItem(
		keyName = "wilderness",
		name = "Show Wilderness methods",
		description = "List money makers that take you into the Wilderness, where you can lose what you carry",
		position = 0
	)
	default boolean wilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "plaque",
		name = "Run plaque",
		description = "While a run is clocked, show its time, profit so far and rate on the game screen",
		position = 1
	)
	default boolean plaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatLine",
		name = "Chat line when a run ends",
		description = "Say in your chat box what a finished run made and its rate an hour",
		position = 2
	)
	default boolean chatLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "runCoach",
		name = "Run coach",
		description = "While a run is clocked, weigh its rate against your best run of the method and the wiki's figure",
		position = 3
	)
	default boolean runCoach()
	{
		return true;
	}

	@ConfigSection(
		name = "Goal",
		description = "What the Goal tab plans for",
		position = 10
	)
	String GOAL = "goalSection";

	@ConfigItem(
		keyName = "goalCoins",
		name = "Coins goal (thousands)",
		description = "Coins to save for, in thousands: 4200 is 4.2M. Planned beside Codex Gear Roadmap's savings goal. 0 for none",
		section = GOAL,
		position = 0
	)
	default int goalCoins()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "goalName",
		name = "Coins goal name",
		description = "What the coins goal is for, shown on the Goal tab",
		section = GOAL,
		position = 1
	)
	default String goalName()
	{
		return "";
	}

	@Range(min = 1, max = 16)
	@ConfigItem(
		keyName = "hoursPerDay",
		name = "Hours played a day",
		description = "How long you play on a day, for the finish date and how many timegated runs fit in",
		section = GOAL,
		position = 2
	)
	default int hoursPerDay()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "readGearRoadmap",
		name = "Plan Gear Roadmap's goal",
		description = "Plan for the savings goal picked in Codex Gear Roadmap, when it is installed",
		section = GOAL,
		position = 3
	)
	default boolean readGearRoadmap()
	{
		return true;
	}

	@ConfigSection(
		name = "Pickpocketing",
		description = "The pickpocket log: your own success rate and rate an hour at each target Money Pilot ranks",
		position = 20
	)
	String PICKPOCKETING = "pickpocketSection";

	@ConfigItem(
		keyName = "pickpocketLog",
		name = "Log pickpocketing",
		description = "Count your picks, stuns, coin pouches, loot and food at Ardougne knights, paladins, Master Farmers, vyres and elves, "
			+ "and rank those methods by your own rate once you have a quarter of an hour of picking",
		section = PICKPOCKETING,
		position = 0
	)
	default boolean pickpocketLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pickpocketPlaque",
		name = "Pickpocket plaque",
		description = "While pickpocketing a logged target, show the rate, your success rate, stuns and coin pouches on the game screen",
		section = PICKPOCKETING,
		position = 1
	)
	default boolean pickpocketPlaque()
	{
		return true;
	}

	@Range(max = 20)
	@ConfigItem(
		keyName = "pouchWarning",
		name = "Pouch warning",
		description = "Warn this many coin pouches before you can hold no more, on the plaque and once in chat. 0 for no warning",
		section = PICKPOCKETING,
		position = 2
	)
	default int pouchWarning()
	{
		return 3;
	}

	@ConfigSection(
		name = "Rogues' Castle chests",
		description = "The chest log: your own loot, Thieving experience and rate an hour at the Rogues' Castle chests",
		position = 30
	)
	String CHESTS = "chestSection";

	@ConfigItem(
		keyName = "chestLog",
		name = "Log the chests",
		description = "Count the Rogues' Castle chests you open, their loot, your Thieving experience, supplies and what a death took, "
			+ "and rank the method by your own rate once you have a quarter of an hour of looting",
		section = CHESTS,
		position = 0
	)
	default boolean chestLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestPlaque",
		name = "Chest plaque",
		description = "At the Rogues' Castle chests, show the rate, Thieving experience an hour and which chests are ready on the game screen",
		section = CHESTS,
		position = 1
	)
	default boolean chestPlaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deathRisk",
		name = "If you die now",
		description = "On the chest plaque, show what a death right now would cost: what you carry less the items you would keep, "
			+ "at today's prices. A figure only; Codex Guardian keeps watch for player killers",
		section = CHESTS,
		position = 2
	)
	default boolean deathRisk()
	{
		return true;
	}

	@ConfigSection(
		name = "Port Roberts stalls",
		description = "The stall log: your own steals, Thieving experience and rate an hour at the Port Roberts market stalls",
		position = 40
	)
	String STALLS = "stallSection";

	@ConfigItem(
		keyName = "stallLog",
		name = "Log the stalls",
		description = "Count your steals from the Port Roberts market stalls, their loot, your Thieving experience and the times a guard "
			+ "caught you, and rank each rotation by your own rate once you have a quarter of an hour of it",
		section = STALLS,
		position = 0
	)
	default boolean stallLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "stallPlaque",
		name = "Stall plaque",
		description = "While stealing from the Port Roberts stalls, show the rate, Thieving experience an hour and times caught on the "
			+ "game screen. Numbers only: no stall or guard is marked",
		section = STALLS,
		position = 1
	)
	default boolean stallPlaque()
	{
		return true;
	}

	@ConfigSection(
		name = "Field notes",
		description = "A local record for checking the plugin's guesses",
		position = 99,
		closedByDefault = true
	)
	String FIELD_NOTES = "fieldNotesSection";

	@ConfigItem(
		keyName = "fieldNotes",
		name = "Keep field notes",
		description = "Write what Codex Money Pilot reads and decides to its folder in .runelite/plugin-data on your computer, for checking its guesses; nothing is sent anywhere",
		section = FIELD_NOTES,
		position = 0
	)
	default boolean fieldNotes()
	{
		return false;
	}
}
