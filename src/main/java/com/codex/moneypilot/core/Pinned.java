package com.codex.moneypilot.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * What the other Codex plugins say the player is working toward, read from what they save to the
 * RuneScape profile and never written: Codex Gear Roadmap's savings goal ({@code codexgearroadmap.goal},
 * an upgrade's name and one item id per piece) and Codex Navigator's goal board
 * ({@code codexnavigator.goalBoard}, the pinned quests, diaries and levels with what each still needs).
 * Both are another plugin's format, so anything that cannot be read is left out rather than guessed at.
 */
public final class Pinned
{
	public static final String GEAR_GROUP = "codexgearroadmap";
	public static final String GEAR_GOAL = "goal";
	public static final String NAVIGATOR_GROUP = "codexnavigator";
	public static final String NAVIGATOR_BOARD = "goalBoard";

	private Pinned()
	{
	}

	/** Gear Roadmap's savings goal: the upgrade's name and the item ids of its pieces. */
	public static final class Gear
	{
		private final String name;
		private final List<Integer> ids;

		Gear(String name, List<Integer> ids)
		{
			this.name = name;
			this.ids = Collections.unmodifiableList(ids);
		}

		public String getName()
		{
			return name;
		}

		public List<Integer> getIds()
		{
			return ids;
		}
	}

	/** A goal pinned in Navigator, with what it still needs. */
	public static final class Goal
	{
		private final String key;
		private final String title;
		private final double hoursLeft;
		private final Map<Skill, Integer> skills;
		private final Set<String> quests;

		Goal(String key, String title, double hoursLeft, Map<Skill, Integer> skills, Set<String> quests)
		{
			this.key = key;
			this.title = title;
			this.hoursLeft = hoursLeft;
			this.skills = skills;
			this.quests = quests;
		}

		public String getKey()
		{
			return key;
		}

		public String getTitle()
		{
			return title;
		}

		/** Navigator's estimate of the hours of training and questing left; 0 when it gave none. */
		public double getHoursLeft()
		{
			return hoursLeft;
		}

		/**
		 * True when finishing this goal meets every need of a locked method that it lacks: each quest
		 * on the goal's way and each level it trains to. A method that also needs something the goal
		 * does not cover (a diary, a level it does not train) is not counted.
		 */
		public boolean opens(Fit fit)
		{
			if (fit.getStatus() != Fit.Status.LOCKED || fit.getUnmet().isEmpty())
			{
				return false;
			}
			for (Need n : fit.getUnmet())
			{
				switch (n.getKind())
				{
					case QUEST:
					case QUEST_STARTED:
						if (!quests.contains(n.getQuest().getName().toLowerCase(Locale.ROOT)))
						{
							return false;
						}
						break;
					case SKILL:
						Integer to = skills.get(n.getSkill());
						if (to == null || to < n.getLevel())
						{
							return false;
						}
						break;
					default:
						return false;
				}
			}
			return true;
		}
	}

	/** Gear Roadmap's saved goal, or null when there is none or it cannot be read. */
	public static Gear gear(Gson gson, String json)
	{
		JsonObject o = object(gson, json);
		if (o == null)
		{
			return null;
		}
		String name = string(o.get("name"));
		JsonElement list = o.get("ids");
		if (name == null || name.trim().isEmpty() || list == null || !list.isJsonArray())
		{
			return null;
		}
		List<Integer> ids = new ArrayList<>();
		for (JsonElement e : list.getAsJsonArray())
		{
			Long id = whole(e);
			if (id != null && id > 0 && id <= Integer.MAX_VALUE && !ids.contains(id.intValue()))
			{
				ids.add(id.intValue());
			}
		}
		return ids.isEmpty() ? null : new Gear(name.trim(), ids);
	}

	/** Navigator's unfinished pinned goals, focus first; empty when there are none or the board cannot be read. */
	public static List<Goal> navigator(Gson gson, String json)
	{
		JsonObject o = object(gson, json);
		if (o == null || o.get("goals") == null || !o.get("goals").isJsonArray())
		{
			return Collections.emptyList();
		}
		List<Goal> out = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("goals"))
		{
			if (e == null || !e.isJsonObject())
			{
				continue;
			}
			JsonObject g = e.getAsJsonObject();
			String key = string(g.get("key"));
			String title = string(g.get("title"));
			JsonElement complete = g.get("complete");
			if (key == null || title == null || title.trim().isEmpty()
				|| complete != null && complete.isJsonPrimitive() && complete.getAsJsonPrimitive().isBoolean() && complete.getAsBoolean())
			{
				continue;
			}
			Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
			JsonElement s = g.get("skills");
			if (s != null && s.isJsonObject())
			{
				for (Map.Entry<String, JsonElement> k : s.getAsJsonObject().entrySet())
				{
					Skill skill = skill(k.getKey());
					Long level = whole(k.getValue());
					if (skill != null && level != null && level > 1 && level <= 99)
					{
						skills.put(skill, level.intValue());
					}
				}
			}
			Set<String> quests = new HashSet<>();
			JsonElement q = g.get("quests");
			if (q != null && q.isJsonArray())
			{
				for (JsonElement n : q.getAsJsonArray())
				{
					String name = string(n);
					if (name != null)
					{
						quests.add(name.trim().toLowerCase(Locale.ROOT));
					}
				}
			}
			out.add(new Goal(key, title.trim(), Math.max(0, number(g.get("hoursLeft"))), skills, quests));
		}
		String focus = string(o.get("focus"));
		if (focus != null)
		{
			// The focus goal leads, as it does in Navigator.
			out.sort((a, b) -> Boolean.compare(!focus.equals(a.key), !focus.equals(b.key)));
		}
		return out;
	}

	private static Skill skill(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equalsIgnoreCase(name))
			{
				return s;
			}
		}
		return null;
	}

	private static JsonObject object(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement e = gson.fromJson(json, JsonElement.class);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static String string(JsonElement e)
	{
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	private static Long whole(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return null;
		}
		double d = e.getAsDouble();
		return d == Math.rint(d) && !Double.isInfinite(d) ? (long) d : null;
	}

	private static double number(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return 0;
		}
		double d = e.getAsDouble();
		return Double.isNaN(d) || Double.isInfinite(d) ? 0 : d;
	}
}
