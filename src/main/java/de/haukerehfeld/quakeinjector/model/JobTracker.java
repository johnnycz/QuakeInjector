package de.haukerehfeld.quakeinjector.model;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public interface JobTracker extends PropertyChangeListener {
	void start();
	void finish(String message);
	void setProgress(int progress);
}
