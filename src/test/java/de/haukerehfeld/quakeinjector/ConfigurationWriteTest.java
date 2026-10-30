package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Saving the settings file, and that a failed save is reported instead of being lost. */
public class ConfigurationWriteTest {
	@TempDir
	Path tmp;

	@Test
	public void writtenSettingsAreReadBack() throws Exception {
		Path file = tmp.resolve("config.properties");
		Configuration config = new Configuration(file.toFile());
		config.EngineCommandLine.set("-fullscreen");

		config.writeOrMakeWritable();

		assertEquals("-fullscreen", new Configuration(file.toFile()).EngineCommandLine.get());
	}

	@Test
	public void aMissingDirectoryFailsTheSave() {
		Configuration config = new Configuration(tmp.resolve("missing/config.properties").toFile());

		assertThrows(IOException.class, config::write);
		assertThrows(IOException.class, config::writeOrMakeWritable);
	}

	@Test
	public void aFileThatCantBeWrittenFailsTheSave() throws Exception {
		// a directory where the file should be: the directory is writable, the file can't be opened
		File file = Files.createDirectory(tmp.resolve("config.properties")).toFile();
		Configuration config = new Configuration(file);

		assertThrows(IOException.class, config::write);
		assertThrows(IOException.class, config::writeOrMakeWritable);
	}
}
