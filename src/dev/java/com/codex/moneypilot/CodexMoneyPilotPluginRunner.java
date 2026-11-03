package com.codex.moneypilot;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexMoneyPilotPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexMoneyPilotPlugin.class);
		RuneLite.main(args);
	}
}
