package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.LoadedPackageDatabase;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseCacheTask;
import de.haukerehfeld.quakeinjector.model.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loading the package database, with a file:// URL standing in for the server.
 */
public class PackageDatabaseCacheTaskTest {
	@TempDir
	Path tmp;

	private Path remoteDatabase;
	private Path cache;
	private Configuration config;

	@BeforeEach
	public void setUp() throws Exception {
		remoteDatabase = tmp.resolve("remote/database.json");
		Files.createDirectories(remoteDatabase.getParent());
		try (InputStream in = getClass().getResourceAsStream("/solr2pp.json")) {
			Files.copy(in, remoteDatabase);
		}
		cache = tmp.resolve("cache/database.json");
		Files.createDirectories(cache.getParent());

		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.LocalDatabaseFile.set(cache.toFile());
	}

	private LoadedPackageDatabase load(Path databaseUrl) throws Exception {
		config.RepositoryDatabasePath.set(databaseUrl.toUri().toURL().toString());
		return new PackageDatabaseCacheTask(config).execute();
	}

	@Test
	public void downloadedDatabaseIsNotFromCache() throws Exception {
		LoadedPackageDatabase database = load(remoteDatabase);

		assertFalse(database.fromCache());
		assertFalse(database.packages().isEmpty());
		assertTrue(Files.size(cache) > 0, "the download must be kept as the new cache");
	}

	@Test
	public void failedDownloadFallsBackToTheCacheAndSaysSo() throws Exception {
		int downloaded = load(remoteDatabase).packages().size();

		LoadedPackageDatabase database = load(tmp.resolve("remote/missing.json"));

		assertTrue(database.fromCache(), "the app shows a warning based on this");
		assertEquals(downloaded, database.packages().size());
		assertNoTempFiles();
	}

	@Test
	public void failsWhenNeitherDownloadNorCacheWork() throws Exception {
		assertThrows(IOException.class, () -> load(tmp.resolve("remote/missing.json")));
		assertNoTempFiles();
	}

	private void assertNoTempFiles() throws IOException {
		try (Stream<Path> files = Files.list(cache.getParent())) {
			assertEquals(0, files.filter(f -> !f.equals(cache)).count(), "temp files left behind");
		}
	}
}
