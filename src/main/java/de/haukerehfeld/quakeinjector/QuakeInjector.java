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

import de.haukerehfeld.quakeinjector.gui.*;
import de.haukerehfeld.quakeinjector.gui.Menu;
import de.haukerehfeld.quakeinjector.guimodel.PackageListModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListSelectionHandler;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.RelativePath;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class QuakeInjector {

	private final static String installedMapsFileName = "installedMaps.xml";
	private final static File installedMapsFile = new File(installedMapsFileName);
	private final SaveInstalled saveInstalled = new SaveInstalled(installedMapsFile);
	
	private final static String zipFilesXml = "zipFiles.xml";

	final static File configFile = new File("config.properties");
	private final PackageInteractionPanel interactionPanel;
	private EngineStarter starter;
	private RequirementList maps;
	private PackageList packages;
	private final PackageListModel maplist;
	private Installer installer;
	private final InstalledPackages installedMaps = new InstalledPackages();
	private Configuration.OfflineMode offline;
	private final Configuration config;

	private final QuakeInjectorView view;

	public QuakeInjector() {

		//load config
		final Future<Configuration> config = new SwingWorker<Configuration,Void>() {
			@Override public Configuration doInBackground() { return new Configuration(configFile); }
		};
		((SwingWorker<?,?>) config).execute();
		Configuration cfg = null;
		try {
			cfg = config.get();
		}
		catch (ExecutionException e) {
			System.err.println("Couldn't load config: " + e.getCause());
			e.getCause().printStackTrace();
		}
		catch (InterruptedException e) {
			System.err.println("Interrupted: " + e);
		}
		this.config = cfg;


		maps = new RequirementList();
		packages = new PackageList(maps);
		maplist = new PackageListModel(packages);
		this.offline = cfg.OfflineMode;

		loadTheme();

		PackageInteractionPanelView packageInteractionPanelView = new PackageInteractionPanelView();
		InstallQueuePanel installQueuePanel = new InstallQueuePanel();
		this.interactionPanel = new PackageInteractionPanel(installQueuePanel, packageInteractionPanelView);

		view = new QuakeInjectorView(maplist, packageInteractionPanelView, installQueuePanel);
		setWindowSize();

		registerViewListeners();
	}

	private void loadTheme() {
		UIThemeOption option = getConfig().uiTheme.get();
		if (option != null) {
			option.init();
		} else {
			UIThemeOption.SYSTEM.init();
		}
	}

	/**
	 * Try setting the saved window size and position
	 */
	private void setWindowSize() {
		Configuration c = getConfig();

		if (c.MainWindowWidth.exists() && c.MainWindowHeight.exists()) {
			int width = c.MainWindowWidth.get();
			int height = c.MainWindowHeight.get();
			if (c.MainWindowPositionX.exists() && c.MainWindowPositionY.exists()) {
				int posX = c.MainWindowPositionX.get();
				int posY = c.MainWindowPositionY.get();
				// System.out.println("Setting window bounds: "
				//                    + posX + ", "
				//                    + posY + ", "
				//                    + width + ", "
				//                    + height);

				view.setBounds(posX, posY, width, height);
			}
			else {
				// System.out.println("Setting window size: " + width + ", " + height);
				view.setSize(width, height);
			}
		}
		else {
			view.pack();
		}
	}

	private void registerViewListeners() {

		var packageDetailPanel = new PackageDetailPanel(view.getPackageDetailPanelView(), getConfig().ScreenshotRepositoryPath.get(), getConfig().mapWebpageBaseUrl.get());
		view.addWindowListener(new QuakeInjectorWindowListener());

		addMenuActionListeners();

		view.addRandomMapButtonActionListener((e) -> {
			// Package list index != current table index, which can change based on the column used to sort the
			// table. Therefore, it has to be converted.
			int mapTableRowIdx = new Random().nextInt(maplist.getRowCount());
			int mapListIdx = view.getPackageTable().getRowSorter().convertRowIndexToModel(mapTableRowIdx);
			Package map = maplist.getPackage(mapListIdx);
			if(!maplist.isPackageInstalled(map)) {
				interactionPanel.install(map, false);
			}
			view.selectRowInMainTable(mapTableRowIdx);
		});

		view.addShowEngineConfigListener((e) -> showEngineConfig());


		PackageListSelectionHandler selectionHandler =
				new PackageListSelectionHandler(maplist, view.getPackageTable());
		view.getPackageTable().getSelectionModel().addListSelectionListener(selectionHandler);
		selectionHandler.addSelectionListener(interactionPanel);
		selectionHandler.addSelectionListener(packageDetailPanel);
	}

	private void addMenuActionListeners() {
		Menu menu = view.getMenu();
		menu.addReparseDatabaseActionListener((e) -> {
			doParseInstalled();
			parseDatabaseAndSetList();
		});

		menu.addCheckInstalledActionListener((e) -> checkForInstalledMaps());

		menu.addQuitActionListener((e) -> {
			view.setVisible(false);
			view.dispose();
		});

		menu.addEngineActionListener((e) -> showEngineConfig());

		menu.addEnableOfflineModeActionListener((e) -> offline.set(!offline.get()));
	}

	/**
	 * Everything that may be run AFTER the initial window is shown should be run here
	 */
	private void init() {
		doParseInstalled();

		final Future<Void> requirementsListUpdater = parseDatabaseAndSetList();

		Configuration.EnginePath enginePath = getConfig().EnginePath;
		boolean workingDirAtExecutable = false;
		File engineExe = new File("");
		if (getConfig().EngineExecutable.existsOrDefault()) {
			engineExe = new File(enginePath.get()
			                          + File.separator
			                          + getConfig().EngineExecutable);
			workingDirAtExecutable = getConfig().WorkingDirAtExecutable.get();
		}
		File workingDir;
		if (workingDirAtExecutable) {
			workingDir = engineExe.getParentFile();
		}
		else {
			workingDir = enginePath.get();
		}

		starter = new EngineStarter(workingDir,
		                            engineExe,
		                            getConfig().EngineCommandLine);
		installer = new Installer(enginePath,
		                          getConfig().DownloadPath);

		interactionPanel.init(installer,
		                      getConfig().RepositoryBasePath,
		                      maps,
		                      starter,
		                      new SaveInstalled(installedMapsFile)
	    );

		if (!installer.checkInstallDirectory()) {
			//wait until database was loaded, then pop up config
			new SwingWorker<Void,Void>() {
				@Override
			    public Void doInBackground() {
					try {
						requirementsListUpdater.get();
					}
					catch (java.lang.InterruptedException e) {}
					catch (java.util.concurrent.ExecutionException e) {}
					return null;
				}
				@Override
			    public void done() {
					view.enginePathNotSetDialogue();
				}
			}.execute();
		}
	}


	private void doParseInstalled() {
		installedMaps.parse(installedMapsFile);
	}

	private InputStream downloadDatabase(String databaseUrl) throws IOException {
		//get download stream
		Download d = Download.create(databaseUrl);
		d.connect();
		InputStream dl;
		//int size = d.getSize();
		// if (size > 0) {
		// 	ProgressListener progress =
		// 	    new SumProgressListener(new PercentageProgressListener(size, this));
		// 	dl = d.getStream(progress);
		// }
		// else {
		dl = d.getStream(null);
		//}
		
		return dl;
	}


	private List<Requirement> parseDatabase(InputStream database)
		throws IOException, org.xml.sax.SAXException {
		final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(config);
		
		List<Requirement> all = parser.parse(database);

		return all;
	}

	/**
	 * Parse the online database
	 */
	private Future<List<Requirement>> doParseDatabase() {
		
		final String databaseUrl = getConfig().RepositoryDatabasePath.get();
		
		final SwingWorker<List<Requirement>, Void> dbParse
		    = new SwingWorker<List<Requirement>,Void>() {
			/** the stream for the database download **/
			private BufferedInputStream downloadStream;
			/** we need to try to download the db to a tmp file first so the old one doesn't get overwritten */
			private File tmpFile;
			/** the stream to the temporary file */
			private BufferedOutputStream tmpWriteStream;
			/** the cached database file **/
			private File cache;
			/** stream from the cached database file, if needed **/
			private BufferedInputStream cacheReadStream;
			/** whether the temporary file was populated with a good DB **/
			private boolean updateCache = false;
			
			private BufferedInputStream cachedDatabaseStream() throws IOException {
				if (cache != null && cache.exists() && cache.canRead()) {
					try {
						return new BufferedInputStream(new FileInputStream(cache));
					}
					catch (IOException e) {}
				}
				throw new IOException("cannot download package database or read local cache");
			}

			@Override
			public List<Requirement> doInBackground() throws IOException, org.xml.sax.SAXException {
				cache = getConfig().LocalDatabaseFile.get();
				cache = cache.getAbsoluteFile();
				InputStream db;
				try {
					//download database and dump to file
					downloadStream = new BufferedInputStream(downloadDatabase(databaseUrl));
					tmpFile = File.createTempFile(cache.getName(), null, cache.getParentFile());
					tmpWriteStream = new BufferedOutputStream(new FileOutputStream(tmpFile));
					db = new DumpInputStream(downloadStream, tmpWriteStream);
					List<Requirement> parseResult = parseDatabase(db);
					updateCache = true;
					return parseResult;
				}
				catch (Exception e) {
					cacheReadStream = cachedDatabaseStream();
					return parseDatabase(cacheReadStream);
				}
			}

			@Override
			public void done() {
				try {
					if (cacheReadStream != null) {
						cacheReadStream.close();
					}
					if (tmpWriteStream != null) {
						tmpWriteStream.close();
					}
					if (downloadStream != null) {
						downloadStream.close();
					}
				}
				catch (IOException e) {}
				if (updateCache == true) {
					if (cache.exists()) {
						if (cache.delete() == false) {
							System.err.println("Couldn't delete the real cache file!");
						}
					}
					if (tmpFile.renameTo(cache) == false) {
						System.err.println("Couldn't move the temporary cache file to the real cache file!");
					}
				}
				else {
					if (tmpFile != null && tmpFile.exists()) {
						tmpFile.delete();
					}
					String msg = "Failed to fetch current database; using previously downloaded info.";
					JOptionPane.showMessageDialog(view,
					                              msg,
					                              "Downloading failed!",
					                              JOptionPane.WARNING_MESSAGE);
				}
			}
		};

		final ProgressPopup dbpopup = new ProgressPopup("Downloading package database",
		                      new ActionListener() {
								  public void actionPerformed(ActionEvent e) {
									  dbParse.cancel(true);
								  }
							  },
		                      view);

		dbParse.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (evt.getPropertyName() == "progress") {
						int p = (Integer) evt.getNewValue();
						dbpopup.setProgress(p);
					}
					else if (evt.getPropertyName() == "state"
					    && evt.getNewValue().equals(SwingWorker.StateValue.DONE)) {
						dbpopup.close();
					}
				}
			});
		dbParse.execute();
		dbpopup.pack();
		dbpopup.setVisible(true);

		return dbParse;
	}



	/**
	 * See what maps are installed
	 */
	private Future<List<PackageFileList>> checkForInstalledMaps() {
		final File enginePath = getConfig().EnginePath.get();

		final File file = new File(zipFilesXml);

		final CheckInstalled checker
		    = new CheckInstalled(view,
		                         getConfig().ZipContentsDatabaseUrl.get(),
		                         getConfig().EnginePath.get().toString(),
		                         maps,
				(list) -> {
						try {
							setInstalledStatus(list);

							synchronized (maps) {
								saveInstalled.write(maps);
							}
						}
						catch (java.util.concurrent.CancellationException e) {
						}
						catch (java.io.IOException e) {
							System.err.println("Couldn't write installedMapsFile: " + e);
							e.printStackTrace();
						}
				});

		final ProgressPopup dbpopup =
		    new ProgressPopup("Checking for installed maps",
		                      new ActionListener() {

								  public void actionPerformed(ActionEvent e) {
									  checker.cancel(true);
								  }
							  },
		                      view);

		checker.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (evt.getPropertyName() == "progress") {
						int p = (Integer) evt.getNewValue();
						dbpopup.setProgress(p);
					}
					else if (evt.getPropertyName() == "state"
					    && evt.getNewValue().equals(SwingWorker.StateValue.DONE)) {
						dbpopup.close();
					}
				}
			});
		checker.execute();
		dbpopup.pack();
		dbpopup.setVisible(true);

		return checker;
	}
	

	/**
	 * Tell maps what maps are already installed
	 */
	void setInstalledStatus(final List<PackageFileList> packages) {
		for (PackageFileList l: packages) {
			maps.setInstalled(l);
		}
		//for (Requirement r: maps) {
			//System.out.println(r);
		//}
		
		maps.notifyChangeListeners();

		
	}

	private Future<Void> parseDatabaseAndSetList() {
		final Future<List<Requirement>> dbParse = doParseDatabase();

		SwingWorker<Void,Void> waitForInstalledMapsAndDb = new SwingWorker<Void,Void>() {
			@Override public Void doInBackground() throws Exception {
				//just wait
				installedMaps.get();
				dbParse.get();

				return null;
			}

			public void done() {
				List<Requirement> packages = null;
				try {
					packages = dbParse.get();
				}
				catch (InterruptedException e) {
					throw new RuntimeExecutionException("parsing database", e);
				}
				catch (ExecutionException e) {
					String ERROR_MESSAGE = "Database parsing failed!";
					Throwable err = e.getCause();
					String msg = err.getMessage();
					try {
						throw err;
					}
					catch (java.net.UnknownHostException exc) {
						msg = "Couldn't establish connection to the server (" + err.getMessage() + ").";
						offline.set(true);
					}
					catch (Throwable any) { /*do nothing*/; }

					JOptionPane.showMessageDialog(view,
					                              ERROR_MESSAGE + " " + msg,
					                              ERROR_MESSAGE,
					                              JOptionPane.ERROR_MESSAGE);
					return;
				}

				maps.setRequirements(packages);
				System.out.println("Setting Requirements");

				try {
					setInstalledStatus(installedMaps.get());
				}
				catch (InterruptedException e) {
					System.err.println("Interrupted while getting installed maps" + e);
					e.printStackTrace();
				}
				catch (ExecutionException err) {
					maps.notifyChangeListeners();
					
					try {
						throw err.getCause();
					}
					catch (InstalledPackages.NoInstalledPackagesFileException e) {
						System.err.println(e.getMessage());
					}
					catch (Throwable e) {
						String ERROR_MESSAGE = "Reading installed maps failed!";
						JOptionPane.showMessageDialog(view,
						                              ERROR_MESSAGE + " " + e.getMessage(),
						                              ERROR_MESSAGE,
						                              JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		};
		waitForInstalledMapsAndDb.execute();
		return waitForInstalledMapsAndDb;
	}

	private void showEngineConfig() {
		showEngineConfig(maps.get("rogue").isInstalled(), maps.get("hipnotic").isInstalled());
	}

	private void showEngineConfig(boolean rogueInstalled, boolean hipnoticInstalled) {
		final EngineConfigDialog d
		    = new EngineConfigDialog(view,
		                             getConfig().EnginePath,
		                             getConfig().EngineExecutable,
		                             getConfig().WorkingDirAtExecutable,
		                             getConfig().DownloadPath,
		                             getConfig().EngineCommandLine,
		                             getConfig().RogueInstalled,
		                             getConfig().HipnoticInstalled,
				                     getConfig().uiTheme
		        );
		d.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					try {
						saveEngineConfig(d.getEnginePath(),
						                 d.getEngineExecutable(),
						                 d.getWorkingDirAtExecutable(),
						                 d.getDownloadPath(),
						                 d.getCommandline(),
						                 d.getRogueInstalled(),
						                 d.getHipnoticInstalled(),
								         d.getUiTheme());
					}
					catch (IOException err) {
						savingFailedDialogue(err);
					}
				}
			});

		d.pack();
		d.setLocationRelativeTo(view);
		d.setVisible(true);
		
	}


	private void savingFailedDialogue(IOException e) {
		String msg = "Saving the configuration file failed: " + e.getMessage() + "\n"
		    + "The directory is probably read-only and cannot be set writable automatically (Vista/Win7 bug), try to set write permissions manually." ;
		JOptionPane.showMessageDialog(view,
		                              msg,
		                              "Saving configuration failed!",
		                              JOptionPane.ERROR_MESSAGE);
	}

	private void saveEngineConfig(File enginePath,
								  File engineExecutable,
								  boolean workingDirAtExecutable,
	                              File downloadPath,
	                              String commandline,
	                              boolean rogueInstalled,
	                              boolean hipnoticInstalled,
	                              UIThemeOption uiThemeOption
	) throws IOException {
		

		Configuration c = getConfig();
		c.EnginePath.set(enginePath);
		c.EngineExecutable.set(RelativePath.getRelativePath(enginePath, engineExecutable));
		c.WorkingDirAtExecutable.set(workingDirAtExecutable);
		c.EngineCommandLine.set(commandline);
		c.RogueInstalled.set(rogueInstalled);
		c.HipnoticInstalled.set(hipnoticInstalled);

		c.DownloadPath.set(downloadPath);
		c.uiTheme.set(uiThemeOption);

		File workingDir;
		if (workingDirAtExecutable) {
			workingDir = engineExecutable.getParentFile();
		}
		else {
			workingDir = enginePath;
		}

		setEngineConfig(workingDir, engineExecutable, getConfig().EngineCommandLine, rogueInstalled, hipnoticInstalled);


		try {
			c.write();
		}
		catch (IOException e) {
			File dir = configFile.getAbsoluteFile().getParentFile();
			System.out.println("Trying to set directory (" + dir + ") writable..");
			try {
				dir.setWritable(true);
			}
			catch (SecurityException securityError) {
				System.out.println("Couldn't set writable: " + securityError);
			}

			c.write();
		}
	}

	/**
	 * @todo 2010-02-09 12:19 hrehfeld    Let this use configuration values to their full extent
	 */
	private void setEngineConfig(File workingDir,
								 File engineExecutable,
	                             Configuration.EngineCommandLine commandline,
	                             boolean rogueInstalled,
	                             boolean hipnoticInstalled) {
		starter.setWorkingDirectory(workingDir);
		starter.setQuakeApplication(engineExecutable);
		starter.setQuakeCommandline(commandline);

		maps.get("rogue").setInstalled(rogueInstalled);
		maps.get("hipnotic").setInstalled(hipnoticInstalled);
		try {
			synchronized (maps) {
				saveInstalled.write(maps);
			}
		}
		catch (java.io.IOException e) {}
	}


	private Configuration getConfig() {
		if (config == null) {
			throw new RuntimeException("Config not initialised!");
		}
		return config;
	}



	public static void main(String[] args) {
		// borrowed from jmtd's wadc:
		// The default setting for useSystemAAFontSettings is off; and the result
		// looks awful on (at least my) Linux systems. We want to switch the default
		// to on, but leave it possible for the user to override our choice.
		if(null == System.getenv("_JAVA_OPTIONS") ||
				!System.getenv("_JAVA_OPTIONS").contains("useSystemAAFontSettings"))
		{
				System.setProperty("awt.useSystemAAFontSettings", "on");
		}

		// override the HTTP user-agent for any connections this program does
		// re https://stackoverflow.com/questions/2529682/setting-user-agent-of-a-java-urlconnection
		System.setProperty("http.agent", "Quakeinjector-" + BuildCommit.getBuildCommit());

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					QuakeInjector qs = new QuakeInjector();
					qs.display();
					qs.init();
				}
			});

	}

	private void display() {
		view.display();
		if (getConfig().MainWindowState.exists()) {
			int state = getConfig().MainWindowState.get();
			view.setExtendedState(state);
			System.out.println("Setting window state: " + state);
		}
	}

	private class QuakeInjectorWindowListener extends WindowAdapter
	{
		@Override
		public void windowClosing(WindowEvent e) {
			if (installer.working()) {
				String msg = "There are maps left in the install queue. Wait until they are finished installing?";

				Object[] options = {"Wait",
				                    "Close immediately"};
				int optionDialog =
				    JOptionPane.showOptionDialog(null,
				                                 msg,
				                                 "Maps still installing",
				                                 JOptionPane.YES_NO_OPTION,
				                                 JOptionPane.WARNING_MESSAGE,
				                                 null,
				                                 options,
				                                 options[0]);
				if (optionDialog == 0) {
					view.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
					return;
				}
				else {
					installer.cancelAll();
					view.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				}
			}
			windowClosed(e);
		}
		@Override
		public void windowClosed(WindowEvent e)
		{
			Configuration config = getConfig();
			Rectangle bounds = view.getBounds();
			config.MainWindowPositionX.set((int) bounds.getX());
			config.MainWindowPositionY.set((int) bounds.getY());
			config.MainWindowWidth.set((int) bounds.getWidth());
			config.MainWindowHeight.set((int) bounds.getHeight());
			config.MainWindowState.set(view.getExtendedState());

			try {
				config.write();
			}
			catch (IOException err) {
				savingFailedDialogue(err);
			}
			//System.out.println("Closing Window: " + (int) bounds.getWidth() + (int) bounds.getHeight());


			System.exit(0);
		}

	}

}
