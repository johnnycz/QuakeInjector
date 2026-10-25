package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Find out which packages are installed by looking for their files in the Quake directory.
 * <p>
 * Runs on any thread and doesn't touch shared models: the caller copies the file lists to check
 * on the UI thread and applies the result itself.
 */
public class CheckInstalled implements AsyncTask<List<PackageFileList>> {
	private final List<PackageFileList> fileLists;
	private final File installDirectory;
	private final CancellationToken token;
	private final AsyncProgressListener progress;

	/**
	 * @param fileLists the files each package is supposed to have ({@code Package.getSupposedFileList})
	 * @param installDirectory the Quake directory
	 * @param progress gets 0..100: the share of packages checked so far
	 */
	public CheckInstalled(List<PackageFileList> fileLists,
	                      File installDirectory,
	                      CancellationToken token,
	                      AsyncProgressListener progress) {
		this.fileLists = fileLists;
		this.installDirectory = installDirectory;
		this.token = token;
		this.progress = progress;
	}

	/**
	 * @return the file lists of the packages whose files are all there
	 * @throws CancelledException if the token was cancelled
	 */
	@Override
	public List<PackageFileList> execute() throws CancelledException {
		List<PackageFileList> installed = new ArrayList<>();
		int checked = 0;
		for (PackageFileList list : fileLists) {
			token.throwIfCancelled();
			progress.onProgress(checked++ * 100L / fileLists.size(), 100);

			if (isInstalled(list)) {
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

	private boolean isInstalled(PackageFileList list) {
		List<String> missingFiles = new ArrayList<>();
		for (FileInfo entry : list) {
			token.throwIfCancelled();
			if (list.size() > 7 && missingFiles.size() > 0.2f * list.size()) {
				System.out.println("Too many missing files for " + list.getId() + ", stopping search!");
				break;
			}

			String filename = entry.getName();
			File f;
			try {
				f = new File(installDirectory, filename);
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
				if (!f.isDirectory() && entry.getSize() > 0 && entry.getSize() != f.length()) {
					System.err.println("File size differs for " + f.getName());
					System.out.println("Counting as missing.");
					missingFiles.add(f.getName());
				}
			}
		}
		return missingFiles.isEmpty();
	}
}
