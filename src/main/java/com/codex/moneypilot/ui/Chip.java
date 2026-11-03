package com.codex.moneypilot.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** A small inked pill: where a figure came from, or how a method fits. */
public class Chip extends JComponent
{
	private final String text;
	private final Color color;

	public Chip(String text, Color color)
	{
		this.text = text.toUpperCase();
		this.color = color;
		setOpaque(false);
		setFont(Theme.small());
		FontMetrics fm = getFontMetrics(getFont());
		Dimension d = new Dimension(fm.stringWidth(this.text) + 10, 14);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h);
		g.setColor(Theme.alpha(color, 34));
		g.fill(shape);
		g.setColor(Theme.alpha(color, 150));
		g.draw(shape);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(color);
		g.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent()) / 2 - 1);
		g.dispose();
	}
}
