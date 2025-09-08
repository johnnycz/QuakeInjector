package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.CommandHandler;

import java.io.File;

public class ConfigViewModel {

	private final CommandHandler commandHandler;
	private final Configuration config;

	public ConfigViewModel(CommandHandler commandHandler, Configuration config) {
		this.commandHandler = commandHandler;
		this.config = config;
	}

	public File enginePath;
	public File engineExecutable;
	public boolean workingDirAtExecutable;
	public File downloadPath;
	public String engineCommandLine;
	public boolean rogueInstalled;
	public boolean hipnoticInstalled;
	public String uiTheme;
	public boolean dialogShown;

	public void updateFromConfig() {
		this.enginePath = config.EnginePath.get();
		this.engineExecutable = config.EngineExecutable.get();
		this.workingDirAtExecutable = config.WorkingDirAtExecutable.get();
		this.downloadPath = config.DownloadPath.get();
		this.engineCommandLine = config.EngineCommandLine.get();
		this.rogueInstalled = config.RogueInstalled.get();
		this.hipnoticInstalled = config.HipnoticInstalled.get();
		this.uiTheme = config.uiTheme.get();
		this.dialogShown = true;
	}

	public void applyConfig() {
		config.EnginePath.set(this.enginePath);
		config.EngineExecutable.set(this.engineExecutable);
		config.WorkingDirAtExecutable.set(this.workingDirAtExecutable);
		config.DownloadPath.set(this.downloadPath);
		config.EngineCommandLine.set(this.engineCommandLine);
		config.RogueInstalled.set(this.rogueInstalled);
		config.HipnoticInstalled.set(this.hipnoticInstalled);
		config.uiTheme.set(this.uiTheme);

		commandHandler.saveConfig();
	}
}
