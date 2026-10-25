package de.haukerehfeld.quakeinjector.model;


public interface JobTrackerProvider {
	JobTracker addJob(String description, Runnable cancelAction);
}
