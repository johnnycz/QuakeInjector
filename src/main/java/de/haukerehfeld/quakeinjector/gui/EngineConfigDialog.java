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
package de.haukerehfeld.quakeinjector.gui;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import de.haukerehfeld.quakeinjector.guimodel.ConfigViewModel;
import de.haukerehfeld.quakeinjector.utils.Utils;

/**
 * The settings dialog. Every control reports its value to the {@link ConfigViewModel}, which decides
 * whether Okay and Apply are enabled: Apply only when something differs from the saved settings.
 */
public class EngineConfigDialog extends JDialog {
	private final static String windowTitle = "Settings";

	private final ConfigViewModel vm;

	private final JPanel configPanel;
	private final JPanel appearancePanel;

	private final JPathPanel enginePath;
	private final JPathPanel engineExecutable;
	private final JTextField engineCommandline;

	private final JPathPanel downloadPath;


	private final JCheckBox rogue;
	private final JCheckBox hipnotic;

	private final WorkingDirOpts workingDirOpts;
	private final JComboBox<DarkLafUIThemeOption> uiTheme;

	public EngineConfigDialog(final Frame frame, ConfigViewModel vm) {
		super(frame, windowTitle, true);
		this.vm = vm;

		configPanel = new JPanel();
		configPanel.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		configPanel.setLayout(new GridBagLayout());

		JLabel description = new JLabel("Configure engine specific settings");
		description.setLabelFor(this);
		description.setBorder(LookAndFeelDefaults.DIALOGDESCRIPTIONBORDER);
		configPanel.add(description, new GridBagConstraints());

		final JButton okay = new JButton("Okay");
		final JButton cancel = new JButton("Cancel");
		final JButton apply = new JButton("Apply");


		class LabelConstraints extends GridBagConstraints {{
				anchor = LINE_START;
				fill = NONE;
				gridx = 0;
				gridwidth = 1;
				gridheight = 1;
				weightx = 0;
				weighty = 0;

		}};
		class InputConstraints extends GridBagConstraints {{
				anchor = LINE_END;
				fill = HORIZONTAL;
				gridx = 1;
				gridwidth = 2;
				gridheight = 1;
				weightx = 1;
				weighty = 0;
		}};

		int row = 1;

		Border leftBorder = BorderFactory
		    .createEmptyBorder(0, 0, 0, LookAndFeelDefaults.FRAMEPADDING);

		{

			JLabel cmdlineLabel = new JLabel("Quake commandline options");
			cmdlineLabel.setBorder(leftBorder);

			this.engineCommandline = new JTextField(vm.getEngineCommandLine(), 40);

			final int row_ = row;
			configPanel.add(cmdlineLabel, new LabelConstraints() {{ gridy = row_; }});
			configPanel.add(engineCommandline, new InputConstraints() {{ gridy = row_; }});
		}
		++row;

		{
			//"Path to quake directory",
			JLabel enginePathLabel = new JLabel("Quake Directory");
			enginePathLabel.setBorder(leftBorder);

			enginePath = new JPathPanel(new JPathPanel.WritableDirectoryVerifier(),
			                            vm.getEnginePath(),
			                            javax.swing.JFileChooser.DIRECTORIES_ONLY);
			final int row_ = row;
			configPanel.add(enginePathLabel, new LabelConstraints() {{ gridy = row_; }});
			configPanel.add(enginePath, new InputConstraints() {{ gridy = row_; }});
		}
		++row;

		JLabel engineExeLabel = new JLabel("Quake Executable");
		engineExeLabel.setBorder(leftBorder);
		engineExecutable = new JPathPanel(
			new JPathPanel.Verifier() {
				public boolean verify(File exe) {
					return Utils.isValidApplication(exe);
				}
				public String errorMessage(File f) {
					return Utils.errorMessageForApplication(f);
				}
			},
			vm.getEngineExecutable(),
			vm.getEnginePath(),
			javax.swing.JFileChooser.FILES_ONLY);

		{
			final int row_ = row;
			configPanel.add(engineExeLabel, new LabelConstraints() {{ gridy = row_; }});
			configPanel.add(engineExecutable, new InputConstraints() {{ gridy = row_; }});
		}

		enginePath.verify();
		engineExecutable.verify();

		++row;

		{
			//"Path to quake directory",
			JLabel downloadLabel = new JLabel("Download Directory");
			downloadLabel.setBorder(leftBorder);
			downloadPath = new JPathPanel(new JPathPanel.WritableDirectoryVerifier(),
			                              vm.getDownloadPath(),
			                              javax.swing.JFileChooser.DIRECTORIES_ONLY);
			downloadPath.verify();

			final int row_ = row;
			configPanel.add(downloadLabel, new LabelConstraints() {{ gridy = row_; }});
			configPanel.add(downloadPath, new InputConstraints() {{ gridy = row_; }});

		}
		++row;

		{
			JLabel expansionsInstalled = new JLabel("Expansion packs installed");
			expansionsInstalled.setBorder(leftBorder);

			rogue = new JCheckBox("rogue");
			rogue.setMnemonic(KeyEvent.VK_R);
			rogue.setSelected(vm.isRogueInstalled());

			hipnotic = new JCheckBox("hipnotic");
			hipnotic.setMnemonic(KeyEvent.VK_H);
			hipnotic.setSelected(vm.isHipnoticInstalled());

			final int row_ = row;
			configPanel.add(expansionsInstalled, new LabelConstraints() {{ gridy = row_; }});
			configPanel.add(rogue, new InputConstraints() {{ gridy = row_; gridwidth = 1; }});
			configPanel.add(hipnotic, new InputConstraints() {{
				gridy = row_;
				gridx = 2;
				gridwidth = 1;
			}});
		}
		++row;

		workingDirOpts = new WorkingDirOpts(row, vm.isWorkingDirAtExecutable());

		appearancePanel = new JPanel();
		appearancePanel.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		appearancePanel.setLayout(new GridBagLayout());

		var themeLabel = new JLabel("Theme");
		uiTheme = new JComboBox<>(DarkLafUIThemeOption.values());
		uiTheme.setSelectedItem(DarkLafUIThemeOption.getByCodeOrDefault(vm.getUiTheme()));

		appearancePanel.add(themeLabel, new LabelConstraints() {{ gridy = 1; ipadx = 10; }});
		appearancePanel.add(this.uiTheme, new InputConstraints() {{ gridy = 1; }});

		// below the theme list, left-aligned with it
		final String restartAdvice = "Restart Quake Injector to use the new theme";
		final var restartAdviceLabel = new JLabel(restartAdvice);
		// keep its space while it shows nothing, so showing it doesn't move the theme list
		restartAdviceLabel.setPreferredSize(restartAdviceLabel.getPreferredSize());
		restartAdviceLabel.setMinimumSize(restartAdviceLabel.getPreferredSize());
		appearancePanel.add(restartAdviceLabel, new LabelConstraints() {{
			gridx = 1;
			gridy = 2;
			gridwidth = 2;
			insets = new Insets(4, 0, 0, 0);
		}});

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		tabbedPane.addTab("Engine Specifics", null, configPanel, "Configure Engine Specifics");
		tabbedPane.addTab("Appearance", null, appearancePanel, "Appearance");
		tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);
		tabbedPane.setMnemonicAt(1, KeyEvent.VK_2);

		add(tabbedPane, BorderLayout.CENTER);

		//change basepath of the exe when quakedir changes
		enginePath.addChangeListener(e -> engineExecutable.setBasePath(enginePath.getPath()));
		//(un)set working dir opt visibility as appropriate
		enginePath.addChangeListener(e -> workingDirOpts.checkDisplay());
		engineExecutable.addChangeListener(e -> workingDirOpts.checkDisplay());

		// Report every value to the view model. Listen to values only: a check box's ChangeListener
		// also fires on hover and focus, and a path panel's on every check, changed or not.
		engineCommandline.getDocument().addDocumentListener(onEdit(this::reportCommandLine));
		enginePath.addChangeListener(e -> reportEnginePath());
		enginePath.addErrorListener(e -> reportEnginePath());
		engineExecutable.addChangeListener(e -> reportEngineExecutable());
		engineExecutable.addErrorListener(e -> reportEngineExecutable());
		downloadPath.addChangeListener(e -> reportDownloadPath());
		downloadPath.addErrorListener(e -> reportDownloadPath());
		rogue.addItemListener(e -> vm.setRogueInstalled(rogue.isSelected()));
		hipnotic.addItemListener(e -> vm.setHipnoticInstalled(hipnotic.isSelected()));
		uiTheme.addItemListener(e -> vm.setUiTheme(getUiTheme().getCode()));

		okay.addActionListener(e -> {
			if (vm.isDirty()) {
				vm.applyConfig();
			}
			dispose();
		});
		apply.addActionListener(e -> vm.applyConfig());
		cancel.addActionListener(e -> dispose());

		add(new OkayCancelApplyPanel(okay, cancel, apply, true), BorderLayout.PAGE_END);

		// The view model outlives the dialog, so stop listening once it is closed
		final de.haukerehfeld.quakeinjector.utils.ChangeListener updateButtons = e -> {
			Edt.check("EngineConfigDialog");
			okay.setEnabled(vm.canOkay());
			apply.setEnabled(vm.canApply());
			restartAdviceLabel.setText(vm.isRestartNeededForTheme() ? restartAdvice : "");
		};
		vm.addChangeListener(updateButtons);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					vm.removeChangeListener(updateButtons);
				}
			});

		// The fields as shown, and whether they are valid; reading them back isn't a change
		reportCommandLine();
		reportEnginePath();
		reportEngineExecutable();
		reportDownloadPath();
		updateButtons.stateChanged(null);
	}

	private void reportCommandLine() {
		vm.setEngineCommandLine(engineCommandline.getText());
	}

	private void reportEnginePath() {
		vm.setEnginePath(enginePath.getPath(), enginePath.verifies());
	}

	private void reportEngineExecutable() {
		vm.setEngineExecutable(engineExecutable.getPath(), engineExecutable.verifies());
	}

	private void reportDownloadPath() {
		vm.setDownloadPath(downloadPath.getPath());
	}

	private static DocumentListener onEdit(Runnable r) {
		return new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				r.run();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				r.run();
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
				r.run();
			}
		};
	}

	public DarkLafUIThemeOption getUiTheme() {
		return (DarkLafUIThemeOption) uiTheme.getSelectedItem();
	}

	class WorkingDirOpts {
		private final JSeparator workingDirBoxSep;
		private final GridBagConstraints sepConstraints;
		private final JLabel workingDirTitle;
		private final GridBagConstraints titleConstraints;
		private final Box workingDirChoices;
		private final GridBagConstraints choicesConstraints;
		private final JRadioButton workAtExe;
		private boolean visible;
		public WorkingDirOpts(int configPanelRow, boolean workAtExeDefault) {
			// Separator
			workingDirBoxSep = new JSeparator(JSeparator.HORIZONTAL);
			sepConstraints = new GridBagConstraints();
			sepConstraints.gridx = 0;
			sepConstraints.gridy = configPanelRow;
			sepConstraints.gridwidth = GridBagConstraints.REMAINDER;
			sepConstraints.fill = GridBagConstraints.HORIZONTAL;
			sepConstraints.insets = new Insets(10, 0, 10, 0);
			sepConstraints.weightx = 1;
			// Explanatory text
			String workingDirBlurb =
				"<html><body><nobr>" +
				"<b>Notice:</b> your Quake Executable is not located in " +
				"your Quake Directory.<br/>Choose where the runtime " +
				"\"working directory\" for Quake should be located:" +
				"</nobr></body></html>";
			workingDirTitle = new JLabel(workingDirBlurb);
			titleConstraints = new GridBagConstraints();
			titleConstraints.gridx = 0;
			titleConstraints.gridy = configPanelRow + 1;
			titleConstraints.gridwidth = GridBagConstraints.REMAINDER;
			// Container for the choices
			workingDirChoices = new Box(BoxLayout.Y_AXIS);
			// Radio buttons for the choices
			JRadioButton workInBase = new JRadioButton("in Quake Directory");
			String baseBlurb =
				"Using the Quake Directory as the working directory may " +
				"fail if necessary libraries or other resources are " +
				"located with the engine.";
			workInBase.setToolTipText(baseBlurb);
			workInBase.setSelected(!workAtExeDefault);
			workAtExe = new JRadioButton("at Quake Executable");
			String exeBlurb =
				"Using the Quake Engine's location as the working directory " +
				"may require adding a -basedir argument to the command line.";
			workAtExe.setToolTipText(exeBlurb);
			workAtExe.setSelected(workAtExeDefault);
			workAtExe.addItemListener(e -> vm.setWorkingDirAtExecutable(workAtExe.isSelected()));
			ButtonGroup workingDirGroup = new ButtonGroup();
			workingDirGroup.add(workInBase);
			workingDirGroup.add(workAtExe);
			workingDirChoices.add(workInBase);
			workingDirChoices.add(workAtExe);
			choicesConstraints = new GridBagConstraints();
			choicesConstraints.gridx = 0;
			choicesConstraints.gridy = configPanelRow + 2;
			choicesConstraints.gridwidth = GridBagConstraints.REMAINDER;
			choicesConstraints.insets = new Insets(5, 0, 5, 0);
			// Start visible if appropriate
			visible = false;
			checkDisplay();
		}
		public void checkDisplay() {
			File exeDir = null;
			if (null != engineExecutable) {
				exeDir = engineExecutable.getPath().getParentFile();
			}
			boolean validPaths =
				null != exeDir &&
				null != enginePath &&
				!(exeDir.getPath().isEmpty()) &&
				!(enginePath.getPath().getPath().isEmpty()) &&
				!(engineExecutable.getPath().equals(enginePath.getPath()));
			boolean showOpt =
				validPaths && !(enginePath.getPath().equals(exeDir));
			if (showOpt) {
				if (!visible) {
					configPanel.add(workingDirBoxSep, sepConstraints);
					configPanel.add(workingDirTitle, titleConstraints);
					configPanel.add(workingDirChoices, choicesConstraints);
					verticalRepack();
					visible = true;
				}
			}
			else {
				if (visible) {
					configPanel.remove(workingDirBoxSep);
					configPanel.remove(workingDirTitle);
					configPanel.remove(workingDirChoices);
					verticalRepack();
					visible = false;
				}
			}
		}
		private void verticalRepack() {
			EngineConfigDialog dialog = EngineConfigDialog.this;
			Rectangle bounds = dialog.getBounds();
			dialog.setMinimumSize(new Dimension(bounds.width, 0));
			dialog.pack();
			dialog.setMinimumSize(null);
		}
		public boolean getWorkingDirAtExecutable() {
			return workAtExe.isSelected();
		}
	}

}
