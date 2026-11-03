package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Fit;
import com.codex.moneypilot.core.Format;
import com.codex.moneypilot.core.Method;
import com.codex.moneypilot.core.Methods;
import com.codex.moneypilot.core.Need;
import com.codex.moneypilot.core.Planner;
import com.codex.moneypilot.core.Rate;
import com.codex.moneypilot.core.Rates;
import com.codex.moneypilot.core.Clocked;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.Instant;
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

	private final Actions actions;
	private final Crest crest = new Crest();
	private final JPanel tabs = new JPanel(new GridLayout(1, 0, 4, 0));
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
			d.add(small("THE GUIDE SUGGESTS", Theme.TEXT_FAINT));
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
		if (sessions != null)
		{
			d.add(payLine("Sessions", sessions.getPerHour(), sessions.getCount() + " in Chronicle", Theme.VIOLET));
		}
		d.add(payLine("Wiki", m.getWikiPerHour(), (m.getBasis() != null ? m.getBasis() + ", " : "") + Methods.CHECKED,
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
		buttons.add(new CodexButton("Wiki guide", false, 20, () -> actions.guide(m)));
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
