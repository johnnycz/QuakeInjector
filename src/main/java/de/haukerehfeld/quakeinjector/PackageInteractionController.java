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

import de.haukerehfeld.quakeinjector.gui.InstallQueuePanel;
import de.haukerehfeld.quakeinjector.feature.install.Installer;
import de.haukerehfeld.quakeinjector.feature.install.SaveInstalled;
import de.haukerehfeld.quakeinjector.feature.play.EngineStarter;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.FileNotWritableException;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

	private final PackageInteractionViewModel vm;

	private EngineStarter starter;
	private RequirementList requirements;
	private final InstallQueuePanel installQueue;

	/**
	 * Currently selected map
	 */
	private de.haukerehfeld.quakeinjector.model.Package selectedMap = null;

	private Installer installer;

	private SaveInstalled installedMaps;
	private DialogProvider dialogProvider;

	public PackageInteractionController(InstallQueuePanel installQueue, PackageInteractionViewModel vm, DialogProvider dialogProvider) {
		this.installQueue = installQueue;
		this.vm = vm;
		this.dialogProvider = dialogProvider;
	}

	public void init(Installer installer,
	                 Configuration.RepositoryBasePath paths,
	                 RequirementList requirements,
	                 EngineStarter starter,
	                 SaveInstalled installedMaps) {
		this.requirements = requirements;
		this.starter = starter;
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

	private boolean checkPlayRequirements(de.haukerehfeld.quakeinjector.model.Package selectedMap) {
		//in theory this should never happen ;)
		if (!selectedMap.isInstalled()) {
			String msg = selectedMap.getId()
			    + " doesn't seem to be installed.";
			String[] options = {"Install",
			                    "Cancel Start"};
			int selectedOption = dialogProvider.showOptions(
					msg, "Map not installed", options, options[1]);
			if (selectedOption == 0) {
				install(selectedMap, false);
			}
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
		
		final InstallQueuePanel.Job progressListener
		    = installQueue.addJob(description,
		                          new ActionListener() {
									  public void actionPerformed(ActionEvent e) {
										  installer.cancel(selectedMap);
									  }
								  });

		installer.install(selectedMap,
		                  selectedMap.getDownloadUrls().get(0), // TODO give user the option to choose the URL
		                  new Installer.InstallErrorHandler() {
							  public void handle(OnlineFileNotFoundException error) {
								  installQueue.finished(progressListener,
								                        "File not found");
								  
								  vm.setInstalling(false);
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
								  progressListener.setProgress(100);
								  installQueue.finished(progressListener, "Success");
								  vm.setInstalling(false);
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
								  installQueue.finished(progressListener, message);
								  vm.setInstalling(false);
							  }
						  },
		                  progressListener);

		vm.setInstalling(true);
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
		
		final InstallQueuePanel.Job progressListener
		    = installQueue.addJob(description,
		                          new ActionListener() {
									  public void actionPerformed(ActionEvent e) {
//cancel button action
									  }
								  });

		installer.uninstall(files,
		                    new Installer.UninstallErrorHandler() {
								@Override
								public void success() {
									installQueue.finished(progressListener, "success");

									synchronized (map) {
										map.setInstalled(false);
									}
									
									SwingWorker<Void,Void> saveInstalled
									    = new SwingWorker<Void,Void>() {
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
									installQueue.finished(progressListener, "fail");
									System.out.println(e.getMessage());
									e.printStackTrace();
								}
							},
		                    progressListener
		    );
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

	@Override
	public void stateChanged(ChangeEvent e) {
		vm.stateChanged(e);
	}

	public void selectionChanged(Package s) {
		this.selectedMap = s;
		vm.setSelectedPackage(s);
	}

}
