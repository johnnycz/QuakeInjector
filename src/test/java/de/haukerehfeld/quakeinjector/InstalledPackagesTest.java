package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.InstalledPackages;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static org.junit.jupiter.api.Assertions.*;

public class InstalledPackagesTest {
	@TempDir
	Path tmp;

	private final ExecutorService disk = Executors.newSingleThreadExecutor(r -> new Thread(r, TestThreads.DISK));
	private final InstalledPackages installedPackages = new InstalledPackages();

	@AfterEach
	public void tearDown() throws Exception {
		disk.shutdownNow();
		disk.awaitTermination(5, TimeUnit.SECONDS);
	}

	private Throwable failureOf(Path file) throws Exception {
		installedPackages.parse(file.toFile(), disk);
		ExecutionException e = assertThrows(ExecutionException.class,
			() -> installedPackages.loaded().get(10, TimeUnit.SECONDS));
		return e.getCause();
	}

	@Test
	public void readsWhatSaveInstalledWrote() throws Exception {
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), "unused");
		PackageFileList files = new PackageFileList("testmap");
		files.add(new FileInfo("maps/a.bsp", 1));
		files.add(new FileInfo("maps", 0));
		pkg.setFileList(files);
		pkg.setInstalled(true);
		Path file = tmp.resolve("installedMaps.xml");
		new SaveInstalled(file.toFile()).write(SaveInstalled.snapshot(List.of(pkg)));

		installedPackages.parse(file.toFile(), disk);
		List<PackageFileList> read = installedPackages.loaded().get(10, TimeUnit.SECONDS);

		assertEquals(1, read.size());
		assertEquals("testmap", read.get(0).getId());
		assertTrue(names(read.get(0)).containsAll(List.of("maps/a.bsp", "maps")), "read: " + names(read.get(0)));
	}

	@Test
	public void missingFileMeansNothingIsInstalledYet() throws Exception {
		assertInstanceOf(InstalledPackages.NoInstalledPackagesFileException.class,
			failureOf(tmp.resolve("does-not-exist.xml")));
	}

	@Test
	public void brokenFileIsAnIoErrorAndIsClosedAgain() throws Exception {
		Path file = Files.writeString(tmp.resolve("installedMaps.xml"), "this is not xml");

		Throwable cause = failureOf(file);

		assertInstanceOf(IOException.class, cause);
		assertFalse(cause instanceof InstalledPackages.NoInstalledPackagesFileException);
		// on Windows an open stream would keep the file locked
		Files.delete(file);
	}

	@Test
	public void readsOnTheGivenExecutor() throws Exception {
		Path file = tmp.resolve("installedMaps.xml");
		new SaveInstalled(file.toFile()).write(SaveInstalled.snapshot(List.of()));

		installedPackages.parse(file.toFile(), disk);
		String thread = installedPackages.loaded()
			.thenApply(ignored -> Thread.currentThread().getName())
			.get(10, TimeUnit.SECONDS);

		assertEquals(TestThreads.DISK, thread);
	}
}
