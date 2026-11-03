package com.codex.moneypilot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/** A drawn tick, cross or question for one need; the game's font has no such glyphs. */
public class Mark extends JComponent
{
	public enum Kind
	{
		MET,
		UNMET,
		UNSURE,
		SUGGESTED
	}

	private final Kind kind;

	public Mark(Kind kind)
	{
		this.kind = kind;
		setOpaque(false);
		Dimension d = new Dimension(12, 12);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	public static Color colour(Kind kind)
	{
		switch (kind)
		{
			case MET:
				return Theme.GREEN;
			case UNMET:
				return Theme.BLOCKED;
			case UNSURE:
				return Theme.GOLD;
			default:
				return Theme.EMBER;
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		Color c = colour(kind);
		float cy = getHeight() / 2f;
		g.setColor(Theme.alpha(c, 40));
		g.fill(new Ellipse2D.Float(0.5f, cy - 5.5f, 11, 11));
		g.setColor(Theme.alpha(c, 170));
		g.draw(new Ellipse2D.Float(0.5f, cy - 5.5f, 11, 11));
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c);
		switch (kind)
		{
			case MET:
			{
				Path2D tick = new Path2D.Float();
				tick.moveTo(3.4f, cy);
				tick.lineTo(5.3f, cy + 2f);
				tick.lineTo(8.8f, cy - 2.4f);
				g.draw(tick);
				break;
			}
			case UNMET:
				g.draw(new Line2D.Float(4f, cy - 2f, 8f, cy + 2f));
				g.draw(new Line2D.Float(8f, cy - 2f, 4f, cy + 2f));
				break;
			case UNSURE:
				g.fill(new Ellipse2D.Float(5.2f, cy + 1.4f, 1.8f, 1.8f));
				g.draw(new Line2D.Float(6.1f, cy - 2.6f, 6.1f, cy - 0.2f));
				break;
			default:
				g.fill(new Ellipse2D.Float(4.4f, cy - 1.6f, 3.2f, 3.2f));
				break;
		}
		g.dispose();
	}
}
