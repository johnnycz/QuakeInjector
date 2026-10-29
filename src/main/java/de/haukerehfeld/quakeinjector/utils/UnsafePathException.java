package de.haukerehfeld.quakeinjector.utils;

import java.io.File;
import java.io.IOException;

/**
 * A path from a package or the database would lead outside the directory it belongs in.
 * See {@link SafePath}.
 */
public class UnsafePathException extends IOException {
	private final String path;

	/** @param reason e.g. "Outside of the directory" */
	public UnsafePathException(String path, File baseDirectory, String reason) {
		super(reason + ": '" + path + "' (in " + baseDirectory + ")");
		this.path = path;
	}

	/** The path as it was given. */
	public String getPath() {
		return path;
	}
}
