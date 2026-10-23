package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.CheckInstalled;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseSolrJsonParser;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks which packages are installed, using the test database as the cached database
 * and a temp directory as the Quake directory.
 */
public class CheckInstalledTest {
	@TempDir
	Path tmp;

	private Path engineDir;
	private Configuration config;
	/** The package used in the tests: the smallest one with several files, all of known size. */
	private PackageFileList pkg;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		Path cachedDatabase = tmp.resolve("database.json");
		try (InputStream in = getClass().getResourceAsStream("/solr2pp.json")) {
			Files.copy(in, cachedDatabase);
		}
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.LocalDatabaseFile.set(cachedDatabase.toFile());

		List<Requirement> packages;
		try (InputStream in = Files.newInputStream(cachedDatabase)) {
			packages = new PackageDatabaseSolrJsonParser(config).parse(in);
		}
		pkg = packages.stream()
			.filter(r -> r instanceof Package)
			.map(r -> ((Package) r).getSupposedFileList())
			.filter(list -> list.size() >= 2 && list.size() <= 7 && allSizesKnown(list))
			.min(Comparator.comparingLong(CheckInstalledTest::totalSize))
			.orElseThrow();
	}

	private static boolean allSizesKnown(PackageFileList list) {
		for (FileInfo f : list) {
			if (f.getSize() <= 0) {
				return false;
			}
		}
		return true;
	}

	private static long totalSize(PackageFileList list) {
		long total = 0;
		for (FileInfo f : list) {
			total += f.getSize();
		}
		return total;
	}

	/** Write all files of the package with their expected sizes, as if it was installed. */
	private void installPackageFiles() throws Exception {
		for (FileInfo f : pkg) {
			Path file = engineDir.resolve(f.getName());
			Files.createDirectories(file.getParent());
			Files.write(file, new byte[(int) f.getSize()]);
		}
	}

	private Path firstFile() {
		return engineDir.resolve(pkg.iterator().next().getName());
	}

	private List<String> check(Set<String> knownIds, CancellationToken token, List<Long> progress) throws Exception {
		List<String> ids = new ArrayList<>();
		for (PackageFileList list : new CheckInstalled(config, engineDir.toString(), knownIds, token,
				(current, total) -> progress.add(current)).execute()) {
			ids.add(list.getId());
		}
		return ids;
	}

	private List<String> check(Set<String> knownIds) throws Exception {
		return check(knownIds, new CancellationToken(), new ArrayList<>());
	}

	@Test
	public void findsAPackageWhoseFilesAreAllThere() throws Exception {
		installPackageFiles();

		assertEquals(List.of(pkg.getId()), check(Set.of(pkg.getId())));
	}

	@Test
	public void aMissingFileMeansNotInstalled() throws Exception {
		installPackageFiles();
		Files.delete(firstFile());

		assertEquals(List.of(), check(Set.of(pkg.getId())));
	}

	@Test
	public void aFileOfTheWrongSizeMeansNotInstalled() throws Exception {
		installPackageFiles();
		Files.write(firstFile(), new byte[1 + (int) Files.size(firstFile())]);

		assertEquals(List.of(), check(Set.of(pkg.getId())));
	}

	@Test
	public void onlyChecksKnownPackages() throws Exception {
		installPackageFiles();

		assertEquals(List.of(), check(Set.of()));
	}

	@Test
	public void noCachedDatabaseFindsNothing() throws Exception {
		installPackageFiles();
		config.LocalDatabaseFile.set(tmp.resolve("missing.json").toFile());

		assertEquals(List.of(), check(Set.of(pkg.getId())));
	}

	@Test
	public void cancelStopsTheCheck() {
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(CancelledException.class, () -> check(Set.of(pkg.getId()), token, new ArrayList<>()));
	}

	@Test
	public void progressGoesUpTo100() throws Exception {
		List<Long> progress = new ArrayList<>();

		check(Set.of(pkg.getId()), new CancellationToken(), progress);

		assertTrue(progress.size() > 10, "progress per package expected: " + progress.size());
		assertTrue(progress.stream().allMatch(p -> p >= 0 && p <= 100), "out of range: " + progress);
		assertEquals(100L, progress.get(progress.size() - 1));
	}
}
