package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.SwingWorkerAdapter;
import de.haukerehfeld.quakeinjector.utils.AsyncListener;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SwingWorkerAdapterTest {
	/** Records the outcome and whether it was delivered on the EDT. */
	private static class Outcome<T> implements AsyncListener<T> {
		final CompletableFuture<Object> result = new CompletableFuture<>();
		volatile boolean onEdt;

		@Override
		public void onSuccess(T value) {
			onEdt = SwingUtilities.isEventDispatchThread();
			result.complete(value);
		}

		@Override
		public void onFailure(Exception exception) {
			onEdt = SwingUtilities.isEventDispatchThread();
			result.complete(exception);
		}
	}

	@Test
	public void deliversTheResultOnTheEdt() throws Exception {
		Outcome<String> outcome = new Outcome<>();

		new SwingWorkerAdapter<>(() -> "parsed", outcome).execute();

		assertEquals("parsed", outcome.result.get(10, TimeUnit.SECONDS));
		assertTrue(outcome.onEdt);
	}

	@Test
	public void cancelStillReachesTheListener() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		Outcome<String> outcome = new Outcome<>();
		SwingWorkerAdapter<String> adapter = new SwingWorkerAdapter<>(() -> {
			started.countDown();
			Thread.sleep(10_000); // a slow download, interrupted by cancel(true)
			return "too late";
		}, outcome);

		adapter.execute();
		assertTrue(started.await(10, TimeUnit.SECONDS));
		adapter.cancel(true); // what the progress popup's cancel button does

		// the listener must hear about it, otherwise the progress popup would never close
		assertInstanceOf(CancelledException.class, outcome.result.get(10, TimeUnit.SECONDS));
		assertTrue(outcome.onEdt);
	}
}
