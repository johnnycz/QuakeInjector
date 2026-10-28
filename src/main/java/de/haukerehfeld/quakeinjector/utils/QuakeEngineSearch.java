package de.haukerehfeld.quakeinjector.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.haukerehfeld.quakeinjector.utils.QuakeEngineDetector.EngineType;

/**
 * Finds the exe files in a Quake directory and its direct subfolders (not deeper), and
 * classifies them with {@link QuakeEngineDetector}. Reads every exe, so call it off the UI thread.
 */
public final class QuakeEngineSearch {

	/** An exe file and what kind of engine it is */
	public static final class Result {
		private final File file;
		private final EngineType type;

		public Result(File file, EngineType type) {
			this.file = file;
			this.type = type;
		}

		public File getFile() {
			return file;
		}

		public EngineType getType() {
			return type;
		}
	}

	/** Called after each file is classified */
	public interface Progress {
		void checked(int done, int total);
	}

	/** Modern engines first, then enhanced, legacy, and non-engines last; by path within each */
	public static final Comparator<Result> ORDER = Comparator
		.comparingInt((Result r) -> rank(r.getType()))
		.thenComparing(r -> r.getFile().getPath().toLowerCase(Locale.ROOT));

	private QuakeEngineSearch() {
	}

	/**
	 * Classify the exe files in quakeDir and its direct subfolders.
	 *
	 * @return the results, in {@link #ORDER}
	 * @throws CancelledException when the token gets cancelled
	 */
	public static List<Result> search(File quakeDir, CancellationToken token, Progress progress) {
		List<File> exes = findExecutables(quakeDir);
		List<Result> results = new ArrayList<>(exes.size());
		for (File exe : exes) {
			token.throwIfCancelled();
			results.add(new Result(exe, QuakeEngineDetector.classify(exe)));
			progress.checked(results.size(), exes.size());
		}
		results.sort(ORDER);
		return results;
	}

	/** The exe files directly in dir and in its direct subfolders */
	public static List<File> findExecutables(File dir) {
		List<File> exes = new ArrayList<>();
		for (File f : listFiles(dir)) {
			if (f.isDirectory()) {
				for (File sub : listFiles(f)) {
					if (isExe(sub)) {
						exes.add(sub);
					}
				}
			}
			else if (isExe(f)) {
				exes.add(f);
			}
		}
		return exes;
	}

	private static List<File> listFiles(File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			return List.of();
		}
		Arrays.sort(files);
		return Arrays.asList(files);
	}

	private static boolean isExe(File f) {
		return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".exe");
	}

	private static int rank(EngineType type) {
		switch (type) {
		case MODERN: return 0;
		case ENHANCED: return 1;
		case LEGACY: return 2;
		default: return 3;
		}
	}
}
