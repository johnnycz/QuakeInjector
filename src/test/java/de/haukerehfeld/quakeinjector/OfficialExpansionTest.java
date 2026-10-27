package de.haukerehfeld.quakeinjector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haukerehfeld.quakeinjector.model.OfficialExpansion;

/**
 * The hardcoded list of official expansions and how they are found in the Quake directory.
 */
public class OfficialExpansionTest {
	@TempDir
	Path quakeDir;

	private final OfficialExpansion rogue = OfficialExpansion.forId("rogue").orElseThrow();

	@Test
	public void theMissionPacksAreOnTheListWithTheirNames() {
		assertEquals("Scourge of Armagon (Mission Pack 1)",
			OfficialExpansion.forId("hipnotic").orElseThrow().name());
		assertEquals("Dissolution of Eternity (Mission Pack 2)", rogue.name());
		assertEquals(2, OfficialExpansion.ALL.size());
	}

	@Test
	public void otherPackagesAreNotOnTheList() {
		assertTrue(OfficialExpansion.forId("quoth").isEmpty());
		assertTrue(OfficialExpansion.forId(null).isEmpty());
	}

	@Test
	public void idsMatchIgnoringCase() {
		assertEquals(rogue, OfficialExpansion.forId("Rogue").orElseThrow());
	}

	@Test
	public void installedWhenItsPakFileIsThere() throws Exception {
		Files.createFile(Files.createDirectories(quakeDir.resolve("rogue")).resolve("pak0.pak"));

		assertTrue(rogue.isInstalledIn(quakeDir.toFile()));
	}

	@Test
	public void upperCaseNamesCount() throws Exception {
		Files.createFile(Files.createDirectories(quakeDir.resolve("ROGUE")).resolve("PAK0.PAK"));

		assertTrue(rogue.isInstalledIn(quakeDir.toFile()));
	}

	@Test
	public void aFolderWithoutThePakFileIsNotInstalled() throws Exception {
		Files.createDirectories(quakeDir.resolve("rogue"));
		Files.createFile(quakeDir.resolve("rogue").resolve("readme.txt"));

		assertFalse(rogue.isInstalledIn(quakeDir.toFile()));
	}

	@Test
	public void aPakFolderIsNotThePakFile() throws Exception {
		Files.createDirectories(quakeDir.resolve("rogue").resolve("pak0.pak"));

		assertFalse(rogue.isInstalledIn(quakeDir.toFile()));
	}

	@Test
	public void anotherPacksFilesDontCount() throws Exception {
		Files.createFile(Files.createDirectories(quakeDir.resolve("hipnotic")).resolve("pak0.pak"));

		assertFalse(rogue.isInstalledIn(quakeDir.toFile()));
	}

	@Test
	public void noQuakeDirectoryMeansNotInstalled() {
		assertFalse(rogue.isInstalledIn(null));
		assertFalse(rogue.isInstalledIn(new File(quakeDir.toFile(), "missing")));
	}
}
