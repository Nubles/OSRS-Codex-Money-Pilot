package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Format;
import com.codex.moneypilot.core.RoguesChests;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small plaque on the game screen at the Rogues' Castle chests: the sitting's rate and Thieving
 * experience an hour, which chests stand ready and when the next restocks, and what a death right now
 * would cost. The last is a figure to read, in the plaque's own quiet colours; keeping watch for player
 * killers is Codex Guardian's work. It shows numbers only and never points at anything to click.
 */
public class ChestPlaque extends Overlay
{
	static final int WIDTH = 168;
	private static final int HEIGHT = 66;
	/** Taller by the risk rows when the risk is shown. */
	private static final int RISKED = 98;

	private final Supplier<View.Looting> looting;

	public ChestPlaque(Supplier<View.Looting> looting)
	{
		this.looting = looting;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View.Looting l = looting.get();
		if (l == null)
		{
			return null;
		}
		paint(g, l);
		return new Dimension(WIDTH, height(l));
	}

	static int height(View.Looting l)
	{
		return l.risk >= 0 ? RISKED : HEIGHT;
	}

	/** Draws the plaque at the origin; the screenshot harness calls this too. */
	public static void paint(Graphics2D g, View.Looting l)
	{
		Graphics2D p = (Graphics2D) g.create();
		Theme.smooth(p);
		int height = height(l);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 8, 8);
		p.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 235), 0, height, Theme.alpha(Theme.INK_DEEP, 235)));
		p.fill(shape);
		p.setColor(Theme.alpha(Theme.GOLD, 150));
		p.draw(shape);
		p.setPaint(new GradientPaint(0, 0, Theme.TEAL, 0, height, Theme.alpha(Theme.TEAL, 80)));
		p.fillRect(1, 5, 2, height - 10);

		p.setFont(Theme.small());
		FontMetrics small = p.getFontMetrics();
		String clock = Format.clock(l.playedMs);
		Ui.shadowed(p, "Rogues' Castle chests", 9, 14, Theme.GOLD);
		Ui.shadowed(p, clock, WIDTH - 8 - small.stringWidth(clock), 14, Theme.TEXT_MUTED);

		p.setFont(Theme.title());
		long rate = l.perHour();
		String big = rate == 0 ? Format.signed(l.profit) : Format.signed(rate);
		Ui.shadowed(p, big, 9, 32, rate < 0 || l.profit < 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		int after = 9 + p.getFontMetrics().stringWidth(big) + 4;
		p.setFont(Theme.small());
		Ui.shadowed(p, rate == 0 ? "so far" : "an hour", after, 32, Theme.TEXT_MUTED);

		long xph = l.xpPerHour();
		String opened = l.opens == 0 ? "No chests yet"
			: l.opens + (l.opens == 1 ? " chest" : " chests") + (xph > 0 ? ", " + Format.gp(xph) + " xp/hr" : "");
		Ui.shadowed(p, Ui.clip(opened, small, WIDTH - 16), 9, 45, Theme.TEXT_MUTED);

		// Which chests stand ready: a gold pip for each closed chest, a hollow one for each open.
		String ready;
		if (l.seen <= 0)
		{
			ready = "No chests in view";
		}
		else
		{
			ready = "Ready " + l.ready + " of " + l.seen;
		}
		Ui.shadowed(p, ready, 9, 59, l.ready > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
		if (l.seen > 0)
		{
			String next = l.ready > 0 ? "" : l.nextTicks <= 0 ? "any moment" : "next " + seconds(l.nextTicks);
			int pips = Math.min(RoguesChests.CHESTS, l.seen);
			int x = WIDTH - 9 - pips * 9;
			if (!next.isEmpty())
			{
				Ui.shadowed(p, next, x - 4 - small.stringWidth(next), 59, Theme.TEXT_MUTED);
			}
			for (int i = 0; i < pips; i++)
			{
				Ellipse2D dot = new Ellipse2D.Float(x + i * 9 + 1, 52, 6, 6);
				if (i < l.ready)
				{
					p.setColor(Theme.GOLD_BRIGHT);
					p.fill(dot);
				}
				else
				{
					p.setColor(Theme.GOLD_DIM);
					p.setStroke(new BasicStroke(1f));
					p.draw(dot);
				}
			}
		}

		if (l.risk >= 0)
		{
			p.setColor(Theme.alpha(Theme.STROKE, 200));
			p.fillRect(9, 66, WIDTH - 18, 1);
			String value = Format.gp(l.risk);
			Ui.shadowed(p, "If you die now", 9, 80, Theme.TEXT_MUTED);
			Ui.shadowed(p, value, WIDTH - 8 - small.stringWidth(value), 80, l.risk > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
			String how = l.kept == 0 ? "you keep nothing" : "you keep your " + l.kept + " most valuable";
			Ui.shadowed(p, Ui.clip(how, small, WIDTH - 16), 9, 92, Theme.TEXT_FAINT);
		}
		p.dispose();
	}

	private static String seconds(long ticks)
	{
		long s = Math.round(ticks * 0.6);
		return s + "s";
	}
}
