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
import de.haukerehfeld.quakeinjector.utils.Download;

import java.io.*;
import java.util.List;

/**
 * Fetch and parse the package database with local caching fallback.
 * If download fails, attempts to use previously cached database, and says so in the result.
 */
public class PackageDatabaseCacheTask implements AsyncTask<LoadedPackageDatabase> {
	private final Configuration config;

	public PackageDatabaseCacheTask(Configuration config) {
		this.config = config;
	}

	@Override
	public LoadedPackageDatabase execute() throws IOException {
		final String databaseUrl = config.RepositoryDatabasePath.get();
		File cache = config.LocalDatabaseFile.get().getAbsoluteFile();
		File tmpFile = null;

		BufferedInputStream downloadStream = null;
		BufferedOutputStream tmpWriteStream = null;
		BufferedInputStream cacheReadStream = null;

		try {
			// Try to download database and cache it
			downloadStream = new BufferedInputStream(downloadDatabase(databaseUrl));
			tmpFile = File.createTempFile(cache.getName(), null, cache.getParentFile());
			tmpWriteStream = new BufferedOutputStream(new FileOutputStream(tmpFile));

			InputStream db = new DumpInputStream(downloadStream, tmpWriteStream);
			List<Requirement> parseResult = parseDatabase(db);

			// Download succeeded, update cache
			closeStreams(downloadStream, tmpWriteStream, cacheReadStream);
			updateCacheFile(cache, tmpFile);

			return new LoadedPackageDatabase(parseResult, false);
		} catch (Exception e) {
			// Download failed, try to use cached version
			System.err.println("Failed to download database: " + e.getMessage());
			closeStreams(downloadStream, tmpWriteStream, null);

			try {
				cacheReadStream = getCachedDatabaseStream(cache);
				List<Requirement> parseResult = parseDatabase(cacheReadStream);
				closeStreams(null, null, cacheReadStream);
				return new LoadedPackageDatabase(parseResult, true);
			} catch (IOException cacheError) {
				closeStreams(null, null, cacheReadStream);
				// Neither download nor cache available
				throw new IOException("Cannot download package database or read local cache", e);
			} finally {
				// Clean up temp file
				if (tmpFile != null && tmpFile.exists()) {
					tmpFile.delete();
				}
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
			try {
				return new BufferedInputStream(new FileInputStream(cache));
			} catch (IOException ignored) {
			}
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

	private void closeStreams(BufferedInputStream download, BufferedOutputStream write, BufferedInputStream cache) {
		try {
			if (download != null) {
				download.close();
			}
			if (write != null) {
				write.close();
			}
			if (cache != null) {
				cache.close();
			}
		} catch (IOException e) {
			// Ignore close errors
		}
	}
}
