package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.SvgIcon;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SVG icon: drawn from the resource, in the component's color, sharp with display scaling.
 */
public class SvgIconTest {
	private static final Color RED = new Color(0xFF0000);

	/** Paints the icon at the given scale; returns the pixels. */
	private static BufferedImage paint(SvgIcon icon, JLabel component, double scale) {
		int pixels = (int) Math.ceil(icon.getIconWidth() * scale);
		BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.scale(scale, scale);
			icon.paintIcon(component, g, 0, 0);
		} finally {
			g.dispose();
		}
		return image;
	}

	private static JLabel component(Color foreground) {
		JLabel label = new JLabel();
		label.setForeground(foreground);
		return label;
	}

	@Test
	public void hasTheGivenSize() {
		SvgIcon icon = new SvgIcon("/delete_icon.svg", 31);
		assertEquals(31, icon.getIconWidth());
		assertEquals(31, icon.getIconHeight());
	}

	@Test
	public void drawsTheShapeInTheComponentColor() {
		BufferedImage image = paint(new SvgIcon("/delete_icon.svg", 24), component(RED), 1);

		int opaque = 0;
		int transparent = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0) {
					transparent++;
				} else if (alpha == 255) {
					opaque++;
					assertEquals(RED.getRGB(), argb, "pixel " + x + "," + y + " in the component color");
				}
			}
		}
		assertTrue(opaque > 30, "the shape is drawn: " + opaque);
		assertTrue(transparent > 100, "the background stays clear: " + transparent);
	}

	@Test
	public void drawsAtTheScreenResolution() {
		SvgIcon icon = new SvgIcon("/delete_icon.svg", 24);
		BufferedImage image = paint(icon, component(RED), 2);

		// the trash can's ribs are thin lines; at twice the size they have twice as many full pixels,
		// which a 24 pixel image stretched to 48 wouldn't have with sharp edges
		int opaque = 0;
		int partly = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getRGB(x, y) >>> 24;
				if (alpha == 255) {
					opaque++;
				} else if (alpha > 0) {
					partly++;
				}
			}
		}
		assertTrue(opaque > 4 * partly, "sharp edges: " + opaque + " full, " + partly + " partly drawn pixels");
	}

	@Test
	public void missingResourceFails() {
		assertThrows(IllegalArgumentException.class, () -> new SvgIcon("/no_such_icon.svg", 24));
	}
}
