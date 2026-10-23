package de.haukerehfeld.quakeinjector.utils;

/**
 * Thrown by a task when it stops because its {@link CancellationToken} was cancelled.
 */
public class CancelledException extends RuntimeException {
	public CancelledException() {
		super("Cancelled");
	}
}
