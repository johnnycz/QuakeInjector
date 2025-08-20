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

import de.haukerehfeld.quakeinjector.gui.PackageInteractionPanelView;
import de.haukerehfeld.quakeinjector.gui.QuakeInjectorView;
import de.haukerehfeld.quakeinjector.guimodel.PackageListSelectionHandler;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import de.haukerehfeld.quakeinjector.utils.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * the panel that shows Info about the selected map
 */
public class PackageInteractionPanel implements ChangeListener,
											 PackageListSelectionHandler.SelectionListener {

	private final PackageInteractionPanelView view;
	private QuakeInjectorView parentView;
	
	private EngineStarter starter;
	private Configuration.RepositoryBasePath paths;
	private RequirementList requirements;
	private final InstallQueuePanel installQueue;

	private boolean ready = false;

	/**
	 * Currently selected map
	 */
	private de.haukerehfeld.quakeinjector.model.Package selectedMap = null;

	private Installer installer;

	private SaveInstalled installedMaps;
	
	public PackageInteractionPanel(InstallQueuePanel installQueue, PackageInteractionPanelView view) {
		this.installQueue = installQueue;
		this.view = view;

		addListeners();

		disableUI();
		refreshUi();
	}

	private void addListeners() {
		view.getUninstallButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				uninstall();
			}
		});

		view.getInstallButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				install();
			}
		});

		view.getPlayButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				start();
			}
		});
	}

	private void disableUI() {
		view.getPlayButton().setEnabled(false);
		view.getInstallButton().setEnabled(false);
		view.getStartmaps().setEnabled(false);
	}

	private void refreshUi() {
		if (!ready || !hasCurrentPackage()) {
			view.getInstallButton().setText(PackageInteractionPanelView.getInstallText());
			disableUI();
			return;
		}

		view.getInstallButton().setText(PackageInteractionPanelView.getInstallText() + " " + selectedMap.getId());

		//we do this regardless of displaying the list, because we can
		//then simply get the selection from the list even if there's
		//only one option
		java.util.List<String> maps = selectedMap.getStartmaps();
		view.getStartmaps().removeAllItems();
		for (String startmap: maps) {
			view.getStartmaps().addItem(startmap);
		}

		if (selectedMap.isInstalled()) {
			view.getInstallButton().setEnabled(false);
			view.getUninstallButton().setEnabled(true);
			view.getPlayButton().setEnabled(true);

			boolean enableList = false;
			if (maps.size() > 1) {
				enableList = true;
			}
			view.getStartmaps().setEnabled(enableList);
		}
		else {
			if (installer.alreadyQueued(selectedMap)) {
				view.getInstallButton().setEnabled(false);
			}
			else {
				view.getInstallButton().setEnabled(true);
			}
			view.getPlayButton().setEnabled(false);
			view.getUninstallButton().setEnabled(false);
			view.getStartmaps().setEnabled(false);
		}

		view.revalidate();
		view.repaint();
	}

	public void init(Installer installer,
	                 Configuration.RepositoryBasePath paths,
	                 RequirementList requirements,
	                 EngineStarter starter,
	                 SaveInstalled installedMaps) {
		this.paths = paths;
		this.requirements = requirements;
		this.starter = starter;
		this.installedMaps = installedMaps;

		this.installer = installer;
		

		ready = true;
		refreshUi();
	}

	public void setParentView(QuakeInjectorView parentView) {
		this.parentView = parentView;
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
			Object[] options = {"Install anyways",
			                    "Cancel Install"};
			int install =
			    JOptionPane.showOptionDialog(view,
			                                 msg,
			                                 "Prerequisites not available for automatic install",
			                                 JOptionPane.YES_NO_OPTION,
			                                 JOptionPane.WARNING_MESSAGE,
			                                 null,
			                                 options,
			                                 options[1]);
			if (install != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean checkPlayRequirements(de.haukerehfeld.quakeinjector.model.Package selectedMap) {
		//in theory this should never happen ;)
		if (!selectedMap.isInstalled()) {
			String msg = selectedMap.getId()
			    + " doesn't seem to be installed.";
			Object[] options = {"Install",
			                    "Cancel Start"};
			int install =
			    JOptionPane.showOptionDialog(view,
			                                 msg,
			                                 "Map not installed",
			                                 JOptionPane.YES_NO_OPTION,
			                                 JOptionPane.WARNING_MESSAGE,
			                                 null,
			                                 options,
			                                 options[1]);
			if (install == 0) {
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
			Object[] options = {"Start anyways",
			                    "Cancel Start"};
			int install =
			    JOptionPane.showOptionDialog(view,
			                                 msg,
			                                 "Prerequisites not installed",
			                                 JOptionPane.YES_NO_OPTION,
			                                 JOptionPane.WARNING_MESSAGE,
			                                 null,
			                                 options,
			                                 options[1]);
			if (install != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean checkInstallDirectory() {
		while (!installer.checkInstallDirectory()) {
			if (!parentView.enginePathNotSetDialogue()) {
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
								  
								  refreshUi();
								  String msg = "The file couldn't be found in the online"
								      + " repository";
								  JOptionPane.showMessageDialog(view,
								                                msg,
								                                "File not found (404)",
								                                JOptionPane.WARNING_MESSAGE);
							  }

							  public List<File> overwrite(Map<String,File> files) {
								  PackageOverwriteDialog overwrite = new PackageOverwriteDialog(parentView);
								  for (Map.Entry<String,File> e: files.entrySet()) {
									  String name = e.getKey();
									  File f = e.getValue();
									  
									  overwrite.addFile(name, f.exists());
								  }

								  overwrite.packAndShow();

								  List<File> overwriteFiles = new ArrayList<File>();

								  if (overwrite.isCanceled()) {
									  return overwriteFiles;
								  }

								  for (String name: overwrite.getOverwritten()) {
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
								  refreshUi();
								  
							  }
							  public void handle(FileNotWritableException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles,
								          "Couldn't write");

								  String msg = "Couldn't write to harddisk! "
								      + error.getMessage();
								  JOptionPane.showMessageDialog(view,
								                                msg,
								                                "Couldn't write to harddisk",
								                                JOptionPane.ERROR_MESSAGE);
							  }
							  public void handle(java.io.IOException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles, "File Error");

								  String msg = "Couldn't open file! "
								      + error.getMessage();
								  JOptionPane.showMessageDialog(view,
								                                msg,
								                                "Couldn't open file!",
								                                JOptionPane.ERROR_MESSAGE);
							  }

							  public void handle(java.net.SocketException error,
							                     PackageFileList alreadyInstalledFiles) {
								  cleanup(alreadyInstalledFiles, "Network Error");

								  String msg = "Download failed! " + error.getMessage();
								  JOptionPane.showMessageDialog(view,
								                                msg,
								                                "Download failed!",
								                                JOptionPane.ERROR_MESSAGE);
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
								  refreshUi();
							  }
						  },
		                  progressListener);

		view.getInstallButton().setEnabled(false);
	}

	public void uninstall() {
		if (!checkInstallDirectory()) {
			return;
		}
		if (!hasCurrentPackage()) { return; }

		uninstall(selectedMap, selectedMap.getFileList());
		view.getUninstallButton().setEnabled(false);
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

									refreshUi();
								}

								@Override
								public void error(Exception e) {
									refreshUi();
									installQueue.finished(progressListener, "fail");
									System.out.println(e.getMessage());
									e.printStackTrace();
								}
							},
		                    progressListener
		    );
	}

	public void start() {
		if (!hasCurrentPackage()) { return; }

		if (!starter.checkPaths()) {
			JOptionPane.showMessageDialog(parentView,
			                              "Quake engine paths aren't set correctly, can't start.",
			                              "Quake engine paths not configured",
			                              JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (!checkPlayRequirements(selectedMap)) {
			return;
		}
		String startmap = (String) view.getStartmaps().getSelectedItem();
		//System.out.println("startmap: " + startmap);

		try {
			Process p = starter.start(selectedMap.getCommandline(), startmap);
			EngineOutputDialog eod = new EngineOutputDialog(parentView, p.getInputStream());
			eod.pack();
			eod.setLocationRelativeTo(parentView);
			eod.setVisible(true);

		}
		catch (java.io.IOException e) {
			/** @todo 2009-05-04 14:28 hrehfeld    pop up dialogue */
			System.out.println("Couldn't start quake engine: " + e.getMessage());
		}

	}

	public void setSelection(de.haukerehfeld.quakeinjector.model.Package map) {
		this.selectedMap = map;

		refreshUi();

	}




	@Override
	public void stateChanged(ChangeEvent e) {
		refreshUi();
	}

	@Override
	public void selectionChanged(Package s) {
		setSelection(s);
	}

	public InstallQueuePanel getInstallQueue() {
		return installQueue;
	}
}