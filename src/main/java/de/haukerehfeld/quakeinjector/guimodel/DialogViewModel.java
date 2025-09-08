package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.InputStream;
import java.util.List;

public class DialogViewModel implements DialogProvider {

	private final ChangeListenerList listeners = new ChangeListenerList();
	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	private void notifyChangeListeners() {
		listeners.notifyChangeListeners(new ChangeEvent(this));
	}

	public final ConfigViewModel configViewModel;

	public String errorMessage;
	public String warningMessage;
	public String optionsMessage;
	public String title;
	public InputStream engineOutput;

	public String[] options;
	public String defaultOption;
	public int selectedOption = -1;

	public List<String> filesToOverwrite;
	public List<String> filesToWrite;
	public List<String> overwritenFiles;

	public boolean engineConfigWindowShown;

	public DialogViewModel(ConfigViewModel configViewModel) {
		this.configViewModel = configViewModel;
	}

	@Override
	public void showWarning(String message, String title) {
		this.warningMessage = message;
		this.title = title;
		notifyChangeListeners();
	}

	@Override
	public void showError(String message, String title) {
		this.errorMessage = message;
		this.title = title;
		notifyChangeListeners();
	}

	@Override
	public int showOptions(String message, String title, String[] options, String defaultOption) {
		this.optionsMessage = message;
		this.title = title;
		this.options = options;
		this.defaultOption = defaultOption;
		this.selectedOption = -1;
		notifyChangeListeners();
		this.optionsMessage = null;
		this.options = null;
		return this.selectedOption;
	}

	@Override
	public void showEngineOutout(InputStream engineOutput) {
		this.engineOutput = engineOutput;
		notifyChangeListeners();
	}

	@Override
	public List<String> showOverwriteDialog(List<String> filesToOverwrite, List<String> filesToWrite) {
		this.filesToOverwrite = filesToOverwrite;
		this.filesToWrite = filesToWrite;
		notifyChangeListeners();
		return this.overwritenFiles;
	}

	@Override
	public boolean askAndshowEngineConfigWindow() {
		this.optionsMessage = "Quake directory is not set correctly.\n"
				+ "It needs to be set before trying to install (or play).";
		this.title = "Quake directory incorrect";

		this.options = new String[] {"Open Engine Configuration", "Cancel"};
		this.defaultOption = options[0];
		this.selectedOption = -1;
		notifyChangeListeners();
		this.options = null;
		this.optionsMessage = null;

		if (this.selectedOption == 0) {
			showEngineConfigWindow();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void showEngineConfigWindow() {
		this.configViewModel.updateFromConfig();
		this.engineConfigWindowShown = true;
		notifyChangeListeners();
		this.engineConfigWindowShown = false;
	}

}
