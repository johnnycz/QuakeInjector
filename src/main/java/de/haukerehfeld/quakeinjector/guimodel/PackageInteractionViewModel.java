package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.CommandHandler;
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

	public PackageInteractionViewModel(CommandHandler commandHandler) {
		this.commandHandler = commandHandler;
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

	public void install() {
		commandHandler.install(selectedPackage);
	}

	public void uninstall() {
		commandHandler.uninstall(selectedPackage);
	}

	public void play(String startmap) {
		commandHandler.play(selectedPackage, startmap);
	}

	public void openPackageWebpage() {
		commandHandler.openPackageWebpage(selectedPackage);
	}

	public void openScreenshotInBrowser() {
		commandHandler.openScreenshotInBrowser(selectedPackage);
	}

	public URI getScreenshotUri() {
		return commandHandler.getScreenshotUri(selectedPackage);
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

	public boolean isInstalling() {
		return commandHandler.isInstallInProgress(selectedPackage);
	}

	public String getTitle() {
		return selectedPackage != null ? selectedPackage.getTitle() : "";
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

	public List<String> getRequirements() {
		return selectedPackage != null ? selectedPackage.getRequirements().stream().map(Requirement::getId).toList() : Collections.emptyList();
	}
}
