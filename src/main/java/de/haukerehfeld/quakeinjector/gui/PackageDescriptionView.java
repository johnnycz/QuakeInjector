package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * The screenshot of the selected package, its date and size, its authors and its description, scrolling
 * together.
 */
public class PackageDescriptionView extends JPanel implements ChangeListener {

	public static final Dimension DEFAULTIMAGESIZE = new Dimension(480, 360);
	/** Shown instead of an empty text, so the row keeps the height of one line. */
	private static final String BLANK = " ";
	private final PackageInteractionViewModel vm;

	private final JLabel date = centeredLabel();
	private final JLabel size = centeredLabel();
	private final JEditorPane authors = centeredText();

	private final ScrollablePanel content;

	private final JLabel image;
	private final JPanel imagePanel;

	private final JEditorPane description;

	private boolean imageDisplayed = false;

	public PackageDescriptionView(PackageInteractionViewModel vm) {
		super(new BorderLayout());

		this.vm = vm;
		vm.addChangeListener(this);
		content = new ScrollablePanel(50, 50) {{
			setLayout(new GridBagLayout());
		}};
		content.setOpaque(false);

		imagePanel = new JPanel();
		imagePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		imagePanel.setOpaque(true);
		imagePanel.setBackground(java.awt.Color.DARK_GRAY);

		imagePanel.setPreferredSize(DEFAULTIMAGESIZE);
		imagePanel.setMinimumSize(DEFAULTIMAGESIZE);
		//imagePanel.setSize(DEFAULTIMAGESIZE);

		image = new JLabel();
		EmptyBorder border = new EmptyBorder(0,0,0,0);
		image.setBorder(border);
		image.setHorizontalAlignment(SwingConstants.CENTER);
		image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		image.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				vm.openScreenshotInBrowser();
			}
		});

		imagePanel.add(image);



		description = new JEditorPane("text/html", "");
		description.setEditable(false);
		description.addHyperlinkListener(new HyperlinkListener() {
			@Override public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
					java.net.URL url = e.getURL();
					if (url != null) {
						BrowserLauncher.openURL(url.toString());
					}
					else {
						System.err.println("Weird hyperlink with null URL: " + e.getDescription());
						String link = "https://www.quaddicted.com/reviews/" + e.getDescription();
						BrowserLauncher.openURL(link);
					}
				}
			}
		});
		content.add(detailsPanel(), new GridBagConstraints() {{
			gridy = 1;
			weightx = 1;
			fill = HORIZONTAL;
			anchor = PAGE_START;
		}});

		content.add(description, new GridBagConstraints() {{
			gridy = 2;
			weightx = 1;
			weighty = 0;
			fill = BOTH;
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

	private void addImage() {
		content.add(imagePanel, new GridBagConstraints() {{
			gridy = 0;
			weightx = 1;
			weighty = 1;
			fill = NONE;
			anchor = CENTER;
		}});
		imageDisplayed = true;
	}

	private void removeImage() {
		content.remove(imagePanel);
		imageDisplayed = false;
	}

	private void refreshUi() {
		boolean selected = !vm.getPackageId().isEmpty();
		date.setText(orBlank(toString(vm.getDate())));
		size.setText(selected ? vm.getSize() / 1000f + " MB" : BLANK);
		String author = vm.getAuthor();
		setCenteredText(authors, author.isEmpty() ? BLANK : "by " + author);

		image.setIcon(null);

		URI screenshotUri = vm.getScreenshotUri();
		if (screenshotUri == null) {
			// nothing selected, or no screenshot
			if (imageDisplayed) {
				removeImage();
			}
		}
		else {
			if (!imageDisplayed) {
				addImage();
			}
			loadScreenshot(screenshotUri, vm.getTitle());
		}

		description.getEditorKit().createDefaultDocument();
		description.setText(vm.getDescription()
				+ toString(vm.getRequirements()) + "<p></p>");
		//scroll to top
		description.setCaretPosition(0);

		revalidate();
		repaint();
	}

	/** Loads the screenshot in the background and shows it, unless another package got selected meanwhile. */
	private void loadScreenshot(URI screenshotUri, String imageTitle) {
		new SwingWorker<ImageIcon,Void>() {
			@Override
			public ImageIcon doInBackground() {
				try {
					URL cleanUrl = new URL(screenshotUri.toASCIIString());
					return new ImageIcon(new ImageIcon(cleanUrl, imageTitle).getImage()
						.getScaledInstance(DEFAULTIMAGESIZE.width, DEFAULTIMAGESIZE.height, Image.SCALE_SMOOTH));
				}
				catch (MalformedURLException e) {
					return null;
				}
			}
			@Override
			public void done() {
				// the selection may have changed while loading
				if (isCancelled() || !screenshotUri.equals(vm.getScreenshotUri())) {
					return;
				}

				ImageIcon icon;
				try {
					icon = get();
				}
				catch (java.lang.InterruptedException e) {
					icon = null;
				}
				catch (java.util.concurrent.ExecutionException e) {
					icon = null;
				}

				if (icon == null || (icon.getImageLoadStatus() & java.awt.MediaTracker.COMPLETE) == 0) {
					if (imageDisplayed) {
						removeImage();
					}
					System.err.println("Couldn't load image " + screenshotUri);
				}
				else {
					image.setIcon(icon);
					image.setMinimumSize(DEFAULTIMAGESIZE);
				}

				revalidate();
				repaint();
			}
		}.execute();
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

	private String toString(java.util.List<String> requirements) {
		if (requirements.isEmpty()) {
			return "";
		}
		List<String> links = new ArrayList<String>(requirements.size());
		for (String r: requirements) {
			links.add("<a href=\"" + r + ".html\">" + r + "</a>");
		}
		return "<p>Requires: " + Utils.join(links, ", ") + ".</p>";
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageDescriptionView");
		refreshUi();
	}
}
