package de.haukerehfeld.quakeinjector.model;

import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.util.ArrayList;
import java.util.List;

/**
 * The packages installed or played most recently, newest first. A package that becomes recent goes to
 * the top (also when it is already on the list); the oldest drop off the end once there are more than
 * {@link #maxSize()}.
 * <p>
 * Kept by package id, so the list survives a reload of the package database. Ids of packages that are no
 * longer in the database stay on the list. Only use it on the UI thread; listeners are notified on it,
 * synchronously, and only when the list changed.
 */
public class RecentPackages {
	/** How many packages the list holds. */
	public static final int MAX_SIZE = 10;

	/** Between the ids in the settings value; package ids never contain it. */
	private static final String SEPARATOR = ",";

	private final int maxSize;
	private final List<String> ids = new ArrayList<>();
	private final ChangeListenerList listeners = new ChangeListenerList();

	public RecentPackages() {
		this(MAX_SIZE);
	}

	public RecentPackages(int maxSize) {
		if (maxSize < 1) {
			throw new IllegalArgumentException("maxSize must be at least 1: " + maxSize);
		}
		this.maxSize = maxSize;
	}

	/**
	 * The list saved with {@link #toSetting()}. Blank or missing reads as empty; empty entries and
	 * repeated ids are skipped, entries past {@code maxSize} dropped.
	 */
	public static RecentPackages fromSetting(String setting, int maxSize) {
		RecentPackages recent = new RecentPackages(maxSize);
		if (setting == null) {
			return recent;
		}
		for (String part : setting.split(SEPARATOR)) {
			String id = part.trim();
			if (!id.isEmpty() && !recent.ids.contains(id) && recent.ids.size() < maxSize) {
				recent.ids.add(id);
			}
		}
		return recent;
	}

	public static RecentPackages fromSetting(String setting) {
		return fromSetting(setting, MAX_SIZE);
	}

	/**
	 * The list saved in {@code setting}, which then follows every change (an empty list removes it).
	 * Only the value changes; the settings are written to disk elsewhere, e.g. when the app closes.
	 */
	public static RecentPackages boundTo(Configuration.RecentPackagesSetting setting) {
		RecentPackages recent = fromSetting(setting.get());
		recent.addChangeListener(e -> {
			String value = recent.toSetting();
			setting.set(value.isEmpty() ? null : value);
		});
		return recent;
	}

	/** The ids, newest first, separated by commas; empty if the list is. */
	public String toSetting() {
		return String.join(SEPARATOR, ids);
	}

	/** Make the package the most recent one. Null or blank ids are ignored. */
	public void add(String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		if (!ids.isEmpty() && ids.get(0).equals(id)) {
			return;
		}
		ids.remove(id);
		ids.add(0, id);
		while (ids.size() > maxSize) {
			ids.remove(ids.size() - 1);
		}
		listeners.notifyChangeListeners(this);
	}

	/** Take the package off the list, e.g. after it was uninstalled. */
	public void remove(String id) {
		if (ids.remove(id)) {
			listeners.notifyChangeListeners(this);
		}
	}

	/** The ids, newest first. A copy: later changes don't show in it. */
	public List<String> ids() {
		return List.copyOf(ids);
	}

	public boolean contains(String id) {
		return ids.contains(id);
	}

	public int maxSize() {
		return maxSize;
	}

	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}
}
