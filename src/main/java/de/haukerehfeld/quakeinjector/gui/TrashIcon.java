package de.haukerehfeld.quakeinjector.gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A trash can, drawn in the component's text color, so it follows the theme and the enabled state.
 */
public class TrashIcon implements Icon {
	private final int size;

	public TrashIcon(int size) {
		this.size = size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setColor(c.isEnabled() ? c.getForeground() : javax.swing.UIManager.getColor("Button.disabledText"));
			g2.translate(x, y);
			float s = size / 24f;
			g2.scale(s, s);
			g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			// handle and lid
			g2.draw(new RoundRectangle2D.Float(9, 2.5f, 6, 3, 1.5f, 1.5f));
			g2.draw(new Line2D.Float(3.5f, 5.5f, 20.5f, 5.5f));
			// can
			Path2D can = new Path2D.Float();
			can.moveTo(5.5f, 5.5f);
			can.lineTo(6.8f, 20f);
			can.quadTo(7f, 21.5f, 8.5f, 21.5f);
			can.lineTo(15.5f, 21.5f);
			can.quadTo(17f, 21.5f, 17.2f, 20f);
			can.lineTo(18.5f, 5.5f);
			g2.draw(can);
			// ribs
			g2.draw(new Line2D.Float(10f, 9.5f, 10f, 17.5f));
			g2.draw(new Line2D.Float(14f, 9.5f, 14f, 17.5f));
		} finally {
			g2.dispose();
		}
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}
}
