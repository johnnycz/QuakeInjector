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
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import de.haukerehfeld.quakeinjector.utils.RelativePath;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the complete install process: download, inspect, ask for overwrites, extract.
 * One {@link CancellationToken} is shared by all stages, so {@link #cancel()} stops whichever stage is running.
 */
public class PackageInstallOrchestrationTask implements AsyncTask<PackageFileList> {
	private final String url;
	private final Package installedPackage;
	private final Installer.InstallErrorHandler handler;
	private final Configuration.DownloadPath downloadDirectory;
	private final Configuration.EnginePath installDirectory;

	private final CancellationToken token = new CancellationToken();

	public PackageInstallOrchestrationTask(String url, Package installedPackage,
	                                        Installer.InstallErrorHandler handler,
	                                        Configuration.DownloadPath downloadDirectory,
	                                        Configuration.EnginePath installDirectory) {
		this.url = url;
		this.installedPackage = installedPackage;
		this.handler = handler;
		this.downloadDirectory = downloadDirectory;
		this.installDirectory = installDirectory;
	}

	@Override
	public PackageFileList execute() throws Exception {
		PackageFileList installedFiles = new PackageFileList(installedPackage.getId());
		ExtractTask extractTask = null;

		try {
			final File downloadFile = new File(downloadDirectory.get().getAbsolutePath() + File.separator +
				installedPackage.getId() + ".zip");
			System.out.println("Downloading to " + downloadFile);

			// Download phase
			long downloadSize = performDownload(downloadFile);
			checkCancelled();

			// Inspect phase
			System.out.println("Inspecting downloaded archive..." + downloadFile);
			Map<String, File> existingFiles;
			try (BufferedInputStream inspectStream = new BufferedInputStream(new FileInputStream(downloadFile))) {
				existingFiles = findExistingFiles(inspectStream);
			}
			System.out.println("done.");
			checkCancelled();

			// Ask for overwrites
			List<File> overwrites = null;
			if (existingFiles != null) {
				overwrites = handler.overwrite(existingFiles);
			}

			// The user may have pressed cancel while the overwrite question was open
			checkCancelled();

			// Extract phase
			if (overwrites == null || !overwrites.isEmpty()) {
				System.out.println("Starting extraction");
				BufferedInputStream in = new BufferedInputStream(new FileInputStream(downloadFile));

				try {
					extractTask = new ExtractTask(in,
						downloadSize,
						installedPackage,
						installDirectory.get(),
						installedPackage.getExtractMapping(),
						overwrites,
						token);

					installedFiles = extractTask.execute();
				} finally {
					in.close();
				}
			} else {
				System.out.println("Canceling install");
				throw new CancelledException();
			}

		} catch (OnlineFileNotFoundException e) {
			handler.handle(e);
			throw e;
		} catch (FileNotWritableException e) {
			handler.handle(e, extractedSoFar(extractTask, installedFiles));
			throw e;
		} catch (java.net.SocketException e) {
			handler.handle(e, extractedSoFar(extractTask, installedFiles));
			throw e;
		} catch (IOException e) {
			handler.handle(e, extractedSoFar(extractTask, installedFiles));
			throw e;
		} catch (CancelledException e) {
			handler.handle(e, extractedSoFar(extractTask, installedFiles));
			throw e;
		}

		return installedFiles;
	}

	/**
	 * Files the extract stage wrote before it failed, so the error handler can clean them up.
	 * Safe to read here: the extract stage ran on this thread and has already stopped.
	 */
	private static PackageFileList extractedSoFar(ExtractTask extractTask, PackageFileList fallback) {
		return extractTask != null ? extractTask.getExtractedFiles() : fallback;
	}

	private long performDownload(File downloadFile) throws Exception {
		long downloadSize;
		if (!downloadFile.exists()) {
			downloadFile.getParentFile().mkdirs();
			FileOutputStream out = new FileOutputStream(downloadFile);
			try {
				AsyncTask<Long> downloadTask = new DownloadTask(Download.create(url), out, token);
				downloadSize = downloadTask.execute();
				out.flush();
				out.close();
			} catch (Exception e) {
				out.close();
				System.out.print("Error downloading file, removing...");
				if (!downloadFile.delete()) {
					System.err.print("Couldn't delete partially downloaded file (" + downloadFile + ")! ");
				}
				System.out.println("done.");
				throw e;
			}
		} else {
			downloadSize = downloadFile.length();
			System.out.println("Skipping download, already existing with length " + downloadSize);
		}
		return downloadSize;
	}

	private Map<String, File> findExistingFiles(final InputStream in) throws Exception {
		// Create inspect task
		AsyncTask<List<ArchiveEntry>> inspectTask = new InspectZipTask(in, token);
		System.out.println("Waiting for inspection...");
		List<ArchiveEntry> entries = inspectTask.execute();

		// Check files
		final Map<String, File> files = new HashMap<>();
		boolean existingFile = false;
		for (ArchiveEntry z : entries) {
			if (z.isDirectory()) {
				continue;
			}
			File f = installDirectory.getUnzipFile(installedPackage, z.getName());
			if (f == null) {
				continue;
			}
			String name = RelativePath.getRelativePath(installDirectory.get(), f).toString();
			files.put(name, f);
			if (f.exists()) {
				existingFile = true;
			}
		}
		if (!existingFile) {
			return null;
		}
		return files;
	}

	/** Stop the install. Safe to call from any thread; the running stage stops at its next check. */
	public void cancel() {
		token.cancel();
	}

	private void checkCancelled() throws CancelledException {
		if (token.isCancelled()) {
			System.out.println("Install cancelled");
			throw new CancelledException();
		}
	}
}
