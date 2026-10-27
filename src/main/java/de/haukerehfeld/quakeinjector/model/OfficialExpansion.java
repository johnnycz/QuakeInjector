package de.haukerehfeld.quakeinjector.model;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * An official Quake expansion, like the mission packs. They are sold, so they aren't in the package
 * database and can't be installed automatically. Whether one is installed is found out by looking in the
 * Quake directory, not from the installed packages list.
 * <p>
 * Installed means {@code <Quake directory>/<id>/pak0.pak} exists; the names are matched ignoring case,
 * because many Quake installs use upper case names and some file systems are case sensitive.
 * Immutable.
 */
public record OfficialExpansion(String id, String name) {

	/** Every official expansion the app knows; ids as used by the package database. */
	public static final List<OfficialExpansion> ALL = List.of(
		new OfficialExpansion("hipnotic", "Scourge of Armagon (Mission Pack 1)"),
		new OfficialExpansion("rogue", "Dissolution of Eternity (Mission Pack 2)"));

	private static final String PAK_FILE = "pak0.pak";

	/** The official expansion with this id, if it is one; ids are matched ignoring case. */
	public static Optional<OfficialExpansion> forId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return ALL.stream().filter(p -> p.id.equalsIgnoreCase(id)).findFirst();
	}

	/** Looks in the Quake directory; an unset or missing directory means not installed. */
	public boolean isInstalledIn(File quakeDirectory) {
		File dir = childIgnoringCase(quakeDirectory, id);
		if (dir == null || !dir.isDirectory()) {
			return false;
		}
		File pak = childIgnoringCase(dir, PAK_FILE);
		return pak != null && pak.isFile();
	}

	/** The entry of {@code dir} called {@code name} in any case, or null if there is none. */
	private static File childIgnoringCase(File dir, String name) {
		if (dir == null) {
			return null;
		}
		File exact = new File(dir, name);
		if (exact.exists()) {
			return exact;
		}
		File[] children = dir.listFiles();
		if (children == null) {
			return null;
		}
		for (File child : children) {
			if (child.getName().equalsIgnoreCase(name)) {
				return child;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return name;
	}
}
