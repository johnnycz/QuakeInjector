package de.haukerehfeld.quakeinjector.feature.play;

import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.JobTrackerProvider;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.util.List;

public class PackageLauncher {

	private final JobTrackerProvider jobTrackerProvider;
	private final DialogProvider dialogProvider;

	private de.haukerehfeld.quakeinjector.model.Package selectedMap = null;
	private EngineStarter starter;

	private boolean hasCurrentPackage() {
		return (selectedMap != null);
	}
	public PackageLauncher(JobTrackerProvider jobTrackerProvider, DialogProvider dialogProvider) {
		this.jobTrackerProvider = jobTrackerProvider;
		this.dialogProvider = dialogProvider;
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
					+ " don't seem to be installed: \n"
					+ Utils.join(unmet, ",\n ")
					+ ".\nYou probably can't play this package.";
			String[] options = {"Start anyways",
					"Cancel Start"};
			int selectedOption = dialogProvider.showOptions(msg, "Prerequisites not installed", options, options[1]);
			return selectedOption == 0;
		}
		return true;
	}

	public void start(String startmap) {
		if (!hasCurrentPackage()) { return; }

		if (!starter.checkPaths()) {
			dialogProvider.showError("Quake engine paths aren't set correctly, can't start.","Quake engine paths not configured");
			return;
		}
		if (!checkPlayRequirements(selectedMap)) {
			return;
		}

		try {
			Process p = starter.start(selectedMap.getCommandline(), startmap);
			dialogProvider.showEngineOutout(p.getInputStream());
		}
		catch (java.io.IOException e) {
			/** @todo 2009-05-04 14:28 hrehfeld    pop up dialogue */
			System.out.println("Couldn't start quake engine: " + e.getMessage());
		}

	}

}
