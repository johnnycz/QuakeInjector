package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.TitleBanner;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TitleBannerTest {
	private static FontMetrics metrics() {
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try {
			return g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
		}
		finally {
			g.dispose();
		}
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
		assertEquals(List.of("Old Town"), TitleBanner.wrap("Old Town", metrics(), 1000));
	}

	@Test
	public void longTitleWrapsAtSpacesWithinTheWidth() {
		FontMetrics metrics = metrics();
		String title = "Map-Center's Quake 30th Anniversary Jam";
		int maxWidth = metrics.stringWidth("Map-Center's Quake");

		List<String> lines = TitleBanner.wrap(title, metrics, maxWidth);

		assertEquals(List.of("Map-Center's Quake", "30th Anniversary", "Jam"), lines);
		for (String line : lines) {
			assertTrue(metrics.stringWidth(line) <= maxWidth, line);
		}
	}

	@Test
	public void wordWiderThanTheWidthGetsALineOfItsOwn() {
		assertEquals(List.of("a", "Supercalifragilistic", "b"),
			TitleBanner.wrap("a Supercalifragilistic b", metrics(), metrics().stringWidth("a b")));
	}

	@Test
	public void extraSpacesAreIgnored() {
		assertEquals(List.of("Old Town"), TitleBanner.wrap("  Old   Town ", metrics(), 1000));
		assertEquals(List.of(), TitleBanner.wrap("   ", metrics(), 1000));
	}
}
