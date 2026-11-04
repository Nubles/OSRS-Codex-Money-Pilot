package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Format;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small plaque on the game screen while stealing from the Port Roberts stalls: the sitting's rate and
 * Thieving experience an hour, the rotation it is filed under and how often a guard caught you. It shows
 * numbers only: it never marks a stall to click, a guard or a tick to steal on.
 */
public class StallPlaque extends Overlay
{
	static final int WIDTH = 168;
	static final int HEIGHT = 66;

	private final Supplier<View.Stealing> stealing;

	public StallPlaque(Supplier<View.Stealing> stealing)
	{
		this.stealing = stealing;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View.Stealing s = stealing.get();
		if (s == null)
		{
			return null;
		}
		paint(g, s);
		return new Dimension(WIDTH, HEIGHT);
	}

	/** Draws the plaque at the origin; the screenshot harness calls this too. */
	public static void paint(Graphics2D g, View.Stealing s)
	{
		Graphics2D p = (Graphics2D) g.create();
		Theme.smooth(p);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, HEIGHT - 1, 8, 8);
		p.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 235), 0, HEIGHT, Theme.alpha(Theme.INK_DEEP, 235)));
		p.fill(shape);
		p.setColor(Theme.alpha(Theme.GOLD, 150));
		p.draw(shape);
		p.setPaint(new GradientPaint(0, 0, Theme.TEAL, 0, HEIGHT, Theme.alpha(Theme.TEAL, 80)));
		p.fillRect(1, 5, 2, HEIGHT - 10);

		p.setFont(Theme.small());
		FontMetrics small = p.getFontMetrics();
		String clock = Format.clock(s.playedMs);
		Ui.shadowed(p, "Port Roberts stalls", 9, 14, Theme.GOLD);
		Ui.shadowed(p, clock, WIDTH - 8 - small.stringWidth(clock), 14, Theme.TEXT_MUTED);

		p.setFont(Theme.title());
		long rate = s.perHour();
		String big = rate == 0 ? Format.signed(s.profit) : Format.signed(rate);
		Ui.shadowed(p, big, 9, 32, rate < 0 || s.profit < 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		int after = 9 + p.getFontMetrics().stringWidth(big) + 4;
		p.setFont(Theme.small());
		Ui.shadowed(p, rate == 0 ? "so far" : "an hour", after, 32, Theme.TEXT_MUTED);

		long xph = s.xpPerHour();
		String steals = s.steals == 0 ? "No steals yet"
			: s.steals + (s.steals == 1 ? " steal" : " steals") + (xph > 0 ? ", " + Format.gp(xph) + " xp/hr" : "");
		Ui.shadowed(p, Ui.clip(steals, small, WIDTH - 16), 9, 45, Theme.TEXT_MUTED);

		String caught = s.caught == 0 ? "" : "caught " + s.caught;
		int room = WIDTH - 16 - (caught.isEmpty() ? 0 : small.stringWidth(caught) + 6);
		String rotation = s.rotation != null ? s.rotation.getTitle()
			: s.last != null ? s.last.getTitle() + ", not logged" : "Steal to begin";
		Ui.shadowed(p, Ui.clip(rotation, small, room), 9, 59, s.rotation != null ? Theme.TEXT : Theme.TEXT_FAINT);
		if (!caught.isEmpty())
		{
			Ui.shadowed(p, caught, WIDTH - 8 - small.stringWidth(caught), 59, Theme.EMBER);
		}
		p.dispose();
	}
}
