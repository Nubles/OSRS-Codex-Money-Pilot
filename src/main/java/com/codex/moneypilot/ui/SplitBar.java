package com.codex.moneypilot.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * A slim inked bar split into coloured parts: how far the coins go toward a goal, or what share of the
 * money each method in a plan brings in. Parts are drawn left to right; what is left is the empty groove.
 */
public class SplitBar extends JComponent
{
	private final double[] shares;
	private final Color[] colors;

	public SplitBar(int height, double[] shares, Color[] colors)
	{
		this.shares = shares.clone();
		this.colors = colors.clone();
		setOpaque(false);
		setPreferredSize(new Dimension(10, height));
		setMinimumSize(new Dimension(10, height));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	/** A single filled part, for progress. */
	public static SplitBar progress(double progress, Color color)
	{
		return new SplitBar(6, new double[]{Math.max(0, Math.min(1, progress))}, new Color[]{color});
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Shape groove = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, h, h);
		g.setColor(Theme.INK_DEEP);
		g.fill(groove);
		Shape old = g.getClip();
		g.clip(groove);
		double x = 0;
		for (int i = 0; i < shares.length; i++)
		{
			double part = Math.max(0, shares[i]) * (w - 1);
			if (part <= 0)
			{
				continue;
			}
			Color c = colors[i % colors.length];
			g.setPaint(new GradientPaint(0, 0, Theme.mix(c, Color.WHITE, 0.12), 0, h, Theme.mix(c, Theme.INK, 0.25)));
			int from = (int) Math.round(x);
			int to = (int) Math.round(Math.min(w - 1, x + part));
			g.fillRect(from, 0, Math.max(1, to - from), h);
			if (i > 0 && from > 0)
			{
				// A hairline between parts so neighbours of a like colour still read as two.
				g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
				g.drawLine(from, 0, from, h);
			}
			x += part;
		}
		g.setClip(old);
		g.setColor(Theme.STROKE);
		g.draw(groove);
		g.dispose();
	}
}
