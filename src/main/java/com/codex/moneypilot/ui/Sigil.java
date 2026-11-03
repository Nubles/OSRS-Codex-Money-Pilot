package com.codex.moneypilot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The Money Pilot mark: a stack of gold coins with a compass needle laid across the top coin, its
 * teal point set toward what pays.
 */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		// Coins under the top one, seen edge on.
		for (int i = 2; i >= 1; i--)
		{
			float cy = y + (9.6f + i * 1.7f) * s;
			Ellipse2D rim = new Ellipse2D.Float(x + 1.6f * s, cy - 2.2f * s, 12.8f * s, 4.6f * s);
			g.setPaint(new GradientPaint(x, cy - 2 * s, Theme.GOLD, x, cy + 2.4f * s, Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.3)));
			g.fill(rim);
			g.setStroke(new BasicStroke(Math.max(0.5f, 0.45f * s)));
			g.setColor(Theme.alpha(Theme.INK_DEEP, 170));
			g.draw(rim);
		}
		// The top coin, face up.
		float r = 6.4f * s;
		float cx = x + 8 * s;
		float cy = y + 7.6f * s;
		Ellipse2D face = new Ellipse2D.Float(cx - r, cy - r * 0.78f, 2 * r, 2 * r * 0.78f);
		g.setPaint(new RadialGradientPaint(cx - r * 0.3f, cy - r * 0.4f, r * 1.4f, new float[]{0, 1},
			new Color[]{Theme.GOLD_BRIGHT, Theme.mix(Theme.GOLD, Theme.GOLD_DIM, 0.5)}));
		g.fill(face);
		g.setStroke(new BasicStroke(Math.max(0.6f, 0.6f * s)));
		g.setColor(Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.35));
		g.draw(face);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 70));
		float ir = r * 0.72f;
		g.draw(new Ellipse2D.Float(cx - ir, cy - ir * 0.78f, 2 * ir, 2 * ir * 0.78f));

		// The needle: a teal point up and to the right, an ink tail down and to the left.
		float len = r * 0.95f;
		float dx = 0.72f;
		float dy = -0.56f;
		float px = -dy * 1.2f * s;
		float py = dx * 1.2f * s;
		Path2D point = new Path2D.Float();
		point.moveTo(cx + dx * len, cy + dy * len);
		point.lineTo(cx + px, cy + py);
		point.lineTo(cx - px, cy - py);
		point.closePath();
		g.setPaint(new GradientPaint(cx, cy, Theme.mix(Theme.TEAL, Theme.INK_DEEP, 0.2), cx + dx * len, cy + dy * len,
			Theme.mix(Theme.TEAL, Color.WHITE, 0.4)));
		g.fill(point);
		Path2D tail = new Path2D.Float();
		tail.moveTo(cx - dx * len * 0.8f, cy - dy * len * 0.8f);
		tail.lineTo(cx + px, cy + py);
		tail.lineTo(cx - px, cy - py);
		tail.closePath();
		g.setColor(Theme.mix(Theme.INK_DEEP, Theme.GOLD_DIM, 0.25));
		g.fill(tail);
		g.setColor(Theme.GOLD_BRIGHT);
		float hub = Math.max(0.8f, 0.9f * s);
		g.fill(new Ellipse2D.Float(cx - hub, cy - hub, 2 * hub, 2 * hub));
	}
}
