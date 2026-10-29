package de.haukerehfeld.quakeinjector.utils;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Turns a relative path that came from a package (zip entry names, extract mappings, file lists,
 * package ids) into a file inside a base directory, refusing anything that would end up outside of it.
 * <p>
 * Use it wherever such a path meets the disk: a malicious zip or database entry could otherwise
 * write, delete or read files anywhere ({@code ../../x}, {@code C:\x}, {@code \\server\share\x}).
 * <p>
 * The check is on the path text only; symbolic links and junctions inside the base directory are
 * followed. Packages can't create those, so only the user's own links are, which is fine.
 */
public final class SafePath {
	private SafePath() {
	}

	/**
	 * @param baseDirectory the directory the path must stay in, e.g. the Quake directory
	 * @param relativePath a path relative to it, '/' or the platform separator
	 * @return the absolute, normalized file; the base directory itself if the path is empty
	 * @throws UnsafePathException if the path leads outside the base directory or isn't a valid path,
	 *         or there is no base directory
	 */
	public static File resolve(File baseDirectory, String relativePath) throws UnsafePathException {
		if (baseDirectory == null) {
			throw new UnsafePathException(relativePath, null, "No directory");
		}
		if (relativePath == null) {
			throw new UnsafePathException(null, baseDirectory, "No path");
		}
		Path base = baseDirectory.toPath().toAbsolutePath().normalize();
		Path relative;
		try {
			relative = Path.of(relativePath);
		}
		catch (InvalidPathException e) {
			throw new UnsafePathException(relativePath, baseDirectory, "Not a valid path");
		}
		if (relative.isAbsolute() || relative.getRoot() != null) {
			// also "C:x" and "\x" on Windows: relative to some drive or root, not to the base directory
			throw new UnsafePathException(relativePath, baseDirectory, "Not a relative path");
		}
		for (Path name : relative) {
			if (isOnlyDots(name.toString())) {
				// Windows trims trailing dots and spaces, so "..." or ". ." isn't what Java sees
				throw new UnsafePathException(relativePath, baseDirectory, "Invalid name '" + name + "'");
			}
		}
		Path target = base.resolve(relative).normalize();
		if (!target.startsWith(base)) {
			throw new UnsafePathException(relativePath, baseDirectory, "Outside of the directory");
		}
		return target.toFile();
	}

	/** Whether {@link #resolve} accepts the path. */
	public static boolean isInside(File baseDirectory, String relativePath) {
		try {
			resolve(baseDirectory, relativePath);
			return true;
		}
		catch (UnsafePathException e) {
			return false;
		}
	}

	/** Dots and spaces only, but not the "." and ".." that normalizing handles. */
	private static boolean isOnlyDots(String name) {
		if (name.equals(".") || name.equals("..")) {
			return false;
		}
		return !name.isEmpty() && name.chars().allMatch(c -> c == '.' || c == ' ');
	}
}
