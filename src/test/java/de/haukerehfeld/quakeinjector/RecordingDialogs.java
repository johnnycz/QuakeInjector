package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.JobTracker;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link DialogProvider} that shows nothing: it records the dialogs asked for and on which thread.
 * Its progress popup doesn't block in {@code start()}, unlike the real modal one.
 */
public class RecordingDialogs implements DialogProvider {
	/** e.g. "error: Checking failed", in call order */
	public final List<String> calls = new CopyOnWriteArrayList<>();
	/** The thread of each call in {@link #calls}. */
	public final List<String> threads = new CopyOnWriteArrayList<>();
	/** Cancel each progress popup right when it is opened, as if the user clicked Cancel at once. */
	public volatile boolean cancelProgressPopups;
	/** The last progress popup opened. */
	public volatile Popup popup;

	private void record(String call) {
		calls.add(call);
		threads.add(Thread.currentThread().getName());
	}

	public static class Popup implements JobTracker {
		public final List<Integer> progress = new CopyOnWriteArrayList<>();
		/** Completes with the finish message. */
		public final CompletableFuture<String> finished = new CompletableFuture<>();

		@Override
		public void start() {
		}

		@Override
		public void finish(String message) {
			finished.complete(message);
		}

		@Override
		public void setProgress(int progress) {
			this.progress.add(progress);
		}
	}

	@Override
	public JobTracker showProgressPopup(String description, Runnable onCancel) {
		record("progress: " + description);
		popup = new Popup();
		if (cancelProgressPopups) {
			onCancel.run();
		}
		return popup;
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
