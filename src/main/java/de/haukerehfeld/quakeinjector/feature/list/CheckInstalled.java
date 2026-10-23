package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Find out which packages are installed by looking for their files in the Quake directory.
 * Uses the file lists of the cached package database.
 * <p>
 * Runs on any thread and doesn't touch shared models: the caller passes the ids of the packages it knows,
 * copied on the UI thread, and applies the result itself.
 */
public class CheckInstalled implements AsyncTask<List<PackageFileList>> {
	private final Configuration configuration;
	private final String enginePath;
	private final Set<String> knownPackageIds;
	private final CancellationToken token;
	private final AsyncProgressListener progress;

	/**
	 * @param knownPackageIds only these packages are checked (the ones in the current package list)
	 * @param progress gets 0..100: the share of packages checked so far
	 */
	public CheckInstalled(Configuration configuration,
	                      String enginePath,
	                      Set<String> knownPackageIds,
	                      CancellationToken token,
	                      AsyncProgressListener progress) {
		this.configuration = configuration;
		this.enginePath = enginePath;
		this.knownPackageIds = knownPackageIds;
		this.token = token;
		this.progress = progress;
	}

	/**
	 * @return the file lists of the packages whose files are all there
	 * @throws CancelledException if the token was cancelled
	 */
	@Override
	public List<PackageFileList> execute() throws CancelledException {
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
			token.throwIfCancelled();
			if (!(requirement instanceof Package pkg)) {
				continue;
			}
			PackageFileList list = pkg.getSupposedFileList();
			progress.onProgress(i++ * 100L / packages.size(), 100);

			if (!knownPackageIds.contains(list.getId())) {
				continue;
			}

			String basedir = enginePath + File.separator;
			List<String> missingFiles = new ArrayList<String>();
			for (FileInfo entry: list) {
				token.throwIfCancelled();
				if (list.size() > 7 && missingFiles.size() > 0.2f * list.size()) {
					System.out.println("Too many missing files for " + list.getId()
					                   + ", stopping search!");
					break;
				}

				String filename = entry.getName();
				File f;
				try {
					f = new File(basedir, filename);
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
		progress.onProgress(100, 100);
		return installed;
	}
}
