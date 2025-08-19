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
package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.repackage.ExtractMapping;
import de.haukerehfeld.quakeinjector.utils.RelativePath;
import de.haukerehfeld.quakeinjector.utils.Utils;
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

import javax.swing.SwingWorker;

/**
 * Install maps in a worker thread
 * Init once and let swing start it - don't reuse
 */
public class InstallWorker extends SwingWorker<PackageFileList, Void> implements
																	  ProgressListener,
																	  Cancelable {

	private final File baseDirectory;
	private final ExtractMapping extractMapping;
	private final Package map;
	private final InputStream input;
	private final List<File> overwrites;

	private long downloadSize = 0;

	/**
	 * files that got installed
	 */
	private final PackageFileList files;

	/**
	 * @param inputSize size of the input stream in bytes, for progress reporting
	 * @param overwrites a list of files that we should overwrite, or
	 * null if everything should be overwritten
	 */
	public InstallWorker(InputStream input,
	                     long inputSize,
	                     Package map,
	                     File baseDirectory,
	                     ExtractMapping extractMapping,
	                     List<File> overwrites) {
		this.map = map;
		this.input = input;
		this.downloadSize = inputSize;
		this.baseDirectory = baseDirectory;
		this.extractMapping = extractMapping;
		this.files = new PackageFileList(map.getId());
		this.overwrites = overwrites;
	}

	@Override
	public PackageFileList doInBackground() throws IOException,
            FileNotFoundException,
            Installer.CancelledException, ArchiveException {
		System.out.println("Installing " + map.getId());

		unzip(input,
		      baseDirectory,
		      map.getId(),
		      overwrites);
		
		map.setInstalled(true);
		return files;
	}


	/**
	 * Unzip from the inputstream to the quake base dir
	 */
	public void unzip(InputStream in,
	                            File basedir,
	                            String mapid,
	                            List<File> overwrites)
            throws IOException, FileNotFoundException, Installer.CancelledException, ArchiveException {
		//build progress filter chain
		ProgressListener progress =
			    new SumProgressListener(
					new PercentageProgressListener(downloadSize,
					                               new CheckCanceledProgressListener(this,
					                                                                 this)));

		ArchiveInputStream<? extends ArchiveEntry> archiveStream = new ArchiveStreamFactory()
				.createArchiveInputStream(in);
		ArchiveEntry sourceEntry;

		boolean extracted = false;
		while((sourceEntry = archiveStream.getNextEntry()) != null) {
			File targetWritable = getTargetFile(sourceEntry);
            if (targetWritable == null) {
                continue;
            }
			String filename = RelativePath.getRelativePath(basedir, targetWritable).toString();

			if (overwrites != null && overwrites.indexOf(targetWritable) < 0) {
				System.out.println("Skipping " + filename + ", because it isn't supposed to be overwritten.");
				continue;
			}

			//create dirs
			List<File> createdDirs = Utils.mkdirs(targetWritable);

			//do nothing for directories other than creating them
			if (!sourceEntry.isDirectory()) {
				File overwrittenFile = targetWritable;
				if (targetWritable.exists()) {
					//create Temp file and rename later
					targetWritable = File.createTempFile("quakeinjector", ".tmp", targetWritable.getParentFile());
					System.out.println("create Temp file " + targetWritable);
				}

				System.out.println("Writing " + filename + " (" + sourceEntry.getSize() + " B)");

				long crc;
				try {
					crc = Utils.writeFile(archiveStream,
					                      targetWritable,
					                      new CompressedProgressListener(sourceEntry.getSize()
					                                                     / (double) sourceEntry.getSize(),
					                                                     progress));
				}
				catch (FileNotFoundException e) {
					throw new FileNotWritableException(e.getMessage());
				}

				if (sourceEntry instanceof ZipArchiveEntry && crc != ((ZipArchiveEntry) sourceEntry).getCrc()) {
					System.err.println("Crc32 didn't match on extraction of " + overwrittenFile + ", removing...");
					targetWritable.delete();
					continue;
				}

				/** @todo 2009-12-19 03:03 hrehfeld    add crc calculation */
				FileInfo info = new FileInfo(filename, crc);
				files.add(info);


				for (File dirname: createdDirs) {
					//save relative paths to files so we can delete dirs later
					files.add(new FileInfo(RelativePath.getRelativePath(basedir, dirname).toString(), 0));
				}

				//if we extracted to temp, rename
				if (!targetWritable.equals(overwrittenFile)) {
					overwrittenFile.delete();
					System.out.println("moving Temp file to " + overwrittenFile);
					targetWritable.renameTo(overwrittenFile);
				}

				extracted = true;
			}
		}

		if (!extracted) {
			throw new java.util.zip.ZipException("No files extracted from zip, is it an empty file?");
		}
		archiveStream.close(); //FIXME close with try-finally
	}

	private File getTargetFile(ArchiveEntry sourceEntry) {
		String extractPath = extractMapping.remap(sourceEntry.getName());
        if (extractPath != null) {
            return new File(baseDirectory, extractPath);
        } else {
            return null;
        }
	}

	public void publish(long progress) {
		if (progress <= 100) {
			setProgress((int) progress);
		}
	}

	public void checkCancelled() throws Installer.CancelledException {
		if (isCancelled()) {
			System.out.println("canceling...");
			throw new Installer.CancelledException();
		}
	}

	public PackageFileList getInstalledFiles() {
		return files;
	}
}