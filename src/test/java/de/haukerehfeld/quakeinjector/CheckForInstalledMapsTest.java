package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.PackageInstallUninstallService;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Check for installed maps" end to end: {@link PackageInstallUninstallService#checkForInstalledMaps}
 * with a real installer, package list and installed-maps file.
 */
public class CheckForInstalledMapsTest {
	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final RecordingDialogs dialogs = new RecordingDialogs();
	/** Threads the package list notified its listeners on. */
	private final List<String> notifiedOn = new CopyOnWriteArrayList<>();

	private Path engineDir;
	private Path installedMapsFile;
	private Installer installer;
	private PackageInstallUninstallService service;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		installedMapsFile = tmp.resolve("installedMaps.xml");
		Configuration config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(tmp.resolve("downloads").toFile());
		installer = new Installer(config.EnginePath, config.DownloadPath, threads.executors());
		service = new PackageInstallUninstallService((description, cancel) -> {
			throw new AssertionError("no install queue job expected: " + description);
		}, dialogs);
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
	}

	private RequirementList packages(Package... packages) {
		RequirementList list = new RequirementList(new ArrayList<Requirement>(List.of(packages)));
		list.addChangeListener(e -> notifiedOn.add(Thread.currentThread().getName()));
		service.init(installer, list, new SaveInstalled(installedMapsFile.toFile()));
		return list;
	}

	private static Package testmap(String id) {
		PackageFileList files = new PackageFileList(id);
		files.add(new FileInfo("maps/" + id + ".bsp", 0, 3));
		return withFiles(id, files);
	}

	private static Package withFiles(String id, PackageFileList files) {
		Package pkg = TestZip.testPackage(id, new ExtractMapping(), "file:///unused.zip");
		pkg.setSupposedFileList(files);
		return pkg;
	}

	private void installFiles(Package pkg) throws Exception {
		for (FileInfo f : pkg.getSupposedFileList()) {
			Path file = engineDir.resolve(f.getName());
			Files.createDirectories(file.getParent());
			Files.write(file, new byte[(int) f.getSize()]);
		}
	}

	/** Run the check from the UI thread and wait until it and the saving after it are done. */
	private void checkForInstalledMaps() throws Exception {
		threads.ui().submit(service::checkForInstalledMaps).get(10, TimeUnit.SECONDS);
		dialogs.popup.finished.get(10, TimeUnit.SECONDS);
		// the rest of the completion, which runs on the UI thread right after finishing the popup
		threads.ui().submit(() -> { }).get(10, TimeUnit.SECONDS);
		// saving the installed list, queued on the disk thread
		installer.runOnDiskThread("waiting for the disk thread", () -> null).get(10, TimeUnit.SECONDS);
	}

	private List<String> savedIds() throws Exception {
		List<String> ids = new ArrayList<>();
		for (PackageFileList list : new SaveInstalled(installedMapsFile.toFile()).read()) {
			ids.add(list.getId());
		}
		return ids;
	}

	@Test
	public void marksTheInstalledPackagesAndSavesThem() throws Exception {
		Package installed = testmap("a");
		Package missing = testmap("b");
		installFiles(installed);
		packages(installed, missing);

		checkForInstalledMaps();

		assertTrue(installed.isInstalled());
		assertSame(installed.getSupposedFileList(), installed.getFileList());
		assertFalse(missing.isInstalled());
		assertEquals(List.of(TestThreads.UI), notifiedOn, "notified once, on the UI thread");
		assertEquals(List.of("a"), savedIds());
		assertEquals("Done", dialogs.popup.finished.get());
		assertEquals(100, dialogs.popup.progress.get(dialogs.popup.progress.size() - 1));
		assertEquals(List.of("progress: Checking for installed maps"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI), dialogs.threads);
	}

	@Test
	public void cancellingChangesNothing() throws Exception {
		Package pkg = testmap("a");
		installFiles(pkg);
		packages(pkg);
		dialogs.cancelProgressPopups = true;

		checkForInstalledMaps();

		assertFalse(pkg.isInstalled());
		assertEquals(List.of(), notifiedOn);
		assertFalse(Files.exists(installedMapsFile), "nothing saved");
		assertEquals(List.of("progress: Checking for installed maps"), dialogs.calls, "no error shown");
	}

	@Test
	public void aFailedCheckShowsAnError() throws Exception {
		PackageFileList unreadable = new PackageFileList("broken") {
			@Override
			public Iterator<FileInfo> iterator() {
				throw new IllegalStateException("can't list the files");
			}
		};
		packages(withFiles("broken", unreadable));

		checkForInstalledMaps();

		assertEquals(List.of(), notifiedOn);
		assertFalse(Files.exists(installedMapsFile), "nothing saved");
		assertEquals(List.of("progress: Checking for installed maps", "error: Checking failed"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI, TestThreads.UI), dialogs.threads);
	}
}
