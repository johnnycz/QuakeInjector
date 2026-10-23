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

import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
 * <li>the UI executor passed in: overwrite questions and all listener / handler callbacks</li>
 * </ul>
 */
public class Installer {
	private final Configuration.EnginePath installDirectory;
	private final Configuration.DownloadPath downloadDirectory;
	private final InstallPipeline.Executors executors;

	private final Map<Package, InstallPipeline> queue = new HashMap<>(); // guarded by itself

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

	public boolean checkInstallDirectory() {
		if (!installDirectory.existsOrDefault()) {
			return false;
		}
		return installDirectory.get().canWrite();
	}

	public boolean checkDownloadDirectory() {
		if (!downloadDirectory.existsOrDefault()) {
			return false;
		}
		return downloadDirectory.get().canWrite();
	}

	public void cancelAll() {
		Set<Package> queued;
		synchronized (queue) {
			queued = new HashSet<>(queue.keySet());
		}
		for (Package inQueue : queued) {
			System.out.println("Canceling " + inQueue);
			cancel(inQueue);
		}
	}

	public boolean working() {
		synchronized (queue) {
			return !queue.isEmpty();
		}
	}

	public Set<Package> getQueue() {
		synchronized (queue) {
			return new HashSet<>(queue.keySet());
		}
	}

	public boolean alreadyQueued(final Package map) {
		synchronized (queue) {
			return queue.containsKey(map);
		}
	}

	/**
	 * Install a package in the background. All listener calls happen on the UI executor.
	 *
	 * @return completes after the listener was told the outcome and the package left the queue;
	 *         an already finished future if the package is already being installed
	 */
	public CompletableFuture<Void> install(final Package selectedMap,
	                                       final String url,
	                                       final InstallListener listener,
	                                       final PropertyChangeListener downloadProgressListener) {
		InstallPipeline pipeline = new InstallPipeline(
			url, selectedMap, listener, downloadDirectory, installDirectory, executors);
		synchronized (queue) {
			if (queue.containsKey(selectedMap)) {
				return CompletableFuture.completedFuture(null);
			}
			queue.put(selectedMap, pipeline);
		}

		return pipeline.start()
			.whenComplete((ignored, error) -> {
				if (error != null) {
					System.err.println("Install listener failed for " + selectedMap.getId() + ": " + error);
					error.printStackTrace();
				}
				synchronized (queue) {
					queue.remove(selectedMap);
				}
			});
	}

	/** Cancel a queued install. The listener hears about it once the running stage has stopped. */
	public void cancel(Package installerMap) {
		InstallPipeline pipeline;
		synchronized (queue) {
			pipeline = queue.get(installerMap);
		}
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
	                                         final UninstallErrorHandler errorHandler,
	                                         PropertyChangeListener progressListener) {
		UninstallTask uninstallTask = new UninstallTask(map, installDirectory.get().getAbsolutePath());

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

	/** Callbacks of {@link #uninstall}, called on the UI executor. */
	public interface UninstallErrorHandler {
		public void success();
		public void error(Exception e);
	}
}
