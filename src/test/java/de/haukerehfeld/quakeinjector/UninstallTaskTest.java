package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.UninstallTask;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

public class UninstallTaskTest {
	@TempDir
	Path engineDir;

	private PackageFileList files() {
		return new PackageFileList("testmap");
	}

	/** Write a file and return its FileInfo with the real CRC, as ExtractTask records it. */
	private FileInfo file(String name, String content) throws Exception {
		Path path = engineDir.resolve(name);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
		CRC32 crc = new CRC32();
		crc.update(content.getBytes(StandardCharsets.UTF_8));
		return new FileInfo(name, crc.getValue(), content.length());
	}

	private static FileInfo dir(String name) {
		return new FileInfo(name, 0);
	}

	private void uninstall(PackageFileList files) throws Exception {
		new UninstallTask(files, engineDir.toString()).execute();
	}

	@Test
	public void removesFilesAndTheirNestedDirectories() throws Exception {
		PackageFileList files = files();
		files.add(file("ad/maps/a.bsp", "A"));
		files.add(file("ad/maps/b.bsp", "B"));
		files.add(file("ad/pak0.pak", "P"));
		files.add(dir("ad/maps"));
		files.add(dir("ad"));

		uninstall(files);

		assertFalse(Files.exists(engineDir.resolve("ad")), "the whole package dir must be gone");
	}

	@Test
	public void keepsDirectoriesThatStillContainOtherFiles() throws Exception {
		PackageFileList files = files();
		files.add(file("maps/a.bsp", "A"));
		files.add(dir("maps"));
		Files.writeString(engineDir.resolve("maps/users-own.bsp"), "MINE");

		uninstall(files);

		assertFalse(Files.exists(engineDir.resolve("maps/a.bsp")));
		assertEquals("MINE", Files.readString(engineDir.resolve("maps/users-own.bsp")));
	}

	@Test
	public void keepsAChangedFileAndItsDirectory() throws Exception {
		PackageFileList files = files();
		files.add(file("maps/a.bsp", "A"));
		files.add(dir("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "CHANGED BY THE USER");

		uninstall(files);

		assertEquals("CHANGED BY THE USER", Files.readString(engineDir.resolve("maps/a.bsp")));
	}

	@Test
	public void siblingWithSimilarNameDoesNotConfuseTheOrder() throws Exception {
		// '-' sorts before '/', so "maps-extra" lands between "maps" and "maps/a.bsp"
		PackageFileList files = files();
		files.add(file("maps/a.bsp", "A"));
		files.add(file("maps-extra/b.bsp", "B"));
		files.add(dir("maps"));
		files.add(dir("maps-extra"));

		uninstall(files);

		assertFalse(Files.exists(engineDir.resolve("maps")));
		assertFalse(Files.exists(engineDir.resolve("maps-extra")));
	}
}
