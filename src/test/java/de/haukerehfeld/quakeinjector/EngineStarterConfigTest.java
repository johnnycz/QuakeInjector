package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.play.EngineStarter;
import de.haukerehfeld.quakeinjector.model.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Setting up the {@link EngineStarter} from the engine settings, at startup ({@code fromConfig}) and after
 * the settings changed ({@code applyConfig}).
 */
public class EngineStarterConfigTest {
	@TempDir
	Path tmp;

	private File quakeDir;
	private Configuration config;

	@BeforeEach
	public void setUp() throws Exception {
		quakeDir = Files.createDirectories(tmp.resolve("quake")).toFile();
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(quakeDir);
	}

	@Test
	public void relativeExecutableIsFoundInTheQuakeDirectory() {
		config.EngineExecutable.set(new File("bin", "quakespasm.exe"));

		EngineStarter starter = EngineStarter.fromConfig(config);

		assertEquals(new File(quakeDir, "bin" + File.separator + "quakespasm.exe"), starter.getQuakeExecutable());
		assertEquals(quakeDir, starter.getWorkingDirectory());
	}

	@Test
	public void absoluteExecutableIsKept() {
		File elsewhere = tmp.resolve("other/engine.exe").toFile().getAbsoluteFile();
		config.EngineExecutable.set(elsewhere);

		EngineStarter starter = EngineStarter.fromConfig(config);

		assertEquals(elsewhere, starter.getQuakeExecutable());
	}

	@Test
	public void workingDirectoryCanBeTheExecutablesDirectory() {
		config.EngineExecutable.set(new File("bin", "quakespasm.exe"));
		config.WorkingDirAtExecutable.set(true);

		EngineStarter starter = EngineStarter.fromConfig(config);

		assertEquals(new File(quakeDir, "bin"), starter.getWorkingDirectory());
	}

	@Test
	public void commandLineComesFromTheSettings() {
		config.EngineCommandLine.set("-heapsize 65536");

		assertEquals("-heapsize 65536", EngineStarter.fromConfig(config).getQuakeCommandline());
	}

	@Test
	public void changedSettingsAreTakenOver() {
		config.EngineExecutable.set(new File("quake.exe"));
		EngineStarter starter = EngineStarter.fromConfig(config);
		File otherQuakeDir = tmp.resolve("other-quake").toFile();

		config.EnginePath.set(otherQuakeDir);
		config.EngineExecutable.set(new File("bin", "vkquake.exe"));
		config.WorkingDirAtExecutable.set(true);
		config.EngineCommandLine.set("-fullscreen");
		starter.applyConfig(config);

		assertEquals(new File(otherQuakeDir, "bin" + File.separator + "vkquake.exe"), starter.getQuakeExecutable());
		assertEquals(new File(otherQuakeDir, "bin"), starter.getWorkingDirectory());
		assertEquals("-fullscreen", starter.getQuakeCommandline());
	}
}
