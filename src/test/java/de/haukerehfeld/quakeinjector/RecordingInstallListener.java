package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.InstallFailure;
import de.haukerehfeld.quakeinjector.feature.install.InstallListener;
import de.haukerehfeld.quakeinjector.model.PackageFileList;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Records which listener calls were made, in order, and on which thread.
 */
public class RecordingInstallListener implements InstallListener {
	public final List<String> calls = new CopyOnWriteArrayList<>();
	public final List<String> threads = new CopyOnWriteArrayList<>();
	public Function<Map<String, File>, List<File>> overwriteAnswer = existing -> null;
	/** Runs inside failed(), to check the state at the moment the listener is told. */
	public Consumer<InstallFailure> onFailed = failure -> { };
	public volatile Map<String, File> askedAbout;
	public volatile PackageFileList succeededWith;
	public volatile InstallFailure failure;
	/** Progress values and their threads; kept apart from {@link #calls} so call order stays readable. */
	public final List<Integer> progress = new CopyOnWriteArrayList<>();
	public final List<String> progressThreads = new CopyOnWriteArrayList<>();

	@Override
	public void progress(int percent) {
		progressThreads.add(Thread.currentThread().getName());
		progress.add(percent);
	}

	private void record(String call) {
		threads.add(Thread.currentThread().getName());
		calls.add(call);
	}

	@Override
	public List<File> chooseOverwrites(Map<String, File> existingFiles) {
		record("overwrite");
		askedAbout = existingFiles;
		return overwriteAnswer.apply(existingFiles);
	}

	@Override
	public void succeeded(PackageFileList installedFiles) {
		record("succeeded");
		succeededWith = installedFiles;
	}

	@Override
	public void failed(InstallFailure failure) {
		record("failed:" + failure.getKind());
		this.failure = failure;
		onFailed.accept(failure);
	}
}
