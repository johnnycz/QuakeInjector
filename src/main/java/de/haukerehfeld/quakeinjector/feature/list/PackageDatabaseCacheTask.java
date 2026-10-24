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
 * Fetch and parse the package database with local caching fallback.
 * If download fails, attempts to use previously cached database, and says so in the result.
 */
public class PackageDatabaseCacheTask implements AsyncTask<LoadedPackageDatabase> {
	private final Configuration config;
	private final CancellationToken token;

	public PackageDatabaseCacheTask(Configuration config) {
		this(config, new CancellationToken());
	}

	/**
	 * @param token cancelling it closes the download; a cancelled load doesn't fall back to the cache
	 */
	public PackageDatabaseCacheTask(Configuration config, CancellationToken token) {
		this.config = config;
		this.token = token;
	}

	/**
	 * @throws CancelledException if the token was cancelled
	 * @throws IOException if neither the download nor the cache could be read
	 */
	@Override
	public LoadedPackageDatabase execute() throws IOException, CancelledException {
		token.throwIfCancelled();
		File cache = config.LocalDatabaseFile.get().getAbsoluteFile();

		try {
			return new LoadedPackageDatabase(downloadAndCache(cache), false);
		} catch (Exception e) {
			if (token.isCancelled()) {
				// the stream was closed by cancel(), so this is a cancellation, not a failed download
				throw new CancelledException();
			}
			System.err.println("Failed to download database: " + e.getMessage());

			try (InputStream cached = getCachedDatabaseStream(cache)) {
				return new LoadedPackageDatabase(parseDatabase(cached), true);
			} catch (IOException cacheError) {
				// Neither download nor cache available
				throw new IOException("Cannot download package database or read local cache", e);
			}
		}
	}

	/** Download and parse the database, keeping a copy as the new cache once all of it was read. */
	private List<Requirement> downloadAndCache(File cache) throws IOException {
		File tmpFile = File.createTempFile(cache.getName(), null, cache.getParentFile());
		try {
			List<Requirement> packages;
			// Closing the stream on cancel wakes up a read() that is blocked on the network
			try (InputStream download = downloadDatabase(config.RepositoryDatabasePath.get());
			     CancellationToken.Registration ignored = token.onCancel(() -> closeQuietly(download));
			     OutputStream copy = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
				packages = parseDatabase(new DumpInputStream(new BufferedInputStream(download), copy));
			}
			token.throwIfCancelled();
			updateCacheFile(cache, tmpFile);
			return packages;
		} finally {
			// still there if anything failed
			if (tmpFile.exists()) {
				tmpFile.delete();
			}
		}
	}

	private List<Requirement> parseDatabase(InputStream database) throws IOException {
		final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(config);
		return parser.parse(database);
	}

	private InputStream downloadDatabase(String databaseUrl) throws IOException {
		Download d = Download.create(databaseUrl);
		d.connect();
		return d.getStream(null);
	}

	private BufferedInputStream getCachedDatabaseStream(File cache) throws IOException {
		if (cache != null && cache.exists() && cache.canRead()) {
			return new BufferedInputStream(new FileInputStream(cache));
		}
		throw new IOException("Cannot read local cache file: " + cache);
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
