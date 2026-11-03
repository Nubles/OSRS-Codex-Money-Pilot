package com.codex.moneypilot.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

public class MethodsTest
{
	@Test
	public void everyMethodIsWhole()
	{
		Set<String> names = new HashSet<>();
		for (Method m : Methods.all())
		{
			assertTrue(m.getId(), m.getId().matches("[a-z0-9-]+"));
			assertTrue(m.getName(), names.add(m.getName()));
			assertTrue(m.getUrl(), m.getUrl().startsWith("https://oldschool.runescape.wiki/w/Money_making_guide/"));
			assertFalse(m.getUrl(), m.getUrl().contains(" "));
			assertTrue(m.getName(), m.getWikiPerHour() > 0);
			assertNotNull(Methods.byId(m.getId()));
			for (Need n : m.getNeeds())
			{
				assertTrue(m + ": " + n, n.getKind() != Need.Kind.SKILL || (n.getLevel() >= 1 && n.getLevel() <= 99));
			}
		}
		assertTrue(Methods.all().size() >= 50);
	}

	@Test
	public void sortedByWikiRate()
	{
		long last = Long.MAX_VALUE;
		for (Method m : Methods.all())
		{
			assertTrue(m.getName(), m.getWikiPerHour() <= last);
			last = m.getWikiPerHour();
		}
	}

	@Test
	public void freeMethodsNeedNoMembersQuest()
	{
		for (Method m : Methods.all())
		{
			if (!m.isMembers())
			{
				for (Need n : m.getNeeds())
				{
					if (n.getQuest() != null)
					{
						assertTrue(m + " needs " + n, n.getQuest() == Quest.DRAGON_SLAYER_I);
					}
				}
			}
		}
	}

	/**
	 * The gates as the wiki gives them on 3 November 2026; a change here must be checked against the
	 * method's page first (see facts in the pull request).
	 */
	@Test
	public void gatesMatchTheWiki()
	{
		assertNeed("blood-runes", Need.skill(Skill.RUNECRAFT, 77));
		assertNeed("blood-runes", Need.quest(Quest.SINS_OF_THE_FATHER));
		assertNeed("blood-runes-abyss", Need.quest(Quest.ENTER_THE_ABYSS));
		assertNeed("death-runes-abyss", Need.skill(Skill.RUNECRAFT, 65));
		assertNeed("death-runes-abyss", Need.quest(Quest.MOURNINGS_END_PART_II));
		assertNeed("wrath-runes", Need.skill(Skill.RUNECRAFT, 95));
		assertNeed("astral-runes", Need.quest(Quest.LUNAR_DIPLOMACY));
		assertNeed("runite-bars-bf", Need.skill(Skill.SMITHING, 85));
		assertNeed("adamantite-bars-bf", Need.skill(Skill.SMITHING, 70));
		assertNeed("vyres", Need.skill(Skill.THIEVING, 82));
		assertNeed("elves", Need.skill(Skill.THIEVING, 85));
		assertNeed("elves", Need.quest(Quest.SONG_OF_THE_ELVES));
		assertNeed("rogues-castle", Need.skill(Skill.THIEVING, 84));
		assertNeed("rogues-castle", Need.skill(Skill.PRAYER, 43));
		assertNeed("tan-leather", Need.skill(Skill.MAGIC, 78));
		assertNeed("hydra", Need.skill(Skill.SLAYER, 95));
		assertNeed("cerberus", Need.skill(Skill.SLAYER, 91));
		assertNeed("kraken", Need.skill(Skill.SLAYER, 87));
		assertNeed("brutal-black-dragons", Need.skill(Skill.SLAYER, 77));
		assertNeed("gargoyles", Need.skill(Skill.SLAYER, 75));
		assertNeed("kreearra", Need.skill(Skill.RANGED, 70));
		assertNeed("zilyana", Need.skill(Skill.AGILITY, 70));
		assertNeed("graardor", Need.skill(Skill.STRENGTH, 70));
		assertNeed("kril", Need.skill(Skill.HITPOINTS, 70));
		assertNeed("vorkath", Need.quest(Quest.DRAGON_SLAYER_II));
		assertNeed("demonic-gorillas", Need.quest(Quest.MONKEY_MADNESS_II));
		assertNeed("dagannoth-kings", Need.quest(Quest.THE_FREMENNIK_TRIALS));
		assertNeed("tombs-expert", Need.quest(Quest.BENEATH_CURSED_SANDS));
		assertNeed("mastering-mixology", Need.skill(Skill.HERBLORE, 60));
		assertNeed("sanfew-serum", Need.skill(Skill.HERBLORE, 65));
		assertNeed("ranarr-potions", Need.skill(Skill.HERBLORE, 30));
		assertNeed("snapdragon-potions", Need.skill(Skill.HERBLORE, 63));
		assertNeed("runite-ore-f2p", Need.skill(Skill.MINING, 85));
		assertNeed("wine-of-zamorak-f2p", Need.skill(Skill.MAGIC, 33));
		// Suggestions only, never gates: the guides mark these "recommended".
		assertEquals(0, Methods.byId("chambers-of-xeric").getNeeds().size());
		assertEquals(0, Methods.byId("corporeal-beast").getNeeds().size());
	}

	private static void assertNeed(String id, Need need)
	{
		Method m = Methods.byId(id);
		assertNotNull(id, m);
		assertTrue(id + " should need " + need + " but needs " + m.getNeeds(), m.getNeeds().contains(need));
	}

	@Test
	public void matchesChronicleNames()
	{
		assertTrue(Methods.byId("vorkath").matches("Vorkath"));
		assertTrue(Methods.byId("vorkath").matches(" vorkath "));
		assertTrue(Methods.byId("revenants-msb").matches("Revenant knight"));
		assertFalse(Methods.byId("revenants-msb").matches("Knight"));
		assertTrue(Methods.byId("dagannoth-kings").matches("Dagannoth Prime"));
		assertFalse(Methods.byId("blood-runes").matches("Runecraft"));
		assertFalse(Methods.byId("vorkath").matches(null));
	}
}
