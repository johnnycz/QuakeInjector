package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine executable is stored relative to the Quake directory, as on master: the settings dialog
 * hands over the full path, and the engine starter needs the full path again.
 */
public class EngineExecutableConfigTest {
	@TempDir
	Path tmp;

	private Path quakeDir;
	private Path configFile;
	private Configuration config;

	@BeforeEach
	public void setUp() throws Exception {
		quakeDir = Files.createDirectories(tmp.resolve("quake"));
		configFile = tmp.resolve("config.properties");
		config = new Configuration(configFile.toFile());
		config.EnginePath.set(quakeDir.toFile());
	}

	@Test
	public void fullPathInsideTheQuakeDirIsStoredRelative() {
		config.EngineExecutable.setRelativeTo(quakeDir.toFile(), quakeDir.resolve("bin/quakespasm.exe").toFile());

		assertEquals(new File("bin", "quakespasm.exe"), config.EngineExecutable.get());
		assertFalse(config.EngineExecutable.get().isAbsolute());
	}

	@Test
	public void storesRelativeInTheConfigFileAndReadsItBack() throws Exception {
		config.EngineExecutable.setRelativeTo(quakeDir.toFile(), quakeDir.resolve("quakespasm.exe").toFile());
		config.write();

		String written = Files.readString(configFile);
		assertTrue(written.contains("EngineExecutable=quakespasm.exe"), "config file: " + written);
		assertEquals(new File("quakespasm.exe"), new Configuration(configFile.toFile()).EngineExecutable.get());
	}

	@Test
	public void relativePathIsResolvedAgainstTheQuakeDirForLaunching() {
		config.EngineExecutable.setRelativeTo(quakeDir.toFile(), quakeDir.resolve("bin/quakespasm.exe").toFile());

		assertEquals(quakeDir.resolve("bin/quakespasm.exe").toFile(),
			config.EngineExecutable.resolveAgainst(quakeDir.toFile()));
	}

	@Test
	public void alreadyRelativePathStaysAsItIs() {
		config.EngineExecutable.setRelativeTo(quakeDir.toFile(), new File("quakespasm.exe"));

		assertEquals(new File("quakespasm.exe"), config.EngineExecutable.get());
	}

	@Test
	public void absolutePathStaysAbsoluteWhenResolving() {
		File elsewhere = tmp.resolve("other/engine.exe").toFile().getAbsoluteFile();
		config.EngineExecutable.set(elsewhere);

		assertEquals(elsewhere, config.EngineExecutable.resolveAgainst(quakeDir.toFile()));
	}

	@Test
	public void withoutAnEnginePathTheFullPathIsKept() {
		File exe = quakeDir.resolve("quakespasm.exe").toFile();

		config.EngineExecutable.setRelativeTo(null, exe);

		assertEquals(exe, config.EngineExecutable.get());
	}

	@Test
	public void pathOnAnotherDriveIsKeptAbsolute() {
		// only meaningful on Windows, where paths on different drives can't be made relative
		File[] roots = File.listRoots();
		File otherRoot = null;
		for (File root : roots) {
			if (!quakeDir.toFile().getAbsolutePath().startsWith(root.getAbsolutePath())) {
				otherRoot = root;
			}
		}
		File exe = new File(otherRoot != null ? otherRoot : new File("Z:\\"), "Games\\quakespasm.exe");
		if (!exe.isAbsolute()) {
			return; // not Windows: every absolute path shares the root "/"
		}

		config.EngineExecutable.setRelativeTo(quakeDir.toFile(), exe);

		assertEquals(exe, config.EngineExecutable.get());
	}
}
