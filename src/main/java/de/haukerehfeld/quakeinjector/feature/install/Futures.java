package de.haukerehfeld.quakeinjector.feature.install;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Small helpers for running checked-exception code as CompletableFuture stages.
 */
final class Futures {
	private Futures() {
	}

	@FunctionalInterface
	interface Stage<T> {
		T run() throws Exception;
	}

	/** Run a stage body, wrapping checked exceptions so CompletableFuture can carry them. */
	static <T> T unchecked(Stage<T> stage) {
		try {
			return stage.run();
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new CompletionException(e);
		}
	}

	/** Strip the CompletionException / ExecutionException wrappers CompletableFuture adds. */
	static Throwable unwrap(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException)
		       && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}
}
