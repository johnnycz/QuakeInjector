package de.haukerehfeld.quakeinjector.model;

import java.net.URI;

public interface CommandHandler {
	void install(Package selectedPackage);
	void uninstall(Package selectedPackage);
	void play(Package selectedPackage, String startmap);
	void saveConfig();
	boolean isInstallInProgress(Package aPackage);
	void openPackageWebpage(Package pkg);
	URI getScreenshotUri(Package pkg);
}
