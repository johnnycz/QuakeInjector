package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * The top of the info panel: the title of the selected package on its screenshot ({@link TitleBanner}), the
 * action buttons below it, and a line. It doesn't scroll, and it keeps its height when nothing is selected.
 */
public class PackageHeaderView extends JPanel implements ChangeListener {
	private final PackageInteractionViewModel vm;

	private final TitleBanner banner = new TitleBanner();
	/** The screenshot shown or being loaded, so it's loaded again only when another package is selected. */
	private URI screenshotUri;

	public PackageHeaderView(PackageInteractionViewModel vm, Component actions) {
		super(new GridBagLayout());
		this.vm = vm;

		banner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		banner.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				vm.openPackageWebpage();
			}
		});
		add(banner, new GridBagConstraints() {{
			fill = HORIZONTAL;
			weightx = 1;
		}});

		add(actions, new GridBagConstraints() {{
			gridy = 1;
			fill = HORIZONTAL;
			weightx = 1;
			insets = new Insets(6, 0, 0, 0);
		}});

		// separates the header from the details and description below
		add(new JSeparator(), new GridBagConstraints() {{
			gridy = 2;
			fill = HORIZONTAL;
			weightx = 1;
			insets = new Insets(6, 0, 0, 0);
		}});

		vm.addChangeListener(this);
		refreshUi();
	}

	private void refreshUi() {
		banner.setTitle(vm.getTitle());

		URI uri = vm.getScreenshotUri();
		if (!Objects.equals(uri, screenshotUri)) {
			screenshotUri = uri;
			banner.setImage(null);
			if (uri != null) {
				loadScreenshot(uri);
			}
		}
	}

	/** Loads the screenshot in the background and shows it, unless another package got selected meanwhile. */
	private void loadScreenshot(URI uri) {
		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() throws Exception {
				// ASCII, so names with other characters work
				return ImageIO.read(new java.net.URL(uri.toASCIIString()));
			}

			@Override
			protected void done() {
				if (!uri.equals(screenshotUri)) {
					return;
				}
				try {
					BufferedImage image = get();
					if (image == null) {
						System.err.println("Couldn't read image " + uri);
					}
					banner.setImage(image);
				}
				catch (InterruptedException | ExecutionException e) {
					System.err.println("Couldn't load image " + uri + ": " + e.getCause());
				}
			}
		}.execute();
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageHeaderView");
		refreshUi();
	}
}
