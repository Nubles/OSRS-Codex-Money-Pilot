package com.codex.moneypilot.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Builds accounts for the tests. */
final class Accounts
{
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private boolean members = true;
	private boolean iron;
	private int combat = 3;
	private int total = 32;

	static Accounts fresh()
	{
		return new Accounts();
	}

	/** Every skill at a level. */
	static Accounts all(int level)
	{
		Accounts a = new Accounts();
		for (Skill s : Skill.values())
		{
			a.levels.put(s, level);
		}
		a.combat = Math.min(126, level + level / 4);
		a.total = level * Skill.values().length;
		return a;
	}

	Accounts level(Skill s, int l)
	{
		levels.put(s, l);
		return this;
	}

	Accounts done(Quest... qs)
	{
		for (Quest q : qs)
		{
			quests.put(q, QuestState.FINISHED);
		}
		return this;
	}

	Accounts started(Quest q)
	{
		quests.put(q, QuestState.IN_PROGRESS);
		return this;
	}

	Accounts varbit(int id, int v)
	{
		varbits.put(id, v);
		return this;
	}

	Accounts f2p()
	{
		members = false;
		return this;
	}

	Accounts iron()
	{
		iron = true;
		return this;
	}

	Accounts total(int t)
	{
		total = t;
		return this;
	}

	Account build()
	{
		return new Account(levels, quests, varbits, combat, total, members, iron, true);
	}
}
