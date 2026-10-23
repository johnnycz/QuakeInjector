package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class InstallerTest {
	@TempDir
	Path tmp;

	private Path engineDir;
	private Path remoteDir;
	private Installer installer;
	private final TestThreads threads = new TestThreads();
	private final RecordingInstallListener listener = new RecordingInstallListener();

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		remoteDir = Files.createDirectories(tmp.resolve("remote"));
		Configuration config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(tmp.resolve("downloads").toFile());
		installer = new Installer(config.EnginePath, config.DownloadPath, threads.executors());
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
	}

	private String remoteZip(TestZip zip) throws Exception {
		return zip.writeTo(remoteDir.resolve("testmap.zip")).toUri().toURL().toString();
	}

	@Test
	public void packageIsQueuedUntilTheListenerWasTold() throws Exception {
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A"));
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		List<Boolean> queuedDuringCallback = new ArrayList<>();
		RecordingInstallListener checking = new RecordingInstallListener() {
			@Override
			public void succeeded(PackageFileList installedFiles) {
				queuedDuringCallback.add(installer.alreadyQueued(pkg));
				super.succeeded(installedFiles);
			}
		};

		var done = installer.install(pkg, url, checking, null);
		assertTrue(installer.alreadyQueued(pkg));
		done.get(10, TimeUnit.SECONDS);

		assertEquals(List.of("succeeded"), checking.calls);
		assertEquals(List.of(TestThreads.UI), checking.threads);
		assertEquals(List.of(true), queuedDuringCallback);
		assertFalse(installer.alreadyQueued(pkg));
		assertFalse(installer.working());
	}

	@Test
	public void secondInstallOfTheSamePackageIsIgnored() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW"));
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		RecordingInstallListener second = new RecordingInstallListener();
		// while the first install waits for the overwrite answer, try installing again
		listener.overwriteAnswer = existing -> {
			assertTrue(installer.install(pkg, url, second, null).isDone(), "second install must be rejected");
			return new ArrayList<>(existing.values());
		};

		installer.install(pkg, url, listener, null).get(10, TimeUnit.SECONDS);

		assertEquals(List.of("overwrite", "succeeded"), listener.calls);
		assertEquals(List.of(), second.calls);
	}

	@Test
	public void cancelReachesTheRunningInstall() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "NEW"));
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		listener.overwriteAnswer = existing -> {
			installer.cancel(pkg);
			return new ArrayList<>(existing.values());
		};

		installer.install(pkg, url, listener, null).get(10, TimeUnit.SECONDS);

		assertEquals(List.of("overwrite", "failed:CANCELLED"), listener.calls);
		assertEquals("OLD", Files.readString(engineDir.resolve("maps/a.bsp")));
		assertFalse(installer.alreadyQueued(pkg));
	}

	@Test
	public void queueChangesAreSentOnTheUiThread() throws Exception {
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A"));
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		List<String> events = new CopyOnWriteArrayList<>();
		// register on the UI thread, as the app does
		threads.ui().submit(() -> installer.addQueueListener(e -> events.add(
			(e.getSource() == pkg ? "testmap" : "?")
			+ " queued=" + installer.alreadyQueued(pkg)
			+ " on " + Thread.currentThread().getName()))).get();

		installer.install(pkg, url, listener, null).get(10, TimeUnit.SECONDS);

		assertEquals(List.of(
			"testmap queued=true on " + TestThreads.UI,
			"testmap queued=false on " + TestThreads.UI), events);
	}

	@Test
	public void failedInstallStaysQueuedUntilCleanedUpAndReported() throws Exception {
		Files.writeString(engineDir.resolve("blocker"), "not a directory");
		String url = remoteZip(new TestZip().file("maps/a.bsp", "A").file("blocker/x.bsp", "X"));
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), url);
		List<String> whenTold = new ArrayList<>();
		listener.onFailed = failure -> whenTold.add("queued=" + installer.alreadyQueued(pkg)
			+ " a.bsp exists=" + Files.exists(engineDir.resolve("maps/a.bsp")));

		installer.install(pkg, url, listener, null).get(10, TimeUnit.SECONDS);

		assertEquals(List.of("failed:NOT_WRITABLE"), listener.calls);
		assertEquals(List.of("queued=true a.bsp exists=false"), whenTold);
		assertFalse(installer.alreadyQueued(pkg));
	}

	@Test
	public void uninstallDeletesFilesAndReportsOnTheUiThread() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "A");
		PackageFileList files = new PackageFileList("testmap");
		files.add(new FileInfo("maps/a.bsp", 0));
		files.add(new FileInfo("maps", 0));
		List<String> calls = new CopyOnWriteArrayList<>();

		installer.uninstall(files, new Installer.UninstallErrorHandler() {
			@Override
			public void success() {
				calls.add("success on " + Thread.currentThread().getName());
			}

			@Override
			public void error(Exception e) {
				calls.add("error on " + Thread.currentThread().getName());
			}
		}, null).get(10, TimeUnit.SECONDS);

		assertEquals(List.of("success on " + TestThreads.UI), calls);
		assertFalse(Files.exists(engineDir.resolve("maps/a.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps")), "the emptied dir must be removed too");
	}
}
