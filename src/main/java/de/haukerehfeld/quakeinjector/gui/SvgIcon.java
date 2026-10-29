package de.haukerehfeld.quakeinjector.gui;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.net.URL;

/**
 * A single-color SVG from the resources, drawn in the component's text color, so it follows the theme and
 * the enabled state. Only the shape of the SVG is used, not its colors.
 * <p>
 * Drawn at the screen's resolution, so it stays sharp with display scaling.
 */
public class SvgIcon implements Icon {
	private final SVGDiagram diagram;
	private final int size;

	/**
	 * @param resource the path of the SVG in the resources, e.g. {@code /delete_icon.svg}
	 * @param size the width and height in (unscaled) pixels
	 */
	public SvgIcon(String resource, int size) {
		URL url = SvgIcon.class.getResource(resource);
		if (url == null) {
			throw new IllegalArgumentException("Missing resource " + resource);
		}
		SVGUniverse universe = new SVGUniverse();
		this.diagram = universe.getDiagram(universe.loadSVG(url));
		if (diagram == null) {
			throw new IllegalArgumentException("Can't read SVG " + resource);
		}
		diagram.setIgnoringClipHeuristic(true);
		this.size = size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			// the screen's scale, so the image has one pixel per screen pixel
			AffineTransform t = g2.getTransform();
			double scaleX = Math.max(1, Math.hypot(t.getScaleX(), t.getShearY()));
			double scaleY = Math.max(1, Math.hypot(t.getShearX(), t.getScaleY()));
			int w = (int) Math.ceil(size * scaleX);
			int h = (int) Math.ceil(size * scaleY);

			BufferedImage image = render(w, h, color(c));
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(image, x, y, size, size, null);
		} finally {
			g2.dispose();
		}
	}

	/** The SVG's shape, filled with the color, w x h pixels. */
	private BufferedImage render(int w, int h, Color color) {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D ig = image.createGraphics();
		try {
			ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			ig.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			ig.scale(w / (double) diagram.getWidth(), h / (double) diagram.getHeight());
			diagram.render(ig);
			// keep the shape, replace its color
			ig.setTransform(new AffineTransform());
			ig.setComposite(AlphaComposite.SrcIn);
			ig.setColor(color);
			ig.fillRect(0, 0, w, h);
		} catch (SVGException e) {
			throw new IllegalStateException("Can't draw SVG", e);
		} finally {
			ig.dispose();
		}
		return image;
	}

	private static Color color(Component c) {
		if (c == null) {
			return Color.BLACK;
		}
		if (!c.isEnabled()) {
			Color disabled = UIManager.getColor("Button.disabledText");
			if (disabled != null) {
				return disabled;
			}
		}
		return c.getForeground();
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
