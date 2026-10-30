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

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

public class QuakeInjector {

	private final static File installedMapsFile = new File("installedMaps.xml");
	
	final static File configFile = new File("config.properties");
	private final PackageInstallUninstallService installUninstallService;
	private PackageLauncher packageLauncher;
	private final CommandRouter router;
	private final DialogProvider dialogProvider;
	private final PackageDatabaseService packageDatabaseService;
	private EngineStarter engineStarter;
	private final RequirementList maps;
	private final PackageTableModel maplist;
	private Installer installer;
	private final PackageInteractionViewModel packageInteractionViewModel;
	private final PackageListViewModel packageListViewModel;
	private final Configuration config;
	private final RecentPackages recentPackages;
	private final PackageUrls packageUrls;

	private final QuakeInjectorView view;

	public QuakeInjector() {

		// a missing or unreadable file gives the defaults
		config = new Configuration(configFile);
		packageUrls = new PackageUrls(config);
		recentPackages = RecentPackages.boundTo(config.RecentPackages);


		this.router = new CommandRouter();
		maps = new RequirementList();
		PackageList packages = new PackageList(maps);

		loadTheme();

		// TODO clean this up
		// initialize model, then view model, then view

		var readmeService = new ReadmeService(() -> config.EnginePath.get(), javax.swing.SwingUtilities::invokeLater);
		packageInteractionViewModel = new PackageInteractionViewModel(router, readmeService);
		packageListViewModel = new PackageListViewModel(packageInteractionViewModel);
		packageListViewModel.setMapList(packages);
		maplist = new PackageTableModel(packageListViewModel, packageInteractionViewModel);
		var jobTrackerProvider = new InstallQueueViewModel();
		var configViewModel = new ConfigViewModel(router, config,
			code -> DarkLafUIThemeOption.getByCodeOrDefault(code).getCode());
		var dialogVm = new DialogViewModel(configViewModel);
		this.dialogProvider = dialogVm;
		this.installUninstallService = new PackageInstallUninstallService(jobTrackerProvider, dialogVm, recentPackages);
		// the installed maps file is read once the first package list is there; installer exists by then
		this.packageDatabaseService = new PackageDatabaseService(config, maps, jobTrackerProvider, dialogVm,
			installUninstallService::loadInstalledStatus, javax.swing.SwingUtilities::invokeLater);

		view = new QuakeInjectorView(maplist, packageInteractionViewModel, new PackageInteractionPanelView(packageInteractionViewModel), new InstallQueuePanel(jobTrackerProvider),
			new RecentPackagesViewModel(recentPackages, packages),
			new PackageHistoryViewModel(packageInteractionViewModel, packages));
		packageInteractionViewModel.setDescriptionLinkHandler(new DescriptionLinkHandler(dialogVm,
			packageListViewModel::packageIdOfSha256, view::showPackage, BrowserLauncher::openURL));

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
				URI uri = packageUrls.webpage(pkg);
				if (uri != null) {
					BrowserLauncher.openURL(uri.toASCIIString());
				}
			}
			catch (URISyntaxException e) {
				dialogProvider.showError("The address of the package's web page isn't valid: " + e.getMessage(),
					"Invalid web page address");
			}
		}

		@Override
		public URI getScreenshotUri(Package pkg) {
			try {
				return packageUrls.screenshot(pkg);
			}
			catch (URISyntaxException e) {
				System.err.println("Invalid screenshot address: " + e.getMessage());
				return null;
			}
		}
	}

	private void loadTheme() {
		DarkLafUIThemeOption.getByCodeOrDefault(config.uiTheme.get()).init();
	}

	/**
	 * Try setting the saved window size and position
	 */
	private void setWindowSize() {
		Configuration c = config;

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

		// downloads the package database again
		view.addRefreshListener((e) -> packageDatabaseService.download());
	}

	private void addMenuActionListeners() {
		Menu menu = view.getMenu();
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
		installer = new Installer(config.EnginePath, config.DownloadPath,
			javax.swing.SwingUtilities::invokeLater);
		// refresh the Install button whenever an install starts or ends (also after a failure)
		installer.addQueueListener(packageInteractionViewModel);

		installUninstallService.init(installer,
				maps,
				new SaveInstalled(installedMapsFile)
	    );

		// select the package of the last session again once the list is there
		if (config.SelectedPackage.exists()) {
			packageListViewModel.restoreSelection(config.SelectedPackage.get());
		}
		packageDatabaseService.loadCachedThenDownload();

		engineStarter = EngineStarter.fromConfig(config);
		packageLauncher = new PackageLauncher(this.dialogProvider, engineStarter, recentPackages);

		if (!installer.checkInstallDirectory()) {
			// Quake directory not set: offer the settings dialog
			// Database will load asynchronously in the background
			dialogProvider.askAndshowEngineConfigWindow();
		}
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
		engineStarter.applyConfig(config);
		saveConfig();
	}

	private void saveConfig() {
		try {
			config.writeOrMakeWritable();
		}
		catch (IOException e) {
			savingFailedDialogue(e);
		}
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
		if (config.MainWindowState.exists()) {
			int state = config.MainWindowState.get();
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

			saveConfig();
			//System.out.println("Closing Window: " + (int) bounds.getWidth() + (int) bounds.getHeight());


			System.exit(0);
		}

	}

}
