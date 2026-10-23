package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.FileNotWritableException;
import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.PackageInstallOrchestrationTask;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static de.haukerehfeld.quakeinjector.TestZip.read;
import static de.haukerehfeld.quakeinjector.TestZip.rel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests of the install process (download, inspect, ask about overwrites, extract)
 * using file:// URLs and real directories.
 */
public class InstallProcessTest {
	@TempDir
	Path tmp;

	private Path engineDir;
	private Path downloadDir;
	private Path remoteDir;
	private Configuration config;
	private final RecordingHandler handler = new RecordingHandler();

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		downloadDir = Files.createDirectories(tmp.resolve("downloads"));
		remoteDir = Files.createDirectories(tmp.resolve("remote"));
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(downloadDir.toFile());
	}

	private PackageInstallOrchestrationTask task(String url) {
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		return new PackageInstallOrchestrationTask(url, pkg, handler, config.DownloadPath, config.EnginePath);
	}

	private String remoteZip(TestZip zip) throws Exception {
		return zip.writeTo(remoteDir.resolve("testmap.zip")).toUri().toURL().toString();
	}

	@Test
	public void installsPackage() throws Exception {
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A"));

		PackageFileList files = task(url).execute();

		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
		assertTrue(names(files).contains(rel("maps", "a.bsp")));
		assertTrue(Files.exists(downloadDir.resolve("testmap.zip")), "download should be cached");
		assertEquals(List.of(), handler.calls, "no handler calls on a plain install");
	}

	@Test
	public void usesCachedDownload() throws Exception {
		new TestZip().file("maps/a.bsp", "A").writeTo(downloadDir.resolve("testmap.zip"));
		String missingUrl = remoteDir.resolve("does-not-exist.zip").toUri().toURL().toString();

		task(missingUrl).execute();

		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
	}

	@Test
	public void asksBeforeOverwritingExistingFiles() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		handler.overwriteAnswer = existing -> new ArrayList<>(existing.values());

		task(url).execute();

		assertEquals(List.of("overwrite"), handler.calls);
		assertTrue(handler.askedAbout.containsValue(engineDir.resolve("maps/a.bsp").toFile()),
			"should ask about the existing file: " + handler.askedAbout);
		assertEquals("NEW", read(engineDir.resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir.resolve("maps/b.bsp")));
	}

	@Test
	public void declinedOverwriteCancelsInstall() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		handler.overwriteAnswer = existing -> List.of();

		assertThrows(Installer.CancelledException.class, () -> task(url).execute());

		assertEquals(List.of("overwrite", "cancelled"), handler.calls);
		assertEquals("OLD", read(engineDir.resolve("maps/a.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps/b.bsp")));
	}

	@Test
	public void missingDownloadIsReportedAsNotFound() throws Exception {
		String missingUrl = remoteDir.resolve("does-not-exist.zip").toUri().toURL().toString();

		assertThrows(OnlineFileNotFoundException.class, () -> task(missingUrl).execute());

		assertEquals(List.of("notFound"), handler.calls);
		assertFalse(Files.exists(downloadDir.resolve("testmap.zip")), "failed download must not be cached");
	}

	/** Records which handler callbacks were made, in order. */
	static class RecordingHandler implements Installer.InstallErrorHandler {
		final List<String> calls = new ArrayList<>();
		Function<Map<String, File>, List<File>> overwriteAnswer = existing -> null;
		Map<String, File> askedAbout;
		PackageFileList filesOnFailure;

		@Override
		public List<File> overwrite(Map<String, File> existingFiles) {
			calls.add("overwrite");
			askedAbout = existingFiles;
			return overwriteAnswer.apply(existingFiles);
		}

		@Override
		public void success(PackageFileList installedFiles) {
			calls.add("success");
		}

		@Override
		public void handle(OnlineFileNotFoundException error) {
			calls.add("notFound");
		}

		@Override
		public void handle(FileNotWritableException error, PackageFileList alreadyInstalledFiles) {
			calls.add("notWritable");
			filesOnFailure = alreadyInstalledFiles;
		}

		@Override
		public void handle(IOException error, PackageFileList alreadyInstalledFiles) {
			calls.add("io");
			filesOnFailure = alreadyInstalledFiles;
		}

		@Override
		public void handle(SocketException error, PackageFileList alreadyInstalledFiles) {
			calls.add("network");
			filesOnFailure = alreadyInstalledFiles;
		}

		@Override
		public void handle(Installer.CancelledException error, PackageFileList alreadyInstalledFiles) {
			calls.add("cancelled");
			filesOnFailure = alreadyInstalledFiles;
		}
	}
}
