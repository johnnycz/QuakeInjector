package de.haukerehfeld.quakeinjector.model;

public interface CommandHandler {
	void install();
	void uninstall();
	void play(String startmap);
	void saveConfig();
	void selectPackage(Package pkg);
}
