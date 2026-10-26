package de.haukerehfeld.quakeinjector.gui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A large title, wrapping onto several lines and centered, on a background image. The image fills the whole
 * area (cut off where its shape differs) and is darkened, and the text has a shadow, so it stays readable.
 * Without an image the background is plain dark.
 */
public class TitleBanner extends JComponent {
	public static final int HEIGHT = 270;
	private static final int PADDING = 16;
	private static final int SHADOW_OFFSET = 2;
	/** The title's size relative to the normal font. */
	private static final float FONT_SCALE = 3f;

	private static final Color NO_IMAGE = new Color(0x2B2B2B);
	/** Only slightly darker: most screenshots are rather dark already. */
	private static final Color OVERLAY = new Color(0, 0, 0, 60);
	private static final Color SHADOW = new Color(0, 0, 0, 180);
	private static final Color TEXT = Color.WHITE;

	private String title = "";
	private Image image;

	public TitleBanner() {
		setOpaque(true);
		setFont(UIManager.getFont("Label.font"));
	}

	public void setTitle(String title) {
		this.title = title != null ? title : "";
		repaint();
	}

	/** Screen readers read the title, like a label. A plain JComponent has no accessible context. */
	@Override
	public AccessibleContext getAccessibleContext() {
		if (accessibleContext == null) {
			accessibleContext = new AccessibleJComponent() {
				@Override
				public String getAccessibleName() {
					return title;
				}

				@Override
				public AccessibleRole getAccessibleRole() {
					return AccessibleRole.LABEL;
				}
			};
		}
		return accessibleContext;
	}

	public String getTitle() {
		return title;
	}

	/** The background image, or null for a plain background. */
	public void setImage(Image image) {
		this.image = image;
		repaint();
	}

	public Image getImage() {
		return image;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(2 * PADDING, HEIGHT);
	}

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			int width = getWidth();
			int height = getHeight();
			if (image != null) {
				paintImage(g, width, height);
				g.setColor(OVERLAY);
			}
			else {
				g.setColor(NO_IMAGE);
			}
			g.fillRect(0, 0, width, height);
			paintTitle(g, width, height);
		}
		finally {
			g.dispose();
		}
	}

	/** Scales the image to cover the whole area, centered, cutting off what sticks out. */
	private void paintImage(Graphics2D g, int width, int height) {
		int imageWidth = image.getWidth(null);
		int imageHeight = image.getHeight(null);
		if (imageWidth <= 0 || imageHeight <= 0) {
			return;
		}
		double scale = Math.max((double) width / imageWidth, (double) height / imageHeight);
		int drawnWidth = (int) Math.ceil(imageWidth * scale);
		int drawnHeight = (int) Math.ceil(imageHeight * scale);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(image, (width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight, null);
	}

	private void paintTitle(Graphics2D g, int width, int height) {
		if (title.isBlank()) {
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font font = getFont().deriveFont(getFont().getSize2D() * FONT_SCALE);
		FontMetrics metrics = g.getFontMetrics(font);
		List<String> lines = wrap(title, metrics, Math.max(1, width - 2 * PADDING));

		g.setFont(font);
		int lineHeight = metrics.getHeight();
		int y = (height - lines.size() * lineHeight) / 2 + metrics.getAscent();
		for (String line : lines) {
			int x = (width - metrics.stringWidth(line)) / 2;
			g.setColor(SHADOW);
			g.drawString(line, x + SHADOW_OFFSET, y + SHADOW_OFFSET);
			g.setColor(TEXT);
			g.drawString(line, x, y);
			y += lineHeight;
		}
	}

	/**
	 * Breaks the text into lines at spaces so each line is at most {@code maxWidth} wide; a single word that is
	 * wider gets a line of its own.
	 */
	public static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (line.length() == 0) {
				line.append(word);
			}
			else if (metrics.stringWidth(line + " " + word) <= maxWidth) {
				line.append(' ').append(word);
			}
			else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}
}
