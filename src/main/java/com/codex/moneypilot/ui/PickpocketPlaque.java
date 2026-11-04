package com.codex.moneypilot.ui;

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
 * A small plaque on the game screen while pickpocketing a target Money Pilot logs: the rate an hour of
 * this sitting, your own success rate and stuns, and the coin pouches held against the cap, turning
 * ember as the cap nears. It shows numbers only and never points at anything to click.
 */
public class PickpocketPlaque extends Overlay
{
	static final int WIDTH = 160;
	private static final int HEIGHT = 50;
	/** Taller by the pouch row for a target that gives coin pouches. */
	private static final int POUCHED = 70;

	private final Supplier<View.Picking> picking;

	public PickpocketPlaque(Supplier<View.Picking> picking)
	{
		this.picking = picking;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View.Picking p = picking.get();
		if (p == null)
		{
			return null;
		}
		paint(g, p);
		return new Dimension(WIDTH, height(p));
	}

	static int height(View.Picking p)
	{
		return p.cap > 0 ? POUCHED : HEIGHT;
	}

	/** Draws the plaque at the origin; the screenshot harness calls this too. */
	public static void paint(Graphics2D g, View.Picking pick)
	{
		Graphics2D p = (Graphics2D) g.create();
		Theme.smooth(p);
		int height = height(pick);
		Color edge = pick.full() ? Theme.BLOOD : pick.nearCap() ? Theme.EMBER : Theme.GOLD;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 8, 8);
		p.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 235), 0, height, Theme.alpha(Theme.INK_DEEP, 235)));
		p.fill(shape);
		p.setColor(Theme.alpha(edge, pick.nearCap() ? 210 : 150));
		p.draw(shape);
		p.setPaint(new GradientPaint(0, 0, Theme.TEAL, 0, height, Theme.alpha(Theme.TEAL, 80)));
		p.fillRect(1, 5, 2, height - 10);

		p.setFont(Theme.small());
		FontMetrics small = p.getFontMetrics();
		String clock = Format.clock(pick.playedMs);
		Ui.shadowed(p, Ui.clip(pick.target.getTitle(), small, WIDTH - 22 - small.stringWidth(clock)), 9, 14, Theme.GOLD);
		Ui.shadowed(p, clock, WIDTH - 8 - small.stringWidth(clock), 14, Theme.TEXT_MUTED);

		p.setFont(Theme.title());
		long rate = pick.perHour();
		String big = rate == 0 ? Format.signed(pick.profit) : Format.signed(rate);
		Ui.shadowed(p, big, 9, 32, rate < 0 || pick.profit < 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		int after = 9 + p.getFontMetrics().stringWidth(big) + 4;
		p.setFont(Theme.small());
		Ui.shadowed(p, rate == 0 ? "so far" : "an hour", after, 32, Theme.TEXT_MUTED);

		String picks = pick.attempts == 0 ? "No picks yet"
			: Math.round(pick.successRate() * 100) + "% of " + pick.attempts + ", " + pick.stuns
			+ (pick.stuns == 1 ? " stun" : " stuns");
		Ui.shadowed(p, Ui.clip(picks, small, WIDTH - 16), 9, 45, Theme.TEXT_MUTED);

		if (pick.cap > 0)
		{
			String count = "Pouches " + pick.pouches + "/" + pick.cap;
			Color tone = pick.full() ? Theme.BLOOD : pick.nearCap() ? Theme.EMBER : Theme.TEXT;
			Ui.shadowed(p, count, 9, 58, tone);
			if (pick.nearCap())
			{
				String say = pick.full() ? "open now" : "open soon";
				Ui.shadowed(p, say, WIDTH - 8 - small.stringWidth(say), 58, tone);
			}
			int x = 9;
			int w = WIDTH - 18;
			p.setColor(Theme.alpha(Theme.STROKE, 220));
			p.fillRoundRect(x, 61, w, 4, 4, 4);
			double share = Math.max(0, Math.min(1, pick.pouches / (double) pick.cap));
			int fill = (int) Math.round(w * share);
			if (fill > 0)
			{
				Color bar = pick.full() ? Theme.BLOOD : pick.nearCap() ? Theme.EMBER : Theme.GOLD;
				p.setPaint(new GradientPaint(x, 0, Theme.mix(bar, Theme.INK, 0.25), x + w, 0, bar));
				p.fillRoundRect(x, 61, Math.max(4, fill), 4, 4, 4);
			}
		}
		p.dispose();
	}
}
