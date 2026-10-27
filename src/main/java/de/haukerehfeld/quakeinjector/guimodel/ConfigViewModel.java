package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The settings dialog: the saved settings, the ones being edited, and whether they can be saved.
 * <p>
 * The view reports every value (and whether the Quake directory and executable are valid) through the
 * setters; Apply is enabled when something differs from what was saved ({@link #isDirty()}) and
 * everything is valid ({@link #isValid()}). Values are compared as the user means them, so a path typed
 * relative or absolute, or an executable stored relative to the Quake directory, isn't a change.
 * Listeners hear about every change of a value or of validity. Only used on the EDT.
 */
public class ConfigViewModel {

	/** The settings the dialog edits. The executable may be relative to the Quake directory. */
	record Settings(File enginePath,
	                File engineExecutable,
	                boolean workingDirAtExecutable,
	                File downloadPath,
	                String engineCommandLine,
	                String uiTheme) {

		boolean sameAs(Settings o) {
			return samePath(enginePath, o.enginePath)
				&& samePath(resolvedExecutable(), o.resolvedExecutable())
				&& workingDirAtExecutable == o.workingDirAtExecutable
				&& samePath(downloadPath, o.downloadPath)
				&& Objects.equals(orEmpty(engineCommandLine), orEmpty(o.engineCommandLine))
				&& Objects.equals(uiTheme, o.uiTheme);
		}

		private File resolvedExecutable() {
			if (engineExecutable == null || engineExecutable.isAbsolute() || enginePath == null) {
				return engineExecutable;
			}
			return new File(enginePath, engineExecutable.getPath());
		}

		private static boolean samePath(File a, File b) {
			return normalize(a).equals(normalize(b));
		}

		/** An unset path reads like an empty text field. */
		private static File normalize(File f) {
			File absolute = (f == null ? new File("") : f).getAbsoluteFile();
			try {
				return absolute.toPath().normalize().toFile();
			}
			catch (InvalidPathException typedSomethingOdd) {
				return absolute;
			}
		}

		private static String orEmpty(String s) {
			return s == null ? "" : s;
		}
	}

	private final CommandHandler commandHandler;
	private final Configuration config;
	private final UnaryOperator<String> uiThemeOrDefault;
	private final ChangeListenerList listeners = new ChangeListenerList();
	/** The theme in use: a new one only takes effect after a restart. */
	private final String runningUiTheme;

	private Settings saved;

	private File enginePath;
	private File engineExecutable;
	private boolean workingDirAtExecutable;
	private File downloadPath;
	private String engineCommandLine;
	private String uiTheme;

	private boolean enginePathValid = true;
	private boolean engineExecutableValid = true;

	/**
	 * @param uiThemeOrDefault turns the saved theme code into the one the dialog shows, e.g. the default
	 *        theme for none or an unknown one, so opening the dialog isn't a change
	 */
	public ConfigViewModel(CommandHandler commandHandler, Configuration config, UnaryOperator<String> uiThemeOrDefault) {
		this.commandHandler = commandHandler;
		this.config = config;
		this.uiThemeOrDefault = uiThemeOrDefault;
		updateFromConfig();
		// built after the theme was installed at startup, which happens only once
		this.runningUiTheme = saved.uiTheme();
	}

	/** Starts editing the saved settings; call before showing the dialog. */
	public void updateFromConfig() {
		saved = new Settings(
			config.EnginePath.get(),
			config.EngineExecutable.get(),
			config.WorkingDirAtExecutable.get(),
			config.DownloadPath.get(),
			config.EngineCommandLine.get(),
			uiThemeOrDefault.apply(config.uiTheme.get()));
		enginePath = saved.enginePath();
		engineExecutable = saved.engineExecutable();
		workingDirAtExecutable = saved.workingDirAtExecutable();
		downloadPath = saved.downloadPath();
		engineCommandLine = saved.engineCommandLine();
		uiTheme = saved.uiTheme();
		enginePathValid = true;
		engineExecutableValid = true;
		notifyChangeListeners();
	}

	/** Saves the edited settings; they become the saved ones. */
	public void applyConfig() {
		config.EnginePath.set(enginePath);
		// the dialog returns the full path; store it relative to the Quake directory, as before the refactor
		config.EngineExecutable.setRelativeTo(enginePath, engineExecutable);
		config.WorkingDirAtExecutable.set(workingDirAtExecutable);
		config.DownloadPath.set(downloadPath);
		config.EngineCommandLine.set(engineCommandLine);
		config.uiTheme.set(uiTheme);

		commandHandler.saveConfig();

		saved = edited();
		notifyChangeListeners();
	}

	/** Something differs from the saved settings. */
	public boolean isDirty() {
		return !edited().sameAs(saved);
	}

	/** The Quake directory and the executable are valid. */
	public boolean isValid() {
		return enginePathValid && engineExecutableValid;
	}

	/** Apply: there is something to save, and it can be saved. */
	public boolean canApply() {
		return isDirty() && isValid();
	}

	/** Okay: saves what changed and closes; with nothing changed it just closes. */
	public boolean canOkay() {
		return !isDirty() || isValid();
	}

	/**
	 * The selected theme isn't the one in use, so it needs a restart. Compared with the running theme,
	 * not the saved one: after Apply the restart is still needed.
	 */
	public boolean isRestartNeededForTheme() {
		return !Objects.equals(uiTheme, runningUiTheme);
	}

	private Settings edited() {
		return new Settings(enginePath, engineExecutable, workingDirAtExecutable, downloadPath,
			engineCommandLine, uiTheme);
	}

	public File getEnginePath() {
		return enginePath;
	}

	public void setEnginePath(File enginePath, boolean valid) {
		if (Objects.equals(this.enginePath, enginePath) && enginePathValid == valid) {
			return;
		}
		this.enginePath = enginePath;
		this.enginePathValid = valid;
		notifyChangeListeners();
	}

	/** As saved: possibly relative to the Quake directory. After the view reported it, the full path. */
	public File getEngineExecutable() {
		return engineExecutable;
	}

	public void setEngineExecutable(File engineExecutable, boolean valid) {
		if (Objects.equals(this.engineExecutable, engineExecutable) && engineExecutableValid == valid) {
			return;
		}
		this.engineExecutable = engineExecutable;
		this.engineExecutableValid = valid;
		notifyChangeListeners();
	}

	public boolean isWorkingDirAtExecutable() {
		return workingDirAtExecutable;
	}

	public void setWorkingDirAtExecutable(boolean workingDirAtExecutable) {
		if (this.workingDirAtExecutable == workingDirAtExecutable) {
			return;
		}
		this.workingDirAtExecutable = workingDirAtExecutable;
		notifyChangeListeners();
	}

	public File getDownloadPath() {
		return downloadPath;
	}

	/** Not checked for validity: the default download directory is only created by the first install. */
	public void setDownloadPath(File downloadPath) {
		if (Objects.equals(this.downloadPath, downloadPath)) {
			return;
		}
		this.downloadPath = downloadPath;
		notifyChangeListeners();
	}

	public String getEngineCommandLine() {
		return engineCommandLine;
	}

	public void setEngineCommandLine(String engineCommandLine) {
		if (Objects.equals(this.engineCommandLine, engineCommandLine)) {
			return;
		}
		this.engineCommandLine = engineCommandLine;
		notifyChangeListeners();
	}

	public String getUiTheme() {
		return uiTheme;
	}

	public void setUiTheme(String uiTheme) {
		if (Objects.equals(this.uiTheme, uiTheme)) {
			return;
		}
		this.uiTheme = uiTheme;
		notifyChangeListeners();
	}

	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	private void notifyChangeListeners() {
		listeners.notifyChangeListeners(this);
	}
}
