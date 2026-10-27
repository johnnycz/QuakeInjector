package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.DialogProvider;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * A {@link DialogProvider} that shows nothing: it records the dialogs asked for and on which thread.
 * Dialogs that return an answer fail the test unless the test set one.
 */
public class RecordingDialogs implements DialogProvider {
	/** e.g. "error: Checking failed", in call order */
	public final List<String> calls = new CopyOnWriteArrayList<>();
	/** The thread of each call in {@link #calls}. */
	public final List<String> threads = new CopyOnWriteArrayList<>();

	/** The option {@link #showOptions} picks (an index into its options); null: not expected. */
	public volatile Integer optionAnswer;
	/** Answers {@link #showOverwriteDialog} with the files to overwrite; null: not expected. */
	public volatile BiFunction<List<String>, List<String>, List<String>> overwriteAnswer;
	/** Answers {@link #askAndshowEngineConfigWindow}; null: not expected. */
	public volatile BooleanSupplier engineConfigAnswer;

	/** The message of the last {@link #showOptions} call. */
	public volatile String optionsMessage;

	/** The arguments of the last {@link #showOverwriteDialog} call. */
	public volatile List<String> askedToOverwrite;
	public volatile List<String> askedToWrite;

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
		if (optionAnswer == null) {
			throw new UnsupportedOperationException("not expected in this test: " + title);
		}
		record("options: " + title);
		optionsMessage = message;
		return optionAnswer;
	}

	@Override
	public void showEngineOutout(InputStream engineOutput) {
		throw new UnsupportedOperationException("not expected in this test");
	}

	@Override
	public List<String> showOverwriteDialog(List<String> filesToOverwrite, List<String> filesToWrite) {
		if (overwriteAnswer == null) {
			throw new UnsupportedOperationException("not expected in this test");
		}
		record("overwrite");
		askedToOverwrite = List.copyOf(filesToOverwrite);
		askedToWrite = List.copyOf(filesToWrite);
		return overwriteAnswer.apply(filesToOverwrite, filesToWrite);
	}

	@Override
	public boolean askAndshowEngineConfigWindow() {
		if (engineConfigAnswer == null) {
			throw new UnsupportedOperationException("not expected in this test");
		}
		record("engine config");
		return engineConfigAnswer.getAsBoolean();
	}

	@Override
	public void showEngineConfigWindow() {
		throw new UnsupportedOperationException("not expected in this test");
	}
}
