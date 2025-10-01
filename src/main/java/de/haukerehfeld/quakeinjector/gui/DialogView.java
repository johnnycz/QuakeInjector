package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.DialogViewModel;

import javax.swing.*;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

public class DialogView implements ChangeListener {

	private final DialogViewModel vm;
	private final Frame parentComponent;
	private ProgressPopup progressPopup;

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
			vm.engineOutput = null;
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
			if (SwingUtilities.isEventDispatchThread()) {
				PackageOverwriteDialog pod = new PackageOverwriteDialog(parentComponent, vm.filesToOverwrite, vm.filesToWrite);
				pod.packAndShow();
				vm.overwritenFiles = pod.getOverwritten();
				vm.filesToOverwrite = null;
				vm.filesToWrite = null;
			} else {
				SwingWorker<java.util.List<String>,Void> dialogue = new SwingWorker<>() {
					public java.util.List<String> doInBackground() {
						PackageOverwriteDialog pod = new PackageOverwriteDialog(parentComponent, vm.filesToOverwrite, vm.filesToWrite);
						pod.packAndShow();
						return pod.getOverwritten();
					}
				};
				try {
					SwingUtilities.invokeAndWait(dialogue);
					vm.overwritenFiles = dialogue.get();
					vm.filesToOverwrite = null;
					vm.filesToWrite = null;
				} catch (InterruptedException ignored) {
				} catch (InvocationTargetException ex) {
					throw new RuntimeException(ex.getCause());
				} catch (ExecutionException ex) {
					throw new RuntimeException(ex.getCause());
				}
			}
		} else if (vm.engineConfigWindowShown) {
			final EngineConfigDialog d = new EngineConfigDialog(parentComponent, vm.configViewModel);

			d.pack();
			d.setLocationRelativeTo(parentComponent);
			d.setVisible(true);
		} else if (vm.progressPopupMessage != null) {
			if (progressPopup == null) {
				this.progressPopup = new ProgressPopup(vm.progressPopupMessage,
						ae -> vm.progressPopupCancelAction.run(), parentComponent);
				progressPopup.pack();
			}
			if (vm.progress != null) {
				progressPopup.setProgress(vm.progress);
			}
			if (vm.progressPopupShow && !progressPopup.isVisible()) {
				progressPopup.setVisible(true);
			}
		} else if (vm.progressPopupMessage == null && !vm.progressPopupShow && progressPopup != null) {
			progressPopup.close();
		}
	}
}
