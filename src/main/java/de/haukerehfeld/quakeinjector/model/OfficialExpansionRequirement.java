package de.haukerehfeld.quakeinjector.model;

import java.io.File;
import java.util.function.Supplier;

import de.haukerehfeld.quakeinjector.utils.ChangeListener;

/**
 * A prerequisite that is an {@link OfficialExpansion}. It is installed when its files are in the Quake
 * directory, looked up on every {@link #isInstalled()}, so an expansion installed while the app runs
 * counts at once. The installed packages list has no say: {@link #setInstalled} does nothing.
 */
public class OfficialExpansionRequirement extends SortableRequirement implements Requirement {
	private final OfficialExpansion expansion;
	private final Supplier<File> quakeDirectory;

	/**
	 * @param quakeDirectory the Quake directory as currently set; may return null when unset
	 */
	public OfficialExpansionRequirement(OfficialExpansion expansion, Supplier<File> quakeDirectory) {
		super(expansion.id());
		this.expansion = expansion;
		this.quakeDirectory = quakeDirectory;
	}

	/**
	 * A prerequisite that isn't in the package database: an official expansion if {@code id} is one,
	 * otherwise an {@link UnavailableRequirement}.
	 */
	public static Requirement forMissingPackage(String id, Supplier<File> quakeDirectory) {
		return OfficialExpansion.forId(id)
			.<Requirement>map(e -> new OfficialExpansionRequirement(e, quakeDirectory))
			.orElseGet(() -> new UnavailableRequirement(id));
	}

	public OfficialExpansion getExpansion() {
		return expansion;
	}

	/** The full name, e.g. "Scourge of Armagon (Mission Pack 1)". */
	public String getName() {
		return expansion.name();
	}

	@Override
	public boolean isInstalled() {
		return expansion.isInstalledIn(quakeDirectory.get());
	}

	/** Ignored: only the Quake directory tells whether an official expansion is installed. */
	@Override
	public void setInstalled(boolean installed) {}

	@Override
	public PackageFileList getFileList() {
		return new PackageFileList(getId());
	}

	@Override
	public void addChangeListener(ChangeListener l) {}

	@Override
	public void removeChangeListener(ChangeListener l) {}

	@Override
	protected void notifyChangeListeners() {}

	/** The full name, so lists of prerequisites in messages read well. */
	@Override
	public String toString() {
		return getName();
	}
}
