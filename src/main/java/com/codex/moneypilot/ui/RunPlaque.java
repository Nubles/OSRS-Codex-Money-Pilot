package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Coach;
import com.codex.moneypilot.core.Format;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small plaque on the game screen while a run is clocked: the method, the time played, what it has
 * made so far and the rate an hour. It shows numbers only and never points at anything to click.
 */
public class RunPlaque extends Overlay
{
	private static final int WIDTH = 150;
	private static final int HEIGHT = 50;
	/** Taller by a line when the run coach has something to say. */
	private static final int COACHED = 62;

	private final Supplier<View.Live> live;

	public RunPlaque(Supplier<View.Live> live)
	{
		this.live = live;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View.Live run = live.get();
		if (run == null)
		{
			return null;
		}
		paint(g, run);
		return new Dimension(WIDTH, height(run));
	}

	static int height(View.Live run)
	{
		return run.coach != null ? COACHED : HEIGHT;
	}

	/** Draws the plaque at the origin; the screenshot harness calls this too. */
	public static void paint(Graphics2D g, View.Live run)
	{
		Graphics2D p = (Graphics2D) g.create();
		Theme.smooth(p);
		int height = height(run);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 8, 8);
		p.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 235), 0, height, Theme.alpha(Theme.INK_DEEP, 235)));
		p.fill(shape);
		p.setColor(Theme.alpha(Theme.GOLD, 150));
		p.draw(shape);
		p.setPaint(new GradientPaint(0, 0, Theme.TEAL, 0, height, Theme.alpha(Theme.TEAL, 80)));
		p.fillRect(1, 5, 2, height - 10);

		p.setFont(Theme.small());
		FontMetrics small = p.getFontMetrics();
		String clock = Format.clock(run.playedMs);
		Ui.shadowed(p, Ui.clip(run.method.getName(), small, WIDTH - 22 - small.stringWidth(clock)), 9, 14, Theme.GOLD);
		Ui.shadowed(p, clock, WIDTH - 8 - small.stringWidth(clock), 14, run.loggedIn ? Theme.TEXT_MUTED : Theme.TEXT_FAINT);

		p.setFont(Theme.title());
		Color worth = run.profit < 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT;
		Ui.shadowed(p, Format.signed(run.profit), 9, 32, worth);
		p.setFont(Theme.small());
		String rate = run.perHour() == 0 ? "rate after a minute" : Format.signed(run.perHour()) + " an hour";
		Ui.shadowed(p, rate, 9, 45, Theme.TEXT_MUTED);
		if (run.coach != null)
		{
			Coach c = run.coach;
			Color tone = c.getVerdict() == Coach.Verdict.BEST || c.getVerdict() == Coach.Verdict.AHEAD ? Theme.GREEN
				: c.getVerdict() == Coach.Verdict.BEHIND ? Theme.EMBER : c.getVerdict() == Coach.Verdict.SETTLING ? Theme.TEXT_FAINT
				: Theme.TEXT;
			Ui.shadowed(p, Ui.clip(c.shortLine(), small, WIDTH - 16), 9, 57, tone);
		}
		p.dispose();
	}
}
