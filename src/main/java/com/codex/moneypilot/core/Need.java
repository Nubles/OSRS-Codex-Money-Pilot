package com.codex.moneypilot.core;

import java.util.Objects;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * One thing a money maker asks of the account: a skill level, a quest, an achievement diary, a
 * combat or total level, or a plain note that cannot be read from the game. Every value here was
 * checked against the method's page on the Old School wiki.
 */
public final class Need
{
	public enum Kind
	{
		SKILL,
		COMBAT,
		TOTAL,
		QUEST,
		/** A quest that only has to be started (or, for Troll Stronghold, partly done). */
		QUEST_STARTED,
		DIARY,
		/** Told to the player, never checked: "High melee stats", "a Hydra slayer task". */
		NOTE
	}

	private final Kind kind;
	private final Skill skill;
	private final int level;
	private final Quest quest;
	private final int varbit;
	private final String label;

	private Need(Kind kind, Skill skill, int level, Quest quest, int varbit, String label)
	{
		this.kind = kind;
		this.skill = skill;
		this.level = level;
		this.quest = quest;
		this.varbit = varbit;
		this.label = label;
	}

	public static Need skill(Skill skill, int level)
	{
		return new Need(Kind.SKILL, skill, level, null, -1, null);
	}

	public static Need combat(int level)
	{
		return new Need(Kind.COMBAT, null, level, null, -1, null);
	}

	public static Need total(int level)
	{
		return new Need(Kind.TOTAL, null, level, null, -1, null);
	}

	public static Need quest(Quest quest)
	{
		return new Need(Kind.QUEST, null, 0, quest, -1, null);
	}

	/** A quest that counts once begun; the label says how far, as the wiki puts it. */
	public static Need started(Quest quest, String label)
	{
		return new Need(Kind.QUEST_STARTED, null, 0, quest, -1, label);
	}

	/** An achievement diary tier, read from its completion varbit. */
	public static Need diary(String label, int varbit)
	{
		return new Need(Kind.DIARY, null, 0, null, varbit, label);
	}

	public static Need note(String label)
	{
		return new Need(Kind.NOTE, null, 0, null, -1, label);
	}

	public Kind getKind()
	{
		return kind;
	}

	public Skill getSkill()
	{
		return skill;
	}

	public int getLevel()
	{
		return level;
	}

	public Quest getQuest()
	{
		return quest;
	}

	public int getVarbit()
	{
		return varbit;
	}

	/** The wording given with the need, or null. */
	public String getLabel()
	{
		return label;
	}

	/** How the need reads in the panel: "Runecraft 77", "Sins of the Father". */
	public String describe()
	{
		switch (kind)
		{
			case SKILL:
				return skill.getName() + " " + level;
			case COMBAT:
				return "Combat level " + level;
			case TOTAL:
				return "Total level " + level;
			case QUEST:
				return quest.getName();
			case QUEST_STARTED:
				return label != null ? label : "Started " + quest.getName();
			default:
				return label;
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Need))
		{
			return false;
		}
		Need n = (Need) o;
		return kind == n.kind && skill == n.skill && level == n.level && quest == n.quest && varbit == n.varbit
			&& Objects.equals(label, n.label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(kind, skill, level, quest, varbit, label);
	}

	@Override
	public String toString()
	{
		return describe();
	}
}
