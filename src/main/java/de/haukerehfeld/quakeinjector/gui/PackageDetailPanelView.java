package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Style;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class PackageDetailPanelView extends JPanel implements ChangeListener {

	public static final Dimension DEFAULTIMAGESIZE = new Dimension(360, 270);
	private static final Dimension NOIMAGESIZE = new Dimension(100, 500);
	private final PackageInteractionViewModel vm;

	private final JLabel title;
	private final JLabel size;
	private final JLabel date;

	private final ScrollablePanel content;

	private final JLabel image;
	private final JPanel imagePanel;

	private final JEditorPane description;

	private boolean imageDisplayed = false;

	public PackageDetailPanelView(PackageInteractionViewModel vm) {
		super(new GridBagLayout());

		this.vm = vm;
		vm.addChangeListener(this);
		content = new ScrollablePanel(50, 50) {{
			setLayout(new GridBagLayout());
		}};
		content.setOpaque(false);
		//content.setBackground();

		title = new JLabel();
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setOpaque(true);
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent arg0) {
				vm.openPackageWebpage();
			}
		});

		content.add(title, new GridBagConstraints() {{
			weightx = 1;
			weighty = 0;
			fill = BOTH;
			anchor = PAGE_START;
			ipadx = 5;
			ipady = 20;
		}});

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
		content.add(description, new GridBagConstraints() {{
			gridy = 2;
			weightx = 1;
			weighty = 0;
			fill = BOTH;
			anchor = PAGE_START;
		}});

		{
			HTMLEditorKit doc = ((HTMLEditorKit) description.getEditorKit());
			StyleSheet styles = doc.getStyleSheet();

			Enumeration rules = styles.getStyleNames();
			while (rules.hasMoreElements()) {
				String name = (String) rules.nextElement();
				Style rule = styles.getStyle(name);
				//System.out.println(rule.toString());
			}
		}



		//Put the editor pane in a scroll pane.
		JScrollPane descriptionScroll = new JScrollPane(content);
		descriptionScroll.getViewport().setBackground(javax.swing.UIManager.getColor("TextPane.background"));
		descriptionScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants
				.VERTICAL_SCROLLBAR_ALWAYS);

		add(descriptionScroll, new GridBagConstraints() {{
			gridy = 0;
			gridwidth = 2;
			weightx = 1;
			weighty = 1;
			fill = BOTH;
			anchor = PAGE_START;
		}});

		int detailHeight = 20;
		date = new JLabel();
		date.setHorizontalAlignment(SwingConstants.CENTER);
		add(date, new GridBagConstraints() {{
			gridy = 1;
			gridx = 0;
			weightx = 1;
			weighty = 0;
			fill = NONE;
			anchor = CENTER;
			ipadx = 5;
			ipady = 3;
		}});

		size = new JLabel();
		size.setHorizontalAlignment(SwingConstants.CENTER);
		add(size, new GridBagConstraints() {{
			gridy = 1;
			gridx = 1;
			weightx = 1;
			weighty = 0;
			fill = NONE;
			anchor = CENTER;
			ipadx = 5;
			ipady = 3;
		}});
	}


	private void addImage() {
		content.add(imagePanel, new GridBagConstraints() {{
			gridy = 1;
			weightx = 1;
			weighty = 1;
			fill = NONE;
			anchor = CENTER;
		}});
		imageDisplayed = true;
	}

	private void removeImage() {
		remove(imagePanel);
		imageDisplayed = false;
	}

	private void refreshUi() {
		title.setText(vm.getTitle());
		date.setText(toString(vm.getDate()));
		size.setText(vm.getSize() / 1000f + " MB");

		if (!imageDisplayed) {
			addImage();
		}

		image.setIcon(null);

		var screenshotUri = vm.getScreenshotUri();

		//load image in bg thread
		new SwingWorker<ImageIcon,Void>() {
			private final URI url = screenshotUri;

			@Override
			public ImageIcon doInBackground() {
				try {
					if (screenshotUri != null) {
						URL cleanUrl = new URL(screenshotUri.toASCIIString());
						return new ImageIcon(new ImageIcon(cleanUrl, vm.getTitle()).getImage().getScaledInstance(360, 270, Image.SCALE_SMOOTH));
					}
				}
				catch (MalformedURLException e) {
				}
				return null;
			}
			@Override
			public void done() {
				//threading: is the image still valid?
				if (isCancelled() || !screenshotUri.equals(url)) {
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
					removeImage();
					System.err.println("Couldn't load image " + screenshotUri);
				}
				else {
					image.setIcon(icon);
					image.setMinimumSize(PackageDetailPanelView.DEFAULTIMAGESIZE);
				}

				revalidate();
				repaint();
			}
		}.execute();

		description.getEditorKit().createDefaultDocument();
		description.setText(vm.getDescription()
				+ toString(vm.getRequirements()) + "<p></p>");
		//scroll to top
		description.setCaretPosition(0);

		revalidate();
		repaint();
	}

	private String toString(Date date) {
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
		refreshUi();
	}
}
