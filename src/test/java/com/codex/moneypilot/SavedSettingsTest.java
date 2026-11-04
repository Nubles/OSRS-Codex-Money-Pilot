package com.codex.moneypilot;

import static org.junit.Assert.assertTrue;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/** Settings keep the keys their values are saved under. */
public class SavedSettingsTest
{
	/** Every key the first release saves settings under. */
	private static final String[] FIRST = {"wilderness", "plaque", "chatLine", "fieldNotes"};
	/** Keys added since. */
	private static final String[] ADDED = {"runCoach", "goalCoins", "goalName", "hoursPerDay", "readGearRoadmap", "pickpocketLog",
		"pickpocketPlaque", "pouchWarning", "chestLog", "chestPlaque", "deathRisk", "stallLog", "stallPlaque"};

	@Test
	public void savedSettingsKeepTheirKeys()
	{
		// A setting's key is where its value is saved: renaming one quietly resets it for every player who changed it.
		Set<String> keys = new HashSet<>();
		for (Method m : CodexMoneyPilotConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
			}
		}
		for (String key : FIRST)
		{
			assertTrue(key, keys.contains(key));
		}
		for (String key : ADDED)
		{
			assertTrue(key, keys.contains(key));
		}
	}
}
