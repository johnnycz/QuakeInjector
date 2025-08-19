package de.haukerehfeld.quakeinjector.utils;

import java.io.File;

public class RelativePath {

	/**
	 * Get relative path of File 'f' with respect to 'home' directory.
	 * <pre>example:
	 * home = /a/b/c
	 * f    = /a/d/e/x.txt
	 * s    = getRelativePath(home,f) = ../../d/e/x.txt
	 * </pre>
	 * <p>Internally it is simply a wrapper of {@link java.nio.file.Path#relativize}
	 */
	public static File getRelativePath(File home, File file) {
		return home.toPath().relativize(file.toPath()).toFile();
	}
}
