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

import de.haukerehfeld.quakeinjector.feature.install.*;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseService;
import de.haukerehfeld.quakeinjector.feature.play.EngineStarter;
import de.haukerehfeld.quakeinjector.feature.play.PackageLauncher;
import de.haukerehfeld.quakeinjector.feature.readme.ReadmeService;
import de.haukerehfeld.quakeinjector.gui.*;
import de.haukerehfeld.quakeinjector.gui.Menu;
import de.haukerehfeld.quakeinjector.guimodel.*;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.BuildCommit;
import de.haukerehfeld.quakeinjector.utils.RuntimeExecutionException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class QuakeInjector {

	private final static String installedMapsFileName = "installedMaps.xml";
	private final static File installedMapsFile = new File(installedMapsFileName);
	
	final static File configFile = new File("config.properties");
	private final PackageInstallUninstallService installUninstallService;
	private PackageLauncher packageLauncher;
	private final CommandRouter router;
	private final DialogProvider dialogProvider;
	private final PackageDatabaseService packageDatabaseService;
	private EngineStarter starter;
	private final RequirementList maps;
	private final PackageTableModel maplist;
	private Installer installer;
	private final PackageInteractionViewModel packageInteractionViewModel;
	private final PackageListViewModel packageListViewModel;
	private final InstalledPackages installedMaps = new InstalledPackages();
	private final Configuration config;
	private final RecentPackages recentPackages;

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

		// kept in the settings, which are written when the app closes
		recentPackages = RecentPackages.fromSetting(getConfig().RecentPackages.get());
		recentPackages.addChangeListener(e -> {
			String setting = recentPackages.toSetting();
			getConfig().RecentPackages.set(setting.isEmpty() ? null : setting);
		});


		this.router = new CommandRouter();
		maps = new RequirementList();
		PackageList packages = new PackageList(maps);

		loadTheme();

		// TODO clean this up
		// initialize model, then view model, then view

		var readmeService = new ReadmeService(() -> getConfig().EnginePath.get(), javax.swing.SwingUtilities::invokeLater);
		packageInteractionViewModel = new PackageInteractionViewModel(router, readmeService);
		packageListViewModel = new PackageListViewModel(packageInteractionViewModel);
		packageListViewModel.setMapList(packages);
		maplist = new PackageTableModel(packageListViewModel, packageInteractionViewModel);
		var jobTrackerProvider = new InstallQueueViewModel();
		var configViewModel = new ConfigViewModel(router, getConfig(),
			code -> DarkLafUIThemeOption.getByCodeOrDefault(code).getCode());
		var dialogVm = new DialogViewModel(configViewModel);
		this.dialogProvider = dialogVm;
		this.installUninstallService = new PackageInstallUninstallService(jobTrackerProvider, dialogVm, recentPackages);
		// the installed maps file is read once the first package list is there; installer exists by then
		this.packageDatabaseService = new PackageDatabaseService(getConfig(), maps, jobTrackerProvider, dialogVm,
			this::loadInstalledStatus, javax.swing.SwingUtilities::invokeLater);

		view = new QuakeInjectorView(maplist, packageInteractionViewModel, new PackageInteractionPanelView(packageInteractionViewModel), new InstallQueuePanel(jobTrackerProvider),
			new RecentPackagesViewModel(recentPackages, packages));

		setWindowSize();
		new DialogView(dialogVm, view);

		registerViewListeners();
	}

	private class CommandRouter implements CommandHandler {
		@Override
		public void install(Package selectedPackage) {
			installUninstallService.install(selectedPackage);
		}

		@Override
		public void uninstall(Package selectedPackage) {
			installUninstallService.uninstall(selectedPackage);
		}

		@Override
		public void play(Package pkg, String startmap) {
			packageLauncher.start(pkg, startmap);
		}

		@Override
		public void saveConfig() {
			QuakeInjector.this.applyAndPersistConfig();
		}

		@Override
		public boolean isInstallInProgress(Package aPackage) {
			return installer != null && installer.alreadyQueued(aPackage);
		}

		@Override
		public void openPackageWebpage(Package pkg) {
			try {
				// URLs with spaces in the path need escaping to %20, not +. We can't use built in URLEncoder
				URL url = new URL(config.mapWebpageBaseUrl.get() + pkg.getSha256());
				URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
				BrowserLauncher.openURL(uri.toASCIIString());
			}
			catch (MalformedURLException | URISyntaxException e) {
				// TODO: Emit an error message or something
			}
		}

		@Override
		public URI getScreenshotUri(Package pkg) {
			try {
				// URLs with spaces in the path need escaping to %20, not +. We can't use built in URLEncoder
				URL url = new URL(config.ScreenshotRepositoryPath.get()
						+ pkg.getSha256().substring(0, 2) + "/" + pkg.getSha256() + "/" + pkg.getId() + ".jpg");
				URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
				return uri;
			}
			catch (MalformedURLException | URISyntaxException e) {
				return null;
			}
		}
	}

	private void loadTheme() {
		DarkLafUIThemeOption.getByCodeOrDefault(getConfig().uiTheme.get()).init();
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

		view.addWindowListener(new QuakeInjectorWindowListener());

		addMenuActionListeners();

		view.addShowEngineConfigListener((e) -> showEngineConfig());
	}

	private void addMenuActionListeners() {
		Menu menu = view.getMenu();
		menu.addReparseDatabaseActionListener((e) -> packageDatabaseService.download());

		menu.addCheckInstalledActionListener((e) -> installUninstallService.checkForInstalledMaps());

		menu.addQuitActionListener((e) -> {
			view.setVisible(false);
			view.dispose();
		});

		menu.addEngineActionListener((e) -> showEngineConfig());
	}

	/**
	 * Everything that may be run AFTER the initial window is shown should be run here
	 */
	private void init() {
		// first: once the database is loaded, the installed maps file is read on the installer's disk thread
		installer = new Installer(getConfig().EnginePath, getConfig().DownloadPath,
			javax.swing.SwingUtilities::invokeLater);
		// refresh the Install button whenever an install starts or ends (also after a failure)
		installer.addQueueListener(packageInteractionViewModel);

		installUninstallService.init(installer,
				maps,
				new SaveInstalled(installedMapsFile)
	    );

		// select the package of the last session again once the list is there
		if (getConfig().SelectedPackage.exists()) {
			packageListViewModel.restoreSelection(getConfig().SelectedPackage.get());
		}
		packageDatabaseService.loadCachedThenDownload();

		Configuration.EnginePath enginePath = getConfig().EnginePath;
		boolean workingDirAtExecutable = false;
		File engineExe = new File("");
		Configuration.EngineExecutable engineExecutable = getConfig().EngineExecutable;
		if (engineExecutable.existsOrDefault()) {
			if (engineExecutable.get().isAbsolute()) {
				dialogProvider.showWarning("The Quake Engine path should be set relative to the Quake Directory, not as an absolute path.","Quake Engine invalid");
				engineExe = engineExecutable.get();
			} else {
				engineExe = new File(enginePath.get()
						+ File.separator
						+ engineExecutable);
			}
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

		packageLauncher = new PackageLauncher(this.dialogProvider, starter, recentPackages);

		if (!installer.checkInstallDirectory()) {
			// Engine path not set - show config dialog
			// Database will load asynchronously in the background
			view.enginePathNotSetDialogue();
		}
	}

	/** Runs on the EDT: mark the packages from the installed packages file as installed. */
	private void applyInstalledStatus(List<PackageFileList> installed, Throwable error) {
		if (error == null) {
			maps.setInstalled(installed);
			return;
		}

		maps.notifyChangeListeners();
		Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
			? error.getCause()
			: error;
		if (cause instanceof InstalledPackages.NoInstalledPackagesFileException) {
			// nothing installed yet
			System.err.println(cause.getMessage());
			return;
		}
		String ERROR_MESSAGE = "Reading installed maps failed!";
		JOptionPane.showMessageDialog(view,
			ERROR_MESSAGE + " " + cause.getMessage(),
			ERROR_MESSAGE,
			JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Runs on the EDT the first time the package list was filled. Later replacements keep the installed
	 * state in memory (RequirementList.setRequirements), so the file is only read once.
	 */
	private void loadInstalledStatus() {
		// On the disk thread: it also writes this file, so the read never sees a half-written one
		installedMaps.parse(installedMapsFile, installer.diskExecutor());
		// Don't block the EDT waiting for it: the read may queue behind an install
		installedMaps.loaded().whenCompleteAsync(this::applyInstalledStatus, javax.swing.SwingUtilities::invokeLater);
	}

	private void showEngineConfig() {
		dialogProvider.showEngineConfigWindow();
	}

	private void savingFailedDialogue(IOException e) {
		String msg = "Saving the configuration file failed: " + e.getMessage() + "\n"
		    + "The directory is probably read-only and cannot be set writable automatically (Vista/Win7 bug), try to set write permissions manually." ;
		dialogProvider.showError(msg,"Saving configuration failed!");
	}

	private void applyAndPersistConfig() {
		Configuration c = getConfig();
		var workingDirAtExecutable = c.WorkingDirAtExecutable.get();
		// stored relative to the Quake directory; the engine starter needs the full path
		var engineExecutable = c.EngineExecutable.resolveAgainst(c.EnginePath.get());

		File workingDir;
		if (workingDirAtExecutable) {
			workingDir = engineExecutable.getParentFile();
		}
		else {
			workingDir = c.EnginePath.get();
		}

		setEngineConfig(workingDir, engineExecutable, getConfig().EngineCommandLine);


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

			try {
				c.write();
			} catch (IOException writeException) {
				savingFailedDialogue(writeException);
			}
		}
	}


	/**
	 * @todo 2010-02-09 12:19 hrehfeld    Let this use configuration values to their full extent
	 */
	private void setEngineConfig(File workingDir,
								 File engineExecutable,
	                             Configuration.EngineCommandLine commandline) {
		starter.setWorkingDirectory(workingDir);
		starter.setQuakeApplication(engineExecutable);
		starter.setQuakeCommandline(commandline);
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
			// nothing selected (e.g. the list never loaded): keep the package of the last session
			String selectedId = packageListViewModel.selectedPackageId();
			if (selectedId != null) {
				config.SelectedPackage.set(selectedId);
			}

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
