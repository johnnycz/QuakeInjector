package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.PackageFileList;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Receives the decisions and outcome of one package install.
 * <p>
 * All methods are called on the UI executor given to the {@link Installer}, never on a worker thread,
 * so implementations may show dialogs and change models directly.
 */
public interface InstallListener {
	/**
	 * Some files in the package already exist; decide which may be overwritten.
	 * May block (for example with a modal dialog): the install waits without holding a worker thread.
	 *
	 * @param existingFiles all files of the package, by path relative to the install directory
	 * @return the files to write, or an empty list to cancel the install
	 */
	List<File> chooseOverwrites(Map<String, File> existingFiles);

	/** The install finished and all files were extracted. */
	void succeeded(PackageFileList installedFiles);

	/**
	 * The install stopped early because it failed or was cancelled.
	 * Any files it had written are already deleted.
	 */
	void failed(InstallFailure failure);
}
