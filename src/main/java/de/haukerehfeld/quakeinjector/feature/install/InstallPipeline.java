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
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.RelativePath;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static de.haukerehfeld.quakeinjector.feature.install.Futures.unchecked;

/**
 * The install process for one package: download, inspect, ask about overwrites, extract.
 * <p>
 * Each stage runs on the executor that suits it, and no thread waits for another:
 * <ul>
 * <li>download on the network executor</li>
 * <li>inspect and extract on the disk executor</li>
 * <li>the overwrite question and the final {@link InstallListener} call on the UI executor</li>
 * </ul>
 * One {@link CancellationToken} is shared by all stages, so {@link #cancel()} stops whichever stage is
 * running. The listener is told about the outcome only after that stage has actually stopped.
 */
public class InstallPipeline {
	/** The threads the stages run on. */
	public record Executors(Executor network, Executor disk, Executor ui) {
	}

	/** The downloaded (or cached) archive. */
	private record Archive(File file, long size) {
	}

	/** The archive plus the existing files the package would overwrite (null if none exist). */
	private record Inspection(Archive archive, Map<String, File> existingFiles) {
	}

	/** What to extract: overwrites is null to write every file. */
	private record ExtractPlan(Archive archive, List<File> overwrites) {
	}

	private final String url;
	private final Package installedPackage;
	private final InstallListener listener;
	private final Configuration.DownloadPath downloadDirectory;
	private final Configuration.EnginePath installDirectory;
	private final Executors executors;

	private final CancellationToken token = new CancellationToken();

	public InstallPipeline(String url, Package installedPackage,
	                       InstallListener listener,
	                       Configuration.DownloadPath downloadDirectory,
	                       Configuration.EnginePath installDirectory,
	                       Executors executors) {
		this.url = url;
		this.installedPackage = installedPackage;
		this.listener = listener;
		this.downloadDirectory = downloadDirectory;
		this.installDirectory = installDirectory;
		this.executors = executors;
	}

	/**
	 * Start the install.
	 *
	 * @return completes on the UI executor once the listener has been told the outcome
	 */
	public CompletableFuture<Void> start() {
		return CompletableFuture
			.supplyAsync(() -> unchecked(this::download), executors.network())
			.thenApplyAsync(archive -> unchecked(() -> inspect(archive)), executors.disk())
			.thenApplyAsync(this::askOverwrites, executors.ui())
			.thenApplyAsync(this::extract, executors.disk())
			.handleAsync(this::finish, executors.ui());
	}

	/** Stop the install. Safe to call from any thread; the running stage stops at its next check. */
	public void cancel() {
		token.cancel();
	}

	private Archive download() throws Exception {
		token.throwIfCancelled();
		final File downloadFile = new File(downloadDirectory.get().getAbsolutePath() + File.separator +
			installedPackage.getId() + ".zip");

		if (downloadFile.exists()) {
			System.out.println("Skipping download, already existing with length " + downloadFile.length());
			return new Archive(downloadFile, downloadFile.length());
		}

		System.out.println("Downloading to " + downloadFile);
		downloadFile.getParentFile().mkdirs();
		try (FileOutputStream out = new FileOutputStream(downloadFile)) {
			long size = new DownloadTask(Download.create(url), out, token).execute();
			return new Archive(downloadFile, size);
		}
		catch (Exception e) {
			// the stream is closed by now, so the partial file can be deleted (also on Windows)
			System.out.print("Error downloading file, removing...");
			if (!downloadFile.delete()) {
				System.err.print("Couldn't delete partially downloaded file (" + downloadFile + ")! ");
			}
			System.out.println("done.");
			throw e;
		}
	}

	private Inspection inspect(Archive archive) throws Exception {
		token.throwIfCancelled();
		System.out.println("Inspecting downloaded archive..." + archive.file());
		try (InputStream in = new BufferedInputStream(new FileInputStream(archive.file()))) {
			List<ArchiveEntry> entries = new InspectZipTask(in, token).execute();
			return new Inspection(archive, findExistingFiles(entries));
		}
	}

	/** @return all files of the package by relative path, or null if none of them exists yet */
	private Map<String, File> findExistingFiles(List<ArchiveEntry> entries) {
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
		return existingFile ? files : null;
	}

	/** Runs on the UI executor, because the listener may show a dialog. */
	private ExtractPlan askOverwrites(Inspection inspection) {
		token.throwIfCancelled();
		if (inspection.existingFiles() == null) {
			return new ExtractPlan(inspection.archive(), null);
		}

		List<File> overwrites = listener.chooseOverwrites(inspection.existingFiles());
		if (overwrites == null || overwrites.isEmpty()) {
			System.out.println("Canceling install");
			throw new CancelledException();
		}
		return new ExtractPlan(inspection.archive(), overwrites);
	}

	private PackageFileList extract(ExtractPlan plan) {
		// The user may have pressed cancel while the overwrite question was open
		token.throwIfCancelled();
		System.out.println("Starting extraction");

		ExtractTask extractTask = null;
		try (InputStream in = new BufferedInputStream(new FileInputStream(plan.archive().file()))) {
			extractTask = new ExtractTask(in,
				plan.archive().size(),
				installedPackage,
				installDirectory.get(),
				installedPackage.getExtractMapping(),
				plan.overwrites(),
				token);
			return extractTask.execute();
		}
		catch (Exception e) {
			// Hand the files written so far to cleanup. Safe to read: extraction has stopped.
			PackageFileList written = extractTask != null
				? extractTask.getExtractedFiles()
				: new PackageFileList(installedPackage.getId());
			throw new InstallFailedException(e, written);
		}
	}

	/** Runs on the UI executor. Tells the listener the outcome. */
	private Void finish(PackageFileList installedFiles, Throwable error) {
		if (error == null) {
			System.out.println("Success installing " + installedPackage.getId());
			listener.succeeded(installedFiles);
		}
		else {
			InstallFailure failure = InstallFailure.of(error, installedPackage.getId());
			System.out.println("Install of " + installedPackage.getId() + " stopped: " + failure);
			if (failure.getKind() == InstallFailure.Kind.UNEXPECTED) {
				failure.getCause().printStackTrace();
			}
			listener.failed(failure);
		}
		return null;
	}
}
