package de.haukerehfeld.quakeinjector.gui;

import javax.swing.SwingUtilities;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks the threading rule: view models notify their views on the Swing event dispatch thread (EDT).
 * <p>
 * A violation is only logged, not fixed by switching threads: view model methods such as
 * {@code DialogViewModel.showOptions} return the user's answer, which only works when they are
 * called on the EDT in the first place. The fix belongs in the caller.
 */
public final class Edt {
	/** Places that already reported a violation; each is logged once, to keep the console readable. */
	private static final Set<String> reported = ConcurrentHashMap.newKeySet();

	private Edt() {
	}

	/**
	 * @param where the calling view, used in the log message
	 * @return true when called on the EDT; otherwise logs a stack trace (once per {@code where})
	 */
	public static boolean check(String where) {
		if (SwingUtilities.isEventDispatchThread()) {
			return true;
		}
		if (reported.add(where)) {
			new IllegalStateException("[EDT violation] " + where + " was notified on thread '"
				+ Thread.currentThread().getName() + "'; view models must notify views on the EDT")
				.printStackTrace();
		}
		return false;
	}
}
