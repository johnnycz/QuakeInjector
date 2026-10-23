package de.haukerehfeld.quakeinjector.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared cancel signal for one operation and all of its stages.
 * <p>
 * Any thread may call {@link #cancel()}. Running tasks poll {@link #throwIfCancelled()} and stop by
 * throwing {@link CancelledException}. A task blocked on I/O can register an {@link #onCancel} callback
 * (for example to close its stream) so it wakes up straight away.
 */
public final class CancellationToken {
	/** Handle for an {@link #onCancel} callback; closing it unregisters the callback. */
	public interface Registration extends AutoCloseable {
		@Override
		void close();
	}

	private volatile boolean cancelled = false;
	private final List<Runnable> callbacks = new ArrayList<>(); // guarded by this

	public void cancel() {
		List<Runnable> toRun;
		synchronized (this) {
			if (cancelled) {
				return;
			}
			cancelled = true;
			toRun = new ArrayList<>(callbacks);
			callbacks.clear();
		}
		for (Runnable callback : toRun) {
			runSafely(callback);
		}
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void throwIfCancelled() throws CancelledException {
		if (cancelled) {
			throw new CancelledException();
		}
	}

	/**
	 * Run {@code callback} once when this token gets cancelled, on the cancelling thread.
	 * If the token is already cancelled, the callback runs immediately on the calling thread.
	 */
	public Registration onCancel(Runnable callback) {
		synchronized (this) {
			if (!cancelled) {
				callbacks.add(callback);
				return () -> {
					synchronized (CancellationToken.this) {
						callbacks.remove(callback);
					}
				};
			}
		}
		runSafely(callback);
		return () -> { };
	}

	private static void runSafely(Runnable callback) {
		try {
			callback.run();
		}
		catch (RuntimeException e) {
			System.err.println("Cancel callback failed: " + e);
			e.printStackTrace();
		}
	}
}
