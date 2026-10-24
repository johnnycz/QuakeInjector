package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.PackageInstallUninstallService;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel.JobVm;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Check for installed maps" end to end: {@link PackageInstallUninstallService#checkForInstalledMaps}
 * with a real installer, package list, installed-maps file and job list view model.
 */
public class CheckForInstalledMapsTest {
	private static final String DESCRIPTION = "Checking for installed maps";

	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final InstallQueueViewModel jobs = new InstallQueueViewModel();
	/** Completes with the finish message of the first job. */
	private final CompletableFuture<String> finished = new CompletableFuture<>();
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
		jobs.addChangeListener(e -> {
			if (e.getSource() instanceof JobVm job && job.isFinished() && job.getId() == 0) {
				finished.complete(job.getFinishedMessage());
			}
		});
		service = new PackageInstallUninstallService(jobs, dialogs);
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

	private <T> T onUi(Callable<T> work) throws Exception {
		return threads.ui().submit(work).get(10, TimeUnit.SECONDS);
	}

	private void onUi(Runnable work) throws Exception {
		threads.ui().submit(work).get(10, TimeUnit.SECONDS);
	}

	/** Keep the disk thread busy, from the call on, until the returned latch is released. */
	private CountDownLatch blockDiskThread() {
		CountDownLatch release = new CountDownLatch(1);
		installer.runOnDiskThread("blocking the disk thread", () -> release.await(10, TimeUnit.SECONDS));
		return release;
	}

	/** Wait until the check finished, the rest of its completion ran and the saving after it is done. */
	private String awaitFinished() throws Exception {
		String message = finished.get(10, TimeUnit.SECONDS);
		onUi(() -> { });
		installer.runOnDiskThread("waiting for the disk thread", () -> null).get(10, TimeUnit.SECONDS);
		return message;
	}

	private JobVm onlyJob() {
		assertEquals(1, jobs.jobs.size(), "one row in the job list");
		return jobs.jobs.get(0);
	}

	private List<String> savedIds() throws Exception {
		List<String> ids = new ArrayList<>();
		for (PackageFileList list : new SaveInstalled(installedMapsFile.toFile()).read()) {
			ids.add(list.getId());
		}
		return ids;
	}

	@Test
	public void showsARowInsteadOfBlocking() throws Exception {
		packages(testmap("a"));
		CountDownLatch release = blockDiskThread();

		// returns while the check still waits for the disk thread
		onUi(service::checkForInstalledMaps);

		JobVm job = onUi(this::onlyJob);
		assertEquals(DESCRIPTION, job.getDescription());
		assertFalse(job.isFinished());
		assertFalse(job.isProgressUnknown(), "a percentage, not a busy bar");
		release.countDown();
		awaitFinished();
	}

	@Test
	public void marksTheInstalledPackagesAndSavesThem() throws Exception {
		Package installed = testmap("a");
		Package missing = testmap("b");
		installFiles(installed);
		packages(installed, missing);

		onUi(service::checkForInstalledMaps);

		assertEquals("Done: 1 map found", awaitFinished());
		assertTrue(installed.isInstalled());
		assertSame(installed.getSupposedFileList(), installed.getFileList());
		assertFalse(missing.isInstalled());
		assertEquals(List.of(TestThreads.UI), notifiedOn, "notified once, on the UI thread");
		assertEquals(List.of("a"), savedIds());
		assertEquals(100, (int) onUi(() -> onlyJob().getProgress()));
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void cancellingTheRowChangesNothing() throws Exception {
		Package pkg = testmap("a");
		installFiles(pkg);
		packages(pkg);
		CountDownLatch release = blockDiskThread();
		onUi(service::checkForInstalledMaps);

		onUi(() -> onlyJob().cancel());
		release.countDown();

		assertEquals("Cancelled", awaitFinished());
		assertFalse(pkg.isInstalled());
		assertEquals(List.of(), notifiedOn);
		assertFalse(Files.exists(installedMapsFile), "nothing saved");
		assertEquals(List.of(), dialogs.calls, "no error shown");
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

		onUi(service::checkForInstalledMaps);

		assertEquals("Failed", awaitFinished());
		assertEquals(List.of(), notifiedOn);
		assertFalse(Files.exists(installedMapsFile), "nothing saved");
		assertEquals(List.of("error: Checking failed"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI), dialogs.threads);
	}

	@Test
	public void checkingAgainWhileCheckingIsIgnored() throws Exception {
		packages(testmap("a"));
		CountDownLatch release = blockDiskThread();

		onUi(service::checkForInstalledMaps);
		onUi(service::checkForInstalledMaps);
		release.countDown();

		awaitFinished();
		onUi(this::onlyJob);
	}

	@Test
	public void canCheckAgainAfterACheckFinished() throws Exception {
		packages(testmap("a"));
		onUi(service::checkForInstalledMaps);
		awaitFinished();

		onUi(service::checkForInstalledMaps);
		installer.runOnDiskThread("waiting for the disk thread", () -> null).get(10, TimeUnit.SECONDS);
		onUi(() -> { });

		assertEquals(2, (int) onUi(() -> jobs.jobs.size()));
		assertTrue(onUi(() -> jobs.jobs.get(1).isFinished()));
	}
}
