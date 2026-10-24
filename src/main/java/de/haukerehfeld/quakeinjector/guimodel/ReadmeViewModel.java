package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.ReadmeSource;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * One readme window: the text files of a package, which one is shown, and its text.
 * <p>
 * Switching files starts reading the new one; a file that finishes reading after the window already
 * moved on to another one is ignored. Only used on the EDT.
 */
public class ReadmeViewModel {
	static final String LOADING = "Loading...";
	static final String NO_FILES = "This package has no text files.";

	private final ReadmeSource source;
	private final String packageId;
	private final String title;
	private final List<String> files;
	private final ChangeListenerList listeners = new ChangeListenerList();

	private int current = -1;
	private String text = "";
	/** Counts reads, so only the latest one is shown. */
	private int reads = 0;

	public ReadmeViewModel(ReadmeSource source, String packageId, String title, List<String> files) {
		this.source = source;
		this.packageId = packageId;
		this.title = title;
		this.files = List.copyOf(files);
	}

	/** Shows the first file; call once the view listens. */
	public void start() {
		if (files.isEmpty()) {
			text = NO_FILES;
			notifyChangeListeners();
		}
		else {
			show(0);
		}
	}

	public void next() {
		show(current + 1);
	}

	public void previous() {
		show(current - 1);
	}

	/** Shows the file at the index, wrapping around at both ends. */
	private void show(int index) {
		if (files.isEmpty()) {
			return;
		}
		current = Math.floorMod(index, files.size());
		String path = files.get(current);
		int read = ++reads;
		text = LOADING;
		notifyChangeListeners();

		source.read(path).whenComplete((content, error) -> {
			if (read != reads) {
				return;
			}
			text = error == null ? content : errorMessage(path, error);
			notifyChangeListeners();
		});
	}

	private static String errorMessage(String path, Throwable error) {
		if (error instanceof FileNotFoundException || error instanceof NoSuchFileException) {
			return "File not found: " + path;
		}
		return "Couldn't read " + path + ": " + error.getMessage();
	}

	public String getPackageId() {
		return packageId;
	}

	public String getTitle() {
		return title;
	}

	public int getFileCount() {
		return files.size();
	}

	/** @return -1 while there are no files */
	public int getCurrentIndex() {
		return current;
	}

	/** @return "" while there are no files */
	public String getCurrentFile() {
		return current >= 0 ? files.get(current) : "";
	}

	public String getText() {
		return text;
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
