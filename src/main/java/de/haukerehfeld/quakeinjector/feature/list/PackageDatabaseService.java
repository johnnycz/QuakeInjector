package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.JobTracker;
import de.haukerehfeld.quakeinjector.model.JobTrackerProvider;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Loads the package database into the package list, showing the progress as a row in the job list.
 * Call it on the UI thread; it reports back there through the injected UI executor.
 */
public class PackageDatabaseService {
	static final String DESCRIPTION = "Downloading package database";

	private final Configuration config;
	private final RequirementList packages;
	private final JobTrackerProvider jobs;
	private final DialogProvider dialogs;
	private final Executor background;
	private final Executor ui;

	/** Only touched on the UI thread. */
	private boolean loading = false;

	/**
	 * @param ui runs callbacks on the UI thread, e.g. {@code SwingUtilities::invokeLater}
	 */
	public PackageDatabaseService(Configuration config, RequirementList packages, JobTrackerProvider jobs,
	                              DialogProvider dialogs, Executor ui) {
		// its own thread, so the list doesn't wait behind a map download
		this(config, packages, jobs, dialogs, Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "quakeinjector-database");
			thread.setDaemon(true);
			return thread;
		}), ui);
	}

	/**
	 * @param background downloads and parses the database
	 */
	public PackageDatabaseService(Configuration config, RequirementList packages, JobTrackerProvider jobs,
	                              DialogProvider dialogs, Executor background, Executor ui) {
		this.config = config;
		this.packages = packages;
		this.jobs = jobs;
		this.dialogs = dialogs;
		this.background = background;
		this.ui = ui;
	}

	/**
	 * Download and parse the package database in the background, falling back to the cached copy,
	 * and replace the package list with it. Returns right away; ignored while a load is running.
	 *
	 * @param whenLoaded runs on the UI thread once the package list was replaced (not if loading failed)
	 */
	public void load(Runnable whenLoaded) {
		if (loading) {
			System.out.println("The package database is already loading");
			return;
		}
		loading = true;

		CancellationToken token = new CancellationToken();
		JobTracker job = jobs.addJob(DESCRIPTION, token::cancel);
		// the server doesn't send the size of the database
		job.setProgress(JobTracker.UNKNOWN_PROGRESS);
		PackageDatabaseCacheTask task = new PackageDatabaseCacheTask(config, token);

		CompletableFuture.supplyAsync(() -> {
				try {
					return task.execute();
				} catch (IOException e) {
					throw new CompletionException(e);
				}
			}, background)
			.whenCompleteAsync((database, error) -> {
				loading = false;
				if (error == null) {
					job.finish(database.fromCache() ? "Offline copy used" : "Done");
					packages.setRequirements(database.packages());
					packages.notifyChangeListeners();
					whenLoaded.run();
					return;
				}
				Throwable cause = error instanceof CompletionException && error.getCause() != null
					? error.getCause()
					: error;
				if (cause instanceof CancelledException) {
					job.finish("Cancelled");
					return;
				}
				job.finish("Failed");
				String msg = cause.getMessage();
				if (cause.getCause() instanceof UnknownHostException) {
					msg = "Couldn't establish connection to the server (" + cause.getCause().getMessage() + ").";
				}
				dialogs.showError("Database parsing failed! " + msg, "Database parsing failed!");
			}, ui);
	}
}
