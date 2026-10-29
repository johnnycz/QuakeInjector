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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Extract a package's files from an archive into the install directory.
 * This is the last stage of a package install.
 */
public class ExtractTask implements AsyncTask<PackageFileList> {
	private final File baseDirectory;
	private final ExtractMapping extractMapping;
	private final Package map;
	private final InputStream input;
	private final List<File> overwrites;
	private final long downloadSize;
	private final CancellationToken token;
	private final AsyncProgressListener progressListener;

	private final PackageFileList files;

	/**
	 * @param input the package archive input stream
	 * @param inputSize size of the input stream in bytes, for progress reporting
	 * @param map the package being installed
	 * @param baseDirectory the installation base directory
	 * @param extractMapping rules for remapping archive paths
	 * @param overwrites a list of files that are allowed to be overwritten,
	 *                   or null if everything should be overwritten
	 * @param token cancel signal, checked between entries and between written chunks
	 */
	public ExtractTask(InputStream input, long inputSize, Package map,
	                   File baseDirectory, ExtractMapping extractMapping,
	                   List<File> overwrites, CancellationToken token) {
		this(input, inputSize, map, baseDirectory, extractMapping, overwrites, token, null);
	}

	/**
	 * @param input the package archive input stream
	 * @param inputSize size of the input stream in bytes, for progress reporting
	 * @param map the package being installed
	 * @param baseDirectory the installation base directory
	 * @param extractMapping rules for remapping archive paths
	 * @param overwrites a list of files that are allowed to be overwritten,
	 *                   or null if everything should be overwritten
	 * @param token cancel signal, checked between entries and between written chunks
	 * @param progressListener optional listener, gets 0..100: the share of the archive read so far
	 */
	public ExtractTask(InputStream input, long inputSize, Package map,
	                   File baseDirectory, ExtractMapping extractMapping,
	                   List<File> overwrites, CancellationToken token,
	                   AsyncProgressListener progressListener) {
		this.map = map;
		this.token = token;
		this.input = input;
		this.downloadSize = inputSize;
		// absolute and normalized like the files SafePath returns, so relative names can be taken from it
		this.baseDirectory = baseDirectory.toPath().toAbsolutePath().normalize().toFile();
		this.extractMapping = extractMapping;
		this.overwrites = overwrites;
		this.progressListener = progressListener;
		this.files = new PackageFileList(map.getId());
	}

	@Override
	public PackageFileList execute() throws IOException, FileNotFoundException,
	                                         CancelledException, ArchiveException {
		System.out.println("Extracting " + map.getId());

		// Only extracts; marking the package as installed is up to whoever runs the install
		unzip(input, baseDirectory, overwrites);
		// the zip reader stops at the central directory, so the archive is never read to its very end
		reportProgress(100);

		return files;
	}

	/**
	 * Unzip from the input stream to the quake base directory.
	 */
	private void unzip(InputStream in, File basedir, List<File> overwrites)
	        throws IOException, FileNotFoundException, CancelledException, ArchiveException {
		// Progress is the share of the (compressed) archive read so far. Counting the uncompressed
		// bytes written against the archive size would pass 100% for any file that compresses well.
		ProgressListener archiveRead = new SumProgressListener(
			new PercentageProgressListener(downloadSize, this::reportProgress));
		// The archive factory needs mark/reset, so buffer on top of the counting stream
		InputStream counted = new BufferedInputStream(new ProgressListenerInputStream(in, archiveRead));

		// Check for cancel before each chunk gets written
		ProgressListener cancelCheck = writtenBytes -> token.throwIfCancelled();

		try (ArchiveInputStream<? extends ArchiveEntry> archiveStream = new ArchiveStreamFactory()
				.createArchiveInputStream(counted)) {
			unzipEntries(archiveStream, basedir, overwrites, cancelCheck);
		}
	}

	private void unzipEntries(ArchiveInputStream<? extends ArchiveEntry> archiveStream, File basedir,
	                          List<File> overwrites, ProgressListener cancelCheck)
	        throws IOException, CancelledException {
		ArchiveEntry sourceEntry;

		boolean extracted = false;
		while ((sourceEntry = archiveStream.getNextEntry()) != null) {
			if (token.isCancelled()) {
				System.out.println("Extraction cancelled");
				throw new CancelledException();
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
				// Record new dirs straight away, so cleanup removes them even if writing the file fails
				recordDirs(basedir, createdDirs);

				File overwrittenFile = targetWritable;
				if (targetWritable.exists()) {
					// Create temp file and rename later
					targetWritable = File.createTempFile("quakeinjector", ".tmp", targetWritable.getParentFile());
					System.out.println("create Temp file " + targetWritable);
				}

				System.out.println("Writing " + filename + " (" + sourceEntry.getSize() + " B)");

				long crc;
				try {
					crc = Utils.writeFile(archiveStream, targetWritable, cancelCheck);
				} catch (IOException | RuntimeException e) {
					// Cancelled or failed mid-file: don't leave a half-written file (or temp file) behind.
					// A file being replaced is untouched, because we were writing to a temp file.
					System.out.println("Extraction stopped (" + e + "), removing partially written " + targetWritable);
					targetWritable.delete();
					if (e instanceof FileNotFoundException) {
						throw new FileNotWritableException(e.getMessage());
					}
					throw e;
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
	}

	/** Save relative paths of created dirs, so uninstall and cleanup can delete them later. */
	private void recordDirs(File basedir, List<File> createdDirs) {
		for (File dirname : createdDirs) {
			files.add(new FileInfo(RelativePath.getRelativePath(basedir, dirname).toString(), 0));
		}
	}

	/**
	 * @return where the entry goes, or null if the extract mapping skips it
	 * @throws UnsafePathException if it would end up outside the install directory; the install stops
	 */
	private File getTargetFile(ArchiveEntry sourceEntry) throws UnsafePathException {
		String extractPath = extractMapping.remap(sourceEntry.getName());
		if (extractPath != null) {
			return SafePath.resolve(baseDirectory, extractPath);
		}
		return null;
	}

	private void reportProgress(long percentage) {
		if (progressListener != null && percentage <= 100) {
			progressListener.onProgress(percentage, 100);
		}
	}

	/**
	 * Files and directories written so far. After a failed or cancelled {@link #execute()}, this is
	 * what needs cleaning up. Only read it after {@code execute()} has returned or thrown.
	 */
	public PackageFileList getExtractedFiles() {
		return files;
	}
}
