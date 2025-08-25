package de.haukerehfeld.quakeinjector.gui;

import javax.swing.*;
import java.awt.*;

public class PackageInteractionPanelView extends JPanel {

	private static final String uninstallText = "Uninstall";
	private static final String installText = "Install";
	private static final String playText = "Play";

	private QuakeInjectorView parentView;
	private JButton uninstallButton;
	private JButton installButton;
	private JButton playButton;

	private JComboBox<String> startmaps;

	public PackageInteractionPanelView() {
		super(new GridBagLayout());
		uninstallButton = new JButton(uninstallText);
		uninstallButton.setEnabled(false);


		add(uninstallButton, new GridBagConstraints() {{
			fill = BOTH;
		}});

		installButton = new JButton(installText);
		installButton.setEnabled(false);
		// int preferredHeight = (int) installButton.getPreferredSize().getHeight();
		// {
		// 	Dimension maxSize = new Dimension(150, preferredHeight);
		// 	installButton.setMinimumSize(maxSize);
		// 	installButton.setPreferredSize(maxSize);
		// }

		add(installButton, new GridBagConstraints() {{
			gridx = 1;
			gridy = 0;
			fill = BOTH;
		}});

		playButton = new JButton(playText);
		playButton.setEnabled(false);

		add(playButton, new GridBagConstraints() {{
			gridx = 0;
			gridy = 1;
			fill = BOTH;
		}});

		startmaps = new JComboBox<>();
		// {
		// 	Dimension maxSize = new Dimension(100, preferredHeight);
		// 	startmaps.setPreferredSize(maxSize);
		// 	startmaps.setMinimumSize(maxSize);
		// }
		add(startmaps, new GridBagConstraints() {{
			gridx = 1;
			gridy = 1;
			fill = BOTH;
			weightx = 1;
		}});
	}



	public JButton getInstallButton() {
		return installButton;
	}

	public JButton getPlayButton() {
		return playButton;
	}

	public JButton getUninstallButton() {
		return uninstallButton;
	}

	public JComboBox<String> getStartmaps() {
		return startmaps;
	}

	public static String getInstallText() {
		return installText;
	}
}
