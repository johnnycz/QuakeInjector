package de.haukerehfeld.quakeinjector.model;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public interface DialogProvider {

	void showWarning(String message, String title);
	void showError(String message, String title);
	int showOptions(String message, String title, String[] options, String defaultOption);
	void showEngineOutout(InputStream engineOutput);
	List<String> showOverwriteDialog(List<String> filesToOverwrite, List<String> filesToWrite);
	boolean askAndshowEngineConfigWindow();
	void showEngineConfigWindow();
	JobTracker showProgressPopup(String description, Runnable onCancel);
}
