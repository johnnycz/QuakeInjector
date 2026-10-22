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

import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.utils.*;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Install a package by extracting files from an archive.
 */
public class InstallTask implements AsyncTask<PackageFileList> {
	private final File baseDirectory;
	private final ExtractMapping extractMapping;
	private final Package map;
	private final InputStream input;
	private final List<File> overwrites;
	private final long downloadSize;
	private final AsyncProgressListener progressListener;

	private final PackageFileList files;
	private volatile boolean cancelled = false;

	/**
	 * @param input the package archive input stream
	 * @param inputSize size of the input stream in bytes, for progress reporting
	 * @param map the package being installed
	 * @param baseDirectory the installation base directory
	 * @param extractMapping rules for remapping archive paths
	 * @param overwrites a list of files that are allowed to be overwritten,
	 *                   or null if everything should be overwritten
	 */
	public InstallTask(InputStream input, long inputSize, Package map,
	                    File baseDirectory, ExtractMapping extractMapping,
	                    List<File> overwrites) {
		this(input, inputSize, map, baseDirectory, extractMapping, overwrites, null);
	}

	/**
	 * @param input the package archive input stream
	 * @param inputSize size of the input stream in bytes, for progress reporting
	 * @param map the package being installed
	 * @param baseDirectory the installation base directory
	 * @param extractMapping rules for remapping archive paths
	 * @param overwrites a list of files that are allowed to be overwritten,
	 *                   or null if everything should be overwritten
	 * @param progressListener optional listener for progress updates
	 */
	public InstallTask(InputStream input, long inputSize, Package map,
	                    File baseDirectory, ExtractMapping extractMapping,
	                    List<File> overwrites, AsyncProgressListener progressListener) {
		this.map = map;
		this.input = input;
		this.downloadSize = inputSize;
		this.baseDirectory = baseDirectory;
		this.extractMapping = extractMapping;
		this.overwrites = overwrites;
		this.progressListener = progressListener;
		this.files = new PackageFileList(map.getId());
	}

	@Override
	public PackageFileList execute() throws IOException, FileNotFoundException,
	                                         Installer.CancelledException, ArchiveException {
		System.out.println("Installing " + map.getId());

		unzip(input, baseDirectory, map.getId(), overwrites);

		map.setInstalled(true);
		return files;
	}

	/**
	 * Unzip from the input stream to the quake base directory.
	 */
	private void unzip(InputStream in, File basedir, String mapid, List<File> overwrites)
	        throws IOException, FileNotFoundException, Installer.CancelledException, ArchiveException {
		// Build progress filter chain
		ProgressListener progress = new SumProgressListener(
			new PercentageProgressListener(downloadSize, this::reportProgress));

		ArchiveInputStream<? extends ArchiveEntry> archiveStream = new ArchiveStreamFactory()
			.createArchiveInputStream(in);
		ArchiveEntry sourceEntry;

		boolean extracted = false;
		while ((sourceEntry = archiveStream.getNextEntry()) != null) {
			if (cancelled) {
				System.out.println("Installation cancelled");
				throw new Installer.CancelledException();
			}

			File targetWritable = getTargetFile(sourceEntry);
			if (targetWritable == null) {
				continue;
			}

			String filename = RelativePath.getRelativePath(basedir, targetWritable).toString();

			if (overwrites != null && !overwrites.contains(targetWritable)) {
				System.out.println("Skipping " + filename + ", because it isn't supposed to be overwritten.");
				continue;
			}

			// Create directories
			List<File> createdDirs = Utils.mkdirs(targetWritable);

			// Do nothing for directories other than creating them
			if (!sourceEntry.isDirectory()) {
				File overwrittenFile = targetWritable;
				if (targetWritable.exists()) {
					// Create temp file and rename later
					targetWritable = File.createTempFile("quakeinjector", ".tmp", targetWritable.getParentFile());
					System.out.println("create Temp file " + targetWritable);
				}

				System.out.println("Writing " + filename + " (" + sourceEntry.getSize() + " B)");

				long crc;
				try {
					crc = Utils.writeFile(archiveStream, targetWritable,
						new CompressedProgressListener(sourceEntry.getSize() / (double) sourceEntry.getSize(),
							progress));
				} catch (FileNotFoundException e) {
					throw new FileNotWritableException(e.getMessage());
				}

				if (sourceEntry instanceof ZipArchiveEntry zipEntry) {
					var zipCrc = zipEntry.getCrc();
					if (zipCrc != -1 && zipCrc != crc) {
						System.err.println("Crc32 didn't match on extraction of " + overwrittenFile + ", removing...");
						targetWritable.delete();
						continue;
					}
				}

				FileInfo info = new FileInfo(filename, crc, targetWritable.length());
				files.add(info);

				for (File dirname : createdDirs) {
					// Save relative paths to files so we can delete dirs later
					files.add(new FileInfo(RelativePath.getRelativePath(basedir, dirname).toString(), 0));
				}

				// If we extracted to temp, rename
				if (!targetWritable.equals(overwrittenFile)) {
					overwrittenFile.delete();
					System.out.println("moving Temp file to " + overwrittenFile);
					targetWritable.renameTo(overwrittenFile);
				}

				extracted = true;
			}
		}

		if (!extracted) {
			throw new ZipException("No files extracted from zip, is it an empty file?");
		}

		archiveStream.close();
	}

	private File getTargetFile(ArchiveEntry sourceEntry) {
		String extractPath = extractMapping.remap(sourceEntry.getName());
		if (extractPath != null) {
			return new File(baseDirectory, extractPath);
		}
		return null;
	}

	private void reportProgress(long percentage) {
		if (progressListener != null && percentage <= 100) {
			progressListener.onProgress(percentage, 100);
		}
	}

	public void cancel() {
		this.cancelled = true;
	}

	public PackageFileList getInstalledFiles() {
		return files;
	}
}
