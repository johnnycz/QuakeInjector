package de.haukerehfeld.quakeinjector.gui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
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
		List<TextLayout> lines = lines(title, font, g.getFontRenderContext(), Math.max(1, width - 2 * PADDING));

		float textHeight = 0;
		for (TextLayout line : lines) {
			textHeight += line.getAscent() + line.getDescent() + line.getLeading();
		}
		float y = (height - textHeight) / 2;
		for (TextLayout line : lines) {
			y += line.getAscent();
			// the visible advance leaves out the space at the end of a line
			float x = (width - line.getVisibleAdvance()) / 2;
			g.setColor(SHADOW);
			line.draw(g, x + SHADOW_OFFSET, y + SHADOW_OFFSET);
			g.setColor(TEXT);
			line.draw(g, x, y);
			y += line.getDescent() + line.getLeading();
		}
	}

	/**
	 * The text broken into lines at most {@code width} wide, by Java's own line breaking (between words where
	 * possible; a word wider than the width is split).
	 */
	public static List<TextLayout> lines(String text, Font font, FontRenderContext context, float width) {
		List<TextLayout> lines = new ArrayList<>();
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return lines;
		}
		AttributedString attributed = new AttributedString(trimmed);
		attributed.addAttribute(TextAttribute.FONT, font);
		AttributedCharacterIterator characters = attributed.getIterator();
		LineBreakMeasurer measurer = new LineBreakMeasurer(characters, context);
		while (measurer.getPosition() < characters.getEndIndex()) {
			lines.add(measurer.nextLayout(width));
		}
		return lines;
	}
}
