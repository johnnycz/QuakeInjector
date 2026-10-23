package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.InstallFailure;
import de.haukerehfeld.quakeinjector.feature.install.InstallPipeline;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static de.haukerehfeld.quakeinjector.TestZip.read;
import static de.haukerehfeld.quakeinjector.TestZip.rel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests of the install process (download, inspect, ask about overwrites, extract)
 * using file:// URLs, real directories and real threads.
 */
public class InstallProcessTest {
	@TempDir
	Path tmp;

	private Path engineDir;
	private Path downloadDir;
	private Path remoteDir;
	private Configuration config;
	private final TestThreads threads = new TestThreads();
	private final RecordingInstallListener listener = new RecordingInstallListener();

	/** The package of the most recently created pipeline. */
	private Package pkg;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		downloadDir = Files.createDirectories(tmp.resolve("downloads"));
		remoteDir = Files.createDirectories(tmp.resolve("remote"));
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(downloadDir.toFile());
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
		// every listener call must happen on the UI thread
		assertTrue(listener.threads.stream().allMatch(TestThreads.UI::equals),
			"listener called on " + listener.threads);
	}

	private InstallPipeline pipeline(String url) {
		pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		return new InstallPipeline(url, pkg, listener, config.DownloadPath, config.EnginePath, threads.executors());
	}

	private void run(InstallPipeline pipeline) throws Exception {
		pipeline.start().get(10, TimeUnit.SECONDS);
	}

	private String remoteZip(TestZip zip) throws Exception {
		return zip.writeTo(remoteDir.resolve("testmap.zip")).toUri().toURL().toString();
	}

	@Test
	public void installsPackage() throws Exception {
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A"));

		run(pipeline(url));

		assertEquals(List.of("succeeded"), listener.calls);
		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
		assertTrue(names(listener.succeededWith).contains(rel("maps", "a.bsp")));
		assertTrue(Files.exists(downloadDir.resolve("testmap.zip")), "download should be cached");
		assertFalse(pkg.isInstalled(), "marking the package installed is the listener's job");
	}

	@Test
	public void failedExtractionIsCleanedUpBeforeTheListenerIsTold() throws Exception {
		// a plain file where the zip expects a directory: inspecting works, writing blocker/x.bsp fails
		Files.writeString(engineDir.resolve("blocker"), "not a directory");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A").file("blocker/x.bsp", "X"));
		List<Boolean> fileExistedWhenTold = new ArrayList<>();
		listener.onFailed = failure -> fileExistedWhenTold.add(Files.exists(engineDir.resolve("maps/a.bsp")));

		run(pipeline(url));

		assertEquals(List.of("failed:NOT_WRITABLE"), listener.calls);
		assertEquals(List.of(false), fileExistedWhenTold, "a.bsp must be removed before the listener is told");
		assertTrue(names(listener.failure.getPartialFiles()).containsAll(List.of(rel("maps", "a.bsp"), rel("maps"))),
			"the failure lists what was written (and removed): " + names(listener.failure.getPartialFiles()));
		assertEquals("not a directory", read(engineDir.resolve("blocker")), "files not written by the install stay");
		assertFalse(pkg.isInstalled());
	}

	@Test
	public void usesCachedDownload() throws Exception {
		new TestZip().file("maps/a.bsp", "A").writeTo(downloadDir.resolve("testmap.zip"));
		String missingUrl = remoteDir.resolve("does-not-exist.zip").toUri().toURL().toString();

		run(pipeline(missingUrl));

		assertEquals(List.of("succeeded"), listener.calls);
		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
	}

	@Test
	public void asksBeforeOverwritingExistingFiles() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		listener.overwriteAnswer = existing -> new ArrayList<>(existing.values());

		run(pipeline(url));

		assertEquals(List.of("overwrite", "succeeded"), listener.calls);
		assertTrue(listener.askedAbout.containsValue(engineDir.resolve("maps/a.bsp").toFile()),
			"should ask about the existing file: " + listener.askedAbout);
		assertEquals("NEW", read(engineDir.resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir.resolve("maps/b.bsp")));
	}

	@Test
	public void declinedOverwriteCancelsInstall() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		listener.overwriteAnswer = existing -> List.of();

		run(pipeline(url));

		assertEquals(List.of("overwrite", "failed:CANCELLED"), listener.calls);
		assertEquals("OLD", read(engineDir.resolve("maps/a.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps/b.bsp")));
	}

	@Test
	public void cancelWhileOverwriteQuestionIsOpenStopsInstall() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		AtomicReference<InstallPipeline> pipeline = new AtomicReference<>(pipeline(url));
		// user presses Cancel in the install queue, then answers the dialog with "overwrite all"
		listener.overwriteAnswer = existing -> {
			pipeline.get().cancel();
			return new ArrayList<>(existing.values());
		};

		run(pipeline.get());

		assertEquals(List.of("overwrite", "failed:CANCELLED"), listener.calls);
		assertEquals("OLD", read(engineDir.resolve("maps/a.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps/b.bsp")));
	}

	@Test
	public void cancelBeforeStartDoesNothing() throws Exception {
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A"));
		InstallPipeline pipeline = pipeline(url);
		pipeline.cancel();

		run(pipeline);

		assertEquals(List.of("failed:CANCELLED"), listener.calls);
		assertFalse(Files.exists(downloadDir.resolve("testmap.zip")));
		assertFalse(Files.exists(engineDir.resolve("maps")));
	}

	@Test
	public void missingDownloadIsReportedAsNotFound() throws Exception {
		String missingUrl = remoteDir.resolve("does-not-exist.zip").toUri().toURL().toString();

		run(pipeline(missingUrl));

		assertEquals(List.of("failed:NOT_FOUND"), listener.calls);
		assertFalse(Files.exists(downloadDir.resolve("testmap.zip")), "failed download must not be cached");
	}

	@Test
	public void brokenArchiveIsReportedAsIoError() throws Exception {
		Files.writeString(downloadDir.resolve("testmap.zip"), "this is not a zip file");

		run(pipeline("unused"));

		assertEquals(List.of("failed:IO"), listener.calls);
		assertEquals(InstallFailure.Kind.IO, listener.failure.getKind());
	}
}
