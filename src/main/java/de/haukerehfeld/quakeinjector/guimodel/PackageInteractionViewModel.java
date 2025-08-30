package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;
import de.haukerehfeld.quakeinjector.model.Package;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Collections;
import java.util.List;

public class PackageInteractionViewModel implements ChangeListener {

	private Package selectedPackage = null;
	private boolean isInstalling;
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

	public Package getSelectedPackage() {
		return selectedPackage;
	}

	public void install() {
		commandHandler.install();
	}

	public void uninstall() {
		commandHandler.uninstall();
	}

	public void play(String startmap) {
		commandHandler.play(startmap);
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
		listeners.notifyChangeListeners(new ChangeEvent(this));
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

	public void setInstalling(boolean installing) {
		isInstalling = installing;
		notifyChangeListeners();
	}

	public boolean isInstalling() {
		return isInstalling;
	}
}
