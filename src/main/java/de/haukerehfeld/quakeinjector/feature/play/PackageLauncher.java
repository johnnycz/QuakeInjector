package de.haukerehfeld.quakeinjector.feature.play;

import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.MissingPrerequisites;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.Requirement;

import java.util.List;

public class PackageLauncher {

	private final DialogProvider dialogProvider;

	private EngineStarter starter;

	public PackageLauncher(DialogProvider dialogProvider, EngineStarter starter) {
		this.dialogProvider = dialogProvider;
		this.starter = starter;
	}

	private boolean checkPlayRequirements(de.haukerehfeld.quakeinjector.model.Package selectedMap) {
		//in theory this should never happen ;)
		if (!selectedMap.isInstalled()) {
			String msg = selectedMap.getId()
					+ " doesn't seem to be installed.";
			dialogProvider.showError(msg, "Map not installed");
			return false;
		}

		List<Requirement> unmet = selectedMap.getUnmetRequirements();
		if (!unmet.isEmpty()) {
			String msg = "The following prerequisites to play "
					+ selectedMap.getId()
					+ " don't seem to be installed:\n"
					+ MissingPrerequisites.describe(unmet)
					+ "\nYou probably can't play this package.";
			String[] options = {"Start anyways",
					"Cancel Start"};
			int selectedOption = dialogProvider.showOptions(msg, "Prerequisites not installed", options, options[1]);
			return selectedOption == 0;
		}
		return true;
	}

	public void start(Package pkg, String startmap) {
		if (pkg == null) { return; }

		if (!starter.checkPaths()) {
			dialogProvider.showError("Quake engine paths aren't set correctly, can't start.","Quake engine paths not configured");
			return;
		}
		if (!checkPlayRequirements(pkg)) {
			return;
		}

		try {
			Process p = starter.start(pkg.getCommandline(), startmap);
			dialogProvider.showEngineOutout(p.getInputStream());
		}
		catch (java.io.IOException e) {
			/** @todo 2009-05-04 14:28 hrehfeld    pop up dialogue */
			System.out.println("Couldn't start quake engine: " + e.getMessage());
		}

	}

}
