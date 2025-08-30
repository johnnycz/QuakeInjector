package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class PackageInteractionPanelView extends JPanel implements ChangeListener {

	private static final String uninstallText = "Uninstall";
	private static final String installText = "Install";
	private static final String playText = "Play";
	private final PackageInteractionViewModel vm;

	private QuakeInjectorView parentView;
	private JButton uninstallButton;
	private JButton installButton;
	private JButton playButton;

	private JComboBox<String> startmaps;

	public PackageInteractionPanelView(PackageInteractionViewModel vm) {
		super(new GridBagLayout());
		this.vm = vm;
		uninstallButton = new JButton(uninstallText);
		uninstallButton.addActionListener((e) -> vm.uninstall());

		add(uninstallButton, new GridBagConstraints() {{
			fill = BOTH;
		}});

		installButton = new JButton(installText);
		installButton.addActionListener((e) -> vm.install());

		add(installButton, new GridBagConstraints() {{
			gridx = 1;
			gridy = 0;
			fill = BOTH;
		}});

		playButton = new JButton(playText);

		add(playButton, new GridBagConstraints() {{
			gridx = 0;
			gridy = 1;
			fill = BOTH;
		}});

		startmaps = new JComboBox<>();
		add(startmaps, new GridBagConstraints() {{
			gridx = 1;
			gridy = 1;
			fill = BOTH;
			weightx = 1;
		}});

		playButton.addActionListener((e) -> vm.play(startmaps.getItemAt(startmaps.getSelectedIndex())));
		updateElements();
	}

	private void updateElements() {
		uninstallButton.setEnabled(vm.isInstalled());
		installButton.setEnabled(!vm.isInstalled() && !vm.isInstalling());
		installButton.setText(vm.isInstalled() ? installText : installText + " " + vm.getPackageId());
		playButton.setEnabled(vm.isInstalled());

		startmaps.setEnabled(vm.isInstalled() && vm.getStartmaps() != null && !vm.getStartmaps().isEmpty());
		startmaps.removeAllItems();
		for (String startmap: vm.getStartmaps()) {
			startmaps.addItem(startmap);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		updateElements();
	}
}
