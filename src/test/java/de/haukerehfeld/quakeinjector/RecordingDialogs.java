package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.DialogProvider;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link DialogProvider} that shows nothing: it records the dialogs asked for and on which thread.
 */
public class RecordingDialogs implements DialogProvider {
	/** e.g. "error: Checking failed", in call order */
	public final List<String> calls = new CopyOnWriteArrayList<>();
	/** The thread of each call in {@link #calls}. */
	public final List<String> threads = new CopyOnWriteArrayList<>();

	private void record(String call) {
		calls.add(call);
		threads.add(Thread.currentThread().getName());
	}

	@Override
	public void showWarning(String message, String title) {
		record("warning: " + title);
	}

	@Override
	public void showError(String message, String title) {
		record("error: " + title);
	}

	@Override
	public int showOptions(String message, String title, String[] options, String defaultOption) {
		throw new UnsupportedOperationException("not expected in this test: " + title);
	}

	@Override
	public void showEngineOutout(InputStream engineOutput) {
		throw new UnsupportedOperationException("not expected in this test");
	}

	@Override
	public List<String> showOverwriteDialog(List<String> filesToOverwrite, List<String> filesToWrite) {
		throw new UnsupportedOperationException("not expected in this test");
	}

	@Override
	public boolean askAndshowEngineConfigWindow() {
		throw new UnsupportedOperationException("not expected in this test");
	}

	@Override
	public void showEngineConfigWindow() {
		throw new UnsupportedOperationException("not expected in this test");
	}
}
