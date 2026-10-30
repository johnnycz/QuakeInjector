package de.haukerehfeld.quakeinjector.gui;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.theme.IntelliJTheme;
import org.junit.jupiter.api.Test;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.Component;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text from the database must never be shown as HTML. Swing keeps the HTML view of a component's text in
 * a client property; without it, the text is shown as it is. Headless, on the event thread.
 */
public class PlainTextTest {
	private static final String HTML = "<html><img src=\"file://attacker/share/x.png\">";

	private static boolean rendersHtml(JComponent c) {
		return c.getClientProperty(BasicHTML.propertyKey) != null;
	}

	private static void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void aNormalLabelWouldShowHtml() throws Exception {
		// makes sure the checks below can fail
		onEdt(() -> assertTrue(rendersHtml(new JLabel(HTML))));
	}

	@Test
	public void componentsShowTheirTextAsIs() throws Exception {
		onEdt(() -> {
			assertFalse(rendersHtml(PlainText.label(HTML)));
			assertFalse(rendersHtml(PlainText.menuItem(HTML)));
			assertFalse(rendersHtml(PlainText.checkBox(HTML)));
			assertEquals(HTML, PlainText.label(HTML).getText());

			JLabel later = PlainText.disableHtml(new JLabel());
			later.setText(HTML);
			assertFalse(rendersHtml(later));
		});
	}

	@Test
	public void dialogMessagesAreOneLabelPerLine() throws Exception {
		onEdt(() -> {
			Box message = (Box) PlainText.message(HTML + "\n\nsecond line");

			assertEquals(3, message.getComponentCount());
			for (Component line : message.getComponents()) {
				assertFalse(rendersHtml((JComponent) line));
			}
			assertEquals("second line", ((JLabel) message.getComponent(2)).getText());
			Object notAString = new Object();
			assertSame(notAString, PlainText.message(notAString));
		});
	}

	@Test
	public void alsoWithDarklaf() throws Exception {
		LookAndFeel before = UIManager.getLookAndFeel();
		try {
			onEdt(() -> {
				LafManager.install(new IntelliJTheme());
				assertTrue(rendersHtml(new JLabel(HTML)), "Darklaf renders HTML in labels too");
				assertFalse(rendersHtml(PlainText.label(HTML)));
				assertFalse(rendersHtml(PlainText.menuItem(HTML)));
				assertFalse(rendersHtml(PlainText.checkBox(HTML)));
			});
		}
		finally {
			onEdt(() -> {
				try {
					UIManager.setLookAndFeel(before);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
	}
}
