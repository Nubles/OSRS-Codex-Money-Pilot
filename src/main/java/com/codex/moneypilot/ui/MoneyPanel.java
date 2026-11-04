package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Coach;
import com.codex.moneypilot.core.Fit;
import com.codex.moneypilot.core.Format;
import com.codex.moneypilot.core.GoalPlan;
import com.codex.moneypilot.core.Target;
import com.codex.moneypilot.core.Method;
import com.codex.moneypilot.core.Methods;
import com.codex.moneypilot.core.Need;
import com.codex.moneypilot.core.Pickpockets;
import com.codex.moneypilot.core.Planner;
import com.codex.moneypilot.core.Rate;
import com.codex.moneypilot.core.Rates;
import com.codex.moneypilot.core.Stalls;
import com.codex.moneypilot.core.Clocked;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Money Pilot side panel: a crest with the best you can make an hour, then three views. Best now
 * ranks the money makers you can do today by your own rate where you have one and the wiki's where
 * you do not. Within reach lists what is nearest to opening up and what it still needs. Your rates
 * shows the runs you have clocked and what Session Chronicle saw you make.
 */
public class MoneyPanel extends PluginPanel
{
	static final int WIDTH = 200;
	static final int TEXT = WIDTH - 34;
	/** Methods shown in Best now before the rest are folded away. */
	static final int SHOWN = 12;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	/** Colours of a plan's parts: the main method, then each run alongside. */
	private static final Color[] PARTS = {Theme.GOLD, Theme.TEAL, Theme.GREEN, Theme.VIOLET, Theme.EMBER};

	private final Actions actions;
	private final Crest crest = new Crest();
	private final JPanel tabs = new JPanel(new GridLayout(0, 2, 4, 4));
	private final JScrollPane scroll = new JScrollPane();
	private final Set<String> open = new HashSet<>();
	private View view = View.empty();
	private Tab tab = Tab.BEST;
	private boolean all;
	private ZoneId zone = ZoneId.systemDefault();

	public MoneyPanel(Actions actions)
	{
		super(false);
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(crest, BorderLayout.NORTH);
		tabs.setOpaque(false);
		tabs.setBorder(new EmptyBorder(8, 8, 0, 8));
		top.add(tabs, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		add(scroll, BorderLayout.CENTER);
		rebuild(false);
	}

	/** For the screenshot harness, which runs in another time zone. */
	public void setZone(ZoneId zone)
	{
		this.zone = zone;
	}

	/** Called on the Swing thread whenever anything shown changes. */
	public void update(View view)
	{
		this.view = view;
		rebuild(false);
	}

	/** Called on the Swing thread to switch view. */
	public void show(Tab tab)
	{
		boolean changed = this.tab != tab;
		this.tab = tab;
		rebuild(changed);
	}

	/** Opens a method's card, for the screenshot harness. */
	public void expand(String id)
	{
		open.add(id);
		rebuild(false);
	}

	public Tab getTab()
	{
		return tab;
	}

	private void rebuild(boolean toTop)
	{
		updateCrest();
		tabs.removeAll();
		for (Tab t : Tab.values())
		{
			tabs.add(new CodexButton(t.getTitle(), t == tab, 20, () -> actions.tab(t)));
		}
		int y = toTop ? 0 : scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(build());
		scroll.getVerticalScrollBar().setValue(y);
		revalidate();
		repaint();
	}

	private void updateCrest()
	{
		if (view.live != null)
		{
			View.Live live = view.live;
			String rate = live.perHour() == 0 ? "rate after a minute" : Format.signed(live.perHour()) + "/hr";
			crest.update("CLOCKING " + live.method.getName().toUpperCase(), Format.signed(live.profit), "so far",
				Format.clock(live.playedMs) + " played, " + rate, live.loggedIn ? Theme.TEAL : Theme.TEXT_MUTED,
				live.profit < 0 ? Theme.BLOOD : Theme.GOLD);
			return;
		}
		if (!view.loggedIn || view.plan == null)
		{
			crest.update("BEST YOU CAN MAKE", null, null, "Log in to plan your money", Theme.TEXT_MUTED, Theme.GOLD);
			return;
		}
		List<Planner.Pick> best = view.plan.getOpen();
		if (best.isEmpty())
		{
			crest.update("BEST YOU CAN MAKE", null, null, "Nothing fits: see Within reach", Theme.TEXT_MUTED, Theme.GOLD);
			return;
		}
		Planner.Pick top = best.get(0);
		crest.update("BEST YOU CAN MAKE", Format.gp(top.getRate().getPerHour()), "an hour",
			top.getMethod().getName(), top.getRate().isOwn() ? Theme.TEAL : Theme.TEXT, Theme.GOLD);
	}

	private StackPanel build()
	{
		if (!view.loggedIn || view.plan == null)
		{
			return loggedOut();
		}
		switch (tab)
		{
			case REACH:
				return reach();
			case YOURS:
				return yours();
			case GOAL:
				return goal();
			default:
				return best();
		}
	}

	// ---- Views ----

	private StackPanel loggedOut()
	{
		StackPanel s = new StackPanel(8, 8);
		Card card = new Card(new BorderLayout(0, 6));
		card.add(Ui.label("Your money, planned", Theme.title(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped("Log in and Money Pilot ranks every money maker you can do by what it pays an hour: the wiki's "
			+ "figure at first, then your own as you clock runs.", Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.CENTER);
		s.add(card);
		s.add(new SectionHeader("How it works"));
		s.add(step("1", "It reads your levels, quests and diaries", "and checks each method's needs, as the wiki gives them."));
		s.add(step("2", "Clock a run of any method", "It values what your bank, pack and gear gained, at today's prices."));
		s.add(step("3", "Your rate replaces the wiki's", "after a quarter of an hour, so the list follows how you play."));
		s.add(note("Codex Session Chronicle's sessions count too, when it is installed."));
		return s;
	}

	private StackPanel best()
	{
		StackPanel s = new StackPanel(6, 8);
		if (view.live != null)
		{
			s.add(liveCard(view.live));
		}
		s.add(filters());
		List<Planner.Pick> picks = view.plan.getOpen();
		if (picks.isEmpty())
		{
			s.add(note(view.shown.isEmpty() ? "Every kind of method is hidden: turn one on above."
				: "Nothing you can do yet in what is shown. Within reach lists what is nearest."));
			return s;
		}
		String trailing = picks.size() + " open";
		s.add(new SectionHeader("Best for you", trailing));
		int shown = all ? picks.size() : Math.min(SHOWN, picks.size());
		for (int i = 0; i < shown; i++)
		{
			s.add(methodCard(picks.get(i), i + 1));
		}
		if (picks.size() > shown)
		{
			s.add(more("Show " + (picks.size() - shown) + " more", () ->
			{
				all = true;
				rebuild(false);
			}));
		}
		if (view.plan.getHidden() > 0)
		{
			s.add(note(hiddenLine()));
		}
		return s;
	}

	private String hiddenLine()
	{
		List<String> why = new ArrayList<>();
		if (!view.account.isMembers())
		{
			why.add("members methods");
		}
		if (view.account.isIronman())
		{
			why.add("methods that buy their supplies");
		}
		if (view.shown.size() < Method.Category.values().length)
		{
			why.add("kinds you hid");
		}
		return view.plan.getHidden() + " left out" + (why.isEmpty() ? "" : ": " + String.join(", ", why)) + ".";
	}

	private StackPanel reach()
	{
		StackPanel s = new StackPanel(6, 8);
		s.add(filters());
		List<Planner.Pick> ups = view.plan.getUpgrades();
		List<Planner.Pick> rest = new ArrayList<>(view.plan.getNear());
		rest.removeAll(ups);
		if (ups.isEmpty() && rest.isEmpty())
		{
			s.add(note("Everything shown is open to you. Well earned."));
			return s;
		}
		if (!ups.isEmpty())
		{
			s.add(new SectionHeader("Pays more than now", String.valueOf(ups.size())));
			for (Planner.Pick p : limit(ups, 8))
			{
				s.add(reachCard(p));
			}
		}
		if (!rest.isEmpty())
		{
			s.add(new SectionHeader("Also within reach", String.valueOf(rest.size())));
			for (Planner.Pick p : limit(rest, ups.isEmpty() ? 10 : 6))
			{
				s.add(reachCard(p));
			}
		}
		return s;
	}

	private StackPanel yours()
	{
		StackPanel s = new StackPanel(6, 8);
		if (view.live != null)
		{
			s.add(liveCard(view.live));
		}
		Map<Method, List<Clocked>> byMethod = new LinkedHashMap<>();
		for (Clocked r : view.runs)
		{
			Method m = Methods.byId(r.getMethod());
			if (m != null && r.isValid())
			{
				byMethod.computeIfAbsent(m, k -> new ArrayList<>()).add(r);
			}
		}
		if (byMethod.isEmpty())
		{
			Card card = new Card(new BorderLayout(0, 6));
			card.add(Ui.label("No runs clocked yet", Theme.body(), Theme.GOLD), BorderLayout.NORTH);
			card.add(Ui.wrapped(view.bankSeen
					? "Open any method in Best now and press Clock a run. Stop when you are done, after banking your loot."
					: "Open your bank once so Money Pilot knows what you start with, then clock a run from Best now.",
				Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.CENTER);
			s.add(card);
		}
		else
		{
			s.add(new SectionHeader("Clocked runs", String.valueOf(view.runs.size())));
			List<Map.Entry<Method, List<Clocked>>> entries = new ArrayList<>(byMethod.entrySet());
			entries.sort((a, b) -> Long.compare(b.getValue().get(0).getStartedAt(), a.getValue().get(0).getStartedAt()));
			for (Map.Entry<Method, List<Clocked>> e : entries)
			{
				s.add(runsCard(e.getKey(), e.getValue()));
			}
		}

		pickpockets(s);
		chests(s);
		stalls(s);
		thieving(s);

		List<Method> chronicled = new ArrayList<>();
		for (Method m : Methods.all())
		{
			if (view.rates.sessions(m) != null)
			{
				chronicled.add(m);
			}
		}
		chronicled.sort((a, b) -> Long.compare(view.rates.sessions(b).getPerHour(), view.rates.sessions(a).getPerHour()));
		s.add(new SectionHeader("From Session Chronicle", view.chronicle ? String.valueOf(chronicled.size()) : null));
		if (!view.chronicle)
		{
			s.add(note("Install Codex Session Chronicle and the money each session makes counts here too."));
		}
		else if (chronicled.isEmpty())
		{
			s.add(note("No sessions yet of a method on the list, of a quarter of an hour or more in the last "
				+ Rates.DAYS + " days."));
		}
		else
		{
			for (Method m : chronicled)
			{
				Rate r = view.rates.sessions(m);
				Card card = new Card(new BorderLayout(6, 2)).accent(Theme.VIOLET);
				card.add(Ui.wrapped(m.getName(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
				card.add(top(rateLabel(r.getPerHour())), BorderLayout.EAST);
				card.add(Ui.label(r.getCount() + (r.getCount() == 1 ? " session, " : " sessions, ") + Format.duration(r.getPlayedMs()),
					Theme.small(), Theme.TEXT_MUTED), BorderLayout.SOUTH);
				s.add(card);
			}
		}
		return s;
	}

	// ---- Pickpocket log ----

	private void pickpockets(StackPanel s)
	{
		if (!view.pickLog)
		{
			return;
		}
		s.add(new SectionHeader("Pickpocket log", view.picked.isEmpty() ? null : String.valueOf(view.picked.size())));
		if (view.picked.isEmpty())
		{
			s.add(note("Pickpocket Ardougne knights, paladins, Master Farmers, vyres or elves and your own success rate, stuns, "
				+ "coin pouches, food and rate an hour gather here. After a quarter of an hour of picking, your rate ranks the "
				+ "method in Best now."));
			return;
		}
		for (View.Picked p : view.picked)
		{
			s.add(pickedCard(p));
		}
		s.add(note("Your own counts from the game's messages, over the last " + Rates.DAYS + " days. Loot and food are valued "
			+ "at today's prices; coin pouches count when you open them."));
	}

	private Card pickedCard(View.Picked p)
	{
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.TEAL);
		JPanel head = row();
		head.add(Ui.wrapped(p.target.getTitle(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(p.perHour())), BorderLayout.EAST);
		JPanel body = column();
		JPanel figures = new JPanel(new GridLayout(1, 3, 6, 0));
		figures.setOpaque(false);
		double rate = p.successRate();
		figures.add(stat("SUCCESS", p.attempts == 0 ? "-" : Math.round(rate * 100) + "%", Theme.progressColor(rate)));
		figures.add(stat("STUNS", String.valueOf(p.stuns), p.stuns == 0 ? Theme.TEXT : Theme.EMBER));
		figures.add(stat(p.target.hasPouch() ? "POUCHES" : "PICKS", whole(p.target.hasPouch() ? p.opened : p.successes),
			Theme.TEXT));
		body.add(figures);
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(new EmptyBorder(3, 0, 3, 0));
		bar.add(SplitBar.progress(rate, Theme.progressColor(rate)), BorderLayout.CENTER);
		body.add(bar);
		body.add(Ui.wrapped(whole(p.successes) + " of " + whole(p.attempts) + " picks in " + Format.duration(p.playedMs) + ", "
			+ whole(p.picksPerHour) + " an hour", Theme.small(), Theme.TEXT_MUTED, TEXT));
		String food = p.food <= 0 && p.foodPerHour <= 0 ? "no food used"
			: "food " + Format.gp(p.food) + " (" + Math.round(p.foodPerHour) + " an hour)";
		body.add(Ui.wrapped("Loot " + Format.gp(p.loot) + ", " + food + ".", Theme.small(), Theme.TEXT_MUTED, TEXT));
		Method m = Methods.byId(p.target.getMethod());
		if (!p.counts)
		{
			body.add(Ui.wrapped("Ranks once you have " + Format.duration(Rates.ENOUGH_MS) + " of picking.", Theme.small(),
				Theme.GOLD_DIM, TEXT));
		}
		else if (m != null)
		{
			long wiki = m.getWikiPerHour();
			String vs = wiki <= 0 ? "" : ": " + Math.round(p.perHour() * 100.0 / wiki) + "% of the wiki's " + Format.gp(wiki);
			body.add(Ui.wrapped("Ranks " + Character.toLowerCase(m.getName().charAt(0)) + m.getName().substring(1)
				+ " in Best now" + vs + ".", Theme.small(), Theme.TEXT_FAINT, TEXT));
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		Pickpockets.Target target = p.target;
		buttons.add(new CodexButton("Forget log", false, 18, () -> actions.forgetPicks(target)));
		body.add(buttons);
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	// ---- Rogues' Castle chest log ----

	private void chests(StackPanel s)
	{
		if (!view.chestLog)
		{
			return;
		}
		View.Chests c = view.chests;
		s.add(new SectionHeader("Rogues' Castle chests", c == null ? null : whole(c.opens)));
		if (c == null)
		{
			s.add(note("Search the chests at the Rogues' Castle and your own loot per chest, Thieving experience and rate an "
				+ "hour gather here, with a death's losses counted against it. After a quarter of an hour of looting, your "
				+ "rate ranks the method in Best now."));
			return;
		}
		s.add(chestCard(c));
		s.add(note("Your own counts over the last " + Rates.DAYS + " days: a chest counts when Thieving experience follows "
			+ "your search. Loot, supplies and what a death took are valued at today's prices, and a death counts against "
			+ "the rate even if you got your items back from a grave."));
	}

	private Card chestCard(View.Chests c)
	{
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.TEAL);
		JPanel head = row();
		head.add(Ui.wrapped("Stealing from the chests", Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(c.perHour())), BorderLayout.EAST);
		JPanel body = column();
		JPanel figures = new JPanel(new GridLayout(1, 3, 6, 0));
		figures.setOpaque(false);
		figures.add(stat("PER CHEST", Format.gp(c.perOpen()), Theme.GOLD));
		figures.add(stat("XP/HR", c.playedMs <= 0 ? "-" : Format.gp(c.xpPerHour()), Theme.TEXT));
		figures.add(stat("DEATHS", String.valueOf(c.deaths), c.deaths == 0 ? Theme.TEXT : Theme.EMBER));
		body.add(figures);
		double searched = c.searchedRate();
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(new EmptyBorder(3, 0, 3, 0));
		bar.add(SplitBar.progress(searched, Theme.progressColor(searched)), BorderLayout.CENTER);
		body.add(bar);
		body.add(Ui.wrapped(whole(c.opens) + (c.opens == 1 ? " chest" : " chests") + " in " + Format.duration(c.playedMs) + ", "
			+ whole(c.opensPerHour()) + " an hour", Theme.small(), Theme.TEXT_MUTED, TEXT));
		if (c.trapped > 0)
		{
			body.add(Ui.wrapped(whole(c.trapped) + " opened without searching: the trap hit.", Theme.small(), Theme.EMBER,
				TEXT));
		}
		String used = c.used <= 0 ? "no supplies used" : "supplies " + Format.gp(c.used);
		String lost = c.deaths == 0 ? "" : ", " + Format.gp(c.lost) + " gone after " + (c.deaths == 1 ? "a death" : c.deaths
			+ " deaths");
		body.add(Ui.wrapped("Loot " + Format.gp(c.loot) + ", " + used + lost + ".", Theme.small(), Theme.TEXT_MUTED, TEXT));
		Method m = Methods.byId(com.codex.moneypilot.core.RoguesChests.METHOD);
		if (!c.counts)
		{
			body.add(Ui.wrapped("Ranks once you have " + Format.duration(Rates.ENOUGH_MS) + " of looting.", Theme.small(),
				Theme.GOLD_DIM, TEXT));
		}
		else if (m != null)
		{
			long wiki = m.getWikiPerHour();
			String vs = wiki <= 0 ? "" : ": " + Math.round(c.perHour() * 100.0 / wiki) + "% of the wiki's " + Format.gp(wiki);
			body.add(Ui.wrapped("Ranks stealing from Rogues' Castle chests in Best now" + vs + ".", Theme.small(),
				Theme.TEXT_FAINT, TEXT));
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Forget log", false, 18, actions::forgetChests));
		body.add(buttons);
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	// ---- Port Roberts stall log ----

	private void stalls(StackPanel s)
	{
		if (!view.stallLog)
		{
			return;
		}
		s.add(new SectionHeader("Port Roberts stalls", view.stolen.isEmpty() ? null : String.valueOf(view.stolen.size())));
		if (view.stolen.isEmpty())
		{
			s.add(note("Steal from the market stalls at Port Roberts and your own steals, times a guard caught you, Thieving "
				+ "experience and rate an hour gather here, by the rotation you work. After a quarter of an hour at a "
				+ "rotation, your rate ranks it in Best now, beside pickpocketing."));
			return;
		}
		for (View.Stolen st : view.stolen)
		{
			s.add(stolenCard(st));
		}
		s.add(note("Your own counts over the last " + Rates.DAYS + " days: a steal counts when Thieving experience comes in "
			+ "while you steal, and its loot is what lands in your pack that tick, valued at today's prices. A sitting is "
			+ "filed under the rotation of the best stall you stole from; time selling, banking and in the jail counts."));
	}

	private Card stolenCard(View.Stolen st)
	{
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.TEAL);
		JPanel head = row();
		head.add(Ui.wrapped(st.rotation.getTitle(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(st.perHour())), BorderLayout.EAST);
		JPanel body = column();
		JPanel figures = new JPanel(new GridLayout(1, 3, 6, 0));
		figures.setOpaque(false);
		figures.add(stat("PER STEAL", Format.gp(st.perSteal()), Theme.GOLD));
		figures.add(stat("XP/HR", st.playedMs <= 0 ? "-" : Format.gp(st.xpPerHour()), Theme.TEXT));
		figures.add(stat("CAUGHT", String.valueOf(st.caught), st.caught == 0 ? Theme.TEXT : Theme.EMBER));
		body.add(figures);
		double ofWiki = st.ofWikiXp();
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(new EmptyBorder(3, 0, 3, 0));
		bar.add(SplitBar.progress(ofWiki, Theme.progressColor(ofWiki)), BorderLayout.CENTER);
		body.add(bar);
		body.add(Ui.wrapped(Math.round(ofWiki * 100) + "% of the wiki's most, " + Format.gp(st.rotation.getWikiXpPerHour())
			+ " xp an hour with no tick lost", Theme.small(), Theme.TEXT_FAINT, TEXT));
		body.add(Ui.wrapped(whole(st.steals) + (st.steals == 1 ? " steal" : " steals") + " in " + Format.duration(st.playedMs)
			+ ", " + whole(st.stealsPerHour()) + " an hour", Theme.small(), Theme.TEXT_MUTED, TEXT));
		String paid = st.paid <= 0 ? "" : ", bribes " + Format.gp(st.paid);
		body.add(Ui.wrapped("Loot " + Format.gp(st.loot) + paid + ".", Theme.small(), Theme.TEXT_MUTED, TEXT));
		Method m = Methods.byId(st.rotation.getMethod());
		if (!st.counts)
		{
			body.add(Ui.wrapped("Ranks once you have " + Format.duration(Rates.ENOUGH_MS) + " at this rotation.", Theme.small(),
				Theme.GOLD_DIM, TEXT));
		}
		else if (m != null)
		{
			long wiki = m.getWikiPerHour();
			String vs = wiki <= 0 ? "" : ": " + Math.round(st.perHour() * 100.0 / wiki) + "% of the wiki estimate, "
				+ Format.gp(wiki);
			body.add(Ui.wrapped("Ranks the rotation in Best now" + vs + ".", Theme.small(), Theme.TEXT_FAINT, TEXT));
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		Stalls.Rotation rotation = st.rotation;
		buttons.add(new CodexButton("Forget log", false, 18, () -> actions.forgetStalls(rotation)));
		body.add(buttons);
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	// ---- Thieving, side by side ----

	/**
	 * Every Thieving method the account can do now, by the rate each ranks by: your own picks, chests and steals
	 * where you have them, the wiki's figure where you do not. A method short of the guide's suggestions is in only
	 * when you have your own rate for it. Shown only once at least one is your own.
	 */
	private void thieving(StackPanel s)
	{
		if (view.plan == null)
		{
			return;
		}
		List<Planner.Pick> picks = new ArrayList<>();
		boolean own = false;
		for (Planner.Pick p : view.plan.getOpen())
		{
			if (p.getMethod().getSkill() == net.runelite.api.Skill.THIEVING)
			{
				picks.add(p);
				own |= p.getRate().isOwn();
			}
		}
		for (Planner.Pick p : view.plan.getNear())
		{
			// Short of the guide's suggestions, but done anyway: your own rate is what it pays you.
			if (p.getMethod().getSkill() == net.runelite.api.Skill.THIEVING && p.getFit().getStatus() == Fit.Status.STRETCH
				&& p.getRate().isOwn())
			{
				picks.add(p);
				own = true;
			}
		}
		picks.sort((a, b) -> Long.compare(b.getRate().getPerHour(), a.getRate().getPerHour()));
		if (picks.size() < 2 || !own)
		{
			return;
		}
		s.add(new SectionHeader("Thieving, side by side", String.valueOf(picks.size())));
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.GOLD);
		JPanel body = column();
		long top = Math.max(1, picks.get(0).getRate().getPerHour());
		for (Planner.Pick p : picks)
		{
			Rate r = p.getRate();
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setOpaque(false);
			line.setAlignmentX(LEFT_ALIGNMENT);
			line.add(Ui.wrapped(shortName(p.getMethod()), Theme.small(), r.isOwn() ? Theme.TEXT : Theme.TEXT_MUTED, TEXT - 64),
				BorderLayout.CENTER);
			line.add(top(Ui.label(Format.gp(r.getPerHour()), Theme.small(), r.isOwn() ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM)),
				BorderLayout.EAST);
			body.add(line);
			JPanel bar = new JPanel(new BorderLayout(6, 0));
			bar.setOpaque(false);
			bar.setAlignmentX(LEFT_ALIGNMENT);
			bar.setBorder(new EmptyBorder(1, 0, 5, 0));
			double share = Math.max(0, Math.min(1, r.getPerHour() / (double) top));
			bar.add(SplitBar.progress(share, r.isOwn() ? Theme.TEAL : Theme.GOLD_DIM), BorderLayout.CENTER);
			bar.add(Ui.label(r.getSource().getTitle(), Theme.small(), r.isOwn() ? Theme.TEAL : Theme.TEXT_FAINT), BorderLayout.EAST);
			body.add(bar);
		}
		card.add(body, BorderLayout.CENTER);
		s.add(card);
		s.add(note("What each pays you an hour, best first: your own rate where you have a quarter of an hour of it, the "
			+ "wiki's figure where you have not."));
	}

	/** A method's name without the verb, for the side by side list. */
	private static String shortName(Method m)
	{
		String n = m.getName();
		for (String verb : new String[]{"Pickpocketing ", "Stealing from "})
		{
			if (n.startsWith(verb))
			{
				n = n.substring(verb.length());
				return Character.toUpperCase(n.charAt(0)) + n.substring(1);
			}
		}
		return n;
	}

	private static String whole(long n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	// ---- Goal ----

	private StackPanel goal()
	{
		StackPanel s = new StackPanel(6, 8);
		List<View.Goal> goals = view.goals.goals;
		if (goals.isEmpty())
		{
			Card card = new Card(new BorderLayout(0, 6)).accent(Theme.GOLD);
			card.add(Ui.label("Saving for something?", Theme.title(), Theme.GOLD), BorderLayout.NORTH);
			card.add(Ui.wrapped(view.account.isIronman()
					? "Type a coins goal in Money Pilot's settings and it works out how fast you can make it, with your "
					+ "bird house, herb and Miscellania runs fitted in alongside."
					: "Pick a savings goal in Codex Gear Roadmap, or type a coins goal in Money Pilot's settings, and it works "
					+ "out how fast you can make it: the best mix of what you can do, with your timegated runs alongside.",
				Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.CENTER);
			s.add(card);
			runsNote(s, null);
			return s;
		}
		View.Goal first = goals.get(0);
		s.add(goalCard(first));
		GoalPlan plan = first.plan;
		if (!first.target.isAffordable() && plan != null)
		{
			GoalPlan.Mix best = plan.getBest();
			if (best != null)
			{
				s.add(new SectionHeader("Fastest for you", days(best)));
				s.add(mixCard(best, true));
				GoalPlan.Mix alone = plan.getAlone();
				if (alone != null && !best.getRuns().isEmpty())
				{
					long saved = alone.getPlayMs() - best.getPlayMs();
					s.add(note("Without the runs: " + Format.duration(alone.getPlayMs()) + " of " + best.getMethod().getName()
						+ (saved >= 60_000L ? ", " + Format.duration(saved) + " longer." : ".")));
				}
			}
			List<GoalPlan.Mix> others = new ArrayList<>(plan.getOthers());
			if (!others.isEmpty() || plan.getRunsOnly() != null)
			{
				s.add(new SectionHeader("Or"));
				for (GoalPlan.Mix m : others)
				{
					s.add(otherCard(m));
				}
				if (plan.getRunsOnly() != null)
				{
					s.add(otherCard(plan.getRunsOnly()));
				}
			}
			if (best == null && plan.getRunsOnly() == null)
			{
				s.add(note("Nothing you can do in what is shown pays yet. Within reach lists what is nearest."));
			}
			if (!plan.getOpeners().isEmpty())
			{
				s.add(new SectionHeader("Pinned in Navigator"));
				for (GoalPlan.Opener o : plan.getOpeners())
				{
					s.add(openerCard(o, best));
				}
			}
			runsNote(s, plan);
		}
		for (View.Goal g : goals.subList(1, goals.size()))
		{
			s.add(new SectionHeader("Also saving for"));
			s.add(goalCard(g));
			if (!g.target.isAffordable() && g.plan != null && g.plan.getBest() != null)
			{
				s.add(otherCard(g.plan.getBest()));
			}
		}
		return s;
	}

	private Card goalCard(View.Goal g)
	{
		Target t = g.target;
		boolean done = t.isAffordable();
		Card card = new Card(new BorderLayout(0, 5)).accent(done ? Theme.GREEN : Theme.GOLD).highlighted(true);
		JPanel head = row();
		head.add(Ui.wrapped(t.getName(), Theme.body(), Theme.GOLD_BRIGHT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(Ui.label(Format.gp(t.getPrice()), Theme.title(), Theme.TEXT)), BorderLayout.EAST);
		JPanel body = column();
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		chips.setOpaque(false);
		chips.setAlignmentX(LEFT_ALIGNMENT);
		chips.add(new Chip(t.getSource() == Target.Source.GEAR_ROADMAP ? "Gear Roadmap" : "Your goal",
			t.getSource() == Target.Source.GEAR_ROADMAP ? Theme.TEAL : Theme.GOLD));
		if (done)
		{
			chips.add(new Chip("Covered", Theme.GREEN));
		}
		body.add(chips);
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(new EmptyBorder(5, 0, 3, 0));
		bar.add(SplitBar.progress(t.getProgress(), done ? Theme.GREEN : Theme.progressColor(t.getProgress())), BorderLayout.CENTER);
		body.add(bar);
		JPanel figures = new JPanel(new GridLayout(1, 2, 6, 0));
		figures.setOpaque(false);
		figures.add(stat("YOUR COINS", Format.gp(t.getCoins()), Theme.TEXT));
		figures.add(stat("SHORT", done ? "-" : Format.gp(t.getShort()), done ? Theme.GREEN : Theme.GOLD_BRIGHT));
		body.add(figures);
		String line;
		if (done)
		{
			line = "Your coins cover it already.";
		}
		else if (g.plan == null || g.plan.getBest() == null && g.plan.getRunsOnly() == null)
		{
			line = "Nothing you can do in what is shown pays toward it yet.";
		}
		else
		{
			line = headline(g.plan);
		}
		body.add(Ui.wrapped(line, Theme.small(), done ? Theme.GREEN : Theme.TEXT, TEXT));
		if (!t.isCoinsKnown())
		{
			body.add(Ui.wrapped("Open your bank so the coins in it count.", Theme.small(), Theme.GOLD, TEXT));
		}
		else if (t.getSource() == Target.Source.GEAR_ROADMAP)
		{
			body.add(Ui.wrapped("Priced at today's Grand Exchange prices, less the pieces you own.", Theme.small(),
				Theme.TEXT_FAINT, TEXT));
		}
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	/** "4.2m short: about 3h 50m of Vorkath at your own 1.1m/hr, with 4 bird house runs alongside." */
	private String headline(GoalPlan plan)
	{
		String shortBy = Format.gp(plan.getNeed()) + " short: ";
		GoalPlan.Mix best = plan.getBest();
		if (best == null)
		{
			GoalPlan.Mix runs = plan.getRunsOnly();
			return shortBy + (runs.isReached() ? "about " + days(runs.getDays()) + " of timegated runs alone."
				: "more than ten years of timegated runs alone.");
		}
		if (!best.isReached())
		{
			return shortBy + "more than ten years at " + rateWords(best.getRate()) + ".";
		}
		String with = "";
		if (!best.getRuns().isEmpty())
		{
			List<String> parts = new ArrayList<>();
			for (GoalPlan.Runs r : best.getRuns())
			{
				parts.add(r.getCount() + " " + runName(r.getMethod(), r.getCount()));
			}
			with = ", with " + and(parts) + " alongside";
		}
		return shortBy + "about " + Format.duration(best.getPlayMs()) + " of " + best.getMethod().getName() + " at "
			+ rateWords(best.getRate()) + with + ". " + finish(best);
	}

	/** "Done today", or "3 days at 2h a day: Fri 6 Nov." */
	private String finish(GoalPlan.Mix mix)
	{
		if (mix.getDays() <= 1)
		{
			return "Done today, at " + hours() + " a day.";
		}
		LocalDate on = Instant.ofEpochMilli(view.now).atZone(zone).toLocalDate().plusDays(mix.getDays() - 1);
		return mix.getDays() + " days at " + hours() + " a day: " + WEEKDAY.format(on).replace(' ', '\u00a0') + ".";
	}

	private String hours()
	{
		double h = view.goals.hoursPerDay;
		return h == Math.rint(h) ? (long) h + "h" : Format.duration(Math.round(h * 3_600_000L));
	}

	private Card mixCard(GoalPlan.Mix mix, boolean main)
	{
		Card card = new Card(new BorderLayout(0, 5)).accent(Theme.GOLD);
		JPanel head = row();
		head.add(Ui.wrapped(mix.getMethod() != null ? mix.getMethod().getName() : "Timegated runs alone", Theme.body(), Theme.TEXT,
			TEXT - 56), BorderLayout.CENTER);
		head.add(top(Ui.label(mix.isReached() ? Format.duration(mix.getPlayMs()) : "10y+", Theme.title(), Theme.GOLD_BRIGHT)),
			BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		JPanel body = column();
		long total = Math.max(1, mix.getProfit());
		double[] shares = new double[1 + mix.getRuns().size()];
		Color[] colors = new Color[shares.length];
		shares[0] = mix.getActiveProfit() / (double) total;
		colors[0] = PARTS[0];
		for (int i = 0; i < mix.getRuns().size(); i++)
		{
			shares[i + 1] = mix.getRuns().get(i).getProfit() / (double) total;
			colors[i + 1] = PARTS[1 + i % (PARTS.length - 1)];
		}
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(new EmptyBorder(1, 0, 3, 0));
		bar.add(new SplitBar(8, shares, colors), BorderLayout.CENTER);
		body.add(bar);
		if (mix.getMethod() != null)
		{
			body.add(partLine(PARTS[0], Format.duration(mix.getActiveMs()) + " at " + rateWords(mix.getRate()),
				mix.getActiveProfit()));
		}
		for (int i = 0; i < mix.getRuns().size(); i++)
		{
			GoalPlan.Runs r = mix.getRuns().get(i);
			body.add(partLine(colors[i + 1], r.getCount() + " " + runName(r.getMethod(), r.getCount()) + ", "
				+ Format.duration(r.getMs()), r.getProfit()));
		}
		if (main)
		{
			body.add(Ui.wrapped(finish(mix), Theme.small(), Theme.TEXT_MUTED, TEXT));
		}
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	private Card otherCard(GoalPlan.Mix mix)
	{
		Card card = new Card(new BorderLayout(6, 2)).accent(mix.getMethod() == null ? Theme.TEAL : Theme.GOLD_DIM);
		String name = mix.getMethod() != null ? mix.getMethod().getName() : "Timegated runs alone";
		card.add(Ui.wrapped(name, Theme.body(), Theme.TEXT, TEXT - 56), BorderLayout.CENTER);
		String time = !mix.isReached() ? "10y+" : mix.getMethod() == null ? days(mix.getDays()) : Format.duration(mix.getPlayMs());
		card.add(top(Ui.label(time, Theme.title(), Theme.GOLD)), BorderLayout.EAST);
		String detail;
		if (mix.getMethod() == null)
		{
			long perDay = mix.getDays() <= 0 ? 0 : mix.getPlayMs() / mix.getDays();
			detail = "About " + Format.duration(perDay) + " of runs a day, nothing else";
		}
		else
		{
			detail = "At " + rateWords(mix.getRate()) + (mix.getRuns().isEmpty() ? "" : ", runs alongside") + ", "
				+ days(mix.getDays());
		}
		card.add(Ui.wrapped(detail, Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.SOUTH);
		return card;
	}

	private Card openerCard(GoalPlan.Opener o, GoalPlan.Mix now)
	{
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.VIOLET);
		JPanel head = row();
		head.add(Ui.wrapped(o.getGoal().getTitle(), Theme.body(), Theme.TEXT, TEXT - 56), BorderLayout.CENTER);
		GoalPlan.Mix m = o.getMix();
		head.add(top(Ui.label(m.isReached() ? Format.duration(m.getPlayMs()) : "10y+", Theme.title(), Theme.VIOLET)), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		double h = o.getGoal().getHoursLeft();
		String left = h <= 0 ? "" : " About " + (h >= 10 ? Math.round(h) + "h" : Format.duration(Math.round(h * 3_600_000L)))
			+ " to finish it, by Navigator.";
		String vs = now != null && now.isReached() && m.isReached() && m.getPlayMs() < now.getPlayMs()
			? " instead of " + Format.duration(now.getPlayMs()) : "";
		card.add(Ui.wrapped("Opens " + o.getMethod().getName() + " at " + rateWords(m.getRate()) + ": then "
			+ Format.duration(m.getPlayMs()) + vs + "." + left, Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.CENTER);
		return card;
	}

	/** Which runs the account can do, and the rule they are counted by. */
	private void runsNote(StackPanel s, GoalPlan plan)
	{
		List<String> names = new ArrayList<>();
		for (Method m : plan != null ? plan.getRuns() : GoalPlan.runsFor(view.account, Methods.recurring()))
		{
			names.add(m.getName());
		}
		String rule = "Daily runs once a day; bird houses and herbs each time they come round while you play, and once "
			+ "at each login after the first day. Rates are the wiki's per run.";
		s.add(note((names.isEmpty() ? "No timegated runs open yet (bird houses need Hunter 89 and Bone Voyage). "
			: "Your timegated runs: " + String.join(", ", names) + ". ") + rule));
	}

	private static JComponent partLine(Color color, String text, long profit)
	{
		JPanel p = new JPanel(new BorderLayout(5, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		JPanel dot = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				Theme.smooth(g2);
				g2.setColor(color);
				g2.fillOval(0, 4, 6, 6);
				g2.dispose();
			}
		};
		dot.setOpaque(false);
		dot.setPreferredSize(new Dimension(6, 12));
		JPanel dotBox = new JPanel(new BorderLayout());
		dotBox.setOpaque(false);
		dotBox.add(dot, BorderLayout.NORTH);
		p.add(dotBox, BorderLayout.WEST);
		p.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, TEXT - 54), BorderLayout.CENTER);
		p.add(top(Ui.label(Format.gp(profit), Theme.small(), Theme.GOLD)), BorderLayout.EAST);
		return p;
	}

	private static String rateWords(com.codex.moneypilot.core.Rate rate)
	{
		if (rate == null)
		{
			return "the wiki's rates";
		}
		String who = rate.getSource() == com.codex.moneypilot.core.Rate.Source.YOURS ? "your own "
			: rate.getSource() == com.codex.moneypilot.core.Rate.Source.PICKPOCKETS ? "your picks' "
			: rate.getSource() == com.codex.moneypilot.core.Rate.Source.CHESTS ? "your chests' "
			: rate.getSource() == com.codex.moneypilot.core.Rate.Source.STALLS ? "your steals' "
			: rate.getSource() == com.codex.moneypilot.core.Rate.Source.SESSIONS ? "your sessions' " : "the wiki's ";
		return who + Format.gp(rate.getPerHour()) + "/hr";
	}

	private static String runName(Method m, int count)
	{
		String base = m.getGroup() != null ? "herb run" : "bird-houses".equals(m.getId()) ? "bird house run"
			: "Miscellania collection";
		return count == 1 ? base : base + "s";
	}

	private static String days(GoalPlan.Mix mix)
	{
		return mix.isReached() ? days(mix.getDays()) : "10y+";
	}

	private static String days(int days)
	{
		return days == 1 ? "today" : days + " days";
	}

	private static String and(List<String> parts)
	{
		if (parts.size() <= 1)
		{
			return String.join("", parts);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	private static JComponent coachRow(Coach c)
	{
		JPanel p = column();
		Color tone;
		switch (c.getVerdict())
		{
			case BEST:
			case AHEAD:
				tone = Theme.GREEN;
				break;
			case BEHIND:
				tone = Theme.EMBER;
				break;
			case SETTLING:
				tone = Theme.TEXT_FAINT;
				break;
			default:
				tone = Theme.TEXT;
				break;
		}
		p.add(Ui.wrapped(c.line(), Theme.small(), tone, TEXT));
		List<String> refs = new ArrayList<>();
		if (c.getBest() != null)
		{
			refs.add("Best " + Format.gp(c.getBest()));
		}
		if (c.getUsual() != null)
		{
			refs.add("usual " + Format.gp(c.getUsual()));
		}
		refs.add("wiki " + Format.gp(c.getWiki()));
		p.add(Ui.wrapped(String.join(", ", refs) + " an hour", Theme.small(), Theme.TEXT_FAINT, TEXT));
		return p;
	}

	// ---- Cards ----

	private Card methodCard(Planner.Pick pick, int rank)
	{
		Method m = pick.getMethod();
		Fit fit = pick.getFit();
		Rate rate = pick.getRate();
		boolean expanded = open.contains(m.getId());
		Card card = new Card(new BorderLayout(0, 4)).accent(category(m.getCategory())).highlighted(expanded);

		JPanel head = row();
		head.add(Ui.wrapped(rank + ". " + m.getName(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(rate.getPerHour())), BorderLayout.EAST);

		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		chips.setOpaque(false);
		chips.add(new Chip(rate.getSource().getTitle(), rate.isOwn() ? Theme.TEAL : Theme.TEXT_MUTED));
		chips.add(statusChip(fit));
		if (m.isWilderness())
		{
			chips.add(new Chip("Wildy", Theme.BLOOD));
		}

		JPanel top = new JPanel(new BorderLayout(0, 3));
		top.setOpaque(false);
		top.add(head, BorderLayout.NORTH);
		top.add(chips, BorderLayout.CENTER);
		String reason = reason(fit);
		if (reason != null && !expanded)
		{
			top.add(Ui.wrapped(reason, Theme.small(), Theme.TEXT_MUTED, TEXT), BorderLayout.SOUTH);
		}
		card.add(top, BorderLayout.NORTH);
		if (expanded)
		{
			card.add(details(pick), BorderLayout.CENTER);
		}
		card.onClick(() -> toggle(m.getId()));
		return card;
	}

	private JComponent details(Planner.Pick pick)
	{
		Method m = pick.getMethod();
		Fit fit = pick.getFit();
		JPanel d = column();
		d.setBorder(new EmptyBorder(4, 0, 0, 0));
		if (!m.getNeeds().isEmpty())
		{
			d.add(small("NEEDS", Theme.TEXT_FAINT));
			for (Need n : m.getNeeds())
			{
				d.add(needRow(n, fit.getUnmet().contains(n) ? Mark.Kind.UNMET
					: fit.getUnsure().contains(n) || n.getKind() == Need.Kind.NOTE ? Mark.Kind.UNSURE : Mark.Kind.MET));
			}
		}
		if (!m.getSuggests().isEmpty())
		{
			d.add(small(m.isEstimate() ? "THE WIKI SUGGESTS" : "THE GUIDE SUGGESTS", Theme.TEXT_FAINT));
			for (Need n : m.getSuggests())
			{
				d.add(needRow(n, fit.getShortOf().contains(n) ? Mark.Kind.SUGGESTED
					: n.getKind() == Need.Kind.NOTE ? Mark.Kind.UNSURE : Mark.Kind.MET));
			}
		}
		d.add(small("WHAT IT PAYS", Theme.TEXT_FAINT));
		Rate own = view.rates.own(m);
		Rate sessions = view.rates.sessions(m);
		if (own != null)
		{
			d.add(payLine("Yours", own.getPerHour(), own.getCount() + (own.getCount() == 1 ? " run, " : " runs, ")
				+ Format.duration(own.getPlayedMs()), Theme.TEAL));
		}
		Rate picks = view.rates.pickpockets(m);
		if (picks != null)
		{
			d.add(payLine("Your picks", picks.getPerHour(), String.format(Locale.ENGLISH, "%,d", picks.getCount())
				+ " picks logged, " + Format.duration(picks.getPlayedMs()), Theme.TEAL));
		}
		Rate chests = view.rates.chests(m);
		if (chests != null)
		{
			d.add(payLine("Your chests", chests.getPerHour(), String.format(Locale.ENGLISH, "%,d", chests.getCount())
				+ " chests logged, " + Format.duration(chests.getPlayedMs()), Theme.TEAL));
		}
		Rate steals = view.rates.stalls(m);
		if (steals != null)
		{
			d.add(payLine("Your steals", steals.getPerHour(), String.format(Locale.ENGLISH, "%,d", steals.getCount())
				+ " steals logged, " + Format.duration(steals.getPlayedMs()), Theme.TEAL));
		}
		if (sessions != null)
		{
			d.add(payLine("Sessions", sessions.getPerHour(), sessions.getCount() + " in Chronicle", Theme.VIOLET));
		}
		d.add(payLine(m.isEstimate() ? "Wiki est." : "Wiki", m.getWikiPerHour(), (m.getBasis() != null ? m.getBasis() + ", " : "") + Methods.CHECKED,
			Theme.TEXT_MUTED));
		if (m.isTrade())
		{
			d.add(Ui.wrapped("Buys what it works on, so the margin moves with prices.", Theme.small(), Theme.TEXT_FAINT, TEXT));
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(6, 0, 0, 0));
		if (view.live == null)
		{
			if (view.bankSeen)
			{
				buttons.add(new CodexButton("Clock a run", true, 20, () -> actions.start(m)));
			}
		}
		buttons.add(new CodexButton(m.isEstimate() ? "Wiki page" : "Wiki guide", false, 20, () -> actions.guide(m)));
		d.add(buttons);
		if (view.live == null && !view.bankSeen)
		{
			d.add(Ui.wrapped("Open your bank once to clock runs.", Theme.small(), Theme.GOLD, TEXT));
		}
		return d;
	}

	private Card reachCard(Planner.Pick pick)
	{
		Method m = pick.getMethod();
		Fit fit = pick.getFit();
		boolean expanded = open.contains(m.getId());
		Card card = new Card(new BorderLayout(0, 4)).accent(category(m.getCategory())).highlighted(expanded);
		JPanel head = row();
		head.add(Ui.wrapped(m.getName(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(pick.getRate().getPerHour())), BorderLayout.EAST);
		JPanel top = new JPanel(new BorderLayout(0, 3));
		top.setOpaque(false);
		top.add(head, BorderLayout.NORTH);
		if (!expanded)
		{
			String line = fit.getStatus() == Fit.Status.STRETCH ? "Guide suggests " + missing(fit.getShortOf())
				: "Needs " + missing(fit.getUnmet());
			top.add(Ui.wrapped(line, Theme.small(), fit.getStatus() == Fit.Status.STRETCH ? Theme.GOLD : Theme.EMBER, TEXT),
				BorderLayout.CENTER);
		}
		card.add(top, BorderLayout.NORTH);
		if (expanded)
		{
			card.add(details(pick), BorderLayout.CENTER);
		}
		card.onClick(() -> toggle(m.getId()));
		return card;
	}

	private String missing(List<Need> needs)
	{
		List<String> parts = new ArrayList<>();
		for (Need n : needs)
		{
			if (n.getKind() == Need.Kind.SKILL)
			{
				parts.add(n.describe() + " (you " + view.account.level(n.getSkill()) + ")");
			}
			else if (n.getKind() == Need.Kind.TOTAL)
			{
				parts.add(n.describe() + " (you " + view.account.getTotal() + ")");
			}
			else
			{
				parts.add(n.describe());
			}
		}
		return String.join(", ", parts);
	}

	private Card liveCard(View.Live live)
	{
		Card card = new Card(new BorderLayout(0, 6)).accent(Theme.TEAL).highlighted(true);
		JPanel head = row();
		head.add(Ui.label("CLOCKING", Theme.small(), Theme.TEAL), BorderLayout.CENTER);
		head.add(Ui.label(Format.clock(live.playedMs), Theme.title(), Theme.TEXT), BorderLayout.EAST);
		JPanel body = column();
		body.add(Ui.label(Ui.clip(live.method.getName(), card.getFontMetrics(Theme.body()), TEXT), Theme.body(), Theme.GOLD));
		JPanel figures = new JPanel(new GridLayout(1, 2, 6, 0));
		figures.setOpaque(false);
		figures.add(stat("SO FAR", Format.signed(live.profit), live.profit < 0 ? Theme.BLOOD : Theme.TEXT));
		figures.add(stat("AN HOUR", live.perHour() == 0 ? "-" : Format.signed(live.perHour()), Theme.TEXT));
		body.add(figures);
		if (live.coach != null)
		{
			body.add(coachRow(live.coach));
		}
		body.add(Ui.wrapped(live.loggedIn ? "Bank your loot before you stop, so it counts."
			: "Paused while you are logged out.", Theme.small(), Theme.TEXT_MUTED, TEXT));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Stop and keep", true, 20, actions::stop));
		buttons.add(new CodexButton("Discard", false, 20, actions::discard));
		body.add(buttons);
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	private Card runsCard(Method m, List<Clocked> runs)
	{
		Card card = new Card(new BorderLayout(0, 4)).accent(Theme.TEAL);
		long profit = 0;
		long ms = 0;
		for (Clocked r : runs)
		{
			profit += r.getProfit();
			ms += r.getPlayedMs();
		}
		long perHour = ms <= 0 ? 0 : Math.round(profit * 3_600_000.0 / ms);
		JPanel head = row();
		head.add(Ui.wrapped(m.getName(), Theme.body(), Theme.TEXT, TEXT - 50), BorderLayout.CENTER);
		head.add(top(rateLabel(perHour)), BorderLayout.EAST);
		JPanel body = column();
		long wiki = m.getWikiPerHour();
		String vs = wiki <= 0 ? "" : ", " + Math.round(perHour * 100.0 / wiki) + "% of the wiki's";
		body.add(Ui.label(runs.size() + (runs.size() == 1 ? " run, " : " runs, ") + Format.duration(ms) + vs,
			Theme.small(), Theme.TEXT_MUTED));
		for (Clocked r : runs.subList(0, Math.min(3, runs.size())))
		{
			JPanel line = row();
			line.add(Ui.label(DAY.format(Instant.ofEpochMilli(r.getStartedAt()).atZone(zone)) + "  " + Format.duration(r.getPlayedMs()),
				Theme.small(), Theme.TEXT_FAINT), BorderLayout.CENTER);
			line.add(Ui.label(Format.signed(r.getProfit()), Theme.small(), r.getProfit() < 0 ? Theme.BLOOD : Theme.GOLD),
				BorderLayout.EAST);
			body.add(line);
		}
		if (ms < Rates.ENOUGH_MS)
		{
			body.add(Ui.wrapped("Counts once you have " + Format.duration(Rates.ENOUGH_MS) + " clocked.", Theme.small(),
				Theme.GOLD_DIM, TEXT));
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Forget runs", false, 18, () -> actions.forget(m)));
		body.add(buttons);
		card.add(head, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	// ---- Pieces ----

	private JPanel filters()
	{
		JPanel p = new JPanel(new GridLayout(2, 2, 4, 4));
		p.setOpaque(false);
		for (Method.Category c : Method.Category.values())
		{
			p.add(new CodexButton(c.getTitle(), view.shown.contains(c), 18, () -> actions.toggle(c)));
		}
		return p;
	}

	private static Color category(Method.Category c)
	{
		switch (c)
		{
			case COMBAT:
				return Theme.EMBER;
			case SKILLING:
				return Theme.TEAL;
			case PROCESSING:
				return Theme.VIOLET;
			default:
				return Theme.GREEN;
		}
	}

	private static Chip statusChip(Fit fit)
	{
		switch (fit.getStatus())
		{
			case READY:
				return new Chip("Ready", Theme.GREEN);
			case ABLE:
				return new Chip("Gear up", Theme.GOLD);
			case CHECK:
				return new Chip("Check", Theme.GOLD);
			case STRETCH:
				return new Chip("Stretch", Theme.EMBER);
			default:
				return new Chip("Locked", Theme.BLOCKED);
		}
	}

	/** The line under a method: what the guide suggests that you lack, or what cannot be read. */
	private static String reason(Fit fit)
	{
		if (!fit.getUnsure().isEmpty())
		{
			List<String> parts = new ArrayList<>();
			fit.getUnsure().forEach(n -> parts.add(n.describe()));
			return "Check you have " + String.join(", ", parts) + ".";
		}
		if (!fit.getShortOf().isEmpty())
		{
			List<String> parts = new ArrayList<>();
			fit.getShortOf().forEach(n -> parts.add(n.describe()));
			return "Guide suggests " + String.join(", ", parts) + ".";
		}
		return null;
	}

	private static JComponent rateLabel(long perHour)
	{
		JLabel l = Ui.label(Format.gp(perHour), Theme.title(), perHour < 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		l.setHorizontalAlignment(JLabel.RIGHT);
		return l;
	}

	/** Keeps a component at the top of a row whose other side may wrap onto two lines. */
	private static JComponent top(JComponent c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c, BorderLayout.NORTH);
		return p;
	}

	private static JComponent needRow(Need n, Mark.Kind kind)
	{
		JPanel p = new JPanel(new BorderLayout(5, 0));
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(1, 0, 1, 0));
		JPanel markBox = new JPanel(new BorderLayout());
		markBox.setOpaque(false);
		markBox.add(new Mark(kind), BorderLayout.NORTH);
		p.add(markBox, BorderLayout.WEST);
		Color text = kind == Mark.Kind.MET ? Theme.TEXT : kind == Mark.Kind.UNMET ? Theme.mix(Theme.BLOCKED, Theme.TEXT, 0.35)
			: Theme.TEXT_MUTED;
		p.add(Ui.wrapped(n.describe(), Theme.small(), text, TEXT - 18), BorderLayout.CENTER);
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private static JComponent payLine(String who, long perHour, String detail, Color color)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.add(Ui.label(who, Theme.small(), color), BorderLayout.WEST);
		p.add(Ui.label(Format.gp(perHour), Theme.small(), perHour < 0 ? Theme.BLOOD : Theme.GOLD), BorderLayout.EAST);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(p, BorderLayout.NORTH);
		wrap.add(Ui.wrapped(detail, Theme.small(), Theme.TEXT_FAINT, TEXT), BorderLayout.CENTER);
		return wrap;
	}

	private static JComponent stat(String label, String value, Color color)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(Ui.label(label, Theme.small(), Theme.TEXT_FAINT), BorderLayout.NORTH);
		p.add(Ui.label(value, Theme.title(), color), BorderLayout.CENTER);
		return p;
	}

	private static JComponent small(String text, Color color)
	{
		JLabel l = Ui.label(text, Theme.small(), color);
		l.setBorder(new EmptyBorder(4, 0, 1, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static Card step(String n, String title, String body)
	{
		Card card = new Card(new BorderLayout(8, 0));
		card.add(Ui.label(n, Theme.title(), Theme.GOLD), BorderLayout.WEST);
		JPanel text = new JPanel(new BorderLayout(0, 2));
		text.setOpaque(false);
		text.add(Ui.wrapped(title, Theme.body(), Theme.TEXT, TEXT - 16), BorderLayout.NORTH);
		text.add(Ui.wrapped(body, Theme.small(), Theme.TEXT_MUTED, TEXT - 16), BorderLayout.CENTER);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private static JComponent note(String text)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(2, 4, 2, 4));
		p.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, TEXT + 8), BorderLayout.CENTER);
		return p;
	}

	private static JComponent more(String text, Runnable action)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		p.setOpaque(false);
		p.add(new CodexButton(text, false, 20, action));
		return p;
	}

	private static JPanel row()
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	/** A column whose rows all span its width. */
	private static JPanel column()
	{
		JPanel p = new JPanel(new StackPanel.StackLayout(1));
		p.setOpaque(false);
		return p;
	}

	private void toggle(String id)
	{
		if (!open.remove(id))
		{
			open.add(id);
		}
		rebuild(false);
	}

	private static <T> List<T> limit(List<T> list, int n)
	{
		return list.size() <= n ? list : list.subList(0, n);
	}
}
