/*
Copyright 2009 Hauke Rehfeld


This file is part of QuakeInjector.

QuakeInjector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

QuakeInjector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.haukerehfeld.quakeinjector.gui.BrowserLauncher;
import de.haukerehfeld.quakeinjector.gui.PackageDetailPanelView;
import de.haukerehfeld.quakeinjector.guimodel.PackageListSelectionHandler;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.Utils;

/**
 * the panel that shows Info about the selected map
 */
public class PackageDetailPanel extends JPanel implements ChangeListener,
		PackageListSelectionHandler.SelectionListener {

	/**
	 * Currently selected map
	 */
	private de.haukerehfeld.quakeinjector.model.Package current = null;

	private boolean imageDisplayed = false;

	private final String screenshotRepositoryPath;
	private final String mapWebpageBaseUrl;

	private final PackageDetailPanelView view;

	/**
	 * Holds the currently valid screenshot url, for threading reasons
	 */
	private String supposedImageUrl = null;

	public PackageDetailPanel(PackageDetailPanelView view, String screenshotRepositoryPath, String mapWebpageBaseUrl) {
		this.view = view;
		this.screenshotRepositoryPath = screenshotRepositoryPath;
		this.mapWebpageBaseUrl = mapWebpageBaseUrl;

		addListeners();
	}

	private void addListeners() {
		view.getTitle().addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent arg0) {
				if (current != null) {
					try {
						// URLs with spaces in the path need escaping to %20, not +. We can't use built in URLEncoder
						URL url = new URL(mapWebpageBaseUrl + current.getSha256());
						URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
						BrowserLauncher.openURL(uri.toASCIIString());
					}
					catch (MalformedURLException | URISyntaxException e) {
						// TODO: Emit an error message or something
					}
				}
			}
		});
		view.getImage().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				// TODO: Refactor
				try {
					// URLs with spaces in the path need escaping to %20, not +. We can't use built in URLEncoder
					URL url = new URL(PackageDetailPanel.this.screenshotRepositoryPath
							+ current.getSha256().substring(0, 2) + "/" + current.getSha256() + "/" + current.getId() + ".jpg");
					URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
					BrowserLauncher.openURL(uri.toASCIIString());
				}
				catch (MalformedURLException | URISyntaxException e) {
					// TODO: Emit an error message or something
				}
			}
		});


	}

	private void addImage() {
		view.getContent().add(view.getImagePanel(), new GridBagConstraints() {{
			gridy = 1;
			weightx = 1;
			weighty = 1;
			fill = NONE;
			anchor = CENTER;
		}});
		imageDisplayed = true;
	}

	private void removeImage() {
		view.getContent().remove(view.getImagePanel());
		imageDisplayed = false;
	}

	private void refreshUi() {
		view.getTitle().setText(current.getTitle());
		view.getDate().setText(toString(current.getDate()));
		view.getSizeLabel().setText(current.getSize() / 1000f + " MB");

		if (!imageDisplayed) {
			addImage();
		}

		view.getImage().setIcon(null);
		
		supposedImageUrl = screenshotRepositoryPath + current.getSha256().substring(0, 2) + "/" + current.getSha256() + "/" + current.getId() + ".jpg";
		
		//load image in bg thread
		new SwingWorker<ImageIcon,Void>() {
			private final String url = supposedImageUrl;
			
			@Override
			public ImageIcon doInBackground() {
				try {
					// URLs with spaces in the path need escaping to %20, not +. We can't use built in URLEncoder
					URL url = new URL(supposedImageUrl);
					URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
					URL cleanUrl = new URL(uri.toASCIIString());

					return new ImageIcon(new ImageIcon(cleanUrl, current.getId()).getImage().getScaledInstance(360, 270, Image.SCALE_SMOOTH));
				}
				catch (MalformedURLException | URISyntaxException e) {
				}
				return null;
			}
			@Override
			public void done() {
				//threading: is the image still valid?
				if (isCancelled() || !supposedImageUrl.equals(url)) {
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
					System.err.println("Couldn't load image " + supposedImageUrl);
				}
				else {
					view.getImage().setIcon(icon);
					view.getImagePanel().setMinimumSize(PackageDetailPanelView.DEFAULTIMAGESIZE);
				}
				
				revalidate();
				repaint();
			}
		}.execute();

		view.getDescription().getEditorKit().createDefaultDocument();
		view.getDescription().setText(current.getDescription()
		                    + toString(current.getRequirements()) + "<p></p>");
		//scroll to top
		view.getDescription().setCaretPosition(0);

		revalidate();
		repaint();
	}

	private String toString(Date date) {
		DateFormat dfm = new SimpleDateFormat("MMM d, yyyy");
		dfm.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
		return dfm.format(date);
	}

	private String toString(List<Requirement> requirements) {
		if (requirements.isEmpty()) {
			return "";
		}
		List<String> links = new ArrayList<String>(requirements.size());
		for (Requirement r: requirements) {
			links.add("<a href=\"" + r.getId() + ".html\">" + r.getId() + "</a>");
		}
		return "<p>Requires: " + Utils.join(links, ", ") + ".</p>";
	}

	@Override
	public void selectionChanged(Package map) {
		this.current = map;

		refreshUi();

	}
	
	@Override
	public void stateChanged(ChangeEvent e) {
		System.out.println("StateChanged()");
		refreshUi();
	}
}
