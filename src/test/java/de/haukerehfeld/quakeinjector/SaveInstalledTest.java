package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static org.junit.jupiter.api.Assertions.*;

public class SaveInstalledTest {
	@TempDir
	Path tmp;

	private static Package installed(String id, String... files) {
		Package pkg = TestZip.testPackage(id, new ExtractMapping(), "unused");
		PackageFileList list = new PackageFileList(id);
		for (String f : files) {
			list.add(new FileInfo(f, 1));
		}
		pkg.setFileList(list);
		pkg.setInstalled(true);
		return pkg;
	}

	@Test
	public void snapshotContainsOnlyInstalledPackages() {
		Package a = installed("a", "maps/a.bsp");
		Package b = TestZip.testPackage("b", new ExtractMapping(), "unused");

		Map<String, Iterable<FileInfo>> snapshot = SaveInstalled.snapshot(List.of(a, b));

		assertEquals(List.of("a"), List.copyOf(snapshot.keySet()));
	}

	@Test
	public void snapshotIsNotAffectedByLaterChanges() {
		Package a = installed("a", "maps/a.bsp");
		Map<String, Iterable<FileInfo>> snapshot = SaveInstalled.snapshot(List.of(a));

		a.getFileList().add(new FileInfo("maps/added-later.bsp", 1));

		int count = 0;
		for (FileInfo ignored : snapshot.get("a")) {
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	public void writtenSnapshotCanBeReadBack() throws Exception {
		SaveInstalled file = new SaveInstalled(tmp.resolve("installed.xml").toFile());

		file.write(SaveInstalled.snapshot(List.of(installed("a", "maps/a.bsp", "maps"))));
		List<PackageFileList> read = file.read();

		assertEquals(1, read.size());
		assertEquals("a", read.get(0).getId());
		assertTrue(names(read.get(0)).containsAll(List.of("maps/a.bsp", "maps")), "read back: " + names(read.get(0)));
	}
}
