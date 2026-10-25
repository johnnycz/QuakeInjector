package de.haukerehfeld.quakeinjector.gui;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * A filled "play" triangle, drawn in the component's text color, so it follows the theme and the enabled state.
 */
public class PlayIcon implements Icon {
	private final int size;

	public PlayIcon(int size) {
		this.size = size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.isEnabled() ? c.getForeground() : UIManager.getColor("Button.disabledText"));
			g2.translate(x, y);
			float s = size / 24f;
			g2.scale(s, s);

			Path2D triangle = new Path2D.Float();
			triangle.moveTo(6, 3);
			triangle.lineTo(21, 12);
			triangle.lineTo(6, 21);
			triangle.closePath();
			g2.fill(triangle);
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
