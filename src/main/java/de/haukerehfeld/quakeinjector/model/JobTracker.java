package de.haukerehfeld.quakeinjector.model;

/**
 * One row in the job list (e.g. an install). Call its methods on the UI thread.
 */
public interface JobTracker {
	/** For {@link #setProgress}: the job is running, but how far along it is isn't known. */
	int UNKNOWN_PROGRESS = -1;

	void start();
	void finish(String message);
	/** @param progress 0..100, or {@link #UNKNOWN_PROGRESS} */
	void setProgress(int progress);
}
