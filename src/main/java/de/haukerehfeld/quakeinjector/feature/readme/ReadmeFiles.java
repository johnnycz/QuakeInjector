package de.haukerehfeld.quakeinjector.feature.readme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Picks the text files out of a package's file list and puts the most likely readme first.
 * <p>
 * The file names are the ones in the Quake directory, so a readme the extract mapping renamed (for
 * example {@code readme.txt} to {@code mapname_readme.txt}) is found under its new name.
 * <p>
 * Order, from what the packages in the database do: {@code <package id>.txt}, then
 * {@code <map name>.txt}, then names containing "readme", then other text files, and licenses and
 * change logs last. Within the same rank, files higher up in the directory tree come first.
 */
public final class ReadmeFiles {
	private static final Pattern NOT_A_README =
		Pattern.compile("licen[cs]e|gpl|copying|changelog|changes|history");

	private ReadmeFiles() { }

	/**
	 * @param packageId the package id, which is the zip name without extension
	 * @param startmaps the package's start maps; the map names in the file list count too
	 * @param fileNames the package's files, relative to the Quake directory
	 * @return the text files, most likely readme first
	 */
	public static List<String> order(String packageId, Collection<String> startmaps, Collection<String> fileNames) {
		Set<String> mapNames = new HashSet<>();
		for (String map : startmaps) {
			mapNames.add(map.toLowerCase(Locale.ROOT));
		}
		List<String> textFiles = new ArrayList<>();
		for (String name : fileNames) {
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.endsWith(".bsp")) {
				mapNames.add(stem(lower));
			}
			else if (lower.endsWith(".txt")) {
				textFiles.add(name);
			}
		}

		String id = packageId.toLowerCase(Locale.ROOT);
		textFiles.sort(Comparator
			.comparingInt((String name) -> rank(stem(name.toLowerCase(Locale.ROOT)), id, mapNames))
			.thenComparingInt(ReadmeFiles::depth)
			.thenComparing(name -> name.toLowerCase(Locale.ROOT)));
		return textFiles;
	}

	private static int rank(String stem, String id, Set<String> mapNames) {
		if (stem.equals(id)) {
			return 0;
		}
		if (mapNames.contains(stem)) {
			return 1;
		}
		if (stem.contains("readme") || stem.contains("read_me")) {
			return 2;
		}
		if (NOT_A_README.matcher(stem).find()) {
			return 4;
		}
		return 3;
	}

	/** The file name without directory and extension; file lists may use either separator. */
	private static String stem(String name) {
		String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		int dot = base.lastIndexOf('.');
		return dot > 0 ? base.substring(0, dot) : base;
	}

	private static int depth(String name) {
		return (int) name.chars().filter(c -> c == '/' || c == '\\').count();
	}
}
