package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;

import javax.swing.*;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import java.awt.*;

/**
 * Install, play and uninstall the selected package.
 * <p>
 * Not installed: only the Install button. Installed: the Play button with the start map below it, and
 * a narrow Uninstall button (trash icon) to the right of both. Both layouts take the same height, so
 * the panel doesn't jump when a package gets installed.
 */
public class PackageInteractionPanelView extends JPanel implements ChangeListener {
	private static final String NOT_INSTALLED = "notInstalled";
	private static final String INSTALLED = "installed";

	private static final Color INSTALL_COLOR = new Color(0x5A9BD8);
	private static final Color PLAY_COLOR = new Color(0x5BAE5B);
	private static final Color UNINSTALL_COLOR = new Color(0xD86464);
	/** The same text color on the button colors in both the light and the dark theme. */
	private static final Color BUTTON_TEXT = Color.WHITE;
	private static final float FONT_SCALE = 1.3f;
	private static final int GAP = 4;

	private final PackageInteractionViewModel vm;
	private final CardLayout cards = new CardLayout();

	private final JButton installButton;
	private final JButton playButton;
	private final JButton uninstallButton;
	private final JComboBox<String> startmaps;

	public PackageInteractionPanelView(PackageInteractionViewModel vm) {
		this.vm = vm;
		setLayout(cards);

		installButton = coloredButton("Install", INSTALL_COLOR);
		installButton.addActionListener((e) -> vm.install());

		playButton = coloredButton("Play", PLAY_COLOR);
		startmaps = new JComboBox<>();
		playButton.addActionListener((e) -> vm.play(startmaps.getItemAt(startmaps.getSelectedIndex())));

		uninstallButton = coloredButton(null, UNINSTALL_COLOR);
		uninstallButton.setIcon(new TrashIcon(installButton.getFont().getSize() + 7));
		// just the icon, so no extra room on the sides
		uninstallButton.setMargin(new Insets(4, 4, 4, 4));
		uninstallButton.setToolTipText("Uninstall");
		uninstallButton.getAccessibleContext().setAccessibleName("Uninstall");
		uninstallButton.addActionListener((e) -> vm.uninstall());

		add(notInstalledCard(), NOT_INSTALLED);
		add(installedCard(), INSTALLED);

		vm.addChangeListener(this);
		updateElements();
	}

	private static JButton coloredButton(String text, Color color) {
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(Font.PLAIN,button.getFont().getSize2D() * FONT_SCALE));
		button.setBackground(color);
		button.setForeground(BUTTON_TEXT);
		Insets margin = button.getMargin();
		if (margin != null) {
			button.setMargin(new Insets(margin.top + 4, margin.left + 8, margin.bottom + 4, margin.right + 8));
		}
		return button;
	}

	/** The Install button, at the top; the space below it matches the start map row of the other layout. */
	private JPanel notInstalledCard() {
		JPanel card = new JPanel(new GridBagLayout());
		card.add(installButton, new GridBagConstraints() {{
			anchor = PAGE_START;
			fill = HORIZONTAL;
			weightx = 1;
			weighty = 1;
		}});
		return card;
	}

	/** Play, with the start map below it, and the narrow Uninstall button to the right of both. */
	private JPanel installedCard() {
		JPanel card = new JPanel(new GridBagLayout());
		card.add(playButton, new GridBagConstraints() {{
			fill = HORIZONTAL;
			weightx = 1;
		}});
		card.add(startmaps, new GridBagConstraints() {{
			gridy = 1;
			fill = HORIZONTAL;
			weightx = 1;
			insets = new Insets(GAP, 0, 0, 0);
		}});
		card.add(uninstallButton, new GridBagConstraints() {{
			gridx = 1;
			gridy = 0;
			gridheight = 2;
			fill = VERTICAL;
			insets = new Insets(0, GAP, 0, 0);
		}});
		return card;
	}

	private void updateElements() {
		boolean installed = vm.isInstalled();
		cards.show(this, installed ? INSTALLED : NOT_INSTALLED);

		// nothing selected, or already being installed
		installButton.setEnabled(!installed && !vm.getPackageId().isEmpty() && !vm.isInstalling());

		startmaps.removeAllItems();
		for (String startmap: vm.getStartmaps()) {
			startmaps.addItem(startmap);
		}
		startmaps.setEnabled(installed && startmaps.getItemCount() > 0);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageInteractionPanelView");
		updateElements();
	}
}
