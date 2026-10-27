package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.model.ReadmeSource;
import de.haukerehfeld.quakeinjector.model.OfficialExpansionRequirement;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;
import de.haukerehfeld.quakeinjector.model.Package;

import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PackageInteractionViewModel implements ChangeListener {

	private Package selectedPackage = null;
	private final ChangeListenerList listeners = new ChangeListenerList();
	private final CommandHandler commandHandler;
	private final ReadmeSource readmeSource;

	/** @param readmeSource null: no readmes */
	public PackageInteractionViewModel(CommandHandler commandHandler, ReadmeSource readmeSource) {
		this.commandHandler = commandHandler;
		this.readmeSource = readmeSource;
	}

	public void setSelectedPackage(Package  p) {
		if (this.selectedPackage == p) {
			return;
		}
		if (this.selectedPackage != null) {
			this.selectedPackage.removeChangeListener(this);
		}
		this.selectedPackage = p;
		if (this.selectedPackage != null) {
			this.selectedPackage.addChangeListener(this);
		}
		notifyChangeListeners();
	}

	/** @return null if nothing is selected */
	public Package getSelectedPackage() {
		return selectedPackage;
	}

	public void install() {
		commandHandler.install(selectedPackage);
	}

	public void uninstall() {
		commandHandler.uninstall(selectedPackage);
	}

	public void play(String startmap) {
		commandHandler.play(selectedPackage, startmap);
	}

	/** Does nothing when no package is selected. */
	public void openPackageWebpage() {
		if (selectedPackage != null) {
			commandHandler.openPackageWebpage(selectedPackage);
		}
	}

	/** Null when no package is selected. */
	public URI getScreenshotUri() {
		return selectedPackage != null ? commandHandler.getScreenshotUri(selectedPackage) : null;
	}
	/**
	 * When the underlying model has changed
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		notifyChangeListeners();
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

	public boolean isInstalled() {
		return selectedPackage != null && selectedPackage.isInstalled();
	}

	public String getPackageId() {
		return selectedPackage != null ? selectedPackage.getId() : "";
	}

	public List<String> getStartmaps() {
		return selectedPackage != null ? selectedPackage.getStartmaps() : Collections.emptyList();
	}

	/** Whether the selected package is installed and has text files to show. */
	public boolean hasTextFiles() {
		return isInstalled() && readmeSource != null && !readmeSource.textFiles(selectedPackage).isEmpty();
	}

	/** A view model for a readme window of the selected package, which must be installed. */
	public ReadmeViewModel openReadme() {
		return new ReadmeViewModel(readmeSource, getPackageId(), getTitle(), readmeSource.textFiles(selectedPackage));
	}

	public boolean isInstalling() {
		return selectedPackage != null && commandHandler.isInstallInProgress(selectedPackage);
	}

	public String getTitle() {
		return selectedPackage != null ? selectedPackage.getTitle() : "";
	}

	public String getAuthor() {
		String author = selectedPackage != null ? selectedPackage.getAuthor() : null;
		return author != null ? author : "";
	}

	public Date getDate() {
		return selectedPackage != null ? selectedPackage.getDate() : null;
	}

	public int getSize() {
		return selectedPackage != null ? selectedPackage.getSize() : 0;
	}

	public String getDescription() {
		return selectedPackage != null ? selectedPackage.getDescription() : "";
	}

	/**
	 * A prerequisite as the package description shows it.
	 * @param hasWebpage whether it has a Quaddicted page to link to; official expansions don't
	 */
	public record Prerequisite(String id, String name, boolean hasWebpage) {}

	public List<Prerequisite> getRequirements() {
		if (selectedPackage == null) {
			return Collections.emptyList();
		}
		return selectedPackage.getRequirements().stream().map(PackageInteractionViewModel::toPrerequisite).toList();
	}

	private static Prerequisite toPrerequisite(Requirement r) {
		if (r instanceof OfficialExpansionRequirement expansion) {
			return new Prerequisite(r.getId(), expansion.getName(), false);
		}
		return new Prerequisite(r.getId(), r.getId(), true);
	}
}
