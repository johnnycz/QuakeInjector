package de.haukerehfeld.quakeinjector.gui;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;

/**
 * Swing shows the text of labels, buttons, menu items, table cells and dialogs as HTML when it starts
 * with {@code <html>}. Text from the package database or a package (titles, ids, file names) must not
 * be, because the HTML view loads images and more (see {@code utils.HtmlSanitizer}). These helpers
 * make components that always show their text as it is.
 * <p>
 * The switch ({@code html.disable}) is read when the text is set, so it has to be on before that.
 */
final class PlainText {
	static final String HTML_DISABLE = "html.disable";

	private PlainText() {
	}

	/** Never show this component's text as HTML; call before setting the text. */
	static <T extends JComponent> T disableHtml(T component) {
		component.putClientProperty(HTML_DISABLE, Boolean.TRUE);
		return component;
	}

	static JLabel label(String text) {
		JLabel label = disableHtml(new JLabel());
		label.setText(text);
		return label;
	}

	static JMenuItem menuItem(String text) {
		JMenuItem item = disableHtml(new JMenuItem());
		item.setText(text);
		return item;
	}

	static JCheckBox checkBox(String text) {
		JCheckBox box = disableHtml(new JCheckBox());
		box.setText(text);
		return box;
	}

	/**
	 * A dialog message that is never shown as HTML: one label per line, like JOptionPane makes for
	 * a String. Anything that isn't a String is returned as is.
	 */
	static Object message(Object message) {
		if (!(message instanceof String text)) {
			return message;
		}
		Box lines = Box.createVerticalBox();
		for (String line : text.split("\n", -1)) {
			// an empty label has no height, so an empty line wouldn't show
			lines.add(label(line.isEmpty() ? " " : line));
		}
		return lines;
	}
}
