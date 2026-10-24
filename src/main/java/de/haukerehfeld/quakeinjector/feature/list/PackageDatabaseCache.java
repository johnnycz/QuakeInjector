package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Requirement;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * The copy of the last downloaded package database ({@code Configuration.LocalDatabaseFile}),
 * written by {@link PackageDatabaseDownloadTask}.
 */
public class PackageDatabaseCache {
	private final Configuration config;

	public PackageDatabaseCache(Configuration config) {
		this.config = config;
	}

	/**
	 * Parse the cache file. Runs on any thread.
	 *
	 * @return empty if there is no cache file yet, or it couldn't be read (logged)
	 */
	public Optional<List<Requirement>> read() {
		File cache = config.LocalDatabaseFile.get().getAbsoluteFile();
		if (!cache.isFile()) {
			System.out.println("No cached package database yet (" + cache + ")");
			return Optional.empty();
		}
		try (InputStream in = new BufferedInputStream(new FileInputStream(cache))) {
			return Optional.of(new PackageDatabaseSolrJsonParser(config).parse(in));
		} catch (Exception e) {
			System.err.println("Couldn't read the cached package database " + cache + ": " + e);
			return Optional.empty();
		}
	}
}
