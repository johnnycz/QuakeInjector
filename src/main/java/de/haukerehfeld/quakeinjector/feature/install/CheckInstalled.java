package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.gui.QuakeInjectorView;
import de.haukerehfeld.quakeinjector.model.*;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.ProgressListener;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.SwingWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.function.Consumer;
import javax.swing.JOptionPane;


public class CheckInstalled extends SwingWorker<List<PackageFileList>, Void>
	implements ProgressListener {

	private final String zipContentsDatabaseUrl;
	private final String enginePath;
	private final RequirementList maps;
	private final QuakeInjectorView mainView;
	private final Consumer<List<PackageFileList>> doneCallback;

	public CheckInstalled(QuakeInjectorView mainView,
	                      String zipContentsDatabaseUrl,
	                      String enginePath,
	                      RequirementList maps,
	                      Consumer<List<PackageFileList>> doneCallback) {
		this.zipContentsDatabaseUrl = zipContentsDatabaseUrl;
		this.enginePath = enginePath;
		this.maps = maps;
		this.mainView = mainView;
		this.doneCallback = doneCallback;
	}

	@Override
	    public List<PackageFileList> doInBackground() throws java.lang.InterruptedException,
	    java.util.concurrent.ExecutionException,
	    java.io.IOException {

		List<PackageFileList> packages = Collections.emptyList();
		{
			//get download stream
			Download d = Download.create(zipContentsDatabaseUrl);
			d.connect();
			final InputStream dl = d.getStream();

			try {
				packages = new InstalledPackageList().read(dl);

				Collections.sort(packages);
			}
			catch (java.io.FileNotFoundException e) {
				System.out.println("Notice: installed maps file doesn't exist yet,"
				                   + " no maps installed? " + e);
			}
			catch (java.io.IOException e) {
				System.err.println("Error: installed maps file couldn't be loaded: " + e);
				e.printStackTrace();
			}
		}
		
		int i = 0;
		List<PackageFileList> installed = new ArrayList<PackageFileList>();
		for (PackageFileList list: packages) {
			publish(i++ * 100 / packages.size());
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
				//System.out.println("Basedir: " + basedir + "; filename: " + filename);
				String file = basedir + filename;
				long supposedCrc = entry.getChecksum();
				File f = new File(file);
				System.out.print("Checking for " + f + "...");
				if (!f.exists()) {
					if (entry.getEssential()) {
						missingFiles.add(file);
						System.out.println("missing!");
					}
				}
				else {
					System.out.println("found!");
					if (!f.isDirectory()) {
						long crc = Utils.getCrc32(new BufferedInputStream(new FileInputStream(f)), null);
						if (supposedCrc != 0 && crc != entry.getChecksum()) {
							System.err.println("Crc differs for file " + file);
							if (entry.getEssential()) {
								System.out.println("Counting as missing.");
								missingFiles.add(file);
							}
						}
						// else {
						// 	System.out.println("Crc matches for " + f + " (" + crc + ")");
						// }
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


