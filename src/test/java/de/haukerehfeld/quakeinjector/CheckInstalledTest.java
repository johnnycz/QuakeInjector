package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.CheckInstalled;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks which packages are installed, with a temp directory as the Quake directory.
 */
public class CheckInstalledTest {
	@TempDir
	Path tmp;

	private Path engineDir;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
	}

	/** A package with a map and a readme, of known sizes. */
	private static PackageFileList testmap(String id) {
		PackageFileList list = new PackageFileList(id);
		list.add(new FileInfo("maps/" + id + ".bsp", 0, 3));
		list.add(new FileInfo(id + ".txt", 0, 5));
		return list;
	}

	/** Write all files of the package with their expected sizes, as if it was installed. */
	private void installFiles(PackageFileList list) throws Exception {
		for (FileInfo f : list) {
			Path file = engineDir.resolve(f.getName());
			Files.createDirectories(file.getParent());
			Files.write(file, new byte[(int) f.getSize()]);
		}
	}

	private List<String> check(List<PackageFileList> lists, CancellationToken token, List<Long> progress) {
		List<String> ids = new ArrayList<>();
		for (PackageFileList list : new CheckInstalled(lists, engineDir.toFile(), token,
				(current, total) -> progress.add(current)).execute()) {
			ids.add(list.getId());
		}
		return ids;
	}

	private List<String> check(PackageFileList... lists) {
		return check(List.of(lists), new CancellationToken(), new ArrayList<>());
	}

	@Test
	public void findsAPackageWhoseFilesAreAllThere() throws Exception {
		PackageFileList pkg = testmap("a");
		installFiles(pkg);

		assertEquals(List.of("a"), check(pkg));
	}

	@Test
	public void aMissingFileMeansNotInstalled() throws Exception {
		PackageFileList pkg = testmap("a");
		installFiles(pkg);
		Files.delete(engineDir.resolve("a.txt"));

		assertEquals(List.of(), check(pkg));
	}

	@Test
	public void aFileOutsideTheQuakeDirectoryNeverCounts() throws Exception {
		// otherwise the package would be marked installed and uninstalling it would delete that file
		Files.writeString(tmp.resolve("outside.txt"), "MINE");
		PackageFileList pkg = testmap("a");
		installFiles(pkg);
		pkg.add(new FileInfo("../outside.txt", 0, 0));

		assertEquals(List.of(), check(pkg));
	}

	@Test
	public void aFileOfTheWrongSizeMeansNotInstalled() throws Exception {
		PackageFileList pkg = testmap("a");
		installFiles(pkg);
		Files.write(engineDir.resolve("a.txt"), new byte[6]);

		assertEquals(List.of(), check(pkg));
	}

	@Test
	public void onlyReportsTheInstalledPackages() throws Exception {
		PackageFileList a = testmap("a");
		PackageFileList b = testmap("b");
		installFiles(b);

		assertEquals(List.of("b"), check(a, b));
	}

	@Test
	public void noPackagesFindsNothing() {
		assertEquals(List.of(), check());
	}

	@Test
	public void cancelStopsTheCheck() {
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(CancelledException.class, () -> check(List.of(testmap("a")), token, new ArrayList<>()));
	}

	@Test
	public void progressGoesUpTo100() {
		List<PackageFileList> lists = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			lists.add(testmap("map" + i));
		}
		List<Long> progress = new ArrayList<>();

		check(lists, new CancellationToken(), progress);

		assertTrue(progress.size() > 10, "progress per package expected: " + progress.size());
		assertTrue(progress.stream().allMatch(p -> p >= 0 && p <= 100), "out of range: " + progress);
		assertEquals(100L, progress.get(progress.size() - 1));
	}
}
