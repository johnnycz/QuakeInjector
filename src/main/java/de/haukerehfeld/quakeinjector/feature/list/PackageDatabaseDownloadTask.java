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
package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.Download;

import java.io.*;
import java.util.List;

/**
 * Download and parse the package database, and keep the download as the new cache file
 * ({@link PackageDatabaseCache}) once all of it was read. Doesn't fall back to the cache.
 */
public class PackageDatabaseDownloadTask implements AsyncTask<List<Requirement>> {
	private final Configuration config;
	private final CancellationToken token;

	/**
	 * @param token cancelling it closes the download
	 */
	public PackageDatabaseDownloadTask(Configuration config, CancellationToken token) {
		this.config = config;
		this.token = token;
	}

	/**
	 * @throws CancelledException if the token was cancelled
	 * @throws IOException if downloading or parsing failed; the cache file is left as it was
	 */
	@Override
	public List<Requirement> execute() throws IOException, CancelledException {
		token.throwIfCancelled();
		File cache = config.LocalDatabaseFile.get().getAbsoluteFile();
		File tmpFile = File.createTempFile(cache.getName(), null, cache.getParentFile());
		try {
			List<Requirement> packages;
			// Closing the stream on cancel wakes up a read() that is blocked on the network
			try (InputStream download = downloadDatabase(config.RepositoryDatabasePath.get());
			     CancellationToken.Registration ignored = token.onCancel(() -> closeQuietly(download));
			     OutputStream copy = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
				packages = new PackageDatabaseSolrJsonParser(config)
					.parse(new DumpInputStream(new BufferedInputStream(download), copy));
			}
			token.throwIfCancelled();
			updateCacheFile(cache, tmpFile);
			return packages;
		} catch (IOException | RuntimeException e) {
			if (token.isCancelled()) {
				// the stream was closed by cancel(), so this is a cancellation, not a failed download
				throw new CancelledException();
			}
			throw e;
		} finally {
			// still there if anything failed
			if (tmpFile.exists()) {
				tmpFile.delete();
			}
		}
	}

	private InputStream downloadDatabase(String databaseUrl) throws IOException {
		Download d = Download.create(databaseUrl);
		d.connect();
		return d.getStream(null);
	}

	private void updateCacheFile(File cache, File tmpFile) {
		if (cache.exists()) {
			if (!cache.delete()) {
				System.err.println("Couldn't delete the real cache file!");
			}
		}
		if (!tmpFile.renameTo(cache)) {
			System.err.println("Couldn't move the temporary cache file to the real cache file!");
		}
	}

	private static void closeQuietly(InputStream in) {
		try {
			in.close();
		} catch (IOException e) {
			System.err.println("Couldn't close the database download: " + e.getMessage());
		}
	}
}
