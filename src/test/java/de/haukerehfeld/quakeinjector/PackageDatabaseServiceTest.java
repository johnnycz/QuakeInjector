package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseService;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel.JobVm;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loading the package database into the package list: the cached copy first, then the download.
 * A file:// URL stands in for the server; the real job list view model shows the progress row.
 */
public class PackageDatabaseServiceTest {
	private static final String DESCRIPTION = "Loading package database";

	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final ExecutorService background = Executors.newSingleThreadExecutor();
	private final InstallQueueViewModel jobs = new InstallQueueViewModel();
	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final RequirementList packages = new RequirementList();
	/** For each time the list was replaced: whether the load's row was finished by then. */
	private final List<Boolean> replacedWhenFinished = new CopyOnWriteArrayList<>();
	/** For each call of whenFirstLoaded: how often the list had been replaced by then. */
	private final List<Integer> firstLoadedAfter = new CopyOnWriteArrayList<>();
	/** Runs inside whenFirstLoaded, on the UI thread. */
	private volatile Runnable duringFirstLoaded = () -> { };
	/** Finish message of each job, by job id. */
	private final Map<Integer, CompletableFuture<String>> finished = new ConcurrentHashMap<>();

	private Path remoteDatabase;
	private Path cache;
	private Configuration config;
	private PackageDatabaseService service;

	@BeforeEach
	public void setUp() throws Exception {
		remoteDatabase = tmp.resolve("remote/database.json");
		Files.createDirectories(remoteDatabase.getParent());
		try (InputStream in = getClass().getResourceAsStream("/solr2pp.json")) {
			Files.copy(in, remoteDatabase);
		}
		cache = tmp.resolve("cache/database.json");
		Files.createDirectories(cache.getParent());

		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.LocalDatabaseFile.set(cache.toFile());
		useServer(remoteDatabase);

		jobs.addChangeListener(e -> {
			if (e.getSource() instanceof JobVm job && job.isFinished()) {
				finishedMessage(job.getId()).complete(job.getFinishedMessage());
			}
		});
		packages.addChangeListener(e -> replacedWhenFinished.add(jobs.jobs.get(jobs.jobs.size() - 1).isFinished()));
		service = new PackageDatabaseService(config, packages, jobs, dialogs, () -> {
			firstLoadedAfter.add(replacedWhenFinished.size());
			duringFirstLoaded.run();
		}, background, threads.ui());
	}

	@AfterEach
	public void tearDown() throws Exception {
		background.shutdownNow();
		threads.close();
	}

	private void useServer(Path database) throws Exception {
		config.RepositoryDatabasePath.set(database.toUri().toURL().toString());
	}

	private void cacheTheServersDatabase() throws Exception {
		Files.copy(remoteDatabase, cache);
	}

	private CompletableFuture<String> finishedMessage(int jobId) {
		return finished.computeIfAbsent(jobId, id -> new CompletableFuture<>());
	}

	private <T> T onUi(Callable<T> work) throws Exception {
		return threads.ui().submit(work).get(10, TimeUnit.SECONDS);
	}

	private void onUi(Runnable work) throws Exception {
		threads.ui().submit(work).get(10, TimeUnit.SECONDS);
	}

	/** Wait until job {@code jobId} finished and the rest of its completion on the UI thread ran. */
	private String awaitFinished(int jobId) throws Exception {
		String message = finishedMessage(jobId).get(10, TimeUnit.SECONDS);
		onUi(() -> { });
		return message;
	}

	/** Keep the background thread busy, from the call on, until the returned latch is released. */
	private CountDownLatch blockBackground() {
		CountDownLatch release = new CountDownLatch(1);
		background.execute(() -> {
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		return release;
	}

	private JobVm onlyJob() {
		assertEquals(1, jobs.jobs.size(), "one row in the job list");
		return jobs.jobs.get(0);
	}

	@Test
	public void showsARowWithUnknownProgressWhileLoading() throws Exception {
		CountDownLatch release = blockBackground();

		onUi(service::loadCachedThenDownload);

		JobVm job = onUi(this::onlyJob);
		assertEquals(DESCRIPTION, job.getDescription());
		assertTrue(job.isProgressUnknown());
		assertFalse(job.isFinished());
		assertEquals(List.of(), dialogs.calls, "no popup");
		release.countDown();
		awaitFinished(0);
	}

	@Test
	public void showsTheCachedListBeforeTheDownloadReplacesIt() throws Exception {
		cacheTheServersDatabase();

		onUi(service::loadCachedThenDownload);

		assertEquals("Done", awaitFinished(0));
		assertEquals(List.of(false, true), replacedWhenFinished, "the cached list, then the downloaded one");
		assertEquals(List.of(1), firstLoadedAfter, "installed state is read once, after the cached list");
		assertTrue(packages.size() > 0);
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void failedDownloadKeepsTheCachedListWithoutADialog() throws Exception {
		cacheTheServersDatabase();
		useServer(tmp.resolve("remote/missing.json"));

		onUi(service::loadCachedThenDownload);

		assertEquals("Download failed, using cached copy", awaitFinished(0));
		assertEquals(List.of(false), replacedWhenFinished, "only the cached list, read once");
		assertEquals(List.of(1), firstLoadedAfter);
		assertTrue(packages.size() > 0);
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void withoutACacheTheDownloadFillsTheList() throws Exception {
		onUi(service::loadCachedThenDownload);

		assertEquals("Done", awaitFinished(0));
		assertEquals(List.of(true), replacedWhenFinished);
		assertEquals(List.of(1), firstLoadedAfter);
		assertTrue(Files.size(cache) > 0, "the download is cached");
	}

	@Test
	public void withoutACacheAFailedDownloadShowsAnError() throws Exception {
		useServer(tmp.resolve("remote/missing.json"));

		onUi(service::loadCachedThenDownload);

		assertEquals("Failed", awaitFinished(0));
		assertEquals(0, packages.size());
		assertEquals(List.of(), firstLoadedAfter);
		assertEquals(List.of("error: Database parsing failed!"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI), dialogs.threads);
	}

	@Test
	public void aBrokenCacheIsSkipped() throws Exception {
		Files.writeString(cache, "{ not json");

		onUi(service::loadCachedThenDownload);

		assertEquals("Done", awaitFinished(0));
		assertEquals(List.of(true), replacedWhenFinished);
		assertTrue(packages.size() > 0);
	}

	@Test
	public void cancellingTheDownloadKeepsTheCachedList() throws Exception {
		cacheTheServersDatabase();
		long cached = Files.size(cache);
		CountDownLatch[] release = new CountDownLatch[1];
		// the download is queued on the background thread right after this
		duringFirstLoaded = () -> release[0] = blockBackground();
		onUi(service::loadCachedThenDownload);
		while (release[0] == null) {
			onUi(() -> { });
		}

		onUi(() -> onlyJob().cancel());
		release[0].countDown();

		assertEquals("Cancelled", awaitFinished(0));
		assertEquals(List.of(false), replacedWhenFinished);
		assertTrue(packages.size() > 0);
		assertEquals(cached, Files.size(cache));
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void downloadOnlyDoesNotFallBackToTheCache() throws Exception {
		cacheTheServersDatabase();
		useServer(tmp.resolve("remote/missing.json"));

		onUi(service::download);

		assertEquals("Failed", awaitFinished(0));
		assertEquals(0, packages.size(), "the cache wasn't read");
	}

	@Test
	public void downloadingAgainKeepsTheInstalledState() throws Exception {
		onUi(service::loadCachedThenDownload);
		awaitFinished(0);
		Package before = onUi(() -> firstPackage());
		PackageFileList files = new PackageFileList(before.getId());
		onUi(() -> packages.setInstalled(List.of(files)));

		onUi(service::download);
		assertEquals("Done", awaitFinished(1));

		Package after = onUi(() -> (Package) packages.get(before.getId()));
		assertNotSame(before, after, "a new list");
		assertTrue(after.isInstalled());
		assertSame(files, after.getFileList());
		assertEquals(List.of(1), firstLoadedAfter, "installed state is read only once");
	}

	private Package firstPackage() {
		for (Requirement r : packages) {
			if (r instanceof Package pkg) {
				return pkg;
			}
		}
		throw new AssertionError("no package");
	}

	@Test
	public void loadingAgainWhileLoadingIsIgnored() throws Exception {
		CountDownLatch release = blockBackground();

		onUi(service::loadCachedThenDownload);
		onUi(service::download);
		release.countDown();

		assertEquals("Done", awaitFinished(0));
		onUi(this::onlyJob);
	}

	@Test
	public void canDownloadAgainAfterALoadFinished() throws Exception {
		onUi(service::loadCachedThenDownload);
		awaitFinished(0);

		onUi(service::download);

		assertEquals("Done", awaitFinished(1));
		assertEquals(2, (int) onUi(() -> jobs.jobs.size()));
	}
}
