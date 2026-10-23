package de.haukerehfeld.quakeinjector.model;

/**
 * One row in the job list (e.g. an install). Call its methods on the UI thread.
 */
public interface JobTracker {
	void start();
	void finish(String message);
	/** @param progress 0..100 */
	void setProgress(int progress);
}
