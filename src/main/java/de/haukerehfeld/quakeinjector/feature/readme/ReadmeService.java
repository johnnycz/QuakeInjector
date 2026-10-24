package de.haukerehfeld.quakeinjector.feature.readme;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.ReadmeSource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * "Readme": the text files of installed packages.
 * <p>
 * The files are taken from the package's installed file list, which holds the names after the extract
 * mapping, relative to the Quake directory; without one, from the database's file list, which is
 * mapped the same way. Reading runs on its own background thread (not the installer's disk thread,
 * so it doesn't wait behind an install) and reports on the UI executor.
 */
public class ReadmeService implements ReadmeSource {
	private final Supplier<File> quakeDirectory;
	private final Executor background;
	private final Executor ui;

	public ReadmeService(Supplier<File> quakeDirectory, Executor ui) {
		this(quakeDirectory, Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "quakeinjector-readme");
			t.setDaemon(true);
			return t;
		}), ui);
	}

	public ReadmeService(Supplier<File> quakeDirectory, Executor background, Executor ui) {
		this.quakeDirectory = quakeDirectory;
		this.background = background;
		this.ui = ui;
	}

	@Override
	public List<String> textFiles(Package pkg) {
		PackageFileList files = pkg.getFileList();
		if (files == null || files.isEmpty()) {
			files = pkg.getSupposedFileList();
		}
		if (files == null) {
			return List.of();
		}
		List<String> names = new ArrayList<>(files.size());
		for (FileInfo file : files) {
			names.add(file.getName());
		}
		return ReadmeFiles.order(pkg.getId(), pkg.getStartmaps(), names);
	}

	@Override
	public CompletableFuture<String> read(String path) {
		File file = new File(quakeDirectory.get(), path);
		// completed on the UI thread, so callers' callbacks run there too
		CompletableFuture<String> result = new CompletableFuture<>();
		CompletableFuture
			.supplyAsync(() -> {
				try {
					return ReadmeReader.read(file);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, background)
			.whenCompleteAsync((text, error) -> {
				if (error == null) {
					result.complete(text);
				}
				else {
					result.completeExceptionally(unwrap(error));
				}
			}, ui);
		return result;
	}

	/** The IOException (or other error) behind the CompletionException and UncheckedIOException wrappers. */
	private static Throwable unwrap(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException || error instanceof UncheckedIOException)
				&& error.getCause() != null) {
			error = error.getCause();
		}
		return error;
	}
}
