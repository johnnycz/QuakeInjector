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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import de.haukerehfeld.quakeinjector.feature.play.EngineStarter;
import de.haukerehfeld.quakeinjector.guimodel.ConfigViewModel;
import de.haukerehfeld.quakeinjector.utils.Utils;

public class EngineConfigDialog extends JDialog {
	private final static String windowTitle = "Settings";

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

/*
							  Configuration.EnginePath enginePathDefault,
							  Configuration.EngineExecutable engineExeDefault,
							  Configuration.WorkingDirAtExecutable workingDirAtExecutable,
							  Configuration.DownloadPath downloadPathDefault,
							  Configuration.EngineCommandLine cmdlineDefault,
							  Configuration.RogueInstalled rogueInstalled,
							  Configuration.HipnoticInstalled hipnoticInstalled,
							  Configuration.UIThemeConfiguration uiThemeConfiguration) {

 */
	public EngineConfigDialog(final Frame frame, ConfigViewModel vm) {
		super(frame, windowTitle, true);

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

			this.engineCommandline = new JTextField(vm.engineCommandLine, 40);

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
			                            vm.enginePath,
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
			vm.engineExecutable,
			vm.enginePath,
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
			                              vm.downloadPath,
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
			rogue.setSelected(vm.rogueInstalled);

			hipnotic = new JCheckBox("hipnotic");
			hipnotic.setMnemonic(KeyEvent.VK_H);
			hipnotic.setSelected(vm.hipnoticInstalled);

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

		workingDirOpts = new WorkingDirOpts(row, vm.workingDirAtExecutable);

		appearancePanel = new JPanel();
		appearancePanel.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		appearancePanel.setLayout(new GridBagLayout());

		var themeLabel = new JLabel("Theme");
		uiTheme = new JComboBox<>(DarkLafUIThemeOption.values());
		uiTheme.setSelectedItem(vm.uiTheme);

		appearancePanel.add(themeLabel, new LabelConstraints() {{ gridy = 1; ipadx = 10; }});
		appearancePanel.add(this.uiTheme, new InputConstraints() {{ gridy = 1; }});

		var restartAdviceLabel = new JLabel("Restart Quake Injector to apply changes");
		appearancePanel.add(restartAdviceLabel, new GridBagConstraints() {{
			gridwidth = 3;
			ipady = 10;
			gridy = 2;
		}});

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		tabbedPane.addTab("Engine Specifics", null, configPanel, "Configure Engine Specifics");
		tabbedPane.addTab("Appearance", null, appearancePanel, "Appearance");
		tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);
		tabbedPane.setMnemonicAt(1, KeyEvent.VK_2);

		add(tabbedPane, BorderLayout.CENTER);
		

		class EnableOkay implements ChangeListener, DocumentListener {
			@Override
			public void changedUpdate(DocumentEvent e) {
				check();
			}
			@Override
			public void insertUpdate(DocumentEvent e) {
				check();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				check();
			}

			@Override
			public void stateChanged(ChangeEvent e) {
				check();
			}
			
			private void check() {
				if (enginePath.verifies() && engineExecutable.verifies()) {
					okay.setEnabled(true);
					apply.setEnabled(true);
				}
			}
		};
		
		final EnableOkay enableOkay = new EnableOkay() ;
		

		engineCommandline.getDocument().addDocumentListener(enableOkay);
		
		enginePath.addErrorListener(new ErrorListener() {
				public void errorOccured(ErrorEvent e) {
					okay.setEnabled(false);
					apply.setEnabled(false);
				}
			});
		//change basepath of the exe when quakedir changes
		enginePath.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					engineExecutable.setBasePath(enginePath.getPath());
				}
			});
		//(un)set working dir opt visibility as appropriate
		enginePath.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					workingDirOpts.checkDisplay();
				}
			});
		enginePath.addChangeListener(enableOkay);

		engineExecutable.addErrorListener(new ErrorListener() {
				public void errorOccured(ErrorEvent e) {
					okay.setEnabled(false);
					apply.setEnabled(false);
				}
			});
		//(un)set working dir opt visibility as appropriate
		engineExecutable.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					workingDirOpts.checkDisplay();
				}
			});
		engineExecutable.addChangeListener(enableOkay);

		downloadPath.addChangeListener(enableOkay);

		rogue.addChangeListener(enableOkay);
		hipnotic.addChangeListener(enableOkay);

		ActionListener save = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					vm.engineExecutable = engineExecutable.getPath();
					vm.enginePath = enginePath.getPath();
					vm.downloadPath = downloadPath.getPath();
					vm.engineCommandLine = engineCommandline.getText();
					vm.rogueInstalled = rogue.isSelected();
					vm.hipnoticInstalled = hipnotic.isSelected();
					vm.workingDirAtExecutable = workingDirOpts.getWorkingDirAtExecutable();
					vm.uiTheme = ((DarkLafUIThemeOption) uiTheme.getSelectedItem()).getCode();
					vm.applyConfig();
					apply.setEnabled(false);
				}
			};
		

		okay.addActionListener(save);
		apply.addActionListener(save);

		ActionListener close = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
					dispose();
				}
			};

		okay.addActionListener(close);
		cancel.addActionListener(close);

		{
			JPanel okayCancelPanel = new OkayCancelApplyPanel(okay, cancel, apply, true);
			add(okayCancelPanel, BorderLayout.PAGE_END);
		}
		
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
