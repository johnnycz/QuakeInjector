package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.TitleBanner;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TitleBannerTest {
	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
	private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);

	/** The text of each line, without the space a line ends with. */
	private static List<String> lineTexts(String text, float width) {
		List<String> texts = new ArrayList<>();
		String trimmed = text.trim();
		int start = 0;
		for (TextLayout line : TitleBanner.lines(text, FONT, CONTEXT, width)) {
			int end = start + line.getCharacterCount();
			texts.add(trimmed.substring(start, end).trim());
			start = end;
		}
		return texts;
	}

	private static float width(String text) {
		return new TextLayout(text, FONT, CONTEXT).getAdvance();
	}

	@Test
	public void bannerShowsTheTitleWithAndWithoutAnImage() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			TitleBanner banner = new TitleBanner();
			banner.setTitle("Map-Center's Quake 30th Anniversary Jam");
			assertEquals("Map-Center's Quake 30th Anniversary Jam",
				banner.getAccessibleContext().getAccessibleName());

			banner.setSize(300, TitleBanner.HEIGHT);
			paint(banner);
			banner.setImage(new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB));
			paint(banner);

			banner.setTitle(null);
			assertEquals("", banner.getTitle());
			paint(banner);
		});
	}

	private static void paint(TitleBanner banner) {
		BufferedImage target = new BufferedImage(banner.getWidth(), banner.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = target.createGraphics();
		try {
			banner.paint(g);
		}
		finally {
			g.dispose();
		}
	}

	@Test
	public void shortTitleStaysOnOneLine() {
		assertEquals(List.of("Old Town"), lineTexts("Old Town", 1000));
	}

	@Test
	public void longTitleWrapsBetweenWordsWithinTheWidth() {
		float width = width("Map-Center's Quake") + 1;

		assertEquals(List.of("Map-Center's Quake", "30th Anniversary", "Jam"),
			lineTexts("Map-Center's Quake 30th Anniversary Jam", width));
		for (TextLayout line : TitleBanner.lines("Map-Center's Quake 30th Anniversary Jam", FONT, CONTEXT, width)) {
			assertTrue(line.getVisibleAdvance() <= width);
		}
	}

	@Test
	public void spacesAroundTheTitleAreIgnored() {
		assertEquals(List.of("Old Town"), lineTexts("  Old Town ", 1000));
		assertEquals(List.of(), lineTexts("   ", 1000));
	}
}
