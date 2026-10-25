package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseCache;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseDownloadTask;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Downloading the package database and reading the cached copy, with a file:// URL standing in for the server.
 */
public class PackageDatabaseDownloadTaskTest {
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

	private List<Requirement> download(Path databaseUrl) throws Exception {
		config.RepositoryDatabasePath.set(databaseUrl.toUri().toURL().toString());
		return new PackageDatabaseDownloadTask(config, new CancellationToken()).execute();
	}

	@Test
	public void downloadIsKeptAsTheNewCache() throws Exception {
		List<Requirement> downloaded = download(remoteDatabase);

		assertFalse(downloaded.isEmpty());
		assertEquals(Files.size(remoteDatabase), Files.size(cache));
		assertNoTempFiles();
	}

	@Test
	public void failedDownloadFailsAndLeavesTheCacheAlone() throws Exception {
		download(remoteDatabase);
		long cached = Files.size(cache);

		assertThrows(IOException.class, () -> download(tmp.resolve("remote/missing.json")));
		assertEquals(cached, Files.size(cache));
		assertNoTempFiles();
	}

	@Test
	public void cancelledDownloadThrowsCancelled() {
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(CancelledException.class, () -> new PackageDatabaseDownloadTask(config, token).execute());
		assertFalse(Files.exists(cache));
		assertNoTempFiles();
	}

	@Test
	public void cacheReadsTheLastDownload() throws Exception {
		int downloaded = download(remoteDatabase).size();

		assertEquals(downloaded, new PackageDatabaseCache(config).read().orElseThrow().size());
	}

	@Test
	public void noCacheFileReadsAsEmpty() {
		assertTrue(new PackageDatabaseCache(config).read().isEmpty());
	}

	@Test
	public void brokenCacheFileReadsAsEmpty() throws Exception {
		Files.writeString(cache, "{ not json");

		assertTrue(new PackageDatabaseCache(config).read().isEmpty());
	}

	private void assertNoTempFiles() {
		try (Stream<Path> files = Files.list(cache.getParent())) {
			assertEquals(0, files.filter(f -> !f.equals(cache)).count(), "temp files left behind");
		} catch (IOException e) {
			fail(e);
		}
	}
}
