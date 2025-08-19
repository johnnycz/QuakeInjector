package de.haukerehfeld.quakeinjector.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Style;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.Enumeration;

public class PackageDetailPanelView extends JPanel {

	public static final Dimension DEFAULTIMAGESIZE = new Dimension(360, 270);
	private static final Dimension NOIMAGESIZE = new Dimension(100, 500);

	private JLabel title;
	private JLabel size;
	private JLabel date;

	private ScrollablePanel content;

	private JLabel image;
	private JPanel imagePanel;

	private JEditorPane description;

	public PackageDetailPanelView() {
		super(new GridBagLayout());

		content = new ScrollablePanel(50, 50) {{
			setLayout(new GridBagLayout());
		}};
		content.setOpaque(false);
		//content.setBackground();

		title = new JLabel();
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setOpaque(true);
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));


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

	public JLabel getTitle() {
		return title;
	}

	public ScrollablePanel getContent() {
		return content;
	}

	public JPanel getImagePanel() {
		return imagePanel;
	}

	public JLabel getImage() {
		return image;
	}

	public JLabel getDate() {
		return date;
	}

	public JLabel getSizeLabel() {
		return size;
	}

	public JEditorPane getDescription() {
		return description;
	}
}
