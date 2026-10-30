package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.utils.HtmlSanitizer;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.*;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * The date and size of the selected package, its authors and its description, scrolling together. The
 * screenshot is behind the title ({@link PackageHeaderView}).
 */
public class PackageDescriptionView extends JPanel implements ChangeListener {

	/** Shown instead of an empty text, so the row keeps the height of one line. */
	private static final String BLANK = " ";
	private final PackageInteractionViewModel vm;

	private final JLabel date = centeredLabel();
	private final JLabel size = centeredLabel();
	private final JEditorPane authors = centeredText();

	private final ScrollablePanel content;

	private final JEditorPane description;

	public PackageDescriptionView(PackageInteractionViewModel vm) {
		super(new BorderLayout());

		this.vm = vm;
		vm.addChangeListener(this);
		content = new ScrollablePanel(50, 50) {{
			setLayout(new GridBagLayout());
		}};
		content.setOpaque(false);

		description = new JEditorPane("text/html", "");
		description.setEditable(false);
		description.addHyperlinkListener(new HyperlinkListener() {
			@Override public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
					// the href as written (getURL() is null for the relative package links)
					vm.openDescriptionLink(e.getDescription());
				}
			}
		});
		content.add(detailsPanel(), new GridBagConstraints() {{
			gridy = 0;
			weightx = 1;
			fill = HORIZONTAL;
			anchor = PAGE_START;
		}});

		content.add(description, new GridBagConstraints() {{
			gridy = 1;
			weightx = 1;
			weighty = 1;
			fill = HORIZONTAL;
			anchor = PAGE_START;
		}});

		//Put the editor pane in a scroll pane.
		JScrollPane descriptionScroll = new JScrollPane(content);
		descriptionScroll.getViewport().setBackground(javax.swing.UIManager.getColor("TextPane.background"));
		descriptionScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants
				.VERTICAL_SCROLLBAR_ALWAYS);

		add(descriptionScroll, BorderLayout.CENTER);
	}


	/** Date and size side by side, and the authors below them (wrapping when there are many). */
	private JPanel detailsPanel() {
		JPanel details = new JPanel(new GridBagLayout());
		details.setOpaque(false);
		details.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JPanel dateAndSize = new JPanel(new GridLayout(1, 2));
		dateAndSize.setOpaque(false);
		dateAndSize.add(date);
		dateAndSize.add(size);
		details.add(dateAndSize, new GridBagConstraints() {{
			weightx = 1;
			fill = HORIZONTAL;
		}});

		details.add(authors, new GridBagConstraints() {{
			gridy = 1;
			weightx = 1;
			fill = HORIZONTAL;
			insets = new Insets(2, 0, 0, 0);
		}});
		return details;
	}

	/** An HTML pane like the description, so it gets the same font and color. */
	private static JEditorPane centeredText() {
		JEditorPane text = new JEditorPane("text/html", "");
		text.setEditable(false);
		text.setOpaque(false);
		text.setBorder(null);
		return text;
	}

	private static void setCenteredText(JEditorPane pane, String text) {
		// a non-breaking space keeps the height of one line when there's no text
		pane.setText("<div align=\"center\">" + (text.isBlank() ? "&nbsp;" : escapeHtml(text)) + "</div>");
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static JLabel centeredLabel() {
		JLabel label = new JLabel();
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
	}

	private void refreshUi() {
		boolean selected = !vm.getPackageId().isEmpty();
		date.setText(orBlank(toString(vm.getDate())));
		size.setText(selected ? vm.getSize() / 1000f + " MB" : BLANK);
		String author = vm.getAuthor();
		setCenteredText(authors, author.isEmpty() ? BLANK : "by " + author);

		description.getEditorKit().createDefaultDocument();
		description.setText(vm.getDescription()
				+ toString(vm.getRequirements()) + "<p></p>");
		//scroll to top
		description.setCaretPosition(0);

		revalidate();
		repaint();
	}

	private static String orBlank(String text) {
		return text.isEmpty() ? BLANK : text;
	}

	private static String toString(Date date) {
		if (date == null) {
			return "";
		}
		DateFormat dfm = new SimpleDateFormat("MMM d, yyyy");
		dfm.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
		return dfm.format(date);
	}

	private String toString(java.util.List<PackageInteractionViewModel.Prerequisite> requirements) {
		if (requirements.isEmpty()) {
			return "";
		}
		List<String> links = new ArrayList<String>(requirements.size());
		for (PackageInteractionViewModel.Prerequisite r: requirements) {
			// names come from the database: escaped. A package links by its SHA-256, so clicking selects it;
			// official expansions and unknown prerequisites aren't packages: just their name
			String name = HtmlSanitizer.escape(r.name());
			links.add(r.isLinked() ? "<a href=\"" + r.sha256() + "\">" + name + "</a>" : name);
		}
		return "<p>Requires: " + Utils.join(links, ", ") + ".</p>";
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageDescriptionView");
		refreshUi();
	}
}
