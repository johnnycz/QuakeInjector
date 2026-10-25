package de.haukerehfeld.quakeinjector.model;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The text files (readmes) of installed packages.
 */
public interface ReadmeSource {
	/**
	 * The package's text files as they are named in the Quake directory (after the extract mapping),
	 * relative to it, most likely readme first. Cheap: looks only at the package's file list.
	 */
	List<String> textFiles(Package pkg);

	/**
	 * Reads a text file in the background.
	 *
	 * @param path relative to the Quake directory, as returned by {@link #textFiles}
	 * @return completes on the UI thread with the text, or exceptionally if the file can't be read
	 */
	CompletableFuture<String> read(String path);
}
