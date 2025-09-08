package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.DialogViewModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class DialogView implements ChangeListener {

	private final DialogViewModel vm;
	private final Frame parentComponent;

	public DialogView(DialogViewModel vm, Frame parentComponent) {
		this.vm = vm;
		this.parentComponent = parentComponent;
		vm.addChangeListener(this);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (vm.errorMessage != null) {
			JOptionPane.showMessageDialog(parentComponent, vm.errorMessage, vm.title, JOptionPane.ERROR_MESSAGE);
			vm.errorMessage = null;
			vm.title = null;
		} else if (vm.warningMessage != null) {
			JOptionPane.showMessageDialog(parentComponent, vm.warningMessage, vm.title, JOptionPane.WARNING_MESSAGE);
			vm.warningMessage = null;
			vm.title = null;
		} else if (vm.engineOutput != null) {
			EngineOutputDialog eod = new EngineOutputDialog(parentComponent, vm.engineOutput);
			eod.pack();
			eod.setLocationRelativeTo(parentComponent);
			eod.setVisible(true);
		} else if (vm.options != null) {
			vm.selectedOption = JOptionPane.showOptionDialog(parentComponent,
					vm.optionsMessage,
					vm.title,
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null,
					vm.options,
					vm.defaultOption);
			vm.optionsMessage = null;
			vm.title = null;
			vm.options = null;
			vm.defaultOption = null;
		} else if (vm.filesToOverwrite != null) {
			PackageOverwriteDialog pod = new PackageOverwriteDialog(parentComponent, vm.filesToOverwrite, vm.filesToWrite);
			pod.packAndShow();
			vm.overwritenFiles = pod.getOverwritten();
			vm.filesToOverwrite = null;
			vm.filesToWrite = null;
		} else if (vm.engineConfigWindowShown) {
			final EngineConfigDialog d = new EngineConfigDialog(parentComponent, vm.configViewModel);

			d.pack();
			d.setLocationRelativeTo(parentComponent);
			d.setVisible(true);
		}
	}
}
