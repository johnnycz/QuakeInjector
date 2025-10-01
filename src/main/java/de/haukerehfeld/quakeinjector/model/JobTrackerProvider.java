package de.haukerehfeld.quakeinjector.model;

import java.awt.event.ActionListener;

public interface JobTrackerProvider {
	JobTracker addJob(String description, Runnable cancelAction);
}
