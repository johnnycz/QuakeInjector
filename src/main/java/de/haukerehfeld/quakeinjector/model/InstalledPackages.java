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
package de.haukerehfeld.quakeinjector.model;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Reads the installed packages file in the background.
 */
public class InstalledPackages {
	private volatile CompletableFuture<List<PackageFileList>> installedPackages;

	/**
	 * Start reading the installed packages file on {@code executor}.
	 * Use the thread that also writes the file, so a read never sees a half-written one.
	 */
	public void parse(File installedPackagesXml, Executor executor) {
		installedPackages = CompletableFuture.supplyAsync(() -> {
			try {
				return parseInstalledPackagesFile(installedPackagesXml);
			}
			catch (IOException e) {
				throw new CompletionException(e);
			}
		}, executor);
	}

	/**
	 * The result of the last {@link #parse}. Fails with a {@link NoInstalledPackagesFileException} if there
	 * is no file yet (nothing installed), or another {@link IOException} if it couldn't be read.
	 * Chain on it rather than blocking: the read may wait for an install that is using the disk thread.
	 */
	public CompletableFuture<List<PackageFileList>> loaded() {
		return installedPackages;
	}

	/**
	 * Thrown if no installed packages xml exists
	 */
	public static class NoInstalledPackagesFileException extends FileNotFoundException {
		public NoInstalledPackagesFileException(String msg) { super(msg); }
		public NoInstalledPackagesFileException() { super(); }
	}

	private List<PackageFileList> parseInstalledPackagesFile(File installedPackagesFile) throws IOException {
		if (!installedPackagesFile.exists()) {
			throw new NoInstalledPackagesFileException("No Installed packages file (" + installedPackagesFile + ")"
			                                       + ", no packages installed?");
		}
		// closed even if parsing fails
		try (InputStream in = new BufferedInputStream(new FileInputStream(installedPackagesFile))) {
			return new InstalledPackageList().read(in);
		}
	}
}
