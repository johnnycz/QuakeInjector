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
package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.UiProgress;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PackageInstallUninstallService {

	private final JobTrackerProvider jobTrackerProvider;
	private final DialogProvider dialogProvider;
	private final RecentPackages recentPackages;

	private RequirementList requirements;

	private Installer installer;

	private SaveInstalled installedMaps;

	/** Whether "check for installed maps" is running. Only touched on the UI thread. */
	private boolean checkingInstalled = false;

	/**
	 * @param recentPackages a package the user installed (not a prerequisite installed along with it)
	 *                       becomes the most recent one; an uninstalled package is taken off
	 */
	public PackageInstallUninstallService(JobTrackerProvider jobTrackerProvider, DialogProvider dialogProvider,
	                                      RecentPackages recentPackages) {
		this.jobTrackerProvider = jobTrackerProvider;
		this.dialogProvider = dialogProvider;
		this.recentPackages = recentPackages;
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

	public void install(Package pkg) {
		if (pkg == null)
			return;

		install(pkg, false);
	}

	private boolean checkInstallRequirements(de.haukerehfeld.quakeinjector.model.Package selectedMap) {
		List<Requirement> unmet = selectedMap.getUnavailableRequirements();
		if (!unmet.isEmpty()) {
			String msg = "The following prerequisites to play "
			    + selectedMap.getId()
			    + " can't be installed automatically:\n"
			    + MissingPrerequisites.describe(unmet)
			    + "\n";
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
		
		var job = jobTrackerProvider.addJob(description, () -> installer.cancel(selectedMap));

		// All listener methods run on the UI thread
		installer.install(selectedMap,
		                  selectedMap.getDownloadUrls().get(0), // TODO give user the option to choose the URL
		                  new InstallListener() {
							  @Override
							  public List<File> chooseOverwrites(Map<String,File> files) {
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

							  @Override
							  public void progress(int percent) {
								  job.setProgress(percent);
							  }

							  @Override
							  public void succeeded(PackageFileList installedFiles) {
								  Requirement r = requirements.get(installedFiles.getId());
								  // set the file list first: listeners of the installed flag may read it
								  if (!(r instanceof de.haukerehfeld.quakeinjector.model.Package)) {
									  System.err.println(r + " isn't a Package!");
								  }
								  else {
									  ((de.haukerehfeld.quakeinjector.model.Package) r).setFileList(installedFiles);
								  }
								  r.setInstalled(true);
								  if (!becauseRequired) {
									  recentPackages.add(installedFiles.getId());
								  }

								  saveInstalledMaps();
								  job.setProgress(100);
								  job.finish("Success");
							  }

							  @Override
							  public void failed(InstallFailure failure) {
								  // The pipeline has already removed the partially written files
								  String reason = failure.getCause().getMessage();
								  switch (failure.getKind()) {
									  case NOT_FOUND -> {
										  job.finish("File not found");
										  dialogProvider.showWarning("The file couldn't be found in the online"
										      + " repository", "File not found (404)");
									  }
									  case NOT_WRITABLE -> {
										  job.finish("Couldn't write");
										  dialogProvider.showError("Couldn't write to harddisk! " + reason,
										      "Couldn't write to harddisk");
									  }
									  case IO -> {
										  job.finish("File Error");
										  dialogProvider.showError("Couldn't open file! " + reason, "Couldn't open file!");
									  }
									  case NETWORK -> {
										  job.finish("Network Error");
										  dialogProvider.showError("Download failed! " + reason, "Download failed!");
									  }
									  case CANCELLED -> job.finish("Canceled");
									  case UNEXPECTED -> {
										  job.finish("Error");
										  dialogProvider.showError("Install failed! " + failure.getCause(), "Install failed");
									  }
								  }
							  }
						  });
	}

	public void uninstall(Package pkg) {
		if (!checkInstallDirectory()) {
			return;
		}
		if (pkg == null) {
			return;
		}

		uninstall(pkg, pkg.getFileList());
	}

	private void uninstall(final de.haukerehfeld.quakeinjector.model.Package map, PackageFileList files) {
		String description = "Uninstalling " + files.getId();

		var job = jobTrackerProvider.addJob(description, () -> {});

		// All handler methods run on the UI thread
		installer.uninstall(files,
		                    new Installer.UninstallErrorHandler() {
								@Override
								public void progress(int percent) {
									job.setProgress(percent);
								}

								@Override
								public void success() {
									job.finish("success");
									map.setInstalled(false);
									recentPackages.remove(files.getId());
									saveInstalledMaps();
								}

								@Override
								public void error(Exception e) {
									job.finish("fail");
									System.out.println(e.getMessage());
									e.printStackTrace();
								}
							});
	}

	/**
	 * See which packages are installed by looking for their files in the Quake directory, mark them as
	 * installed and save the installed list. Call on the UI thread; returns right away, the progress shows
	 * as a row in the job list. Ignored while a check is running.
	 */
	public void checkForInstalledMaps() {
		if (checkingInstalled) {
			System.out.println("Already checking for installed maps");
			return;
		}
		checkingInstalled = true;

		// copy on the UI thread: the check runs on the disk thread, while the UI thread may replace the list
		List<PackageFileList> fileLists = new ArrayList<>();
		for (Requirement r : requirements) {
			if (r instanceof Package pkg && pkg.getSupposedFileList() != null) {
				fileLists.add(pkg.getSupposedFileList());
			}
		}

		CancellationToken token = new CancellationToken();
		JobTracker job = jobTrackerProvider.addJob("Checking for installed maps", token::cancel);
		UiProgress progress = new UiProgress(installer.uiExecutor(), job::setProgress);
		CheckInstalled checker = new CheckInstalled(fileLists,
		                                            installer.installDirectory(),
		                                            token,
		                                            (current, total) -> progress.report((int) current));

		// on the disk thread, so it doesn't look at files an install is just writing
		CompletableFuture.supplyAsync(checker::execute, installer.diskExecutor())
			.whenCompleteAsync((installed, error) -> {
				checkingInstalled = false;
				if (error == null) {
					job.finish("Done: " + (installed.size() == 1 ? "1 map" : installed.size() + " maps") + " found");
					// by package id, so it also applies if the package database was reloaded meanwhile
					requirements.setInstalled(installed);
					saveInstalledMaps();
					return;
				}
				Throwable cause = Futures.unwrap(error);
				if (cause instanceof CancelledException) {
					job.finish("Cancelled");
					return;
				}
				job.finish("Failed");
				cause.printStackTrace();
				dialogProvider.showError("Checking for installed maps failed! " + cause.getMessage(),
					"Checking failed");
			}, installer.uiExecutor());
	}

	/**
	 * Save which packages are installed. The list is copied here, on the UI thread, and written on
	 * the disk thread. Writes happen in order, so the file always ends up with the latest state.
	 */
	private void saveInstalledMaps() {
		var snapshot = SaveInstalled.snapshot(requirements);
		installer.runOnDiskThread("Writing the installed maps file", () -> {
			installedMaps.write(snapshot);
			return null;
		});
	}

}
