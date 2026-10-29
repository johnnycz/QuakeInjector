package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.utils.SafePath;
import de.haukerehfeld.quakeinjector.utils.UnsafePathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SafePathTest {
	@TempDir
	Path tmp;

	private File base;

	@BeforeEach
	public void setUp() throws Exception {
		base = Files.createDirectories(tmp.resolve("quake")).toFile();
	}

	private void assertUnsafe(String path) {
		assertThrows(UnsafePathException.class, () -> SafePath.resolve(base, path), path);
		assertFalse(SafePath.isInside(base, path), path);
	}

	@Test
	public void pathsInsideResolveToTheFileInTheDirectory() throws Exception {
		assertEquals(tmp.resolve("quake/maps/a.bsp").toFile(), SafePath.resolve(base, "maps/a.bsp"));
		assertEquals(tmp.resolve("quake/a.txt").toFile(), SafePath.resolve(base, "maps/../a.txt"));
		assertEquals(tmp.resolve("quake/maps").toFile(), SafePath.resolve(base, "./maps/"));
	}

	@Test
	public void anEmptyPathIsTheDirectoryItself() throws Exception {
		assertEquals(base, SafePath.resolve(base, ""));
	}

	@Test
	public void theResultIsAbsoluteAndNormalizedEvenForARelativeDirectory() throws Exception {
		File relativeBase = new File("some/../quake");
		File resolved = SafePath.resolve(relativeBase, "maps/a.bsp");

		assertTrue(resolved.isAbsolute());
		assertEquals(new File("quake/maps/a.bsp").getAbsoluteFile(), resolved);
	}

	@Test
	public void goingUpOutOfTheDirectoryIsRefused() {
		assertUnsafe("..");
		assertUnsafe("../a.txt");
		assertUnsafe("maps/../../a.txt");
		assertUnsafe("../../../../../../../../a.txt");
	}

	@Test
	public void aSiblingWhoseNameStartsTheSameIsOutside() {
		assertUnsafe("../quake2/a.txt");
		assertUnsafe("../quake.txt");
	}

	@Test
	public void absolutePathsAreRefused() {
		assertUnsafe(tmp.resolve("a.txt").toAbsolutePath().toString());
		// even one that happens to point into the directory: the database has no business naming those
		assertUnsafe(tmp.resolve("quake/a.txt").toAbsolutePath().toString());
	}

	@Test
	public void namesOfOnlyDotsAreRefused() {
		assertUnsafe("...");
		assertUnsafe("maps/..../a.txt");
	}

	@Test
	public void noPathOrNoDirectoryIsRefused() {
		assertThrows(UnsafePathException.class, () -> SafePath.resolve(base, null));
		assertThrows(UnsafePathException.class, () -> SafePath.resolve(null, "a.txt"));
		assertFalse(SafePath.isInside(null, "a.txt"));
	}

	@Test
	public void theMessageNamesThePath() {
		UnsafePathException e = assertThrows(UnsafePathException.class, () -> SafePath.resolve(base, "../evil.bat"));

		assertEquals("../evil.bat", e.getPath());
		assertTrue(e.getMessage().contains("../evil.bat"), e.getMessage());
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	public void windowsPathsOutsideAreRefused() {
		assertUnsafe("..\\a.txt");
		assertUnsafe("maps\\..\\..\\a.txt");
		assertUnsafe("C:\\Windows\\a.txt");
		assertUnsafe("C:a.txt");
		assertUnsafe("\\a.txt");
		assertUnsafe("\\\\server\\share\\a.txt");
		assertUnsafe("//server/share/a.txt");
		// alternate data streams and other invalid names
		assertUnsafe("a.txt:stream");
		assertUnsafe("a*.txt");
		assertUnsafe(". .\\a.txt");
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	public void windowsSeparatorsInsideWork() throws Exception {
		assertEquals(tmp.resolve("quake/maps/a.bsp").toFile(), SafePath.resolve(base, "maps\\a.bsp"));
	}
}
