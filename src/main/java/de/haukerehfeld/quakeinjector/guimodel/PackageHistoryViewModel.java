package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Back and Forward through the packages viewed, like a web browser's history (for this session only).
 * <p>
 * A newly selected package is first <em>pending</em>: it goes into the history only when the view calls
 * {@link #settle()}, after it stayed selected for a moment, so the rows passed with the arrow keys aren't
 * recorded. Going Back or Forward records a pending package first, so Back returns to the package that was
 * shown before it.
 * <p>
 * Recording a package drops the entries after the current one (as a browser drops its Forward history).
 * Entries whose package isn't in the loaded list (any more) stay, but Back and Forward skip them.
 * Listeners are notified when what {@link #canGoBack} or {@link #canGoForward} return may have changed,
 * and when a package becomes pending ({@link #hasPending}).
 */
public class PackageHistoryViewModel implements ChangeListener {
	/** At most this many entries; the oldest go first */
	public static final int MAX_SIZE = 100;

	private final PackageInteractionViewModel selection;
	private final PackageList packages;
	private final ChangeListenerList listeners = new ChangeListenerList();

	private final List<String> entries = new ArrayList<>();
	/** The entry shown, or -1 while the history is empty */
	private int current = -1;
	/** The selected package not recorded yet, or null */
	private String pending;
	/** The id of the selected package when the selection last changed */
	private String selectedId;

	public PackageHistoryViewModel(PackageInteractionViewModel selection, PackageList packages) {
		this.selection = selection;
		this.packages = packages;
		selection.addChangeListener(this);
		packages.addChangeListener(this);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == packages) {
			// other packages may be in the list now
			notifyChangeListeners();
			return;
		}
		// the selection view model also tells about changes of the selected package: only a new id counts
		Package selected = selection.getSelectedPackage();
		String id = selected == null ? null : selected.getId();
		if (id == null || id.equals(selectedId)) {
			return;
		}
		selectedId = id;
		pending = id.equals(currentId()) ? null : id;
		notifyChangeListeners();
	}

	/** @return true while a selected package waits for {@link #settle()} */
	public boolean hasPending() {
		return pending != null;
	}

	/** Records the pending package, if there is one: the selection stayed on it long enough. */
	public void settle() {
		if (pending == null) {
			return;
		}
		record(pending);
		notifyChangeListeners();
	}

	/** @return true if the oldest entry was dropped to make room */
	private boolean record(String id) {
		pending = null;
		entries.subList(current + 1, entries.size()).clear();
		entries.add(id);
		boolean dropped = entries.size() > MAX_SIZE;
		if (dropped) {
			entries.remove(0);
		}
		current = entries.size() - 1;
		return dropped;
	}

	public boolean canGoBack() {
		return previous() >= 0;
	}

	public boolean canGoForward() {
		return next() >= 0;
	}

	/**
	 * Moves back one entry. The caller shows the package; selecting it doesn't record it again.
	 * @return the id of the package to show, or null if there is nothing to go back to
	 */
	public String back() {
		return moveTo(previous());
	}

	/**
	 * Moves forward one entry. The caller shows the package; selecting it doesn't record it again.
	 * @return the id of the package to show, or null if there is nothing to go forward to
	 */
	public String forward() {
		return moveTo(next());
	}

	private String moveTo(int index) {
		if (index < 0) {
			return null;
		}
		if (pending != null && record(pending)) {
			// the oldest entry was dropped: the others moved down one
			index--;
			if (index < 0) {
				notifyChangeListeners();
				return null;
			}
		}
		current = index;
		notifyChangeListeners();
		return entries.get(current);
	}

	/** The entry Back goes to, or -1. A pending package becomes the current entry first. */
	private int previous() {
		int from = pending != null ? current : current - 1;
		for (int i = from; i >= 0; i--) {
			if (inList(i)) {
				return i;
			}
		}
		return -1;
	}

	/** The entry Forward goes to, or -1. Recording a pending package drops the Forward entries. */
	private int next() {
		if (pending != null) {
			return -1;
		}
		for (int i = current + 1; i < entries.size(); i++) {
			if (inList(i)) {
				return i;
			}
		}
		return -1;
	}

	private boolean inList(int index) {
		return packages.get(entries.get(index)) != null;
	}

	private String currentId() {
		return current < 0 ? null : entries.get(current);
	}

	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	private void notifyChangeListeners() {
		listeners.notifyChangeListeners(this);
	}
}
