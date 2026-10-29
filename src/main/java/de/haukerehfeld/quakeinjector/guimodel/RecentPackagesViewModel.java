package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.util.ArrayList;
import java.util.List;

/**
 * The Recent menu: the recently installed or played packages that are in the loaded package list, newest
 * first. Ids that aren't in the list (database not loaded yet, or the package was removed from it) are left
 * out. Listeners are notified when the recent list changes or the package list is replaced.
 */
public class RecentPackagesViewModel implements ChangeListener {

	/**
	 * One menu entry.
	 * @param label    "Title (id)", or the id if the package has no title
	 * @param mnemonic the number to show in front of the label (1-9, then 0), or null past the tenth item
	 */
	public record Item(String id, String label, Character mnemonic) {}

	private final RecentPackages recent;
	private final PackageList packages;
	private final ChangeListenerList listeners = new ChangeListenerList();

	public RecentPackagesViewModel(RecentPackages recent, PackageList packages) {
		this.recent = recent;
		this.packages = packages;
		recent.addChangeListener(this);
		packages.addChangeListener(this);
	}

	/** The entries to show, newest first; empty if none of the recent packages is in the list. */
	public List<Item> items() {
		List<Item> items = new ArrayList<>();
		for (String id : recent.ids()) {
			Package pkg = packages.get(id);
			if (pkg == null) {
				continue;
			}
			items.add(new Item(id, label(pkg), mnemonic(items.size())));
		}
		return items;
	}

	private static String label(Package pkg) {
		String title = pkg.getTitle();
		if (title == null || title.isBlank()) {
			return pkg.getId();
		}
		return title.trim() + " (" + pkg.getId() + ")";
	}

	/** 1-9 for the first nine items, 0 for the tenth, none after that. */
	private static Character mnemonic(int index) {
		if (index < 9) {
			return (char) ('1' + index);
		}
		if (index == 9) {
			return '0';
		}
		return null;
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		listeners.notifyChangeListeners(this);
	}

	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}
}
