package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.Configuration;
import de.haukerehfeld.quakeinjector.gui.QuakeInjectorView;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.utils.ProgressListener;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.SwingWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.function.Consumer;
import javax.swing.JOptionPane;


public class CheckInstalled extends SwingWorker<List<PackageFileList>, Void>
	implements ProgressListener {

	private final Configuration configuration;
	private final String enginePath;
	private final RequirementList maps;
	private final QuakeInjectorView mainView;
	private final Consumer<List<PackageFileList>> doneCallback;

	public CheckInstalled(QuakeInjectorView mainView,
	                      Configuration configuration,
	                      String enginePath,
	                      RequirementList maps,
	                      Consumer<List<PackageFileList>> doneCallback) {
		this.configuration = configuration;
		this.enginePath = enginePath;
		this.maps = maps;
		this.mainView = mainView;
		this.doneCallback = doneCallback;
	}

	@Override
	    public List<PackageFileList> doInBackground() throws java.lang.InterruptedException,
	    java.util.concurrent.ExecutionException,
	    java.io.IOException {

		File localDatabaseFile = configuration.LocalDatabaseFile.get();
		if (!localDatabaseFile.exists() || !localDatabaseFile.isFile() || !localDatabaseFile.canRead()) {
			System.err.println("Cached database file not found");
			return Collections.emptyList();
		}
		List<Requirement> packages;
		try (FileInputStream dl = new FileInputStream(localDatabaseFile)) {
			packages = new PackageDatabaseSolrJsonParser(configuration).parse(dl);

			Collections.sort(packages);
		}
		catch (java.io.FileNotFoundException e) {
			System.out.println("Notice: installed maps file doesn't exist yet,"
			                   + " no maps installed? " + e);
			return Collections.emptyList();
		}
		catch (java.io.IOException e) {
			System.err.println("Error: installed maps file couldn't be loaded: " + e);
			e.printStackTrace();
			return Collections.emptyList();
		}

		int i = 0;
		List<PackageFileList> installed = new ArrayList<PackageFileList>();
		for (Requirement requirement: packages) {
			if (!(requirement instanceof Package pkg)) {
				continue;
			}
			PackageFileList list = pkg.getSupposedFileList();
			publish(i++ * 100L / packages.size());
			Requirement r = maps.get(list.getId());
			String basedir = enginePath + File.separator;
			if (r instanceof UnavailableRequirement) {
				continue;
			}

			List<String> missingFiles = new ArrayList<String>();
			for (FileInfo entry: list) {
				if (isCancelled()) {
					throw new java.util.concurrent.CancellationException();
				}

				if (list.size() > 7 && missingFiles.size() > 0.2f * list.size()) {
					System.out.println("Too many missing files for " + list.getId()
					                   + ", stopping search!");
					break;
				}

				String filename = entry.getName();
				File f;
				try {
					f = configuration.EnginePath.getUnzipFile(pkg, filename);
				} catch (Exception e) {
					missingFiles.add(filename);
					continue;
				}
				System.out.print("Checking for " + filename + "...");
				if (!f.exists()) {
					missingFiles.add(filename);
					System.out.println("missing!");
				}
				else {
					System.out.println("found!");
					if (!f.isDirectory()) {
						if (entry.getSize() > 0 && entry.getSize() != f.length()) {
							System.err.println("File size differs for " + f.getName());
							System.out.println("Counting as missing.");
							missingFiles.add(f.getName());
						}
					}
				}
			}

			if (missingFiles.isEmpty()) {
				System.out.println(list.getId() + " seems to be installed.");
				//if we would allow for missing files in an installed package, we'd need to have custom file lists!
				installed.add(list);
			}
			else {
				System.out.println(list.getId() + " has missing files, not installed.");
			}
		}

		return installed;
	}


	@Override
    public void done() {
		try {
			doneCallback.accept(get());
		}
		catch (java.lang.InterruptedException e) {
			System.err.println("Interrupted: " + e);
			e.printStackTrace();
		}
		catch (java.util.concurrent.ExecutionException e) {
			System.err.println("Exception: " + e);
			e.printStackTrace();
			try {
				throw e.getCause();
			}
			catch (java.net.ConnectException err) {
				String msg = "Downloading file database failed, " + err.getMessage() + "!";
				JOptionPane.showMessageDialog(mainView,
				                              msg,
				                              "Downloading failed!",
				                              JOptionPane.ERROR_MESSAGE);
				
			}
			catch (Throwable err) {
			}
		}
		catch (java.util.concurrent.CancellationException e) {
		}
	}

	public void publish(long progress) {
		if (progress <= 100) {
			setProgress((int) progress);
		}
	}
	
}


