package com.codex.moneypilot.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * What Money Pilot knows of the account, read on the client thread and handed over whole: real
 * (unboosted) levels, quest progress, the diaries it asks about, membership and ironman status.
 */
public final class Account
{
	public static final Account UNKNOWN = new Account(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
		0, 0, false, false, false);

	/** Whether a need is met: some, like part of Troll Stronghold, cannot be read from the game. */
	public enum Check
	{
		MET,
		UNMET,
		UNSURE
	}

	private final Map<Skill, Integer> levels;
	private final Map<Quest, QuestState> quests;
	private final Map<Integer, Integer> varbits;
	private final int combat;
	private final int total;
	private final boolean members;
	private final boolean ironman;
	private final boolean known;

	public Account(Map<Skill, Integer> levels, Map<Quest, QuestState> quests, Map<Integer, Integer> varbits, int combat,
		int total, boolean members, boolean ironman, boolean known)
	{
		this.levels = levels.isEmpty() ? Collections.emptyMap() : new EnumMap<>(levels);
		this.quests = quests.isEmpty() ? Collections.emptyMap() : new EnumMap<>(quests);
		this.varbits = new HashMap<>(varbits);
		this.combat = combat;
		this.total = total;
		this.members = members;
		this.ironman = ironman;
		this.known = known;
	}

	public int level(Skill skill)
	{
		Integer l = levels.get(skill);
		return l == null ? 1 : l;
	}

	public QuestState quest(Quest quest)
	{
		QuestState s = quests.get(quest);
		return s == null ? QuestState.NOT_STARTED : s;
	}

	public int getCombat()
	{
		return combat;
	}

	public int getTotal()
	{
		return total;
	}

	public boolean isMembers()
	{
		return members;
	}

	public boolean isIronman()
	{
		return ironman;
	}

	/** False before the account has been read (logged out, or the first ticks after login). */
	public boolean isKnown()
	{
		return known;
	}

	/** How far the account has to go for a need: levels short for a level, 1 for anything else unmet. */
	public int gap(Need need)
	{
		switch (need.getKind())
		{
			case SKILL:
				return Math.max(0, need.getLevel() - level(need.getSkill()));
			case COMBAT:
				return Math.max(0, need.getLevel() - combat);
			case TOTAL:
				return Math.max(0, need.getLevel() - total);
			default:
				return check(need) == Check.UNMET ? 1 : 0;
		}
	}

	public Check check(Need need)
	{
		switch (need.getKind())
		{
			case SKILL:
			case COMBAT:
			case TOTAL:
				return gap(need) == 0 ? Check.MET : Check.UNMET;
			case QUEST:
				return quest(need.getQuest()) == QuestState.FINISHED ? Check.MET : Check.UNMET;
			case QUEST_STARTED:
			{
				QuestState s = quest(need.getQuest());
				if (s == QuestState.FINISHED)
				{
					return Check.MET;
				}
				if (s == QuestState.NOT_STARTED)
				{
					return Check.UNMET;
				}
				// Regicide and The Giant Dwarf only need starting; Troll Stronghold needs Dad beaten,
				// which the quest log does not say.
				return need.getLabel() != null && need.getLabel().contains("Dad") ? Check.UNSURE : Check.MET;
			}
			case DIARY:
			{
				Integer v = varbits.get(need.getVarbit());
				return v != null && v > 0 ? Check.MET : Check.UNMET;
			}
			default:
				return Check.UNSURE;
		}
	}
}
