package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.InstallPipeline;
import de.haukerehfeld.quakeinjector.feature.readme.ReadmeService;
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.haukerehfeld.quakeinjector.TestZip.rel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The readme files of installed packages, end to end: install a real zip whose extract mapping renames
 * the readme, then list and read it through the service.
 */
public class ReadmeServiceTest {
	@TempDir
	Path tmp;

	private Path engineDir;
	private Configuration config;
	private final TestThreads threads = new TestThreads();
	private final ExecutorService readmeThread = Executors.newSingleThreadExecutor();
	private ReadmeService service;

	@BeforeEach
	public void setUp() throws Exception {
		engineDir = Files.createDirectories(tmp.resolve("quake"));
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(engineDir.toFile());
		config.DownloadPath.set(Files.createDirectories(tmp.resolve("downloads")).toFile());
		service = new ReadmeService(config.EnginePath::get, readmeThread, threads.ui());
	}

	@AfterEach
	public void tearDown() throws Exception {
		threads.close();
		readmeThread.shutdownNow();
	}

	/** Installs the zip like the app does and gives the package the installed file list. */
	private Package install(TestZip zip, ExtractMapping mapping) throws Exception {
		String url = zip.writeTo(tmp.resolve("testmap.zip")).toUri().toURL().toString();
		Package pkg = TestZip.testPackage("testmap", mapping, url);
		RecordingInstallListener listener = new RecordingInstallListener();
		new InstallPipeline(url, pkg, listener, config.DownloadPath, config.EnginePath, threads.executors())
			.start().get(10, TimeUnit.SECONDS);
		assertEquals(List.of("succeeded"), listener.calls);
		pkg.setFileList(listener.succeededWith);
		pkg.setInstalled(true);
		return pkg;
	}

	@Test
	public void findsAndReadsTheReadmeUnderItsMappedName() throws Exception {
		ExtractMapping mapping = new ExtractMapping();
		mapping.addMapping("/", "testmap");
		mapping.addMapping("readme.txt", "testmap/testmap_readme.txt");
		Package pkg = install(new TestZip()
			.file("readme.txt", "the readme")
			.file("maps/testmap.bsp", "BSP"), mapping);

		List<String> files = service.textFiles(pkg);

		assertEquals(List.of(rel("testmap", "testmap_readme.txt")), files.stream().map(f -> f.replace('\\', '/')).toList());
		assertFalse(Files.exists(engineDir.resolve("readme.txt")), "the archive's own name isn't used");
		assertEquals("the readme", service.read(files.get(0)).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void readResultArrivesOnTheUiThread() throws Exception {
		Package pkg = install(new TestZip().file("testmap.txt", "text"), new ExtractMapping());
		AtomicReference<String> thread = new AtomicReference<>();

		service.read(service.textFiles(pkg).get(0))
			.whenComplete((text, error) -> thread.set(Thread.currentThread().getName()))
			.get(5, TimeUnit.SECONDS);

		assertEquals(TestThreads.UI, thread.get());
	}

	@Test
	public void missingFileFailsWithTheIoError() throws Exception {
		Package pkg = install(new TestZip().file("testmap.txt", "text"), new ExtractMapping());
		String path = service.textFiles(pkg).get(0);
		Files.delete(engineDir.resolve(path));

		ExecutionException e = assertThrows(ExecutionException.class, () -> service.read(path).get(5, TimeUnit.SECONDS));

		assertInstanceOf(NoSuchFileException.class, e.getCause());
	}

	@Test
	public void withoutAnInstalledFileListTheDatabaseListIsUsed() {
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), "file:///unused.zip");
		PackageFileList supposed = new PackageFileList("testmap");
		supposed.add(new FileInfo("testmap/testmap_readme.txt", 0, 10));
		pkg.setSupposedFileList(supposed);

		assertEquals(List.of("testmap/testmap_readme.txt"), service.textFiles(pkg));
	}

	@Test
	public void noFileListsNoFiles() {
		Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), "file:///unused.zip");

		assertEquals(List.of(), service.textFiles(pkg));
	}
}
