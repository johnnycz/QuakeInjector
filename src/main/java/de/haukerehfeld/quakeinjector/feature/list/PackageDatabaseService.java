package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.JobTracker;
import de.haukerehfeld.quakeinjector.model.JobTrackerProvider;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Loads the package database into the package list, showing the progress as a row in the job list.
 * Call it on the UI thread; it reports back there through the injected UI executor.
 */
public class PackageDatabaseService {
	static final String DESCRIPTION = "Loading package database";

	private final Configuration config;
	private final RequirementList packages;
	private final JobTrackerProvider jobs;
	private final DialogProvider dialogs;
	private final Runnable whenFirstLoaded;
	private final Executor background;
	private final Executor ui;

	/** Only touched on the UI thread. */
	private boolean loading = false;
	/** Whether the package list was filled yet, from the cache or a download. Only touched on the UI thread. */
	private boolean listLoaded = false;

	/**
	 * @param whenFirstLoaded runs on the UI thread the first time the package list was filled
	 * @param ui runs callbacks on the UI thread, e.g. {@code SwingUtilities::invokeLater}
	 */
	public PackageDatabaseService(Configuration config, RequirementList packages, JobTrackerProvider jobs,
	                              DialogProvider dialogs, Runnable whenFirstLoaded, Executor ui) {
		// its own thread, so the list doesn't wait behind a map download
		this(config, packages, jobs, dialogs, whenFirstLoaded, Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "quakeinjector-database");
			thread.setDaemon(true);
			return thread;
		}), ui);
	}

	/**
	 * @param background reads the cache and downloads the database
	 */
	public PackageDatabaseService(Configuration config, RequirementList packages, JobTrackerProvider jobs,
	                              DialogProvider dialogs, Runnable whenFirstLoaded,
	                              Executor background, Executor ui) {
		this.config = config;
		this.packages = packages;
		this.jobs = jobs;
		this.dialogs = dialogs;
		this.whenFirstLoaded = whenFirstLoaded;
		this.background = background;
		this.ui = ui;
	}

	/**
	 * At startup: show the cached package database right away, if there is one, then download the
	 * current one and replace the list with it. If the download fails, the cached list stays.
	 * Returns right away; ignored while a load is running.
	 */
	public void loadCachedThenDownload() {
		load(true);
	}

	/**
	 * Download the current package database and replace the list with it; if that fails, the current
	 * list stays. Returns right away; ignored while a load is running.
	 */
	public void download() {
		load(false);
	}

	private void load(boolean cacheFirst) {
		if (loading) {
			System.out.println("The package database is already loading");
			return;
		}
		loading = true;

		CancellationToken token = new CancellationToken();
		JobTracker job = jobs.addJob(DESCRIPTION, token::cancel);
		// the server doesn't send the size of the database
		job.setProgress(JobTracker.UNKNOWN_PROGRESS);

		// whatever goes wrong with the cached copy, the download still runs
		CompletableFuture<Void> cached = cacheFirst
			? CompletableFuture.supplyAsync(new PackageDatabaseCache(config)::read, background)
				.thenAcceptAsync(list -> list.ifPresent(this::showList), ui)
				.exceptionally(error -> {
					System.err.println("Showing the cached package database failed, downloading it anyway:");
					unwrap(error).printStackTrace();
					return null;
				})
			: CompletableFuture.completedFuture(null);

		PackageDatabaseDownloadTask download = new PackageDatabaseDownloadTask(config, token);
		cached
			.thenApplyAsync(ignored -> {
				try {
					return download.execute();
				} catch (IOException e) {
					throw new CompletionException(e);
				}
			}, background)
			.whenCompleteAsync((downloaded, error) -> {
				loading = false;
				if (error != null) {
					downloadFailed(job, unwrap(error));
					return;
				}
				try {
					showList(downloaded);
				} catch (RuntimeException e) {
					showingFailed(job, e);
					return;
				}
				job.finish("Done");
			}, ui);
	}

	/** The download worked, but putting it into the package list failed: a bug, not a network problem. */
	private void showingFailed(JobTracker job, RuntimeException e) {
		System.err.println("Showing the downloaded package database failed:");
		e.printStackTrace();
		job.finish("Failed");
		dialogs.showError("Showing the package list failed! " + e, "Showing the package list failed");
	}

	/** Replace the package list; the installed state carries over (see {@link RequirementList#setRequirements}). */
	private void showList(List<Requirement> list) {
		packages.setRequirements(list);
		packages.notifyChangeListeners();
		if (!listLoaded) {
			listLoaded = true;
			whenFirstLoaded.run();
		}
	}

	private void downloadFailed(JobTracker job, Throwable cause) {
		if (cause instanceof CancelledException) {
			job.finish("Cancelled");
			return;
		}
		System.err.println("Downloading the package database failed: " + cause);
		if (listLoaded) {
			// the list shown is the cached copy, or the last download, which the cache also holds
			job.finish("Download failed, using cached copy");
			return;
		}
		job.finish("Failed");
		String msg = cause.getMessage();
		if (cause instanceof UnknownHostException) {
			msg = "Couldn't establish connection to the server (" + cause.getMessage() + ").";
		}
		dialogs.showError("Database parsing failed! " + msg, "Database parsing failed!");
	}

	private static Throwable unwrap(Throwable error) {
		Throwable cause = error;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}
}
