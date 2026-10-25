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
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Uninstall a package by removing all its files.
 */
public class UninstallTask implements AsyncTask<Void> {
	private final String baseDirectory;
	private final PackageFileList files;
	private final AsyncProgressListener progressListener;

	public UninstallTask(PackageFileList files, String baseDirectory) {
		this(files, baseDirectory, null);
	}

	public UninstallTask(PackageFileList files, String baseDirectory, AsyncProgressListener progressListener) {
		this.files = files;
		this.baseDirectory = baseDirectory;
		this.progressListener = progressListener;
	}

	@Override
	public Void execute() throws Exception {
		uninstall(files);
		return null;
	}

	private void uninstall(PackageFileList files) {
		// Delete files before the directories that contain them, otherwise deleting a directory fails
		// because it isn't empty yet. A path always sorts after its parent ("maps" < "maps/a.bsp"),
		// so going through the names in reverse order deletes children first.
		List<FileInfo> ordered = new ArrayList<>();
		files.forEach(ordered::add);
		ordered.sort(Comparator.comparing(FileInfo::getName).reversed());

		final int fileCount = files.size();
		int i = 1;
		Iterator<FileInfo> it = ordered.iterator();

		while (it.hasNext()) {
			FileInfo file = it.next();
			File f = new File(baseDirectory + File.separator + file.getName());

			long supposedCrc = file.getChecksum();
			String skipMsg = "Couldn't check CRC for " + f + ", deleting...";

			if (supposedCrc != 0) {
				try {
					BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
					long crc = Utils.getCrc32(in, null);
					in.close();
					if (crc != supposedCrc) {
						System.err.println("CRC for " + f + " didn't match, not deleting");
						continue;
					}
				} catch (IOException e) {
					System.err.println(skipMsg);
				}
			} else if (file.getSize() != 0) {
				if (!f.exists() || !f.isFile() || f.length() != file.getSize()) {
					System.err.printf("Size for %s didn't match: expected %d but was %d. Not deleting.%n",
						f.getName(), file.getSize(), f.length());
					continue;
				}
			} else {
				System.err.println(skipMsg);
			}

			if (!f.delete()) {
				System.out.println("Couldn't delete " + f);
			} else {
				System.out.println("Deleted file " + f);
			}

			int progress = i * 100 / fileCount;
			reportProgress(progress, 100);
			i++;
		}
	}

	private void reportProgress(long current, long total) {
		if (progressListener != null) {
			progressListener.onProgress(current, total);
		}
	}
}
