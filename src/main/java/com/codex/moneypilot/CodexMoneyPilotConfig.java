package com.codex.moneypilot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
