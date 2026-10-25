package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class EdtTest {
	@Test
	public void passesOnTheEdt() throws Exception {
		AtomicBoolean result = new AtomicBoolean();

		SwingUtilities.invokeAndWait(() -> result.set(Edt.check("EdtTest on EDT")));

		assertTrue(result.get());
	}

	@Test
	public void reportsCallsFromOtherThreads() {
		// JUnit runs tests on its own thread, not on the EDT
		assertFalse(Edt.check("EdtTest off EDT"));
		// a second violation from the same place is still detected, just not logged again
		assertFalse(Edt.check("EdtTest off EDT"));
	}
}
