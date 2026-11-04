package com.codex.moneypilot.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Skill;

/**
 * A way to make money, as the Old School wiki's money making guide describes it: what it needs
 * (hard gates: a level the game checks, a quest, a diary), what the guide suggests on top, and the
 * profit an hour the guide gives. Your own figure replaces the guide's once you have one.
 */
public final class Method
{
	public enum Category
	{
		COMBAT("Combat"),
		SKILLING("Skilling"),
		PROCESSING("Processing"),
		COLLECTING("Collecting");

		private final String title;

		Category(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}
	}

	private final String id;
	private final String name;
	private final String page;
	private final Category category;
	private final long wikiPerHour;
	private final String basis;
	private boolean members = true;
	/** Buys what it works on, so an ironman cannot do it. */
	private boolean trade;
	private boolean wilderness;
	private Skill skill;
	private final List<Need> needs = new ArrayList<>();
	private final List<Need> suggests = new ArrayList<>();
	private final List<String> matches = new ArrayList<>();
	/** For a timegated run (bird houses, a herb run): what one run pays, how long it takes and how often it comes round. */
	private long perRun;
	private long runMs;
	private long everyMs;
	/** Runs that share what they use, such as the herb patches: only the best paying of a group is done. */
	private String group;

	Method(String id, String name, String page, Category category, long wikiPerHour, String basis)
	{
		this.id = id;
		this.name = name;
		this.page = page;
		this.category = category;
		this.wikiPerHour = wikiPerHour;
		this.basis = basis;
	}

	Method f2p()
	{
		members = false;
		return this;
	}

	Method trade()
	{
		trade = true;
		return this;
	}

	Method wilderness()
	{
		wilderness = true;
		return this;
	}

	Method need(Need... add)
	{
		Collections.addAll(needs, add);
		return this;
	}

	Method suggest(Need... add)
	{
		Collections.addAll(suggests, add);
		return this;
	}

	/**
	 * Makes this a timegated run, done alongside other play: what one run pays by the guide, how long
	 * it takes, and the guide's minimum recurrence time.
	 */
	Method recurring(long pays, long seconds, long minutes, String sharedBy)
	{
		perRun = pays;
		runMs = seconds * 1000L;
		everyMs = minutes * 60_000L;
		group = sharedBy;
		return this;
	}

	/** The skill the method trains, matched against Session Chronicle's skilling sessions. */
	Method skill(Skill trained)
	{
		skill = trained;
		return this;
	}

	/**
	 * Names that Session Chronicle files a session under when it was spent on this method: the
	 * monster or chest that drops the loot. A name ending in "*" matches every name it begins.
	 */
	Method match(String... names)
	{
		for (String n : names)
		{
			matches.add(n.toLowerCase(Locale.ROOT));
		}
		return this;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	/** The guide's address on the wiki. */
	public String getUrl()
	{
		return "https://oldschool.runescape.wiki/w/Money_making_guide/" + page.replace(' ', '_');
	}

	public Category getCategory()
	{
		return category;
	}

	/** Profit an hour by the wiki's guide, as it stood when this list was checked. */
	public long getWikiPerHour()
	{
		return wikiPerHour;
	}

	/** What the guide's figure assumes, such as "30 kills an hour"; may be null. */
	public String getBasis()
	{
		return basis;
	}

	public boolean isMembers()
	{
		return members;
	}

	public boolean isTrade()
	{
		return trade;
	}

	public boolean isWilderness()
	{
		return wilderness;
	}

	public Skill getSkill()
	{
		return skill;
	}

	public List<Need> getNeeds()
	{
		return Collections.unmodifiableList(needs);
	}

	public List<Need> getSuggests()
	{
		return Collections.unmodifiableList(suggests);
	}

	/** True for a timegated run done alongside other play, which is planned rather than ranked. */
	public boolean isRecurring()
	{
		return everyMs > 0;
	}

	/** What one run pays by the guide; 0 for a method that is not timegated. */
	public long getPerRun()
	{
		return perRun;
	}

	/** How long one run takes, by the guide. */
	public long getRunMs()
	{
		return runMs;
	}

	/** How often the run comes round, by the guide (its minimum recurrence time). */
	public long getEveryMs()
	{
		return everyMs;
	}

	/** Runs of the same group use the same patches, so only one of them is done; null when it stands alone. */
	public String getGroup()
	{
		return group;
	}

	/** True when Session Chronicle's activity name is this method's. */
	public boolean matches(String activity)
	{
		if (activity == null)
		{
			return false;
		}
		String a = activity.trim().toLowerCase(Locale.ROOT);
		for (String m : matches)
		{
			if (m.endsWith("*") ? a.startsWith(m.substring(0, m.length() - 1)) : a.equals(m))
			{
				return true;
			}
		}
		return false;
	}

	public boolean hasMatches()
	{
		return !matches.isEmpty();
	}

	@Override
	public String toString()
	{
		return name;
	}
}
