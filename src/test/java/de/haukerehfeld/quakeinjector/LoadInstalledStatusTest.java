package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.PackageInstallUninstallService;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
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

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading the installed maps file at startup through
 * {@link PackageInstallUninstallService#loadInstalledStatus}: the packages in it are marked installed
 * with their files, a missing file means nothing is installed, a broken one shows an error.
 */
public class LoadInstalledStatusTest {
	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final RecordingDialogs dialogs = new RecordingDialogs();

	private Path installedMapsFile;
	private Installer installer;
	private PackageInstallUninstallService service;
	private RequirementList list;
	/** The thread of each change notification of the list. */
	private final List<String> listChanges = new CopyOnWriteArrayList<>();

	@BeforeEach
	public void setUp() throws Exception {
		installedMapsFile = tmp.resolve("installedMaps.xml");
		Configuration config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(Files.createDirectories(tmp.resolve("quake")).toFile());
		config.DownloadPath.set(Files.createDirectories(tmp.resolve("downloads")).toFile());
		installer = new Installer(config.EnginePath, config.DownloadPath, threads.executors());
		service = new PackageInstallUninstallService(new InstallQueueViewModel(), dialogs, new RecentPackages());
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
	}

	private void packages(Package... packages) {
		list = new RequirementList(new ArrayList<Requirement>(List.of(packages)));
		list.addChangeListener(e -> listChanges.add(Thread.currentThread().getName()));
		service.init(installer, list, new SaveInstalled(installedMapsFile.toFile()));
	}

	private static Package pkg(String id) {
		return TestZip.testPackage(id, new ExtractMapping(), "file:///" + id + ".zip");
	}

	/** Write an installed maps file in which {@code id} is installed with maps/{id}.bsp. */
	private void writeInstalled(String id) throws Exception {
		Package installed = pkg(id);
		PackageFileList files = new PackageFileList(id);
		files.add(new FileInfo("maps/" + id + ".bsp", 1));
		installed.setFileList(files);
		installed.setInstalled(true);
		new SaveInstalled(installedMapsFile.toFile()).write(SaveInstalled.snapshot(List.of(installed)));
	}

	private void load() throws Exception {
		threads.ui().submit(() -> service.loadInstalledStatus()).get(10, TimeUnit.SECONDS)
			.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void packagesInTheFileAreMarkedInstalledWithTheirFiles() throws Exception {
		writeInstalled("a");
		Package a = pkg("a");
		Package b = pkg("b");
		packages(a, b);

		load();

		assertTrue(a.isInstalled());
		assertEquals(List.of("maps/a.bsp"), names(a.getFileList()));
		assertFalse(b.isInstalled());
		assertEquals(List.of(TestThreads.UI), listChanges, "the list changes once, on the UI thread");
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void withoutAFileNothingIsInstalledAndNothingShown() throws Exception {
		Package a = pkg("a");
		packages(a);

		load();

		assertFalse(a.isInstalled());
		assertEquals(List.of(TestThreads.UI), listChanges, "the list is shown anyway");
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void aBrokenFileShowsAnErrorOnTheUiThread() throws Exception {
		Files.writeString(installedMapsFile, "this is not xml");
		Package a = pkg("a");
		packages(a);

		load();

		assertFalse(a.isInstalled());
		assertEquals(List.of(TestThreads.UI), listChanges);
		assertEquals(List.of("error: Reading installed maps failed!"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI), dialogs.threads);
	}
}
