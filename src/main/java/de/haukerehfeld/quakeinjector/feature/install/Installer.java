/*
Copyright 2009 Hauke Rehfeld


This file is part of QuakeInjector.

QuakeInjector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

QuakeInjector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.UiProgress;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.haukerehfeld.quakeinjector.feature.install.Futures.unchecked;

/**
 * Runs package installs and uninstalls in the background.
 * <p>
 * Threads:
 * <ul>
 * <li>one network thread: downloads run one at a time</li>
 * <li>one disk thread: inspecting, extracting and uninstalling run one at a time, so cleanup
 *     never overlaps with an extraction</li>
 * <li>the UI executor passed in: overwrite questions, all listener / handler callbacks and
 *     queue change events</li>
 * </ul>
 */
public class Installer {
	private final Configuration.EnginePath installDirectory;
	private final Configuration.DownloadPath downloadDirectory;
	private final InstallPipeline.Executors executors;

	/**
	 * Installs that haven't reported their outcome yet, by package id: reloading the package database
	 * replaces the Package objects while an install may be running.
	 */
	private final Map<String, InstallPipeline> queue = new ConcurrentHashMap<>();

	/** Only touched on the UI executor. */
	private final ChangeListenerList queueListeners = new ChangeListenerList();

	/**
	 * @param uiExecutor runs callbacks on the UI thread, e.g. {@code SwingUtilities::invokeLater}
	 */
	public Installer(Configuration.EnginePath installDirectory, Configuration.DownloadPath downloadDirectory,
	                 Executor uiExecutor) {
		this(installDirectory, downloadDirectory, new InstallPipeline.Executors(
			singleThread("quakeinjector-network"), singleThread("quakeinjector-disk"), uiExecutor));
	}

	public Installer(Configuration.EnginePath installDirectory, Configuration.DownloadPath downloadDirectory,
	                 InstallPipeline.Executors executors) {
		this.installDirectory = installDirectory;
		this.downloadDirectory = downloadDirectory;
		this.executors = executors;
	}

	/** Daemon thread, so a running download doesn't keep the app from exiting. */
	private static ExecutorService singleThread(String name) {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * The disk thread: runs extraction, uninstall and writing the installed-packages list one at a time.
	 * Use it for other work on those files, so it never overlaps with them.
	 */
	public Executor diskExecutor() {
		return executors.disk();
	}

	/** The executor callbacks run on: the UI thread. */
	public Executor uiExecutor() {
		return executors.ui();
	}

	/** The Quake directory packages are installed into. */
	public File installDirectory() {
		return installDirectory.get();
	}

	public boolean checkInstallDirectory() {
		if (!installDirectory.existsOrDefault()) {
			return false;
		}
		return installDirectory.get().canWrite();
	}

	public void cancelAll() {
		for (Map.Entry<String, InstallPipeline> inQueue : queue.entrySet()) {
			System.out.println("Canceling " + inQueue.getKey());
			inQueue.getValue().cancel();
		}
	}

	public boolean working() {
		return !queue.isEmpty();
	}

	/** Whether a package with this id is being installed, also if it is another Package object. */
	public boolean alreadyQueued(final Package map) {
		return queue.containsKey(map.getId());
	}

	/**
	 * Be told whenever a package enters or leaves the install queue (see {@link #alreadyQueued}).
	 * Events arrive on the UI executor; the event source is the package. Register on the UI thread.
	 */
	public void addQueueListener(ChangeListener listener) {
		queueListeners.addChangeListener(listener);
	}

	public void removeQueueListener(ChangeListener listener) {
		queueListeners.removeChangeListener(listener);
	}

	/**
	 * Install a package in the background. All listener calls happen on the UI executor.
	 * The package stays queued until the listener was told the outcome; after a failure, the files
	 * written so far are already removed by then.
	 *
	 * @return completes after the listener was told the outcome and the package left the queue;
	 *         an already finished future if the package is already being installed
	 */
	public CompletableFuture<Void> install(final Package selectedMap,
	                                       final String url,
	                                       final InstallListener listener) {
		InstallPipeline pipeline = new InstallPipeline(
			url, selectedMap, listener, downloadDirectory, installDirectory, executors);
		if (queue.putIfAbsent(selectedMap.getId(), pipeline) != null) {
			return CompletableFuture.completedFuture(null);
		}
		executors.ui().execute(() -> queueChanged(selectedMap));

		// runs on the UI executor, right after the listener was called
		return pipeline.start()
			.whenComplete((ignored, error) -> {
				if (error != null) {
					System.err.println("Install listener failed for " + selectedMap.getId() + ": " + error);
					error.printStackTrace();
				}
				queue.remove(selectedMap.getId());
				queueChanged(selectedMap);
			});
	}

	private void queueChanged(Package changed) {
		queueListeners.notifyChangeListeners(changed);
	}

	/**
	 * Cancel a queued install. Only signals the running stage; the listener hears about it, and the
	 * package leaves the queue, once that stage has stopped and its partial files are removed.
	 */
	public void cancel(Package installerMap) {
		InstallPipeline pipeline = queue.get(installerMap.getId());
		if (pipeline != null) {
			pipeline.cancel();
		}
	}

	/**
	 * Delete a package's files on the disk thread. The handler is called on the UI executor.
	 *
	 * @return completes after the handler was called
	 */
	public CompletableFuture<Void> uninstall(PackageFileList map,
	                                         final UninstallErrorHandler errorHandler) {
		UiProgress progress = new UiProgress(executors.ui(), errorHandler::progress);
		UninstallTask uninstallTask = new UninstallTask(map, installDirectory.get().getAbsolutePath(),
			(current, total) -> progress.report((int) (current * 100 / Math.max(1, total))));

		return CompletableFuture
			.runAsync(() -> unchecked(uninstallTask::execute), executors.disk())
			.handleAsync((ignored, error) -> {
				if (error == null) {
					errorHandler.success();
				}
				else {
					Throwable cause = Futures.unwrap(error);
					errorHandler.error(cause instanceof Exception e ? e : new Exception(cause));
				}
				return null;
			}, executors.ui());
	}

	/**
	 * Run a short piece of file work (like saving the installed-packages list) on the disk thread,
	 * after any extraction or uninstall that is already running. Errors are logged.
	 */
	public CompletableFuture<Void> runOnDiskThread(String description, Futures.Stage<?> work) {
		return CompletableFuture
			.runAsync(() -> unchecked(work), executors.disk())
			.whenComplete((ignored, error) -> {
				if (error != null) {
					System.err.println(description + " failed: " + Futures.unwrap(error));
				}
			});
	}

	/** Callbacks of {@link #uninstall}, called on the UI executor. */
	public interface UninstallErrorHandler {
		/** Uninstall progress, 0..100; only called when the whole percentage changes. */
		default void progress(int percent) {
		}

		public void success();
		public void error(Exception e);
	}
}
