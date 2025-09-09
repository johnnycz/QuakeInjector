/*
  Copyright 2009 Hauke Rehfeld


  This file is part of QuakeInjector.

  QuakeInjector is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  QuakeInjector is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.FileNotWritableException;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * the panel that shows Info about the selected map
 */
public class PackageInteractionController implements ChangeListener {

	private final JobTrackerProvider jobTrackerProvider;
	private final DialogProvider dialogProvider;
	private final PackageInteractionViewModel vm;

	private RequirementList requirements;

	/**
	 * Currently selected map
	 *
	 * TODO where should be this tracked? Who's the owner? Is it defined in the model or in the viewmodel? Is it in multiple view models?
	 * Is it useful for the applogic to be aware which package is selected? Or should app logic always be given the package as a parameter to operate on?
	 *
	 * It seems like there's an issue when a package is being installed and a different package is selected in the UI.
	 */
	private de.haukerehfeld.quakeinjector.model.Package selectedMap = null;

	private Installer installer;

	private SaveInstalled installedMaps;

	public PackageInteractionController(JobTrackerProvider jobTrackerProvider, PackageInteractionViewModel vm, DialogProvider dialogProvider) {
		this.jobTrackerProvider = jobTrackerProvider;
		this.vm = vm;
		this.dialogProvider = dialogProvider;
	}

	public void init(Installer installer,
	                 RequirementList requirements,
	                 SaveInstalled installedMaps) {
		this.requirements = requirements;
		this.installedMaps = installedMaps;

		this.installer = installer;
	}

	public void installRequirements(de.haukerehfeld.quakeinjector.model.Package map) {
		for (de.haukerehfeld.quakeinjector.model.Package requirement: map.getAvailableRequirements()) {
			String id = requirement.getId();
			
			if (requirement.isInstalled()) {
				System.out.print("Required package " + id + " already installed.");
			}
			else {
				System.out.print("Required package " + id + " not installed. Installing...");
				install(requirement, true);
			}
		}
	}

	private boolean hasCurrentPackage() {
		return (selectedMap != null);
	}

	public void install() {
		if (!hasCurrentPackage()) { return; }
		
		install(selectedMap, false);
	}

	private boolean checkInstallRequirements(de.haukerehfeld.quakeinjector.model.Package selectedMap) {
		List<Requirement> unmet = selectedMap.getUnavailableRequirements();
		if (!unmet.isEmpty()) {
			String msg = "The following prerequisites to play "
			    + selectedMap.getId()
			    + " can't be installed automatically:\n"
			    + Utils.join(unmet, ",\n")
			    + ".\n";
			String[] options = {"Install anyways", "Cancel Install"};
			var selectedOption = dialogProvider.showOptions(
					msg, "Prerequisites not available for automatic install", options, options[1]);
			return selectedOption == 0;
		}
		return true;
	}


	private boolean checkInstallDirectory() {
		while (!installer.checkInstallDirectory()) {
			if (!dialogProvider.askAndshowEngineConfigWindow()) {
				return false;
			}
		}
		return true;
	}
	
	public void install(final de.haukerehfeld.quakeinjector.model.Package selectedMap, boolean becauseRequired) {
		if (!checkInstallDirectory()
		    || installer.alreadyQueued(selectedMap)
		    || !checkInstallRequirements(selectedMap)) {
			return;
		}
		installRequirements(selectedMap);


		String description = "Installing ";
		if (becauseRequired) {
			description += "prerequisite ";
		}
		description += selectedMap.getId();
		
		var job = jobTrackerProvider.addJob(description, e -> installer.cancel(selectedMap));

		installer.install(selectedMap,
		                  selectedMap.getDownloadUrls().get(0), // TODO give user the option to choose the URL
		                  new Installer.InstallErrorHandler() {
							  public void handle(OnlineFileNotFoundException error) {
								  job.finish("File not found");
								  
								  String msg = "The file couldn't be found in the online"
								      + " repository";
								  dialogProvider.showWarning(msg, "File not found (404)");
							  }

							  public List<File> overwrite(Map<String,File> files) {
								  List<String> overwriteList = new ArrayList<>();
								  List<String> alwaysWriteList = new ArrayList<>();
								  for (Map.Entry<String,File> e: files.entrySet()) {
									  String name = e.getKey();
									  File f = e.getValue();

									  if (f.exists()) {
										  overwriteList.add(name);
									  } else {
										  alwaysWriteList.add(name);
									  }
								  }

								  List<File> overwriteFiles = new ArrayList<>();
								  List<String> overwritten = dialogProvider.showOverwriteDialog(overwriteList, alwaysWriteList);

								  if (overwritten == null || overwritten.isEmpty()) {
									  return overwriteFiles;
								  }

								  for (String name: overwritten) {
									  overwriteFiles.add(files.get(name));
								  }
								  return overwriteFiles;
							  }
							  
							  public void success(PackageFileList installedFiles) {
								  Requirement r = requirements.get(installedFiles.getId());
								  r.setInstalled(true);
								  if (!(r instanceof de.haukerehfeld.quakeinjector.model.Package)) {
									  System.err.println(r + " isn't a Package!");
								  }
								  else {
									  ((de.haukerehfeld.quakeinjector.model.Package) r).setFileList(installedFiles);
								  }

								  try {
									  installedMaps.write(requirements);
								  }
								  catch (java.io.IOException e) {
									  System.out.println("Couldn't write installed Maps file!"
									                     + e.getMessage());
								  }
								  job.setProgress(100);
								  job.finish("Success");
							  }
							  public void handle(FileNotWritableException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles,
								          "Couldn't write");

								  String msg = "Couldn't write to harddisk! "
								      + error.getMessage();
								  dialogProvider.showError(msg, "Couldn't write to harddisk");
							  }
							  public void handle(java.io.IOException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles, "File Error");

								  String msg = "Couldn't open file! "
								      + error.getMessage();
								  dialogProvider.showError(msg,"Couldn't open file!");
							  }

							  public void handle(java.net.SocketException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles, "Network Error");

								  String msg = "Download failed! " + error.getMessage();
								  dialogProvider.showError(msg, "Download failed!");
							  }
							  
							  public void handle(Installer.CancelledException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles, "Canceled");
							  }

							  private void cleanup(PackageFileList alreadyInstalledFiles,
							                       String message) {
								  System.out.println("Cleaning up...");
								  uninstall(selectedMap, alreadyInstalledFiles);
								  job.finish(message);
							  }
						  },
		                  job);
	}

	public void uninstall() {
		if (!checkInstallDirectory()) {
			return;
		}
		if (!hasCurrentPackage()) { return; }

		uninstall(selectedMap, selectedMap.getFileList());
	}

	private void uninstall(final de.haukerehfeld.quakeinjector.model.Package map, PackageFileList files) {
		String description = "Uninstalling " + files.getId();
		
		var job = jobTrackerProvider.addJob(description, e -> {});

		installer.uninstall(files,
		                    new Installer.UninstallErrorHandler() {
								@Override
								public void success() {
									job.finish("success");

									synchronized (map) {
										map.setInstalled(false);
									}
									
									SwingWorker<Void,Void> saveInstalled
									    = new SwingWorker<>() {
										@Override
										public Void doInBackground() {
											
											try {
												installedMaps.write(requirements);
											}
											catch (java.io.IOException e) {
												System.out.println("Couldn't write installed Maps file!" + e.getMessage());
											}
											return null;
										}
									};
									saveInstalled.execute();
								}

								@Override
								public void error(Exception e) {
									job.finish("fail");
									System.out.println(e.getMessage());
									e.printStackTrace();
								}
							},
		                    job
		    );
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		vm.stateChanged(e);
	}

	public void selectionChanged(Package s) {
		this.selectedMap = s;
		vm.setSelectedPackage(s);
	}

}
