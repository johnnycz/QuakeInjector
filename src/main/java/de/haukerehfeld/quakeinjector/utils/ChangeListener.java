package de.haukerehfeld.quakeinjector.utils;

import java.util.EventListener;

public interface ChangeListener extends EventListener {
	void stateChanged(ChangeEvent e);
}
