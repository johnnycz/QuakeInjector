package de.haukerehfeld.quakeinjector.utils;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Forwards progress from a worker thread to the UI executor, as a whole percentage.
 * Only posts when the percentage changes, so a stage reporting every 1 KB doesn't flood the UI thread.
 */
public final class UiProgress {
	private final Executor ui;
	private final IntConsumer target;
	private final AtomicInteger lastPercent = new AtomicInteger(-1);

	/**
	 * @param target receives 0..100 on the UI executor
	 */
	public UiProgress(Executor ui, IntConsumer target) {
		this.ui = ui;
		this.target = target;
	}

	/** Report progress from any thread; values outside 0..100 are clamped. */
	public void report(int percent) {
		int clamped = Math.max(0, Math.min(100, percent));
		if (lastPercent.getAndSet(clamped) != clamped) {
			ui.execute(() -> target.accept(clamped));
		}
	}

	/** Report a stage's own 0..100 progress as the range {@code from..to} of the overall progress. */
	public void reportStage(long stagePercent, int from, int to) {
		report(from + (int) (stagePercent * (to - from) / 100));
	}
}
