package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.InstallPipeline;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Named single-thread executors standing in for the app's network, disk and UI (EDT) threads.
 */
public class TestThreads implements AutoCloseable {
	public static final String UI = "test-ui";
	public static final String NETWORK = "test-network";
	public static final String DISK = "test-disk";

	private final ExecutorService ui = named(UI);
	private final ExecutorService network = named(NETWORK);
	private final ExecutorService disk = named(DISK);

	private static ExecutorService named(String name) {
		return Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		});
	}

	public InstallPipeline.Executors executors() {
		return new InstallPipeline.Executors(network, disk, ui);
	}

	public ExecutorService ui() {
		return ui;
	}

	@Override
	public void close() throws InterruptedException {
		for (ExecutorService e : List.of(ui, network, disk)) {
			e.shutdownNow();
			e.awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}
