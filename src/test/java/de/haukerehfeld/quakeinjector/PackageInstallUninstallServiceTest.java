package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.PackageInstallUninstallService;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel.JobVm;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.OfficialExpansionRequirement;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import de.haukerehfeld.quakeinjector.model.UnavailableRequirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static de.haukerehfeld.quakeinjector.TestZip.read;
import static de.haukerehfeld.quakeinjector.TestZip.rel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Installing and uninstalling through {@link PackageInstallUninstallService}, end to end: a real
 * installer, package list, installed-maps file and job list, with zips from {@code file://} URLs.
 * Covers what the service decides itself: when to ask or stop, prerequisites, the overwrite question,
 * which message a failure shows, and marking and saving the installed state.
 */
public class PackageInstallUninstallServiceTest {
	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final InstallQueueViewModel jobs = new InstallQueueViewModel();
	/** Released once per job that finished. */
	private final Semaphore finishedJobs = new Semaphore(0);
	private final Set<Integer> finishedIds = ConcurrentHashMap.newKeySet();
	private final RecentPackages recent = new RecentPackages();

	private Path engineDir;
	private Path remoteDir;
	private Path downloadDir;
	private Path installedMapsFile;
	private Installer installer;
	private PackageInstallUninstallService service;
	private RequirementList list;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		remoteDir = Files.createDirectories(tmp.resolve("remote"));
		downloadDir = Files.createDirectories(tmp.resolve("downloads"));
		installedMapsFile = tmp.resolve("installedMaps.xml");
		Configuration config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(downloadDir.toFile());
		installer = new Installer(config.EnginePath, config.DownloadPath, threads.executors());
		jobs.addChangeListener(e -> {
			if (e.getSource() instanceof JobVm job && job.isFinished() && finishedIds.add(job.getId())) {
				finishedJobs.release();
			}
		});
		service = new PackageInstallUninstallService(jobs, dialogs, recent);
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
	}

	private void packages(Package... packages) {
		list = new RequirementList(new ArrayList<Requirement>(List.of(packages)));
		service.init(installer, list, new SaveInstalled(installedMapsFile.toFile()));
	}

	/** A package downloaded from a zip with these files. */
	private Package remotePackage(String id, TestZip zip) throws IOException {
		String url = zip.writeTo(remoteDir.resolve(id + ".zip")).toUri().toURL().toString();
		return TestZip.testPackage(id, new ExtractMapping(), url);
	}

	/** A package whose download doesn't exist. */
	private Package missingPackage(String id) throws IOException {
		String url = remoteDir.resolve(id + ".zip").toUri().toURL().toString();
		return TestZip.testPackage(id, new ExtractMapping(), url);
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

	/** Wait until this many jobs finished, the rest of their completion ran and the saving after it is done. */
	private void awaitFinished(int count) throws Exception {
		assertTrue(finishedJobs.tryAcquire(count, 10, TimeUnit.SECONDS), count + " jobs should finish");
		onUi(() -> { });
		installer.runOnDiskThread("waiting for the disk thread", () -> null).get(10, TimeUnit.SECONDS);
		onUi(() -> { });
	}

	private List<JobVm> jobList() throws Exception {
		return onUi(() -> List.copyOf(jobs.jobs));
	}

	private JobVm onlyJob() throws Exception {
		List<JobVm> all = jobList();
		assertEquals(1, all.size(), "one row in the job list");
		return all.get(0);
	}

	private List<String> descriptions() throws Exception {
		return jobList().stream().map(JobVm::getDescription).toList();
	}

	private List<String> finishedMessages() throws Exception {
		return jobList().stream().map(JobVm::getFinishedMessage).toList();
	}

	private List<String> savedIds() throws Exception {
		List<String> ids = new ArrayList<>();
		for (PackageFileList l : new SaveInstalled(installedMapsFile.toFile()).read()) {
			ids.add(l.getId());
		}
		return ids;
	}

	private void assertNothingInstalledOrSaved(Package pkg) {
		assertFalse(pkg.isInstalled());
		assertNull(pkg.getFileList());
		assertFalse(Files.exists(installedMapsFile), "nothing saved");
	}

	private void assertDialogsOnUiThread() {
		for (String thread : dialogs.threads) {
			assertEquals(TestThreads.UI, thread);
		}
	}

	// --- installing ---

	@Test
	public void installMarksThePackageInstalledAndSavesIt() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		List<Boolean> hadFileListWhenInstalled = new CopyOnWriteArrayList<>();
		pkg.addChangeListener(e -> hadFileListWhenInstalled.add(pkg.getFileList() != null));

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		JobVm job = onlyJob();
		assertEquals("Installing a", job.getDescription());
		assertEquals("Success", job.getFinishedMessage());
		assertEquals(100, job.getProgress());
		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
		assertTrue(pkg.isInstalled());
		assertTrue(names(pkg.getFileList()).contains(rel("maps", "a.bsp")), names(pkg.getFileList()).toString());
		assertEquals(List.of(true), hadFileListWhenInstalled, "the file list is set before the installed flag");
		assertEquals(List.of("a"), savedIds());
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void installOfNothingDoesNothing() throws Exception {
		packages();

		onUi(() -> service.install(null));

		assertEquals(List.of(), jobList());
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void installWhileTheSamePackageIsQueuedIsIgnored() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		CountDownLatch release = blockDiskThread();

		onUi(() -> service.install(pkg));
		onUi(() -> service.install(pkg));
		release.countDown();
		awaitFinished(1);

		assertEquals("Success", onlyJob().getFinishedMessage());
	}

	@Test
	public void theInstalledStateGoesToThePackageInTheCurrentList() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		CountDownLatch release = blockDiskThread();
		onUi(() -> service.install(pkg));

		// the package database is reloaded while the install runs: a new object for the same package
		Package reloaded = TestZip.testPackage("a", new ExtractMapping(), pkg.getDownloadUrls().get(0));
		onUi(() -> list.setRequirements(new ArrayList<>(List.of(reloaded))));
		release.countDown();
		awaitFinished(1);

		assertEquals("Success", onlyJob().getFinishedMessage());
		assertTrue(reloaded.isInstalled());
		assertTrue(names(reloaded.getFileList()).contains(rel("maps", "a.bsp")));
		assertEquals(List.of("a"), savedIds());
	}

	@Test
	public void aPackageGoneFromTheReloadedListIsStillReportedAsInstalled() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		CountDownLatch release = blockDiskThread();
		onUi(() -> service.install(pkg));

		onUi(() -> list.setRequirements(new ArrayList<>()));
		release.countDown();
		awaitFinished(1);

		assertEquals("Success", onlyJob().getFinishedMessage());
		assertEquals("A", read(engineDir.resolve("maps/a.bsp")));
		assertEquals(List.of(), savedIds());
	}

	// --- install directory ---

	@Test
	public void withoutAnInstallDirectoryTheUserIsAskedToConfigureOne() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		Files.delete(engineDir);
		// the user sets up the directory in the config window
		dialogs.engineConfigAnswer = () -> {
			try {
				Files.createDirectories(engineDir);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return true;
		};

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of("engine config"), dialogs.calls);
		assertDialogsOnUiThread();
		assertEquals("Success", onlyJob().getFinishedMessage());
		assertTrue(pkg.isInstalled());
	}

	@Test
	public void theUserIsAskedAgainUntilTheInstallDirectoryWorks() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		Files.delete(engineDir);
		int[] asked = {0};
		dialogs.engineConfigAnswer = () -> {
			// the first time, the user closes the window without fixing anything
			if (++asked[0] == 2) {
				try {
					Files.createDirectories(engineDir);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			return true;
		};

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of("engine config", "engine config"), dialogs.calls);
		assertEquals("Success", onlyJob().getFinishedMessage());
	}

	@Test
	public void cancellingTheConfigWindowStopsTheInstall() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		Files.delete(engineDir);
		dialogs.engineConfigAnswer = () -> false;

		onUi(() -> service.install(pkg));

		assertEquals(List.of("engine config"), dialogs.calls);
		assertEquals(List.of(), jobList());
		assertFalse(installer.alreadyQueued(pkg));
		assertNothingInstalledOrSaved(pkg);
	}

	// --- prerequisites ---

	@Test
	public void missingPrerequisitesAreInstalledToo() throws Exception {
		Package prerequisite = remotePackage("base", new TestZip().file("maps/base.bsp", "BASE"));
		Package alreadyInstalled = remotePackage("old", new TestZip().file("maps/old.bsp", "OLD"));
		alreadyInstalled.setInstalled(true);
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(prerequisite, alreadyInstalled));
		packages(pkg, prerequisite, alreadyInstalled);

		onUi(() -> service.install(pkg));
		awaitFinished(2);

		assertEquals(List.of("Installing prerequisite base", "Installing a"), descriptions());
		assertEquals(List.of("Success", "Success"), finishedMessages());
		assertTrue(prerequisite.isInstalled());
		assertTrue(pkg.isInstalled());
		assertEquals("BASE", read(engineDir.resolve("maps/base.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps/old.bsp")), "an installed prerequisite isn't installed again");
		assertTrue(savedIds().containsAll(List.of("a", "base")), savedIds().toString());
	}

	@Test
	public void unavailablePrerequisitesAreAskedAbout() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(new UnavailableRequirement("gone")));
		packages(pkg);
		dialogs.optionAnswer = 0; // "Install anyways"

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of("options: Prerequisites not available for automatic install"), dialogs.calls);
		assertDialogsOnUiThread();
		assertEquals("Success", onlyJob().getFinishedMessage());
		assertTrue(pkg.isInstalled());
	}

	@Test
	public void declinedUnavailablePrerequisitesStopTheInstall() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(new UnavailableRequirement("gone")));
		packages(pkg);
		dialogs.optionAnswer = 1; // "Cancel Install"

		onUi(() -> service.install(pkg));

		assertEquals(List.of("options: Prerequisites not available for automatic install"), dialogs.calls);
		assertEquals(List.of(), jobList());
		assertNothingInstalledOrSaved(pkg);
	}

	@Test
	public void anOfficialExpansionInTheQuakeDirectoryIsntAskedAbout() throws Exception {
		Files.createFile(Files.createDirectories(engineDir.resolve("hipnotic")).resolve("pak0.pak"));
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(OfficialExpansionRequirement.forMissingPackage("hipnotic", engineDir::toFile)));
		packages(pkg);
		// no answer set: any question fails the test

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of(), dialogs.calls);
		assertTrue(pkg.isInstalled());
	}

	@Test
	public void aMissingOfficialExpansionIsAskedAboutByItsFullName() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(OfficialExpansionRequirement.forMissingPackage("hipnotic", engineDir::toFile)));
		packages(pkg);
		dialogs.optionAnswer = 1; // "Cancel Install"

		onUi(() -> service.install(pkg));

		assertEquals(List.of("options: Prerequisites not available for automatic install"), dialogs.calls);
		assertTrue(dialogs.optionsMessage.contains(
			"- Scourge of Armagon (Mission Pack 1): official expansion, not found in your Quake directory"),
			dialogs.optionsMessage);
		assertNothingInstalledOrSaved(pkg);
	}

	// --- overwriting ---

	@Test
	public void existingFilesAreOverwrittenWhenTheUserAgrees() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		packages(pkg);
		dialogs.overwriteAnswer = (overwrite, write) -> answer(write, overwrite);

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of("overwrite"), dialogs.calls);
		assertDialogsOnUiThread();
		assertEquals(List.of(relativeName("maps", "a.bsp")), dialogs.askedToOverwrite, "the existing file");
		assertEquals(List.of(relativeName("maps", "b.bsp")), dialogs.askedToWrite, "the new file");
		assertEquals("Success", onlyJob().getFinishedMessage());
		assertEquals("NEW", read(engineDir.resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir.resolve("maps/b.bsp")));
		assertTrue(pkg.isInstalled());
	}

	@Test
	public void existingFilesTheUserDidntPickAreKept() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		packages(pkg);
		dialogs.overwriteAnswer = (overwrite, write) -> answer(write, List.of());

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals("Success", onlyJob().getFinishedMessage());
		assertEquals("OLD", read(engineDir.resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir.resolve("maps/b.bsp")));
		assertTrue(pkg.isInstalled());
	}

	/** What the overwrite dialog returns: the new files, plus the existing files the user picked. */
	private static List<String> answer(List<String> newFiles, List<String> picked) {
		List<String> answer = new ArrayList<>(newFiles);
		answer.addAll(picked);
		return answer;
	}

	@Test
	public void declinedOverwriteCancelsTheInstall() throws Exception {
		Files.createDirectories(engineDir.resolve("maps"));
		Files.writeString(engineDir.resolve("maps/a.bsp"), "OLD");
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "NEW").file("maps/b.bsp", "B"));
		packages(pkg);
		dialogs.overwriteAnswer = (overwrite, write) -> null;

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals(List.of("overwrite"), dialogs.calls, "no error after declining");
		assertEquals("Canceled", onlyJob().getFinishedMessage());
		assertEquals("OLD", read(engineDir.resolve("maps/a.bsp")));
		assertFalse(Files.exists(engineDir.resolve("maps/b.bsp")));
		assertNothingInstalledOrSaved(pkg);
	}

	/** A file name as the overwrite question gets it: relative to the Quake directory, with the OS separator. */
	private String relativeName(String first, String... more) {
		return engineDir.relativize(engineDir.resolve(first).resolve(String.join("/", more))).toString();
	}

	// --- failures ---

	@Test
	public void aMissingDownloadShowsAWarning() throws Exception {
		Package pkg = missingPackage("a");
		packages(pkg);

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals("File not found", onlyJob().getFinishedMessage());
		assertEquals(List.of("warning: File not found (404)"), dialogs.calls);
		assertDialogsOnUiThread();
		assertNothingInstalledOrSaved(pkg);
	}

	@Test
	public void anUnwritableFileShowsAnError() throws Exception {
		// a plain file where the zip expects a directory
		Files.writeString(engineDir.resolve("blocker"), "not a directory");
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A").file("blocker/x.bsp", "X"));
		packages(pkg);

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals("Couldn't write", onlyJob().getFinishedMessage());
		assertEquals(List.of("error: Couldn't write to harddisk"), dialogs.calls);
		assertDialogsOnUiThread();
		assertNothingInstalledOrSaved(pkg);
	}

	@Test
	public void aBrokenArchiveShowsAnError() throws Exception {
		Package pkg = missingPackage("a");
		// a cached download that isn't a zip
		Files.writeString(downloadDir.resolve(pkg.getFilename()), "this is not a zip file");
		packages(pkg);

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals("File Error", onlyJob().getFinishedMessage());
		assertEquals(List.of("error: Couldn't open file!"), dialogs.calls);
		assertDialogsOnUiThread();
		assertNothingInstalledOrSaved(pkg);
	}

	@Test
	public void cancellingTheRowStopsTheInstallWithoutAnError() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		CountDownLatch release = blockDiskThread();
		onUi(() -> service.install(pkg));

		JobVm job = onlyJob();
		onUi(job::cancel);
		release.countDown();
		awaitFinished(1);

		assertEquals("Canceled", onlyJob().getFinishedMessage());
		assertEquals(List.of(), dialogs.calls);
		assertFalse(Files.exists(engineDir.resolve("maps/a.bsp")));
		assertNothingInstalledOrSaved(pkg);
	}

	// --- uninstalling ---

	@Test
	public void uninstallRemovesTheFilesAndSavesTheList() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		onUi(() -> service.install(pkg));
		awaitFinished(1);

		onUi(() -> service.uninstall(pkg));
		awaitFinished(1);

		assertEquals(List.of("Installing a", "Uninstalling a"), descriptions());
		assertEquals("success", jobList().get(1).getFinishedMessage());
		assertFalse(Files.exists(engineDir.resolve("maps/a.bsp")));
		assertFalse(pkg.isInstalled());
		assertEquals(List.of(), savedIds());
		assertEquals(List.of(), dialogs.calls);
	}

	// --- recent packages ---

	private List<String> recentIds() throws Exception {
		return onUi(recent::ids);
	}

	@Test
	public void anInstalledPackageBecomesTheMostRecent() throws Exception {
		Package a = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		Package b = remotePackage("b", new TestZip().file("maps/b.bsp", "B"));
		packages(a, b);

		onUi(() -> service.install(a));
		awaitFinished(1);
		onUi(() -> service.install(b));
		awaitFinished(1);

		assertEquals(List.of("b", "a"), recentIds());
	}

	@Test
	public void aPrerequisiteInstalledAlongDoesntBecomeRecent() throws Exception {
		Package prerequisite = remotePackage("base", new TestZip().file("maps/base.bsp", "BASE"));
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		pkg.setRequirements(List.of(prerequisite));
		packages(pkg, prerequisite);

		onUi(() -> service.install(pkg));
		awaitFinished(2);

		assertTrue(prerequisite.isInstalled());
		assertEquals(List.of("a"), recentIds());
	}

	@Test
	public void aPrerequisiteInstalledOnItsOwnBecomesRecent() throws Exception {
		Package prerequisite = remotePackage("base", new TestZip().file("maps/base.bsp", "BASE"));
		packages(prerequisite);

		onUi(() -> service.install(prerequisite));
		awaitFinished(1);

		assertEquals(List.of("base"), recentIds());
	}

	@Test
	public void aFailedInstallDoesntBecomeRecent() throws Exception {
		Package pkg = missingPackage("a");
		packages(pkg);
		onUi(() -> recent.add("other"));

		onUi(() -> service.install(pkg));
		awaitFinished(1);

		assertEquals("File not found", onlyJob().getFinishedMessage());
		assertEquals(List.of("other"), recentIds());
	}

	@Test
	public void aCancelledInstallDoesntBecomeRecent() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		CountDownLatch release = blockDiskThread();
		onUi(() -> service.install(pkg));

		onUi(onlyJob()::cancel);
		release.countDown();
		awaitFinished(1);

		assertEquals("Canceled", onlyJob().getFinishedMessage());
		assertEquals(List.of(), recentIds());
	}

	@Test
	public void anUninstalledPackageIsTakenOffTheRecentList() throws Exception {
		Package a = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		Package b = remotePackage("b", new TestZip().file("maps/b.bsp", "B"));
		packages(a, b);
		onUi(() -> service.install(a));
		awaitFinished(1);
		onUi(() -> service.install(b));
		awaitFinished(1);

		onUi(() -> service.uninstall(a));
		awaitFinished(1);

		assertFalse(a.isInstalled());
		assertEquals(List.of("b"), recentIds());
	}

	@Test
	public void uninstallOfNothingDoesNothing() throws Exception {
		packages();

		onUi(() -> service.uninstall(null));

		assertEquals(List.of(), jobList());
	}

	@Test
	public void cancellingTheConfigWindowStopsTheUninstall() throws Exception {
		Package pkg = remotePackage("a", new TestZip().file("maps/a.bsp", "A"));
		packages(pkg);
		onUi(() -> service.install(pkg));
		awaitFinished(1);
		// the Quake directory can't be written to any more
		Path moved = tmp.resolve("moved");
		Files.move(engineDir, moved);
		dialogs.engineConfigAnswer = () -> false;

		onUi(() -> service.uninstall(pkg));

		assertEquals(List.of("engine config"), dialogs.calls);
		assertEquals(1, jobList().size(), "no uninstall row");
		assertTrue(pkg.isInstalled());
		assertEquals("A", read(moved.resolve("maps/a.bsp")));
	}
}
