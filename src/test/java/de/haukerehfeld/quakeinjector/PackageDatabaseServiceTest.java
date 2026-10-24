package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseService;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel.JobVm;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.JobTracker;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loading the package database into the package list, with a file:// URL standing in for the server
 * and the real job list view model showing the progress row.
 */
public class PackageDatabaseServiceTest {
	private static final String DESCRIPTION = "Downloading package database";

	@TempDir
	Path tmp;

	private final TestThreads threads = new TestThreads();
	private final ExecutorService background = Executors.newSingleThreadExecutor();
	private final InstallQueueViewModel jobs = new InstallQueueViewModel();
	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final RequirementList packages = new RequirementList();
	/** Threads {@code whenLoaded} ran on. */
	private final List<String> loadedOn = new CopyOnWriteArrayList<>();
	/** Completes with the message of the first job that finishes. */
	private final CompletableFuture<String> finished = new CompletableFuture<>();

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
				finished.complete(job.getFinishedMessage());
			}
		});
		service = new PackageDatabaseService(config, packages, jobs, dialogs, background, threads.ui());
	}

	@AfterEach
	public void tearDown() throws Exception {
		background.shutdownNow();
		threads.close();
	}

	private void useServer(Path database) throws Exception {
		config.RepositoryDatabasePath.set(database.toUri().toURL().toString());
	}

	private void startLoad() throws Exception {
		threads.ui().submit(() -> service.load(() -> loadedOn.add(Thread.currentThread().getName())))
			.get(10, TimeUnit.SECONDS);
	}

	/** Wait until the load finished and the rest of its completion on the UI thread ran. */
	private String awaitFinished() throws Exception {
		String message = finished.get(10, TimeUnit.SECONDS);
		threads.ui().submit(() -> { }).get(10, TimeUnit.SECONDS);
		return message;
	}

	/** Keep the background thread busy until the returned latch is released. */
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

		startLoad();

		JobVm job = threads.ui().submit(this::onlyJob).get(10, TimeUnit.SECONDS);
		assertEquals(DESCRIPTION, job.getDescription());
		assertTrue(job.isProgressUnknown());
		assertFalse(job.isFinished());
		assertEquals(List.of(), dialogs.calls, "no popup");
		release.countDown();
		awaitFinished();
	}

	@Test
	public void downloadReplacesThePackageList() throws Exception {
		List<String> notifiedOn = new CopyOnWriteArrayList<>();
		packages.addChangeListener(e -> notifiedOn.add(Thread.currentThread().getName()));

		startLoad();

		assertEquals("Done", awaitFinished());
		assertTrue(packages.size() > 0);
		assertEquals(List.of(TestThreads.UI), notifiedOn);
		assertEquals(List.of(TestThreads.UI), loadedOn);
		assertTrue(Files.size(cache) > 0, "the download must be kept as the new cache");
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void usingTheCachedCopyIsShownInTheRowNotInADialog() throws Exception {
		startLoad();
		awaitFinished();
		int downloaded = packages.size();
		useServer(tmp.resolve("remote/missing.json"));
		CompletableFuture<String> second = new CompletableFuture<>();
		jobs.addChangeListener(e -> {
			if (e.getSource() instanceof JobVm job && job.isFinished() && job.getId() == 1) {
				second.complete(job.getFinishedMessage());
			}
		});

		startLoad();

		assertEquals("Offline copy used", second.get(10, TimeUnit.SECONDS));
		threads.ui().submit(() -> { }).get(10, TimeUnit.SECONDS);
		assertEquals(downloaded, packages.size());
		assertEquals(2, loadedOn.size());
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void failureShowsAnErrorAndKeepsTheList() throws Exception {
		useServer(tmp.resolve("remote/missing.json"));

		startLoad();

		assertEquals("Failed", awaitFinished());
		assertEquals(0, packages.size());
		assertEquals(List.of(), loadedOn);
		assertEquals(List.of("error: Database parsing failed!"), dialogs.calls);
		assertEquals(List.of(TestThreads.UI), dialogs.threads);
	}

	@Test
	public void cancelStopsTheLoad() throws Exception {
		CountDownLatch release = blockBackground();
		startLoad();

		threads.ui().submit(() -> onlyJob().cancel()).get(10, TimeUnit.SECONDS);
		release.countDown();

		assertEquals("Cancelled", awaitFinished());
		assertEquals(0, packages.size());
		assertEquals(List.of(), loadedOn);
		assertFalse(Files.exists(cache), "nothing cached");
		assertEquals(List.of(), dialogs.calls);
	}

	@Test
	public void loadingAgainWhileLoadingIsIgnored() throws Exception {
		CountDownLatch release = blockBackground();

		startLoad();
		startLoad();
		release.countDown();

		assertEquals("Done", awaitFinished());
		threads.ui().submit(this::onlyJob).get(10, TimeUnit.SECONDS);
		assertEquals(List.of(TestThreads.UI), loadedOn);
	}

	@Test
	public void canLoadAgainAfterALoadFinished() throws Exception {
		startLoad();
		awaitFinished();

		startLoad();
		background.submit(() -> { }).get(10, TimeUnit.SECONDS);
		threads.ui().submit(() -> { }).get(10, TimeUnit.SECONDS);

		assertEquals(2, threads.ui().submit(() -> jobs.jobs.size()).get(10, TimeUnit.SECONDS));
		assertEquals(List.of(TestThreads.UI, TestThreads.UI), loadedOn);
		assertEquals(JobTracker.UNKNOWN_PROGRESS, jobs.jobs.get(1).getProgress());
	}
}
