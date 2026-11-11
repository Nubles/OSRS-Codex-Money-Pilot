package com.codex.moneypilot.ui;

import static org.junit.Assert.assertEquals;
import com.codex.moneypilot.core.Methods;
import com.codex.moneypilot.core.Pickpockets;
import com.codex.moneypilot.core.Stalls;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JComponent;
import org.junit.Test;

/**
 * Drawing never leaves the graphics it was handed changed: RuneLite draws every overlay with one graphics, so a
 * plaque that cleared its clip (setClip(null)) or kept a transform, composite, stroke or font would spoil the
 * rest of the frame. Each piece is drawn into a graphics with a clip set, and everything is checked after.
 */
public class ClipTest
{
	private static void unchanged(String what, Consumer<Graphics2D> draw)
	{
		unchanged(what, draw, true);
	}

	/**
	 * @param all also the font and colour; Swing's own paint sets those on a panel's graphics, as it does for every
	 *            component, so only the clip, transform, stroke and composite are a panel piece's to keep
	 */
	private static void unchanged(String what, Consumer<Graphics2D> draw, boolean all)
	{
		BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.translate(7, 5);
		g.setClip(new Rectangle(3, 4, 180, 90));
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		g.setStroke(new BasicStroke(2f));
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
		g.setColor(Color.MAGENTA);
		Shape clip = g.getClip();
		AffineTransform transform = g.getTransform();
		Font font = g.getFont();
		Stroke stroke = g.getStroke();
		Composite composite = g.getComposite();
		Color color = g.getColor();
		draw.accept(g);
		assertEquals(what + " clip", clip, g.getClip());
		assertEquals(what + " transform", transform, g.getTransform());
		assertEquals(what + " stroke", stroke, g.getStroke());
		assertEquals(what + " composite", composite, g.getComposite());
		if (all)
		{
			assertEquals(what + " font", font, g.getFont());
			assertEquals(what + " colour", color, g.getColor());
		}
		g.dispose();
	}

	private static void component(String what, JComponent c, int w, int h)
	{
		c.setSize(w, h);
		c.doLayout();
		unchanged(what, c::paint, false);
	}

	@Test
	public void cardsAndBarsRestoreTheClip()
	{
		Card card = new Card(new BorderLayout()).accent(Theme.TEAL).highlighted(true);
		component("card", card, 180, 60);
		component("bar", SplitBar.progress(0.6, Theme.GOLD), 150, 8);
		component("parts", new SplitBar(8, new double[]{0.3, 0.5, 0.2}, new Color[]{Theme.GOLD, Theme.TEAL}), 150, 8);
	}

	@Test
	public void plaquesLeaveTheOverlayGraphicsAlone()
	{
		View.Live live = new View.Live(Methods.byId("blood-runes"), 38 * 60_000L, 1_930_000, true);
		unchanged("run plaque", g -> RunPlaque.paint(g, live));
		unchanged("run plaque, logged out", g -> RunPlaque.paint(g, new View.Live(Methods.byId("vorkath"), 0, -5, false)));
		unchanged("pickpocket plaque", g -> PickpocketPlaque.paint(g, new View.Picking(Pickpockets.Target.KNIGHTS, 400, 380,
			20, 20 * 60_000L, 250_000, 26, 28, 3)));
		unchanged("chest plaque", g -> ChestPlaque.paint(g, new View.Looting(40, 1, 0, 10 * 60_000L, 400_000, 30_000, 4, 2,
			12, 900_000, 3)));
		unchanged("stall plaque", g -> StallPlaque.paint(g, new View.Stealing(Stalls.Rotation.SILVER, null, 12, 1,
			5 * 60_000L, 9_000, 2_000)));
	}
}
