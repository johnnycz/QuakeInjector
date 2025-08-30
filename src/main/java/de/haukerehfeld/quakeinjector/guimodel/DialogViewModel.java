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


}
