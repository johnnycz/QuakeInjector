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
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import de.haukerehfeld.quakeinjector.utils.RelativePath;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Installer {
	private static final int simultanousDownloads = 1;
	private static final int simultanousExtractions = 1;
	private static final int simultanousInspectors = 1;
	private static final int simultanousWaiters = 15;

	private final Configuration.EnginePath installDirectory;
	private final Configuration.DownloadPath downloadDirectory;
	
	private final ExecutorService activeDownloaders = Executors.newFixedThreadPool(simultanousDownloads);
	private final ExecutorService activeInspectors = Executors.newFixedThreadPool(simultanousInspectors);
	private final ExecutorService activeExtractors = Executors.newFixedThreadPool(simultanousExtractions);
	private final ExecutorService activeWaiters = Executors.newFixedThreadPool(simultanousWaiters);

	private final Map<de.haukerehfeld.quakeinjector.model.Package,Worker> queue = new HashMap<de.haukerehfeld.quakeinjector.model.Package,Worker>();

	public Installer(Configuration.EnginePath installDirectory, Configuration.DownloadPath downloadDirectory) {
		this.installDirectory = installDirectory;
		this.downloadDirectory = downloadDirectory;
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
		synchronized (queue) {
			for (de.haukerehfeld.quakeinjector.model.Package inQueue: new java.util.HashSet<de.haukerehfeld.quakeinjector.model.Package>(getQueue())) {
				cancel(inQueue);
				System.out.println("Canceling " + inQueue);
			}
		}
	}
	

	public boolean working() {
		return !queue.isEmpty();
	}

	public Set<de.haukerehfeld.quakeinjector.model.Package> getQueue() {
		return queue.keySet();
	}

	public boolean alreadyQueued(final de.haukerehfeld.quakeinjector.model.Package map) {
		return queue.get(map) != null;
	}

	/**
	 * Create a task to download a file from a URL.
	 * The task will download to the specified output stream.
	 *
	 * @param url the URL to download from
	 * @param out the output stream to write to
	 * @return an AsyncTask that performs the download
	 * @throws IOException if creating the download connection fails
	 */
	public AsyncTask<Long> createDownloadTask(String url, OutputStream out) throws IOException {
		Download download = Download.create(url);
		return new DownloadTask(download, out);
	}

	/**
	 * Create a task to inspect a ZIP archive and list all entries.
	 *
	 * @param input the input stream of the archive
	 * @return an AsyncTask that inspects the archive
	 */
	public AsyncTask<List<ArchiveEntry>> createInspectTask(InputStream input) {
		return new InspectZipTask(input);
	}

	/**
	 * Create a task to extract a package's files from an archive.
	 *
	 * @param input the archive input stream
	 * @param inputSize size of the archive for progress reporting
	 * @param map the package being installed
	 * @param overwrites list of files allowed to overwrite (null = all)
	 * @return an AsyncTask that performs the extraction
	 */
	public AsyncTask<PackageFileList> createExtractTask(InputStream input, long inputSize,
	                                                     Package map, List<File> overwrites) {
		return new ExtractTask(input, inputSize, map, installDirectory.get(),
			map.getExtractMapping(), overwrites);
	}

	/**
	 * Create a task to uninstall a package.
	 *
	 * @param files the files to uninstall
	 * @return an AsyncTask that performs the uninstallation
	 */
	public AsyncTask<Void> createUninstallTask(PackageFileList files) {
		return new UninstallTask(files, installDirectory.get().getAbsolutePath());
	}

	public void install(final de.haukerehfeld.quakeinjector.model.Package selectedMap,
	                    final String url,
						final InstallErrorHandler errorHandler,
						final PropertyChangeListener downloadProgressListener) {
		if (alreadyQueued(selectedMap)) {
			return;
		}

		// Create and execute the install orchestration task
		PackageInstallOrchestrationTask orchestrationTask = new PackageInstallOrchestrationTask(
			url, selectedMap, errorHandler, downloadDirectory, installDirectory);

		Worker orchestrator = new Worker(url, selectedMap, errorHandler, downloadProgressListener, orchestrationTask);
		synchronized (queue) { queue.put(selectedMap, orchestrator); }
		synchronized (activeWaiters) { activeWaiters.submit(orchestrator); }
	}

	public void cancel(de.haukerehfeld.quakeinjector.model.Package installerMap) {
		Worker w;
		synchronized (queue) { w = queue.get(installerMap); }
		if (w != null) {
			w.cancel();
		}
	}


	public void uninstall(PackageFileList map,
	                      final UninstallErrorHandler errorHandler,
	                      PropertyChangeListener progressListener) {
		UninstallTask uninstallTask = new UninstallTask(map, installDirectory.get().getAbsolutePath());

		// Execute asynchronously via executor service
		synchronized (activeWaiters) {
			activeWaiters.submit(() -> {
				try {
					uninstallTask.execute();
					errorHandler.success();
				} catch (Exception e) {
					errorHandler.error(e);
				}
			});
		}
	}

	public interface InstallErrorHandler {
		/**
		 * Decide which files in the package should be overwritten
		 * @param existingFiles in the package
		 * @return all the files that should be overwritten or an empty list to overwrite no files and cancel install
		 */
		public List<File> overwrite(Map<String,File> existingFiles);
		public void success(PackageFileList installedFiles);
		public void handle(OnlineFileNotFoundException error);
		public void handle(FileNotWritableException error, PackageFileList alreadyInstalledFiles);
		public void handle(IOException error, PackageFileList alreadyInstalledFiles);
		public void handle(java.net.SocketException error, PackageFileList alreadyInstalledFiles);
		public void handle(CancelledException error, PackageFileList alreadyInstalledFiles);
	}

	public interface UninstallErrorHandler {
		public void success();
		public void error(Exception e);
	}

	public static class CancelledException extends RuntimeException {}

	/**
	 * Executes package installation orchestration asynchronously.
	 */
	private class Worker implements Runnable {
		private final String url;
		private final Package installedPackage;
		private final InstallErrorHandler handler;
		private final PropertyChangeListener downloadProgressListener;
		private final PackageInstallOrchestrationTask task;
		private volatile boolean cancelled = false;

		public Worker(String url, Package installedPackage,
		              InstallErrorHandler handler,
		              PropertyChangeListener downloadProgressListener,
		              PackageInstallOrchestrationTask task) {
			this.url = url;
			this.installedPackage = installedPackage;
			this.handler = handler;
			this.downloadProgressListener = downloadProgressListener;
			this.task = task;
		}

		@Override
		public void run() {
			try {
				// Execute the orchestration task (created in constructor)
				PackageFileList files = task.execute();
				System.out.println("Success installing");
				handler.success(files);

			} catch (OnlineFileNotFoundException e) {
				// Already handled by task
				System.out.println("File not found during installation");
			} catch (java.net.SocketException e) {
				// Already handled by task
				System.out.println("Socket error during installation");
			} catch (FileNotWritableException e) {
				// Already handled by task
				System.out.println("File write error during installation");
			} catch (IOException e) {
				// Already handled by task
				System.out.println("IO error during installation");
			} catch (CancelledException e) {
				// Already handled by task
				System.out.println("Installation cancelled");
			} catch (Exception e) {
				System.err.println("Unexpected error during installation: " + e);
				e.printStackTrace();
			} finally {
				System.out.println("Done with installation");
				synchronized (queue) { queue.remove(installedPackage); }
			}
		}

		public void cancel() {
			cancelled = true;
			if (task != null) {
				task.cancel();
			}
		}
	}	
}